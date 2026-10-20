package com.kawuxing.cleaner.model

/** 垃圾类别。defaultOn=false 的项默认不勾选，必须用户自己确认。 */
enum class JunkCategory(
    val title: String,
    val desc: String,
    val defaultOn: Boolean
) {
    APP_CACHE("应用缓存", "图片、视频、网页等可再生缓存", true),
    THUMBNAIL("缩略图缓存", "相册 / 文件管理器生成的缩略图", true),
    LOG("日志与临时文件", "log / tmp / bak / dump 等调试残留", true),
    APK("安装包残留", "安装完成后遗留的 apk 文件", true),
    AD("广告缓存", "广告 SDK 留下的缓存目录", true),
    RESIDUE("卸载残留", "已卸载应用遗留的数据目录", true),
    EMPTY_DIR("空文件夹", "不再存放任何文件的空目录", true),
    BIG_FILE("大文件", "超过 50MB 的文件，需逐个确认", false)
}

data class JunkItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean
)

data class JunkGroup(
    val category: JunkCategory,
    val items: List<JunkItem>,
    val totalBytes: Long,
    val totalCount: Int
) {
    /** 列出的条数少于实际条数时为 true（超大目录只保留前 N 条做展示与清理） */
    val partiallyListed: Boolean get() = totalCount > items.size
}

data class FolderSize(val path: String, val size: Long)

data class ScanResult(
    val groups: List<JunkGroup>,
    val typeBytes: Map<String, Long>,
    val topFolders: List<FolderSize>,
    val bigFiles: List<JunkItem>,
    val scannedFiles: Int,
    val elapsedMs: Long,
    val storageTotal: Long,
    val storageFree: Long,
    val truncated: Boolean
) {
    val junkTotal: Long
        get() {
            var sum = 0L
            for (g in groups) sum += g.totalBytes
            return sum
        }

    fun group(category: JunkCategory): JunkGroup? = groups.firstOrNull { it.category == category }
}

data class CleanOutcome(
    val freedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
    val perCategory: Map<JunkCategory, Long>
)

data class AppCacheEntry(
    val packageName: String,
    val label: String,
    val cacheBytes: Long,
    val dataBytes: Long,
    val system: Boolean
)

data class MemState(
    val total: Long = 0L,
    val avail: Long = 0L,
    val cached: Long = 0L,
    val swapTotal: Long = 0L,
    val swapFree: Long = 0L
) {
    val used: Long get() = if (total > avail) total - avail else 0L
    val usedPercent: Float get() = if (total <= 0L) 0f else used.toFloat() / total.toFloat()
}
