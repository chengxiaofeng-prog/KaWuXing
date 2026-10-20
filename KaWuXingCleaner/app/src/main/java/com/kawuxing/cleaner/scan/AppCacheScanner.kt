package com.kawuxing.cleaner.scan

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.kawuxing.cleaner.model.AppCacheEntry

/**
 * 各应用缓存统计。
 *
 * 注意能力边界：Android 11+ 之后，第三方应用**不能**直接删除别的应用缓存，
 * 所以这里只负责「准确统计 + 按大小排序」，点进去跳系统应用详情页清除。
 * 这一点比虚标「一键清掉 3GB」诚实。
 */
class AppCacheScanner(private val context: Context) {

    fun scan(limit: Int = 60): List<AppCacheEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val sm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            return emptyList()
        }

        val entries = ArrayList<AppCacheEntry>()
        for (info in packages) {
            val ai = info.applicationInfo ?: continue
            try {
                val stats = sm.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    info.packageName,
                    Process.myUserHandle()
                )
                val cache = stats.cacheBytes
                if (cache <= 0L) continue
                val label = try {
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    info.packageName
                }
                entries.add(
                    AppCacheEntry(
                        packageName = info.packageName,
                        label = label,
                        cacheBytes = cache,
                        dataBytes = stats.dataBytes,
                        system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                )
            } catch (e: Exception) {
                // 系统应用或缺少授权时查询会抛异常，直接跳过
            }
        }
        entries.sortByDescending { it.cacheBytes }
        return if (entries.size > limit) entries.subList(0, limit).toList() else entries
    }
}
