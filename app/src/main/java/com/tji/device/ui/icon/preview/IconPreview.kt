package com.tji.device.ui.icon.preview

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tji.device.product.firebucket.ui.icon.PaintBucket
import com.tji.device.ui.icon.common.BatteryOutline
import com.tji.device.ui.icon.common.InfoCircle
import com.tji.device.ui.icon.common.minimize

// 最灵活的图标配置数据类
data class IconConfig(
    val icon: ImageVector,
    val name: String = "",

    // 基础样式
    val size: Dp = 50.dp,
    val tint: Color = Color.Unspecified,
    val alpha: Float = 1f,

    // 容器样式
    val containerShape: Shape = RoundedCornerShape(8.dp),
    val backgroundColor: Color = Color.Transparent,
    val backgroundBrush: Brush? = null, // 支持渐变背景

    // 边框样式
    val borderWidth: Dp = 0.dp,
    val borderColor: Color = Color.Transparent,
    val borderBrush: Brush? = null, // 支持渐变边框

    // 阴影和高度
    val elevation: Dp = 0.dp,
    val shadowColor: Color = Color.Black,

    // 变换效果
    val rotation: Float = 0f,
    val scale: Float = 1f,

    // 内边距
    val padding: Dp = 16.dp,
    val iconPadding: Dp = 0.dp,

    // 动画效果
    val animated: Boolean = false,
    val animationType: AnimationType = AnimationType.NONE,

    // 交互
    val clickable: Boolean = false,
    val onClick: () -> Unit = {},

    // 完全自定义 Modifier（最高优先级）
    val customModifier: Modifier = Modifier,
    val customIconModifier: Modifier = Modifier
)

// 动画类型
enum class AnimationType {
    NONE,
    ROTATE,           // 旋转
    PULSE,            // 脉动
    BOUNCE,           // 弹跳
    SHAKE,            // 摇晃
    FADE              // 淡入淡出
}

// 示例配置列表
val iconList = listOf(
    // 基础样式
    IconConfig(
        icon = InfoCircle,
        name = "信息图标",
        size = 50.dp,
        tint = Color(0xFF2196F3),
        backgroundColor = Color(0xFFE3F2FD),
        padding = 12.dp
    ),

    // 渐变背景 + 阴影
    IconConfig(
        icon = PaintBucket,
        name = "渐变背景",
        size = 60.dp,
        tint = Color.White,
        backgroundBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D))
        ),
        containerShape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        padding = 20.dp
    ),

    // 圆形 + 边框
    IconConfig(
        icon = minimize,
        name = "圆形边框",
        size = 45.dp,
        tint = Color(0xFF4CAF50),
        backgroundColor = Color.White,
        containerShape = CircleShape,
        borderWidth = 3.dp,
        borderColor = Color(0xFF4CAF50),
        elevation = 4.dp,
        padding = 16.dp
    ),

    // 旋转动画
    IconConfig(
        icon = InfoCircle,
        name = "旋转动画",
        size = 50.dp,
        tint = Color(0xFF9C27B0),
        backgroundColor = Color(0xFFF3E5F5),
        containerShape = CircleShape,
        animated = true,
        animationType = AnimationType.ROTATE,
        padding = 12.dp
    ),

    // 脉动动画 + 渐变边框
    IconConfig(
        icon = PaintBucket,
        name = "脉动效果",
        size = 55.dp,
        tint = Color(0xFFFF5722),
        backgroundColor = Color(0xFFFFF3E0),
        borderWidth = 4.dp,
        borderBrush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFFFF5722),
                Color(0xFFFF9800),
                Color(0xFFFF5722)
            )
        ),
        containerShape = RoundedCornerShape(12.dp),
        animated = true,
        animationType = AnimationType.PULSE,
        padding = 14.dp
    ),

    // 可点击 + 自定义变换
    IconConfig(
        icon = minimize,
        name = "可点击",
        size = 50.dp,
        tint = Color.White,
        backgroundColor = Color(0xFF00BCD4),
        containerShape = RoundedCornerShape(24.dp),
        rotation = 45f,
        scale = 1.2f,
        elevation = 6.dp,
        clickable = true,
        onClick = { println("图标被点击了!") },
        padding = 18.dp
    ),

    // 完全自定义 Modifier
    IconConfig(
        icon = InfoCircle,
        name = "自定义样式",
        size = 60.dp,
        tint = Color(0xFFFFEB3B),
        customModifier = Modifier
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        customIconModifier = Modifier
            .rotate(15f)
            .scale(1.1f)
    )
)

@Composable
fun IconDisplay() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "灵活的图标展示系统",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(iconList) {
                IconItem(config = it)
            }
        }
    }
}

@Composable
fun IconItem(config: IconConfig) {
    // 动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "iconAnimation")

    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (config.animationType == AnimationType.ROTATE) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val animatedScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (config.animationType == AnimationType.PULSE) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (config.animationType == AnimationType.FADE) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 图标容器
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (config.customModifier != Modifier) {
                config.customModifier
            } else {
                Modifier
                    .let { if (config.elevation > 0.dp) it.shadow(config.elevation, config.containerShape) else it }
                    .let {
                        if (config.backgroundBrush != null) {
                            it.background(config.backgroundBrush, config.containerShape)
                        } else {
                            it.background(config.backgroundColor, config.containerShape)
                        }
                    }
                    .let {
                        if (config.borderWidth > 0.dp) {
                            if (config.borderBrush != null) {
                                it.border(config.borderWidth, config.borderBrush, config.containerShape)
                            } else {
                                it.border(config.borderWidth, config.borderColor, config.containerShape)
                            }
                        } else it
                    }
                    .padding(config.padding)
                    .let { if (config.clickable) it.clickable { config.onClick() } else it }
            }
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = config.name,
                tint = config.tint,
                modifier = if (config.customIconModifier != Modifier) {
                    config.customIconModifier.size(config.size)
                } else {
                    Modifier
                        .size(config.size)
                        .padding(config.iconPadding)
                        .let { if (config.animated) it.rotate(animatedRotation) else it.rotate(config.rotation) }
                        .let { if (config.animated) it.scale(animatedScale) else it.scale(config.scale) }
                        .let { if (config.animated) it.alpha(animatedAlpha * config.alpha) else it.alpha(config.alpha) }
                }
            )
        }

        // 图标名称
        if (config.name.isNotEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = config.name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview
@Composable
fun PreviewIconDisplay() {
    MaterialTheme {
        IconDisplay()
    }
}


@Preview(showBackground = true)
@Composable
private fun BatteryOutlineIconPreview() {
    MaterialTheme {
        IconItem(
            config = IconConfig(
                icon = BatteryOutline,
                tint = Color.Black,
                backgroundColor = Color(0xFFF5F5F5)
            )
        )
    }
}
