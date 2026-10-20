package com.kawuxing.cleaner.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kawuxing.cleaner.core.Fmt
import com.kawuxing.cleaner.model.JunkItem
import com.kawuxing.cleaner.ui.theme.Amber
import com.kawuxing.cleaner.ui.theme.Brand
import com.kawuxing.cleaner.ui.theme.Danger
import com.kawuxing.cleaner.ui.theme.Sky
import com.kawuxing.cleaner.ui.theme.Violet
import com.kawuxing.cleaner.vm.CleanerViewModel

private val typeColors = mapOf(
    "视频" to Danger,
    "图片" to Sky,
    "音频" to Violet,
    "文档" to Amber,
    "安装包" to Color(0xFFEC4899),
    "压缩包" to Color(0xFF14B8A6),
    "其他" to Color(0xFF64748B)
)

@Composable
fun StorageScreen(vm: CleanerViewModel, onOpenTab: (Int) -> Unit) {
    val res = vm.result
    val used = vm.storageTotal - vm.storageFree

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle(title = "存储分析", subtitle = "按文件类型和目录，看清空间被谁占了") }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RingGauge(
                        percent = vm.storageUsedPercent,
                        value = Fmt.percent(vm.storageUsedPercent),
                        label = "已使用",
                        sub = "可用 ${Fmt.size(vm.storageFree)}",
                        color = if (vm.storageUsedPercent > 0.9f) Danger else Brand
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "共 ${Fmt.size(vm.storageTotal)} · 已用 ${Fmt.size(used)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (res == null) {
            item { EmptyHint("先到「清理」页做一次扫描，这里才会出现类型与目录明细") }
            item { PrimaryButton(text = "去扫描", onClick = { onOpenTab(0) }) }
        } else {
            val typed = res.typeBytes.values.sum()
            if (typed > 0L) {
                item {
                    SectionCard {
                        Text(
                            text = "按类型（已扫描 ${Fmt.size(typed)}）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        val order = listOf("视频", "图片", "音频", "文档", "安装包", "压缩包", "其他")
                        for (name in order) {
                            val bytes = res.typeBytes[name] ?: 0L
                            if (bytes > 0L) {
                                TypeBar(
                                    label = name,
                                    bytes = bytes,
                                    total = typed,
                                    color = typeColors[name] ?: Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            if (res.topFolders.isNotEmpty()) {
                item {
                    SectionCard {
                        Text(
                            text = "占用最大的目录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        for (f in res.topFolders) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = f.path.substringAfterLast('/'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = f.path.substringBeforeLast('/'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = Fmt.sizeShort(f.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (res.bigFiles.isNotEmpty()) {
                item {
                    SectionCard {
                        Text(
                            text = "大文件 Top ${minOf(10, res.bigFiles.size)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "默认不清理，请在「清理」页勾选后再删",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        for (f in res.bigFiles.take(10)) {
                            BigFileRow(f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigFileRow(item: JunkItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.path.substringBeforeLast('/'),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Fmt.time(item.modified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = Fmt.sizeShort(item.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Danger
        )
    }
}
