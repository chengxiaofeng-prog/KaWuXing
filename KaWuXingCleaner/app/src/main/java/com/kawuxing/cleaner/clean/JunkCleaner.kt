package com.kawuxing.cleaner.clean

import android.os.Environment
import com.kawuxing.cleaner.model.CleanOutcome
import com.kawuxing.cleaner.model.JunkCategory
import com.kawuxing.cleaner.model.ScanResult
import java.io.File

/**
 * 真正执行删除的地方。三条硬约束：
 * 1. 只删共享存储（/storage/emulated/0）下的路径，绝不跨出这一层；
 * 2. 命明白名单的路径一律跳过（扫描阶段跳过，这里再兜一层）；
 * 3. 删除按「实际删掉的字节数」记账，不按扫描预估值，避免虚报释放量。
 */
class JunkCleaner {

    fun clean(
        result: ScanResult,
        categories: Set<JunkCategory>,
        whitelist: Set<String> = emptySet(),
        onProgress: (done: Int, total: Int, path: String) -> Unit
    ): CleanOutcome {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val white = whitelist.map { it.lowercase() }.filter { it.isNotEmpty() }
        val targets = result.groups.filter { categories.contains(it.category) }
        val total = targets.sumOf { it.totalCount }

        var done = 0
        var freed = 0L
        var deleted = 0
        var failed = 0
        val per = HashMap<JunkCategory, Long>()

        for (g in targets) {
            var groupFreed = 0L
            for (item in g.items) {
                val f = File(item.path)
                if (!f.exists()) {
                    done++
                    continue
                }
                if (!f.absolutePath.startsWith(rootPath)) {
                    done++
                    failed++
                    continue
                }
                val lower = f.absolutePath.lowercase()
                var blocked = false
                for (k in white) {
                    if (lower.contains(k)) {
                        blocked = true
                        break
                    }
                }
                if (blocked) {
                    done++
                    continue
                }

                val before = sizeOf(f)
                val ok = if (f.isDirectory) deleteDir(f) else f.delete()
                if (ok) {
                    freed += before
                    groupFreed += before
                    deleted++
                } else {
                    failed++
                }
                done++
                if (done % 25 == 0) onProgress(done, total, item.path)
            }
            per[g.category] = groupFreed
        }

        // 文件删完之后，目录结构可能才变空，这里补一刀
        val emptyGroup = result.groups.firstOrNull { it.category == JunkCategory.EMPTY_DIR }
        if (emptyGroup != null && categories.contains(JunkCategory.EMPTY_DIR)) {
            for (item in emptyGroup.items) {
                val f = File(item.path)
                if (f.isDirectory && f.list()?.isEmpty() == true && f.delete()) {
                    deleted++
                }
            }
        }

        onProgress(total, total, "")
        return CleanOutcome(freed, deleted, failed, per)
    }

    private fun sizeOf(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        var sum = 0L
        var guard = 0
        val stack = ArrayDeque<File>()
        stack.add(f)
        while (stack.isNotEmpty() && guard < 200_000) {
            val d = stack.removeFirst()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                guard++
                if (k.isDirectory) stack.add(k) else sum += k.length()
            }
        }
        return sum
    }

    private fun deleteDir(dir: File): Boolean {
        val kids = dir.listFiles() ?: return dir.delete()
        for (k in kids) {
            if (k.isDirectory) deleteDir(k) else k.delete()
        }
        return dir.delete()
    }
}
