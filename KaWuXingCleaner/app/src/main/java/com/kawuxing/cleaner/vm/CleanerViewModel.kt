package com.kawuxing.cleaner.vm

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kawuxing.cleaner.clean.JunkCleaner
import com.kawuxing.cleaner.core.Perm
import com.kawuxing.cleaner.data.Prefs
import com.kawuxing.cleaner.model.AppCacheEntry
import com.kawuxing.cleaner.model.CleanOutcome
import com.kawuxing.cleaner.model.JunkCategory
import com.kawuxing.cleaner.model.MemState
import com.kawuxing.cleaner.model.ScanResult
import com.kawuxing.cleaner.scan.AppCacheScanner
import com.kawuxing.cleaner.scan.JunkScanner
import com.kawuxing.cleaner.scan.MemoryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Phase { IDLE, SCANNING, RESULT, CLEANING, DONE }

class CleanerViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val scanner = JunkScanner(app)
    private val cleaner = JunkCleaner()
    private val appCacheScanner = AppCacheScanner(app)
    private var scanJob: Job? = null

    @Volatile
    private var scanCancelled = false

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var progress by mutableStateOf(0f)
        private set
    var progressText by mutableStateOf("")
        private set
    var result by mutableStateOf<ScanResult?>(null)
        private set
    var selected by mutableStateOf<Set<JunkCategory>>(emptySet())
        private set
    var outcome by mutableStateOf<CleanOutcome?>(null)
        private set

    var appCaches by mutableStateOf<List<AppCacheEntry>>(emptyList())
        private set
    var appCacheLoading by mutableStateOf(false)
        private set
    var mem by mutableStateOf(MemState())
        private set

    var storageTotal by mutableStateOf(0L)
        private set
    var storageFree by mutableStateOf(0L)
        private set

    var totalFreed by mutableStateOf(prefs.totalFreed)
        private set
    var lastCleanAt by mutableStateOf(prefs.lastCleanAt)
        private set
    var scanCount by mutableStateOf(prefs.scanCount)
        private set
    var whitelist by mutableStateOf(prefs.whitelist())
        private set

    var hasAllFiles by mutableStateOf(false)
        private set
    var hasUsage by mutableStateOf(false)
        private set

    init {
        refreshPerms()
        refreshStorage()
        mem = MemoryReader.read()
    }

    // ---------------- 权限 / 基础状态 ----------------

    fun refreshPerms() {
        val ctx = getApplication<Application>()
        hasAllFiles = Perm.allFiles(ctx)
        hasUsage = Perm.usageAccess(ctx)
    }

    fun refreshStorage() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            storageTotal = stat.blockCountLong * stat.blockSizeLong
            storageFree = stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            storageTotal = 0L
            storageFree = 0L
        }
    }

    fun refreshMemory() {
        mem = MemoryReader.read()
    }

    val storageUsedPercent: Float
        get() = if (storageTotal <= 0L) 0f else 1f - storageFree.toFloat() / storageTotal.toFloat()

    val selectedBytes: Long
        get() {
            val r = result ?: return 0L
            var sum = 0L
            for (g in r.groups) {
                if (selected.contains(g.category)) sum += g.totalBytes
            }
            return sum
        }

    val selectedCount: Int
        get() {
            val r = result ?: return 0
            var sum = 0
            for (g in r.groups) {
                if (selected.contains(g.category)) sum += g.totalCount
            }
            return sum
        }

    // ---------------- 扫描 ----------------

    fun startScan() {
        if (phase == Phase.SCANNING || phase == Phase.CLEANING) return
        refreshPerms()
        refreshStorage()
        outcome = null
        result = null
        selected = emptySet()
        phase = Phase.SCANNING
        progress = 0f
        progressText = "正在遍历存储…"
        scanCancelled = false

        scanJob = viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                scanner.scan(
                    prefs = prefs,
                    onProgress = { p ->
                        progressText = p.currentPath
                    },
                    isActive = { !scanCancelled }
                )
            }
            result = res
            selected = res.groups.filter { it.category.defaultOn }.map { it.category }.toSet()
            scanCount = scanCount + 1
            prefs.scanCount = scanCount
            phase = Phase.RESULT
            progress = 1f
            progressText = ""
            refreshStorage()
        }
    }

    fun cancelScan() {
        scanCancelled = true
        scanJob?.cancel()
        scanJob = null
        phase = if (result == null) Phase.IDLE else Phase.RESULT
        progress = 0f
        progressText = ""
    }

    fun reset() {
        scanCancelled = true
        scanJob?.cancel()
        scanJob = null
        phase = Phase.IDLE
        result = null
        outcome = null
        selected = emptySet()
        progress = 0f
        progressText = ""
    }

    // ---------------- 清理 ----------------

    fun toggle(category: JunkCategory) {
        val cur = selected.toMutableSet()
        if (!cur.add(category)) cur.remove(category)
        selected = cur
    }

    fun startClean() {
        val res = result ?: return
        if (selected.isEmpty()) return
        if (phase == Phase.CLEANING) return
        phase = Phase.CLEANING
        progress = 0f
        progressText = "正在清理…"

        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                cleaner.clean(res, selected, whitelist) { done, total, path ->
                    progress = if (total <= 0) 0f else done.toFloat() / total.toFloat()
                    progressText = path
                }
            }
            outcome = out
            totalFreed = totalFreed + out.freedBytes
            prefs.totalFreed = totalFreed
            prefs.lastFreed = out.freedBytes
            lastCleanAt = System.currentTimeMillis()
            prefs.lastCleanAt = lastCleanAt
            phase = Phase.DONE
            progress = 1f
            refreshStorage()
        }
    }

    // ---------------- 应用缓存 ----------------

    fun loadAppCaches() {
        if (appCacheLoading) return
        refreshPerms()
        appCacheLoading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { appCacheScanner.scan() }
            appCaches = list
            appCacheLoading = false
        }
    }

    /** 只清自己的缓存（别的应用系统不允许第三方删除） */
    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val dirs = ArrayList<File>()
        dirs.add(ctx.cacheDir)
        dirs.add(ctx.codeCacheDir)
        ctx.externalCacheDirs?.forEach { dirs.add(it) }
        var freed = 0L
        for (d in dirs) {
            freed += dirSize(d)
            d.listFiles()?.forEach { deleteRecursive(it) }
        }
        freed
    }

    private fun dirSize(dir: File): Long {
        var sum = 0L
        var guard = 0
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty() && guard < 20_000) {
            val d = stack.removeFirst()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                guard++
                if (k.isDirectory) stack.add(k) else sum += k.length()
            }
        }
        return sum
    }

    private fun deleteRecursive(f: File) {
        if (f.isDirectory) {
            f.listFiles()?.forEach { deleteRecursive(it) }
        }
        f.delete()
    }

    // ---------------- 白名单 ----------------

    fun addWhitelist(keyword: String) {
        prefs.addWhite(keyword)
        whitelist = prefs.whitelist()
    }

    fun removeWhitelist(keyword: String) {
        prefs.removeWhite(keyword)
        whitelist = prefs.whitelist()
    }
}
