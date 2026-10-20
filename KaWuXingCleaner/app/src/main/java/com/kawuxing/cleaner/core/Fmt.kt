package com.kawuxing.cleaner.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 统一的体积 / 时间格式化，避免每个界面各写一份。 */
object Fmt {

    private val units = arrayOf("B", "KB", "MB", "GB", "TB")

    fun size(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024.0 && i < units.size - 1) {
            v /= 1024.0
            i++
        }
        return if (i == 0) "$bytes B" else String.format(Locale.US, "%.2f %s", v, units[i])
    }

    /** 卡片里的紧凑写法：1.2G / 846M */
    fun sizeShort(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1fG", bytes.toDouble() / 1073741824.0)
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.0fM", bytes.toDouble() / 1048576.0)
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.0fK", bytes.toDouble() / 1024.0)
        }
        return "${bytes}B"
    }

    fun percent(p: Float): String = String.format(Locale.US, "%d%%", (p.coerceIn(0f, 1f) * 100).toInt())

    fun time(ts: Long): String =
        if (ts <= 0L) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ts))

    fun duration(ms: Long): String {
        val s = ms / 1000.0
        return if (s < 60) String.format(Locale.US, "%.1f 秒", s) else String.format(Locale.US, "%.0f 分 %.0f 秒", s / 60, s % 60)
    }
}
