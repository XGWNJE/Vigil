// src/main/java/com/example/vigil/ui/monitoring/MonitoringScreen.kt
package com.example.vigil.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text // 使用 Material 2 的 Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.vigil.ui.theme.VigilTheme // 导入自定义主题

/**
 * “监控”屏幕的 Composable 函数。
 * 暂时作为占位符。
 */
@Composable
fun MonitoringScreen() {
    // TODO: 在这里构建监控界面的 UI
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("监控界面 (待实现)")
    }
}

@Preview(showBackground = true)
@Composable
fun MonitoringScreenPreview() {
    VigilTheme { // 在预览中使用自定义主题
        MonitoringScreen()
    }
}
