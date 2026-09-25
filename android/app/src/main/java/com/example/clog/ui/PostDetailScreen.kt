package com.example.clog.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.R
import com.example.clog.data.GsonSavers
import com.example.clog.data.TimeUtils
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Comment
import com.example.clog.data.api.CommentRequest
import com.example.clog.data.api.Post
import com.example.clog.data.api.User
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 旋转屏幕时保留帖子详情/评论数据的 Saver */
private val postSaver = GsonSavers.ofNullable<Post>(object : TypeToken<Post>() {}.type)
private val commentListSaver = GsonSavers.ofType<List<Comment>>(object : TypeToken<List<Comment>>() {}.type)

/** 帖子详情：正文 + 媒体 + 互动 + 评论区（贴吧式：楼层 + 楼中楼） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    me: User,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenUser: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    // 发送评论后定位到对应楼层用：requester 挂在目标楼层节点上，bringIntoView 滚过去
    val commentBringer = remember { BringIntoViewRequester() }
    var bringFloorId by remember { mutableStateOf<Long?>(null) }
    var post by rememberSaveable(stateSaver = postSaver) { mutableStateOf<Post?>(null) }
    var comments by rememberSaveable(stateSaver = commentListSaver) { mutableStateOf<List<Comment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var confirmDeletePost by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var reported by remember { mutableStateOf(false) }
    // —— 楼中楼相关 ——
    var sort by rememberSaveable { mutableStateOf("new") } // new=楼层正序(默认，贴吧式) hot=热评在前
    var authorOnly by remember { mutableStateOf(false) }   // 只看楼主
    var replyTo by remember { mutableStateOf<Comment?>(null) } // 正在回复的楼层
    var expandingFloor by remember { mutableStateOf<Long?>(null) }
    // 评论点赞防连点：请求进行中的评论 id（乐观更新期间忽略重复点击）
    var pendingCommentLikes by remember { mutableStateOf(setOf<Long>()) }
    // 评论区首帧只渲染前 8 层，下一帧补全——拆散一次性组合尖峰
    var visibleFloors by remember { mutableStateOf(8) }

    suspend fun loadComments() {
        comments = ApiClient.api.comments(postId, sort = sort, authorOnly = authorOnly)
    }

    LaunchedEffect(postId) {
        runCatching {
            post = ApiClient.api.postDetail(postId)
            loadComments()
        }.onFailure { loadError = friendlyError(it) }
    }

    // 切换排序/只看楼主时重新拉评论（跳过首次组合，避免与上面重复请求）
    var filterInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(sort, authorOnly) {
        if (filterInitialized) {
            replyTo = null
            runCatching { loadComments() }.onFailure { toast(context, friendlyError(it)) }
        } else {
            filterInitialized = true
        }
    }

    // 评论列表变化后，下一帧把剩余楼层补全渲染
    LaunchedEffect(comments) {
        if (visibleFloors != comments.size) {
            delay(32)
            visibleFloors = comments.size
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "帖子", onBack = onBack) }
    ) { innerPadding ->
        val current = post
        if (current == null) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (loadError != null) {
                    ErrorRetry(
                        padding = PaddingValues(0.dp),
                        message = loadError ?: "加载失败",
                        onRetry = {
                            scope.launch {
                                loadError = null
                                runCatching {
                                    post = ApiClient.api.postDetail(postId)
                                    loadComments()
                                }.onFailure { loadError = friendlyError(it) }
                            }
                        }
                    )
                } else {
                    // 骨架屏：保持页面结构稳定，内容到位后自然替换
                    DetailSkeleton()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    // 键盘弹出时压缩视口，输入区不会被遮挡
                    .imePadding()
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 作者行：点头像/昵称进入作者主页（灯色昵称 + 行尾 › 提示可点击）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clogRowClickable {
                        current.author?.id?.let { onOpenUser(it) }
                    }
                ) {
                    Avatar(letter = initialOf(current.author?.nickname), size = 40)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.author?.nickname ?: "未知",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "@${current.author?.username} · ${TimeUtils.relative(current.createdAt)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "›",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                current.title?.takeIf { it.isNotBlank() }?.let { t ->
                    // 主题：衬线粗体，正文之上的标题行
                    Text(
                        text = t,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = current.content,
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                )
                (current.media ?: emptyList()).takeIf { it.isNotEmpty() }?.let { media ->
                    Spacer(modifier = Modifier.height(12.dp))
                    MediaContent(media = media, expand = true)
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 互动行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LikeRow(
                        liked = current.liked == true,
                        count = current.likeCount ?: 0,
                        onLike = {
                            // 乐观更新：先变色，失败回滚
                            val original = current
                            runOptimistic(
                                scope = scope,
                                context = context,
                                optimistic = {
                                    post = post?.copy(
                                        liked = post?.liked != true,
                                        likeCount = (post?.likeCount ?: 0) +
                                                if (post?.liked == true) -1 else 1
                                    )
                                },
                                onResult = { resp ->
                                    post = post?.copy(liked = resp.liked, likeCount = resp.likeCount)
                                },
                                rollback = { post = original },
                                request = { ApiClient.api.toggleLike(postId) }
                            )
                        }
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
                            text = formatCount(current.commentCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clogPressable {
                            // 乐观更新收藏：先变色，失败回滚
                            val original = current
                            runOptimistic(
                                scope = scope,
                                context = context,
                                optimistic = {
                                    post = post?.copy(
                                        collected = post?.collected != true,
                                        collectCount = (post?.collectCount ?: 0) +
                                                if (post?.collected == true) -1 else 1
                                    )
                                },
                                onResult = { resp ->
                                    post = post?.copy(
                                        collected = resp.collected,
                                        collectCount = resp.collectCount
                                    )
                                },
                                rollback = { post = original },
                                request = { ApiClient.api.toggleFavorite(postId) }
                            )
                        }
                    ) {
                        Image(
                            painter = painterResource(
                                if (current.collected == true) R.drawable.ic_star_filled
                                else R.drawable.ic_star_outline
                            ),
                            contentDescription = if (current.collected == true) "已收藏" else "收藏",
                            colorFilter = ColorFilter.tint(
                                if (current.collected == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(current.collectCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clogPressable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "来自 clog 的帖子")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "@${current.author?.username}: ${current.content}"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "分享帖子"))
                        }
                    ) {
                        Text(
                            text = "↗",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "分享",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    // 浏览量：柱状数据图标 + 数量（与其余互动图标同组）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_views),
                            contentDescription = "浏览",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatCount(current.viewCount),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                // 作者可删除
                if (current.author?.id == me.id) {
                    TextButton(onClick = { confirmDeletePost = true }) {
                        Text(
                            text = "删除帖子",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    ApiClient.api.reportPost(
                                        postId,
                                        com.example.clog.data.api.ReportRequest()
                                    )
                                }.onSuccess { reported = true }
                                    .onFailure { toast(context, friendlyError(it)) }
                            }
                        },
                        enabled = !reported
                    ) {
                        Text(
                            text = if (reported) "已举报 ✓" else "举报",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                // —— 评论区头部：总数 + 排序切换 + 只看楼主 ——
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "评论 ${formatCount(current.commentCount)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    CommentToggle("最新", sort == "new") { sort = "new" }
                    CommentToggle("热评", sort == "hot") { sort = "hot" }
                    CommentToggle("只看楼主", authorOnly) { authorOnly = !authorOnly }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (comments.isEmpty()) {
                    EmptyState(
                        title = if (authorOnly) "楼主还没有发过评论" else "还没有评论",
                        subtitle = if (authorOnly) null else "说点什么，占个沙发",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    )
                } else {
                    comments.take(visibleFloors).forEach { c ->
                        FloorComment(
                            comment = c,
                            modifier = if (c.id == bringFloorId) {
                                Modifier.bringIntoViewRequester(commentBringer)
                            } else Modifier,
                            meId = me.id,
                            expanding = expandingFloor == c.id,
                            onLike = {
                                // 乐观更新楼层点赞：先变色，失败回滚；请求进行中忽略连点
                                if (c.id !in pendingCommentLikes) {
                                    pendingCommentLikes = pendingCommentLikes + c.id
                                    val original = c
                                    runOptimistic(
                                        scope = scope,
                                        context = context,
                                        optimistic = {
                                            comments = comments.map {
                                                if (it.id == c.id) {
                                                    it.copy(
                                                        liked = it.liked != true,
                                                        likeCount = (it.likeCount ?: 0) +
                                                                if (it.liked == true) -1 else 1
                                                    )
                                                } else it
                                            }
                                        },
                                        onResult = { resp ->
                                            comments = comments.map {
                                                if (it.id == c.id) {
                                                    it.copy(liked = resp.liked, likeCount = resp.likeCount)
                                                } else it
                                            }
                                        },
                                        rollback = {
                                            comments = comments.map { if (it.id == c.id) original else it }
                                        },
                                        request = {
                                            try {
                                                ApiClient.api.toggleCommentLike(c.id)
                                            } finally {
                                                pendingCommentLikes = pendingCommentLikes - c.id
                                            }
                                        }
                                    )
                                }
                            },
                            onReply = { replyTo = c },
                            onDelete = { commentToDelete = c },
                            onExpandReplies = {
                                expandingFloor = c.id
                                scope.launch {
                                    runCatching { ApiClient.api.commentReplies(c.id) }
                                        .onSuccess { all ->
                                            comments = comments.map {
                                                if (it.id == c.id) {
                                                    it.copy(replies = all, hasMoreReplies = false)
                                                } else it
                                            }
                                        }
                                        .onFailure { toast(context, friendlyError(it)) }
                                    expandingFloor = null
                                }
                            },
                            onReplyLike = { reply ->
                                // 乐观更新楼中楼点赞：先变色，失败回滚；请求进行中忽略连点
                                if (reply.id !in pendingCommentLikes) {
                                    pendingCommentLikes = pendingCommentLikes + reply.id
                                    val originalReply = reply
                                    runOptimistic(
                                        scope = scope,
                                        context = context,
                                        optimistic = {
                                            comments = comments.map { floor ->
                                                if (floor.id == c.id) {
                                                    floor.copy(
                                                        replies = floor.replies?.map {
                                                            if (it.id == reply.id) {
                                                                it.copy(
                                                                    liked = it.liked != true,
                                                                    likeCount = (it.likeCount ?: 0) +
                                                                            if (it.liked == true) -1 else 1
                                                                )
                                                            } else it
                                                        }
                                                    )
                                                } else floor
                                            }
                                        },
                                        onResult = { resp ->
                                            comments = comments.map { floor ->
                                                if (floor.id == c.id) {
                                                    floor.copy(
                                                        replies = floor.replies?.map {
                                                            if (it.id == reply.id) {
                                                                it.copy(liked = resp.liked, likeCount = resp.likeCount)
                                                            } else it
                                                        }
                                                    )
                                                } else floor
                                            }
                                        },
                                        rollback = {
                                            comments = comments.map { floor ->
                                                if (floor.id == c.id) {
                                                    floor.copy(
                                                        replies = floor.replies?.map {
                                                            if (it.id == reply.id) originalReply else it
                                                        }
                                                    )
                                                } else floor
                                            }
                                        },
                                        request = {
                                            try {
                                                ApiClient.api.toggleCommentLike(reply.id)
                                            } finally {
                                                pendingCommentLikes = pendingCommentLikes - reply.id
                                            }
                                        }
                                    )
                                }
                            },
                            onReplyReply = { replyTo = c }, // 楼中楼里点回复，仍回复楼层（贴吧式）
                            onReplyDelete = { reply -> commentToDelete = reply }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 回复目标提示
                replyTo?.let { target ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "回复 @${target.user?.nickname ?: "TA"}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { replyTo = null }) {
                            Text(
                                text = "取消",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                // 评论输入
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = {
                            Text(
                                text = if (replyTo != null) {
                                    "回复 @${replyTo?.user?.nickname ?: "TA"}…"
                                } else "说点什么…",
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            sending = true
                            scope.launch {
                                val target = replyTo
                                runCatching {
                                    ApiClient.api.addComment(
                                        postId,
                                        CommentRequest(commentText.trim(), parent_id = target?.id)
                                    )
                                }.onSuccess { newComment ->
                                    if (newComment.parentId != null) {
                                        // 楼中楼：挂到对应楼层下
                                        comments = comments.map {
                                            if (it.id == newComment.parentId) {
                                                it.copy(
                                                    replies = (it.replies ?: emptyList()) + newComment,
                                                    replyCount = (it.replyCount ?: 0) + 1
                                                )
                                            } else it
                                        }
                                    } else {
                                        comments = comments + newComment
                                    }
                                    commentText = ""
                                    sendError = null
                                    replyTo = null
                                    post = post?.copy(commentCount = (post?.commentCount ?: 0) + 1)
                                    // 收起键盘，并把新评论（楼层或楼中楼所在楼层）带到视野内
                                    keyboard?.hide()
                                    bringFloorId = newComment.parentId ?: newComment.id
                                    delay(80) // 等一帧，让 requester 挂到目标楼层节点上
                                    commentBringer.bringIntoView()
                                }.onFailure { e ->
                                    // 保留草稿，错误单独显示，不覆盖用户输入
                                    sendError = friendlyError(e)
                                }
                                sending = false
                            }
                        },
                        enabled = commentText.isNotBlank() && !sending
                    ) {
                        Text("发送")
                    }
                }
                sendError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        // —— 确认对话框（与内容区平级，覆盖层显示） ——
        if (confirmDeletePost) {
            ConfirmDialog(
                title = "删除帖子",
                message = "删除后无法恢复，确定删除这篇帖子吗？",
                confirmLabel = "删除",
                danger = true,
                onConfirm = {
                    confirmDeletePost = false
                    scope.launch {
                        runCatching { ApiClient.api.deletePost(postId) }
                            .onSuccess { onDeleted() }
                            .onFailure { toast(context, friendlyError(it)) }
                    }
                },
                onDismiss = { confirmDeletePost = false }
            )
        }
        commentToDelete?.let { c ->
            ConfirmDialog(
                title = if (c.parentId == null) "删除评论" else "删除回复",
                message = if (c.parentId == null) "删除后无法恢复，楼层下的回复也会一并删除。"
                else "确定删除这条回复吗？",
                confirmLabel = "删除",
                danger = true,
                onConfirm = {
                    commentToDelete = null
                    scope.launch {
                        runCatching { ApiClient.api.deleteComment(c.id) }
                            .onSuccess {
                                if (c.parentId == null) {
                                    // 删除整层楼
                                    comments = comments.filterNot { it.id == c.id }
                                    post = post?.copy(
                                        commentCount = ((post?.commentCount ?: 1) - 1 -
                                                (c.replyCount ?: 0)).coerceAtLeast(0)
                                    )
                                } else {
                                    // 删除楼中楼里的回复
                                    comments = comments.map {
                                        if (it.id == c.parentId) {
                                            it.copy(
                                                replies = it.replies?.filterNot { r -> r.id == c.id },
                                                replyCount = ((it.replyCount ?: 1) - 1).coerceAtLeast(0)
                                            )
                                        } else it
                                    }
                                    post = post?.copy(
                                        commentCount = ((post?.commentCount ?: 1) - 1).coerceAtLeast(0)
                                    )
                                }
                            }
                            .onFailure { toast(context, friendlyError(it)) }
                    }
                },
                onDismiss = { commentToDelete = null }
            )
        }
    }
}

/** 评论区工具条开关：热评/最新/只看楼主（选中亮灯色） */
@Composable
private fun CommentToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clogPressable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

/** 详情页骨架：作者行 + 标题 + 正文行（加载时页面结构稳定） */
@Composable
private fun DetailSkeleton() {
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(base)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(base)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(base)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(base)
        )
        Spacer(modifier = Modifier.height(12.dp))
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i % 2 == 0) 1f else 0.8f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(base)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** 一层楼：楼层内容 + 楼中楼（默认预览 3 条，超出可展开全部）。
 *  楼号来自服务端（身份标识）：只看楼主等过滤后楼号保持原值、跳号显示（贴吧行为）。 */
@Composable
private fun FloorComment(
    comment: Comment,
    modifier: Modifier = Modifier,
    meId: Long,
    expanding: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onExpandReplies: () -> Unit,
    onReplyLike: (Comment) -> Unit,
    onReplyReply: () -> Unit,
    onReplyDelete: (Comment) -> Unit
) {
    Row(modifier = modifier.padding(vertical = 14.dp)) {
        Avatar(letter = initialOf(comment.user?.nickname), size = 30)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.nickname ?: "未知",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${comment.floorNumber ?: "?"}楼",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = TimeUtils.relative(comment.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Text(
                text = comment.content,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeRow(
                    liked = comment.liked == true,
                    count = comment.likeCount ?: 0,
                    small = true,
                    onLike = onLike
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "回复",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clogPressable(onClick = onReply)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
                if (comment.user?.id == meId) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clogPressable(onClick = onDelete)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
            // 楼中楼：暖灰浅底块，像书页边注
            (comment.replies ?: emptyList()).takeIf { it.isNotEmpty() }?.let { replies ->
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    replies.forEach { r ->
                        ReplyRow(
                            reply = r,
                            isMine = r.user?.id == meId,
                            onLike = { onReplyLike(r) },
                            onReply = onReplyReply,
                            onDelete = { onReplyDelete(r) }
                        )
                    }
                    if (comment.hasMoreReplies == true) {
                        TextButton(onClick = onExpandReplies, enabled = !expanding) {
                            Text(
                                text = if (expanding) "加载中…"
                                else "展开全部 ${comment.replyCount ?: 0} 条回复",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

/** 楼中楼里的一条回复：昵称：内容 + 时间/赞/回复/删除 */
@Composable
private fun ReplyRow(
    reply: Comment,
    isMine: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "${reply.user?.nickname ?: "未知"}：${reply.content}",
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TimeUtils.relative(reply.createdAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            LikeRow(
                liked = reply.liked == true,
                count = reply.likeCount ?: 0,
                small = true,
                onLike = onLike
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "回复",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .clogPressable(onClick = onReply)
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            )
            if (isMine) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clogPressable(onClick = onDelete)
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                )
            }
        }
    }
}
