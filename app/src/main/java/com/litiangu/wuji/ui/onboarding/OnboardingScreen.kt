package com.litiangu.wuji.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * 引导页（首次启动展示，详见 `逻辑设计文档/引导页+首次启动引导功能.md`）。
 *
 * 设计要点：
 * - 仅展示品牌主张 + 单一 CTA，不做任何业务逻辑；标记写入与跳转由 [onStart] 回调交给上层。
 * - 装饰插画用 Compose 自绘（品牌色圆角卡 + 大号图标），不引入外部图片资源。
 * - CTA 主色 #FF5500、圆角 12dp（对齐规范）。
 *
 * 系统返回键行为：本页作为根内容展示，按返回即退出 App，不写标记（默认行为，无需额外处理）。
 *
 * @param onStart 点击 CTA：上层负责写入 hasLaunchedBefore 并进入新增物品流程。
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WujiSpace.lg, vertical = WujiSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 装饰插画：品牌色圆角卡 + 居中大号图标（占位图形，后续可替换为正式插画资源）
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(BrandOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(BrandOrange.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory,
                    contentDescription = null,
                    tint = BrandOrange,
                    modifier = Modifier.size(72.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(WujiSpace.xl))

        // 标题：displayMedium + 700
        Text(
            text = "新的物品",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(WujiSpace.sm))

        // 副标题
        Text(
            text = "存下此刻，物有所值",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(WujiSpace.md))

        // 说明文字（品牌向，居中、限制宽度提升可读性）
        Text(
            text = "为每一件物品留下时间、价格与故事。断舍离，从看清自己拥有什么开始。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        Spacer(modifier = Modifier.height(WujiSpace.xl))

        // 主 CTA：品牌橙底 + 白字 + 12dp 圆角 + 尾部箭头
        Button(
            onClick = onStart,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(52.dp),
        ) {
            Text(
                text = "开始你的第一个物品记录",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(WujiSpace.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
