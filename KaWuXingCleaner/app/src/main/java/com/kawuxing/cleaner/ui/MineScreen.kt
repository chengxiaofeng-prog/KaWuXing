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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.kawuxing.cleaner.vm.CleanerViewModel

@Composable
fun MineScreen(vm: CleanerViewModel) {
    val ctx = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle(title = "我的", subtitle = "权限、白名单与清理记录") }

        item {
            SectionCard {
                Text(
                    text = "权限状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                KeyValueRow("所有文件访问", if (vm.hasAllFiles) "已开启" else "未开启")
                if (!vm.hasAllFiles) {
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Perm.allFilesIntent(ctx))
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }) {
                        Text(text = "去开启「所有文件访问」→", fontWeight = FontWeight.Bold)
                    }
                }
                KeyValueRow("使用情况访问", if (vm.hasUsage) "已开启" else "未开启")
                if (!vm.hasUsage) {
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Perm.usageIntent())
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }) {
                        Text(text = "去开启「使用情况访问」→", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            SectionCard {
                Text(
                    text = "清理记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                KeyValueRow("累计释放", Fmt.size(vm.totalFreed))
                KeyValueRow("上次清理", Fmt.time(vm.lastCleanAt))
                KeyValueRow("累计扫描", "${vm.scanCount} 次")
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
                        text = "白名单",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = {
                        keyword = ""
                        showAdd = true
                    }) {
                        Text(text = "添加")
                    }
                }
                Text(
                    text = "路径里包含关键字的文件/目录，扫描和删除都会跳过。例如 DCIM/Camera、Tencent/MicroMsg。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                if (vm.whitelist.isEmpty()) {
                    Text(
                        text = "暂无白名单",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (k in vm.whitelist.sorted()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = k,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { vm.removeWhitelist(k) }) {
                                Text(text = "移除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                KeyValueRow("版本", "1.0.0")
                KeyValueRow("数据处理", "全部本地完成")
                Text(
                    text = "本应用不联网、不上传任何文件列表或文件内容；" +
                        "所有扫描、统计、删除都在手机本地执行。开源地址：github.com/chengxiaofeng-prog/KaWuXing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "清理有风险：缓存类文件删了会自动重建，但你自己放进缓存目录的东西删了不会回来。" +
                        "大文件请逐个确认后再勾选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.addWhitelist(keyword)
                    showAdd = false
                }) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            },
            title = { Text("添加白名单") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "填路径关键字，不区分大小写",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        singleLine = true,
                        label = { Text("例如 DCIM/Camera") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}
