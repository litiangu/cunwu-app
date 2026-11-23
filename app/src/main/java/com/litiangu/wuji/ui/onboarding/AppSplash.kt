package com.litiangu.wuji.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.BrandOrange

/**
 * 品牌启动页：应用冷启动 / 首页数据首读期间盖在最上层。
 * 展示 logo + 应用名「存物」+ Slogan「存下此刻，物有所值」+ 加载圈，
 * 替代纯白背景，等待有语义、不显白屏。
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
            // 线框色跟随主题：浅色 = 近黑，深色 = 近白；橙点保持品牌色。
            // 不能直接用 R.drawable.ic_launcher_foreground：它的描边写死 #050505（为白底桌面图标设计），
            // 深色背景上几乎不可见；而资源限定符 -night 只跟随系统 uiMode，
            // 不跟随应用内「深色模式」三档，用 drawable-night 也解决不了。
            val strokeColor = MaterialTheme.colorScheme.onSurface
            val logo = remember(strokeColor) { brandLogoVector(strokeColor) }
            Image(
                imageVector = logo,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "存物",
                style = MaterialTheme.typography.headlineMedium,
                color = BrandOrange,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "存下此刻，物有所值",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = BrandOrange)
        }
    }
}

// 品牌 logo 橙点色（与 ic_launcher_foreground.xml 一致）
private val LogoDot = Color(0xFFE85D4A)

/**
 * 品牌 logo（圆角方框 + 右下橙点），与 res/drawable/ic_launcher_foreground.xml 路径完全一致，
 * 只把描边色改为参数。改 logo 时两处需同步。
 */
private fun brandLogoVector(stroke: Color): ImageVector =
    ImageVector.Builder(
        name = "BrandLogo",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 512f,
        viewportHeight = 512f,
    ).apply {
        addGroup(scaleX = 0.7f, scaleY = 0.7f, translationX = 76.8f, translationY = 76.8f)
        addPath(
            pathData = addPathNodes(
                "M285,404H168A60,60 0,0 1,108 344V168A60,60 0,0 1,168 108H344A60,60 0,0 1,404 168V285"
            ),
            fill = null,
            stroke = SolidColor(stroke),
            strokeLineWidth = 26f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = addPathNodes("M360,360m-50,0a50,50 0,1 1,100 0a50,50 0,1 1,-100 0"),
            fill = SolidColor(LogoDot),
        )
        clearGroup()
    }.build()
