package com.kawuxing.cleaner.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kawuxing.cleaner.core.Fmt
import com.kawuxing.cleaner.core.Perm
import com.kawuxing.cleaner.ui.theme.Brand
import com.kawuxing.cleaner.ui.theme.Sky
import com.kawuxing.cleaner.vm.CleanerViewModel
import kotlinx.coroutines.launch

@Composable
fun BoostScreen(vm: CleanerViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val mem = vm.mem
    val appTotal = vm.appCaches.sumOf { it.cacheBytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle(title = "内存加速", subtitle = "数据直接读 /proc/meminfo，不做「虚拟加速」") }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RingGauge(
                        percent = mem.usedPercent,
                        value = if (mem.total > 0L) Fmt.percent(mem.usedPercent) else "—",
                        label = "内存占用",
                        sub = if (mem.total > 0L) "共 ${Fmt.size(mem.total)}" else "读取受限",
                        color = Sky
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (busy) "加速中…" else "一键加速",
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val freed = vm.clearOwnCache()
                                vm.refreshMemory()
                                busy = false
                                Toast.makeText(
                                    ctx,
                                    "已清理本应用缓存 ${Fmt.size(freed)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }

        item {
            SectionCard {
                Text(
                    text = "内存明细",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                KeyValueRow("总内存", if (mem.total > 0L) Fmt.size(mem.total) else "—")
                KeyValueRow("可用", if (mem.total > 0L) Fmt.size(mem.avail) else "—")
                KeyValueRow("已用", if (mem.total > 0L) Fmt.size(mem.used) else "—")
                KeyValueRow("页缓存（可回收）", if (mem.cached > 0L) Fmt.size(mem.cached) else "—")
                KeyValueRow(
                    "交换分区",
                    if (mem.swapTotal > 0L) {
                        "${Fmt.size(mem.swapTotal - mem.swapFree)} / ${Fmt.size(mem.swapTotal)}"
                    } else {
                        "未启用"
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Android 8 之后系统不允许第三方应用强杀别的进程，也不允许清理其他应用的私有数据。" +
                        "所谓「一键加速」大多只是回收自家内存 + 让系统自己回收，本应用不给假数字。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "各应用缓存",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { vm.loadAppCaches() }) {
                        Text(text = if (vm.appCacheLoading) "统计中…" else "统计")
                    }
                }

                if (!vm.hasUsage) {
                    Text(
                        text = "需要「使用情况访问」权限才能统计其他应用的缓存体积（只统计，不删除）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Perm.usageIntent())
                        } catch (e: Exception) {
                            // 个别 ROM 缺少该页面
                        }
                    }) {
                        Text(text = "去开启 →", fontWeight = FontWeight.Bold)
                    }
                }

                if (vm.appCacheLoading) {
                    Spacer(Modifier.height(8.dp))
                    IndeterminateBar()
                }

                for (entry in vm.appCaches.take(8)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    ctx.startActivity(Perm.appDetailsIntent(ctx, entry.packageName))
                                } catch (e: Exception) {
                                    // 应用可能已卸载
                                }
                            }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = Fmt.sizeShort(entry.cacheBytes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand
                        )
                    }
                }

                if (vm.appCaches.isNotEmpty()) {
                    Text(
                        text = "统计到 ${Fmt.size(appTotal)} · 点条目跳系统「应用详情」清除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!vm.appCacheLoading && vm.hasUsage) {
                    Text(
                        text = "点右上角「统计」，读取各应用缓存体积",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
