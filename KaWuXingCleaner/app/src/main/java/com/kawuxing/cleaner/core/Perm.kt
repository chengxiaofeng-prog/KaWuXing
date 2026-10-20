package com.kawuxing.cleaner.core

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings

/**
 * 权限集中管理。清理类应用在 Android 11+ 上的能力边界：
 * - 「所有文件访问权限」(MANAGE_EXTERNAL_STORAGE)：全盘读写共享存储，是扫描垃圾的前提；
 * - 「使用情况访问」(PACKAGE_USAGE_STATS)：用 StorageStatsManager 统计各应用缓存；
 * - 其他应用私有目录（/data/data/...）任何第三方应用都无法触碰，本应用只统计不越权。
 */
object Perm {

    /** 是否已获得全盘文件访问权限 */
    fun allFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    /** 跳转到本应用的「所有文件访问权限」开关 */
    fun allFilesIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + context.packageName)
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            )
        }

    /** 是否已获得使用情况访问权限（用于读取各应用缓存体积） */
    fun usageAccess(context: Context): Boolean {
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun usageIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsIntent(context: Context, pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))

    fun storageIntent(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
}
