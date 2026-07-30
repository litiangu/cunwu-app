package com.litiangu.wuji.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.WujiTheme

@Composable
fun HomeScreen() {
    // Step 1 仅做主题验证：本地布尔切换，不接系统/设置档位（Step 6 接入）
    var dark by remember { mutableStateOf(false) }
    WujiTheme(darkTheme = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "物记 · Wuji",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Step 1 脚手架验证：点击下方按钮切换深色主题",
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(onClick = { dark = !dark }) {
                    Text(if (dark) "当前：深色（演示）" else "当前：浅色（演示）")
                }
            }
        }
    }
}
