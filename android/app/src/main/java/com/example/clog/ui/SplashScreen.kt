package com.example.clog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开屏：承接系统启动画面（同样的纸色背景、同样的 160dp 图标尺寸）。
 * 花瓶先浮现，花朵从花瓶口轻轻生长绽放，字标淡入，最后整体淡出进入主页。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alphaAnim = remember { Animatable(0f) }
    val bloomAnim = remember { Animatable(0.42f) }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, tween(durationMillis = 320))   // 花瓶浮现
        delay(100)
        // 花朵从花瓶口生长、绽放（轻微回弹）
        launch {
            bloomAnim.animateTo(
                1.06f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f)
            )
            bloomAnim.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
            )
        }
        delay(750)                                            // 停留片刻
        alphaAnim.animateTo(0f, tween(durationMillis = 300))  // 淡出进入主页
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 160dp：与系统启动画面的图标规格一致
            Box(modifier = Modifier.size(160.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_base),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = alphaAnim.value }
                )
                Image(
                    painter = painterResource(R.drawable.ic_splash_flowers),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = alphaAnim.value
                            scaleX = bloomAnim.value
                            scaleY = bloomAnim.value
                        }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "clog",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.graphicsLayer { alpha = alphaAnim.value }
            )
        }
    }
}
