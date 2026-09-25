package com.example.clog.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Lamp,
    onPrimary = PaperInk,
    primaryContainer = LampSoft,
    onPrimaryContainer = Ink,
    secondary = Flower,
    onSecondary = PaperInk,
    secondaryContainer = FlowerSoft,
    onSecondaryContainer = Color(0xFF4C5A3F),
    background = Paper,
    onBackground = Ink,
    surface = PaperCard,
    onSurface = Ink,
    surfaceVariant = PaperLine,
    onSurfaceVariant = InkSoft,
    outline = PaperLine,
    outlineVariant = PaperLine
)

private val DarkColors = darkColorScheme(
    primary = Lamp,
    onPrimary = PaperInk,
    primaryContainer = LampSoftNight,
    onPrimaryContainer = NightInk,
    secondary = FlowerNight,
    onSecondary = PaperInk,
    secondaryContainer = FlowerNightSoft,
    onSecondaryContainer = Color(0xFFC4CEB2),
    background = NightPaper,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightLine,
    onSurfaceVariant = NightInkSoft,
    outline = NightLine,
    outlineVariant = NightLine
)

// 标题用衬线体，书页感；正文行距放宽，阅读像翻书
private val ClogTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 27.sp
    )
)

/**
 * clog 的主题：书房——纸的底色、墨的文字、一盏灯、一枝花。
 * 跟随系统自动切换深夜书房模式。
 * 点击反馈全局统一为克制的墨色涟漪（半透明墨色 × 默认按压透明度 ≈ 6%），
 * 保留「按下了」的感知，但不出现突兀的浅色矩形。
 */
@Composable
fun ClogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        typography = ClogTypography
    ) {
        CompositionLocalProvider(
            LocalIndication provides ripple(color = scheme.onSurface.copy(alpha = 0.5f))
        ) {
            content()
        }
    }
}
