package com.kawuxing.cleaner.scan

import com.kawuxing.cleaner.model.MemState
import java.io.File

/** 直接从 /proc/meminfo 读内存，不依赖任何需要额外权限的 API。 */
object MemoryReader {

    fun read(): MemState {
        val map = HashMap<String, Long>()
        try {
            File("/proc/meminfo").forEachLine { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val key = line.substring(0, idx).trim()
                    val rest = line.substring(idx + 1).trim()
                    val num = rest.split(" ").firstOrNull()?.toLongOrNull() ?: 0L
                    map[key] = num * 1024L
                }
            }
        } catch (e: Exception) {
            // 某些机型内核限制读取，读不到就返回 0，界面会显示「—」
        }
        return MemState(
            total = map["MemTotal"] ?: 0L,
            avail = map["MemAvailable"] ?: (map["MemFree"] ?: 0L),
            cached = map["Cached"] ?: 0L,
            swapTotal = map["SwapTotal"] ?: 0L,
            swapFree = map["SwapFree"] ?: 0L
        )
    }
}
