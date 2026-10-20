package com.kawuxing.cleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kawuxing.cleaner.ui.BoostScreen
import com.kawuxing.cleaner.ui.HomeScreen
import com.kawuxing.cleaner.ui.MineScreen
import com.kawuxing.cleaner.ui.StorageScreen
import com.kawuxing.cleaner.ui.theme.KaWuXingTheme
import com.kawuxing.cleaner.vm.CleanerViewModel

class MainActivity : ComponentActivity() {

    private val vm: CleanerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KaWuXingTheme {
                AppScaffold(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPerms()
        vm.refreshMemory()
        vm.refreshStorage()
    }
}

private data class TabItem(val title: String, val icon: ImageVector)

@Composable
private fun AppScaffold(vm: CleanerViewModel) {
    var index by remember { mutableStateOf(0) }
    val tabs = listOf(
        TabItem("清理", Icons.Default.Home),
        TabItem("存储", Icons.Default.List),
        TabItem("加速", Icons.Default.Refresh),
        TabItem("我的", Icons.Default.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = index == i,
                        onClick = { index = i },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(text = tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (index) {
                0 -> HomeScreen(vm = vm, onOpenTab = { index = it })
                1 -> StorageScreen(vm = vm, onOpenTab = { index = it })
                2 -> BoostScreen(vm = vm)
                else -> MineScreen(vm = vm)
            }
        }
    }
}
