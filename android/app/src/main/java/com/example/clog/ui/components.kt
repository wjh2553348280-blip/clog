package com.example.clog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.CrossfadePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.example.clog.R
import com.example.clog.data.TimeUtils
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.MediaItem
import com.example.clog.data.api.Post
import com.example.clog.ui.theme.PaperInk
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 昵称首字：字母头像用 */
fun initialOf(nickname: String?): String =
    nickname?.trim()?.firstOrNull()?.toString() ?: "?"

/** 字母头像：灯色底 + 纸色字（像盖在书页上的藏书章） */
@Composable
fun Avatar(letter: String, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.take(1).ifBlank { "?" },
            color = PaperInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size * 0.45f).sp
        )
    }
}

/** 点赞按钮：♡ 空心 / ♥ 实心（选中态=灯色）+ 数量。small 用于评论区等紧凑场景。
 *  点赞瞬间爱心轻轻弹一下（1→1.3→1），克制但有反馈。 */
@Composable
fun LikeRow(liked: Boolean, count: Int, onLike: () -> Unit, small: Boolean = false) {
    val scale = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(liked) {
        if (initialized) {
            if (liked) {
                scale.animateTo(1.3f, animationSpec = tween(90))
                scale.animateTo(1f, animationSpec = tween(160))
            } else {
                scale.snapTo(1f)
            }
        } else {
            initialized = true
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clogPressable(onClick = onLike)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        val iconColor = if (liked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
        Image(
            painter = painterResource(
                if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            ),
            contentDescription = if (liked) "已点赞" else "点赞",
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier
                .size(16.dp)
                .scale(scale.value)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatCount(count),
            color = if (liked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (small) 13.sp else 14.sp
        )
    }
}

/**
 * 乐观更新：先把 UI 切到 optimistic() 的状态（用户立刻看到反馈），
 * 请求成功用服务器返回值 onResult 校准，失败则 rollback() 并弹错误提示。
 */
fun <T> runOptimistic(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    optimistic: () -> Unit,
    onResult: (T) -> Unit,
    rollback: () -> Unit,
    request: suspend () -> T
) {
    optimistic()
    scope.launch {
        runCatching { request() }
            .onSuccess { onResult(it) }
            .onFailure {
                rollback()
                toast(context, friendlyError(it))
            }
    }
}

/** 视频播放器（Media3 ExoPlayer），带缓冲指示与加载失败提示 */
@Composable
fun VideoPlayer(url: String) {
    val context = LocalContext.current
    var buffering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == ExoPlayer.STATE_BUFFERING
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    failed = true
                }
            })
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply { this.player = player }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (buffering && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
        if (failed) {
            Text(
                text = "视频加载失败",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

/** 带渐显的图片：Coil CrossfadePainter 包装，占位/失败底色保持一致 */
@Composable
private fun FadeImage(
    model: Any?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val painter = rememberAsyncImagePainter(
        model = model,
        placeholder = ColorPainter(base),
        error = ColorPainter(base)
    )
    Image(
        painter = CrossfadePainter(
            start = ColorPainter(Color.Transparent),
            end = painter,
            duration = 200.milliseconds
        ),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

/**
 * 媒体展示：
 * - 详情页（expand=true）：图片全宽（带渐显）、视频直接播放
 * - 信息流卡片（expand=false）：图片网格、视频显示占位块（点卡片进详情再播）
 */
@Composable
fun MediaContent(media: List<MediaItem>, expand: Boolean) {
    if (media.isEmpty()) return
    val images = media.filter { it.type == "image" }
    val video = media.firstOrNull { it.type == "video" }
    val shape = RoundedCornerShape(10.dp)

    // —— 按目标显示尺寸给 Coil 下采样提示：缩略图不解码原图，大幅降低内存与滚动开销 ——
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val screenW = ctx.resources.displayMetrics.widthPixels
    val cardW = screenW - (16 * density).toInt() * 2            // 卡片内容宽（左右各 16dp）
    val cell2 = (cardW - (4 * density).toInt()) / 2             // 两列网格单元
    val cell3 = (cardW - (4 * density).toInt() * 2) / 3         // 三列网格单元

    /** 只约束宽度、保持原始宽高比的请求：Crop/FillWidth 裁切由布局完成，解码即显示精度 */
    fun sizedRequest(url: String, widthPx: Int) = ImageRequest.Builder(ctx)
        .data(ApiClient.mediaUrl(url))
        .size(widthPx)
        .build()

    if (images.isNotEmpty()) {
        if (expand) {
            // 详情页：一张张全宽显示（带 200ms 自然渐显，此场景不回收、缓存无闪烁风险）
            images.forEach { item ->
                FadeImage(
                    model = sizedRequest(ApiClient.mediaUrl(item.url), screenW),
                    contentDescription = "帖子图片",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        } else {
            // 信息流：网格缩略图
            when (images.size) {
                1 -> AsyncImage(
                    model = sizedRequest(ApiClient.mediaUrl(images[0].url), screenW),
                    contentDescription = "帖子图片",
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(shape)
                )
                2, 4 -> {
                    val perRow = 2
                    val rows = images.chunked(perRow)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowItems.forEach { item ->
                                    AsyncImage(
                                        model = sizedRequest(ApiClient.mediaUrl(item.url), cell2),
                                        contentDescription = "帖子图片",
                                        contentScale = ContentScale.Crop,
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(shape)
                                    )
                                }
                                if (rowItems.size < perRow) {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 3 张及以上：三列网格
                    val rows = images.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowItems.forEach { item ->
                                    AsyncImage(
                                        model = sizedRequest(ApiClient.mediaUrl(item.url), cell3),
                                        contentDescription = "帖子图片",
                                        contentScale = ContentScale.Crop,
                                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(shape)
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (video != null) {
        Spacer(modifier = Modifier.height(if (images.isNotEmpty()) 10.dp else 0.dp))
        if (expand) {
            VideoPlayer(url = ApiClient.mediaUrl(video.url))
        } else {
            // 信息流：视频占位块（灯色播放圆钮 + 右下角标签）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
                Text(
                    text = "视频",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                )
            }
        }
    }
}

/** 短帖卡片：头像 + 昵称 + 时间 + 社区 + 文字 + 媒体 + 互动行（X 时间线风格） */
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onCollect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val media = post.media ?: emptyList()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clogRowClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row {
            Avatar(letter = initialOf(post.author?.nickname), size = 40)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.author?.nickname ?: "未知",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "@${post.author?.username} · ${TimeUtils.relative(post.createdAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                post.title?.takeIf { it.isNotBlank() }?.let { t ->
                    // 主题：衬线半粗，正文之上的标题行
                    Text(
                        text = t,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = post.content,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (media.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaContent(media = media, expand = false)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LikeRow(
                        liked = post.liked == true,
                        count = post.likeCount ?: 0,
                        onLike = onLike
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                    // 评论：聊天气泡图标 + 数量（与点赞/收藏同尺寸）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_comment),
                            contentDescription = "评论",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(post.commentCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (onCollect != null) {
                            Modifier.clogPressable(onClick = onCollect)
                        } else Modifier
                    ) {
                        Image(
                            painter = painterResource(
                                if (post.collected == true) R.drawable.ic_star_filled
                                else R.drawable.ic_star_outline
                            ),
                            contentDescription = if (post.collected == true) "已收藏" else "收藏",
                            colorFilter = ColorFilter.tint(
                                if (post.collected == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(post.collectCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    // 浏览量：柱状数据图标 + 数量（与其余三个互动图标同组同尺寸）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_views),
                            contentDescription = "浏览",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(post.viewCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
    PaperDivider()
}

/** 极淡的信息流分隔线：浅色=黑 5%，深色=白 8%（主题感知，避免硬编码单色在深色模式失效） */
@Composable
fun PaperDivider() {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val tint = if (light) Color.Black else Color.White
    HorizontalDivider(
        thickness = 0.5.dp,
        color = tint.copy(alpha = if (light) 0.05f else 0.08f)
    )
}

/** 短提示（全 app 统一用 Toast，避免每个文件各自 new） */
fun toast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

/** 按压反馈：内容轻微缩小再回弹（无涟漪矩形），用于点赞/收藏等小图标与文字按钮 */
@Composable
fun Modifier.clogPressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(110),
        label = "pressScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/** 无按压视觉（无涟漪矩形），用于整卡/整行这类大面积点击 */
@Composable
fun Modifier.clogRowClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

/** 计数缩写：999 以下原样，1000~9999 显示 1.2k，1 万以上显示 1.2万（手动计算避免 Locale 小数逗号问题） */
fun formatCount(n: Int?): String {
    val v = n ?: 0
    return when {
        v < 1000 -> v.toString()
        v < 10000 -> {
            val tenths = v / 100
            "${tenths / 10}.${tenths % 10}k"
        }
        else -> {
            val w = v / 10000
            if (w >= 10) "${w}万" else {
                val tenths = v / 1000
                "${tenths / 10}.${tenths % 10}万"
            }
        }
    }
}

/** 居中错误提示 + 重试按钮：主文案 + 原因 + 灯色描边重试（统一错误态） */
@Composable
fun ErrorRetry(padding: androidx.compose.foundation.layout.PaddingValues, message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "连不上书房了",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("重试", fontSize = 14.sp)
            }
        }
    }
}

/** 空态：主文案 + 副文案（无装饰符号，纯文字） */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        subtitle?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 帖子卡片骨架：加载时保持结构稳定（色板浅底静态占位，无动画） */
@Composable
fun PostSkeleton(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(base)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(base)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(base)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(base)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(base)
            )
        }
    }
    PaperDivider()
}

/** 危险操作确认对话框：删除/退出/放弃草稿共用 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(message, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 二级页顶栏：衬线粗体标题（20sp），无返回按键（返回统一走系统返回手势/按键） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClogBackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
