package com.litiangu.wuji.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.BrandOrange

/**
 * 品牌启动页：应用冷启动 / 首页数据首读期间盖在最上层。
 * 用「物记」logo + 加载圈替代纯白背景，等待有语义、不显白屏。
 */
@Composable
fun AppSplash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "物记",
                style = MaterialTheme.typography.headlineMedium,
                color = BrandOrange,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = BrandOrange)
        }
    }
}
