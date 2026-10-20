package com.kawuxing.cleaner.data

import android.content.Context
import android.content.SharedPreferences

/** 轻量本地存储：白名单、累计释放量、上次清理时间。 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("kawuxing_cleaner", Context.MODE_PRIVATE)

    var totalFreed: Long
        get() = sp.getLong("total_freed", 0L)
        set(value) {
            sp.edit().putLong("total_freed", value).apply()
        }

    var lastCleanAt: Long
        get() = sp.getLong("last_clean", 0L)
        set(value) {
            sp.edit().putLong("last_clean", value).apply()
        }

    var scanCount: Int
        get() = sp.getInt("scan_count", 0)
        set(value) {
            sp.edit().putInt("scan_count", value).apply()
        }

    var lastFreed: Long
        get() = sp.getLong("last_freed", 0L)
        set(value) {
            sp.edit().putLong("last_freed", value).apply()
        }

    fun whitelist(): Set<String> {
        val raw = sp.getStringSet("whitelist", null) ?: return emptySet()
        return raw.toSet()
    }

    fun addWhite(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        val set = whitelist().toMutableSet()
        set.add(k)
        sp.edit().putStringSet("whitelist", set).apply()
    }

    fun removeWhite(keyword: String) {
        val set = whitelist().toMutableSet()
        set.remove(keyword)
        sp.edit().putStringSet("whitelist", set).apply()
    }
}
