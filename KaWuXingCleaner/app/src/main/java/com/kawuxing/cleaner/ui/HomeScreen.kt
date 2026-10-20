package com.kawuxing.cleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.kawuxing.cleaner.model.JunkGroup
import com.kawuxing.cleaner.ui.theme.Brand
import com.kawuxing.cleaner.ui.theme.Danger
import com.kawuxing.cleaner.vm.CleanerViewModel
import com.kawuxing.cleaner.vm.Phase

@Composable
fun HomeScreen(vm: CleanerViewModel, onOpenTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    var detail by remember { mutableStateOf<JunkGroup?>(null) }
    val res = vm.result

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(title = "悟空清理", subtitle = "缓存 · 残留 · 日志 · 大文件，一次扫干净")
        }

        if (!vm.hasAllFiles) {
            item {
                PermissionCard(
                    text = "Android 11 之后，读取全盘缓存与残留目录需要「所有文件访问权限」，否则只能看到本应用自己的目录。",
                    action = "去开启 →",
                    onClick = {
                        try {
                            ctx.startActivity(Perm.allFilesIntent(ctx))
                        } catch (e: Exception) {
                            // 个别 ROM 没有该设置页，忽略
                        }
                    }
                )
            }
        }

        item { GaugeCard(vm) }

        when (vm.phase) {
            Phase.SCANNING -> item { ScanningCard(vm) }
            Phase.CLEANING -> item { CleaningCard(vm) }
            Phase.DONE -> item { DoneCard(vm) }
            else -> Unit
        }

        if (res != null && (vm.phase == Phase.RESULT || vm.phase == Phase.CLEANING)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "清理项 · ${res.groups.size} 类",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "已选 ${Fmt.size(vm.selectedBytes)} / ${Fmt.size(res.junkTotal)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(res.groups) { group ->
                CategoryRow(
                    group = group,
                    checked = vm.selected.contains(group.category),
                    onToggle = { vm.toggle(group.category) },
                    onDetail = { detail = group }
                )
            }
            if (res.truncated) {
                item {
                    EmptyHint("本次扫描达到上限（${res.scannedFiles} 个文件），属于抽样结果，未覆盖全部目录")
                }
            }
            item {
                Text(
                    text = "扫描 ${res.scannedFiles} 个文件 · 用时 ${Fmt.duration(res.elapsedMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.phase == Phase.IDLE) {
            item { SafetyTips() }
        }
    }

    val current = detail
    if (current != null) {
        ItemListDialog(group = current, onClose = { detail = null })
    }
}

@Composable
private fun GaugeCard(vm: CleanerViewModel) {
    val buttonText: String
    val enabled: Boolean
    when (vm.phase) {
        Phase.RESULT -> {
            buttonText = if (vm.selectedCount > 0) "立即清理 ${Fmt.size(vm.selectedBytes)}" else "请先选择清理项"
            enabled = vm.selectedCount > 0
        }
        Phase.SCANNING -> {
            buttonText = "停止扫描"
            enabled = true
        }
        Phase.CLEANING -> {
            buttonText = "清理中…"
            enabled = false
        }
        else -> {
            buttonText = "一键扫描"
            enabled = true
        }
    }

    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingGauge(
                percent = vm.storageUsedPercent,
                value = Fmt.percent(vm.storageUsedPercent),
                label = "存储占用",
                sub = when (vm.phase) {
                    Phase.RESULT -> "可清理 ${Fmt.size(vm.selectedBytes)}"
                    Phase.DONE -> "本次已释放 ${Fmt.size(vm.outcome?.freedBytes ?: 0L)}"
                    else -> "可用 ${Fmt.size(vm.storageFree)}"
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "共 ${Fmt.size(vm.storageTotal)} · 已用 ${Fmt.size(vm.storageTotal - vm.storageFree)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = buttonText,
                enabled = enabled,
                onClick = {
                    when (vm.phase) {
                        Phase.RESULT -> vm.startClean()
                        Phase.SCANNING -> vm.cancelScan()
                        Phase.DONE -> {
                            vm.reset()
                            vm.startScan()
                        }
                        Phase.IDLE -> vm.startScan()
                        else -> Unit
                    }
                }
            )
            if (vm.phase == Phase.RESULT) {
                TextButton(onClick = { vm.reset() }) {
                    Text(text = "重新扫描", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScanningCard(vm: CleanerViewModel) {
    SectionCard {
        Text(
            text = "正在扫描…",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Brand
        )
        Spacer(Modifier.height(10.dp))
        IndeterminateBar()
        Spacer(Modifier.height(10.dp))
        Text(
            text = vm.progressText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CleaningCard(vm: CleanerViewModel) {
    SectionCard {
        Text(
            text = "正在清理…",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Brand
        )
        Spacer(Modifier.height(10.dp))
        ProgressBar(fraction = vm.progress)
        Spacer(Modifier.height(10.dp))
        Text(
            text = vm.progressText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DoneCard(vm: CleanerViewModel) {
    val out = vm.outcome
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "清理完成",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Brand
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Fmt.size(out?.freedBytes ?: 0L),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "本次释放",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "删除 ${out?.deletedCount ?: 0} 项" +
                    (if ((out?.failedCount ?: 0) > 0) " · ${out?.failedCount} 项被系统拒绝" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = if ((out?.failedCount ?: 0) > 0) Danger else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(text = "完成", onClick = { vm.reset() })
        }
    }
}

@Composable
private fun SafetyTips() {
    SectionCard {
        Text(
            text = "清理规则",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        TipRow("默认只勾选可再生的缓存、日志、临时文件，删了会自动重建")
        TipRow("「大文件」默认不勾选，路径与修改时间都列出来，自己确认再删")
        TipRow("照片、文档等个人文件不会被自动归类为垃圾；如需保护某个目录，可在「我的」里加白名单")
        TipRow("Android 不允许第三方强杀其他应用，也不允许清别的应用的私有数据，本应用不做越权操作")
    }
}

@Composable
private fun TipRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = Brand,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ItemListDialog(group: JunkGroup, onClose: () -> Unit) {
    val shown = group.items.take(200)
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        },
        title = {
            Text(text = "${group.category.title} · ${group.totalCount} 项")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "合计 ${Fmt.size(group.totalBytes)}" +
                        (if (group.partiallyListed) "（此处只列出前 ${shown.size} 项，清理时按类别整体处理）" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.name.ifEmpty { row.path },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = row.path.substringBeforeLast('/', ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (row.size > 0L) Fmt.sizeShort(row.size) else "空",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}
