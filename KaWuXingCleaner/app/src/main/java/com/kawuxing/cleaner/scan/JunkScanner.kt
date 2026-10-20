package com.kawuxing.cleaner.scan

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.kawuxing.cleaner.data.Prefs
import com.kawuxing.cleaner.model.FolderSize
import com.kawuxing.cleaner.model.JunkCategory
import com.kawuxing.cleaner.model.JunkGroup
import com.kawuxing.cleaner.model.JunkItem
import com.kawuxing.cleaner.model.ScanResult
import java.io.File
import java.util.ArrayDeque

/**
 * 共享存储垃圾扫描器。
 *
 * 设计要点：
 * 1. 单次遍历（BFS），避免对每个目录重复递归求大小；
 * 2. 遇到「缓存类目录」整体记账后不再下钻，防止同一个文件被重复统计；
 * 3. 全程有文件数上限与时间预算，超过就截断返回（truncated=true），不做无上限遍历；
 * 4. 「大文件」默认不勾选，只展示路径与修改时间，由用户自己决定。
 */
class JunkScanner(private val context: Context) {

    data class Progress(val scannedFiles: Int, val currentPath: String, val junkBytes: Long)

    companion object {
        const val MAX_FILES = 250_000
        const val MAX_ITEMS_PER_GROUP = 3000
        const val TIME_BUDGET_MS = 90_000L
        const val BIG_FILE_THRESHOLD = 50L * 1024L * 1024L
    }

    private val cacheDirNames = setOf(
        "cache", ".cache", "caches", "webviewcache", "webview_cache",
        "image_cache", "imagecache", "imgcache", "httpcache", "glide_cache",
        "okhttp", "picasso-cache", "volley"
    )
    private val thumbnailNames = setOf(".thumbnails", "thumbnails", ".thumbnail", "thumbnail")
    private val adDirNames = setOf(
        ".adcache", "adcache", "ad_cache", "adcache2", ".ads", "advertcache", "adcache_dir"
    )
    private val logExts = setOf("log", "tmp", "temp", "bak", "dump", "trace", "cch", "old")
    private val apkExts = setOf("apk", "apks", "xapk", "aab")
    private val videoExts = setOf("mp4", "mkv", "avi", "mov", "3gp", "flv", "ts", "webm", "wmv", "rmvb", "m4v")
    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "dng", "svg")
    private val audioExts = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "ape", "amr", "wma")
    private val docExts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv")
    private val zipExts = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "iso", "xz")

    fun scan(
        prefs: Prefs,
        onProgress: (Progress) -> Unit,
        isActive: () -> Boolean
    ): ScanResult {
        val started = System.currentTimeMillis()
        val root = Environment.getExternalStorageDirectory()
        val whitelist = prefs.whitelist().map { it.lowercase() }.filter { it.isNotEmpty() }

        val bucket = LinkedHashMap<JunkCategory, MutableList<JunkItem>>()
        for (c in JunkCategory.values()) bucket[c] = ArrayList()
        val totals = HashMap<JunkCategory, Long>()
        val counts = HashMap<JunkCategory, Int>()

        fun record(cat: JunkCategory, item: JunkItem) {
            val list = bucket[cat]!!
            if (list.size < MAX_ITEMS_PER_GROUP) list.add(item)
            totals[cat] = (totals[cat] ?: 0L) + item.size
            counts[cat] = (counts[cat] ?: 0) + 1
        }

        val typeBytes = HashMap<String, Long>()
        val folderBytes = HashMap<String, Long>()
        val bigFiles = ArrayList<JunkItem>()
        val installed = installedPackages()

        var scanned = 0
        var truncated = false
        var lastReport = 0
        val deadline = started + TIME_BUDGET_MS
        val queue = ArrayDeque<File>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            if (!isActive()) {
                truncated = true
                break
            }
            if (System.currentTimeMillis() > deadline || scanned > MAX_FILES) {
                truncated = true
                break
            }
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue

            if (children.isEmpty()) {
                if (dir != root && !isProtected(dir, whitelist)) {
                    record(
                        JunkCategory.EMPTY_DIR,
                        JunkItem(dir.absolutePath, dir.name, 0L, dir.lastModified(), true)
                    )
                }
                continue
            }

            for (child in children) {
                val name = child.name
                val lower = name.lowercase()

                if (child.isDirectory) {
                    if (isProtected(child, whitelist)) continue
                    when {
                        cacheDirNames.contains(lower) -> {
                            val measured = measure(child)
                            record(
                                JunkCategory.APP_CACHE,
                                JunkItem(child.absolutePath, name, measured.first, child.lastModified(), true)
                            )
                            addFolderBytes(folderBytes, child, root, measured.first)
                        }
                        thumbnailNames.contains(lower) -> {
                            val measured = measure(child)
                            record(
                                JunkCategory.THUMBNAIL,
                                JunkItem(child.absolutePath, name, measured.first, child.lastModified(), true)
                            )
                            addFolderBytes(folderBytes, child, root, measured.first)
                        }
                        adDirNames.contains(lower) -> {
                            val measured = measure(child)
                            record(
                                JunkCategory.AD,
                                JunkItem(child.absolutePath, name, measured.first, child.lastModified(), true)
                            )
                            addFolderBytes(folderBytes, child, root, measured.first)
                        }
                        isResidue(dir, name, installed) -> {
                            val measured = measure(child)
                            record(
                                JunkCategory.RESIDUE,
                                JunkItem(child.absolutePath, name, measured.first, child.lastModified(), true)
                            )
                            addFolderBytes(folderBytes, child, root, measured.first)
                        }
                        else -> queue.add(child)
                    }
                    continue
                }

                val len = child.length()
                val ext = lower.substringAfterLast('.', "")
                scanned++

                if (len > 0L) {
                    val type = typeOf(ext)
                    typeBytes[type] = (typeBytes[type] ?: 0L) + len
                    addFolderBytes(folderBytes, child, root, len)
                }

                if (len >= BIG_FILE_THRESHOLD) {
                    if (bigFiles.size < MAX_ITEMS_PER_GROUP) {
                        bigFiles.add(JunkItem(child.absolutePath, name, len, child.lastModified(), false))
                    }
                }

                val item = JunkItem(child.absolutePath, name, len, child.lastModified(), false)
                when {
                    apkExts.contains(ext) -> record(JunkCategory.APK, item)
                    logExts.contains(ext) || lower.startsWith("~") || lower.startsWith("tmp_") ->
                        record(JunkCategory.LOG, item)
                }
            }

            if (scanned - lastReport >= 400) {
                lastReport = scanned
                var junk = 0L
                for (v in totals.values) junk += v
                onProgress(Progress(scanned, dir.absolutePath, junk))
            }
        }

        for (f in bigFiles.sortedByDescending { it.size }) {
            record(JunkCategory.BIG_FILE, f)
        }

        val groups = ArrayList<JunkGroup>()
        for (c in JunkCategory.values()) {
            val count = counts[c] ?: 0
            if (count > 0) {
                val items = (bucket[c] ?: emptyList<JunkItem>()).sortedByDescending { it.size }
                groups.add(JunkGroup(c, items, totals[c] ?: 0L, count))
            }
        }

        val stat = StatFs(root.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong

        val topFolders = folderBytes.entries
            .filter { it.value > 1024L * 1024L }
            .sortedByDescending { it.value }
            .take(8)
            .map { FolderSize(it.key, it.value) }

        return ScanResult(
            groups = groups,
            typeBytes = typeBytes,
            topFolders = topFolders,
            bigFiles = bigFiles.sortedByDescending { it.size },
            scannedFiles = scanned,
            elapsedMs = System.currentTimeMillis() - started,
            storageTotal = total,
            storageFree = free,
            truncated = truncated
        )
    }

    /** 统计目录体积（字节）与文件数 */
    private fun measure(dir: File): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        var guard = 0
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty() && guard < 120_000) {
            val d = stack.removeFirst()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                guard++
                if (k.isDirectory) {
                    stack.add(k)
                } else {
                    bytes += k.length()
                    files++
                }
            }
        }
        return bytes to files
    }

    private fun addFolderBytes(
        map: HashMap<String, Long>,
        file: File,
        root: File,
        bytes: Long
    ) {
        if (bytes <= 0L) return
        val rootPath = root.absolutePath
        val path = file.absolutePath
        if (!path.startsWith(rootPath)) return
        val rest = path.substring(rootPath.length).trimStart('/')
        if (rest.isEmpty()) return
        val first = rest.substringBefore('/')
        val key = rootPath + "/" + first
        map[key] = (map[key] ?: 0L) + bytes
    }

    private fun isProtected(file: File, whitelist: List<String>): Boolean {
        if (whitelist.isEmpty()) return false
        val path = file.absolutePath.lowercase()
        for (k in whitelist) {
            if (path.contains(k)) return true
        }
        return false
    }

    /** /Android/data/<pkg> 或 /Android/obb/<pkg> 里，包名对应的应用已不存在 → 卸载残留 */
    private fun isResidue(parent: File, name: String, installed: Set<String>): Boolean {
        if (installed.isEmpty()) return false
        val p = parent.absolutePath
        val inData = p.endsWith("/Android/data") || p.endsWith("/Android/obb")
        if (!inData) return false
        if (!name.contains('.')) return false
        return !installed.contains(name)
    }

    private fun installedPackages(): Set<String> {
        return try {
            context.packageManager.getInstalledPackages(0)
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun typeOf(ext: String): String = when {
        videoExts.contains(ext) -> "视频"
        imageExts.contains(ext) -> "图片"
        audioExts.contains(ext) -> "音频"
        docExts.contains(ext) -> "文档"
        apkExts.contains(ext) -> "安装包"
        zipExts.contains(ext) -> "压缩包"
        else -> "其他"
    }
}
