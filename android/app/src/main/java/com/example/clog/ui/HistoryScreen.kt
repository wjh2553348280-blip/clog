package com.example.clog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.GsonSavers
import com.example.clog.data.TimeUtils
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.HistoryItem
import com.example.clog.data.api.Post
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/** 旋转屏幕时保留浏览历史数据的 Saver */
private val historySaver = GsonSavers.ofType<List<HistoryItem>>(object : TypeToken<List<HistoryItem>>() {}.type)

/** 浏览历史：最近看过的帖子（每帖一条，按最后浏览时间倒序） */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    var items by rememberSaveable(stateSaver = historySaver) { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        runCatching { ApiClient.api.myHistory() }
            .onSuccess { items = it; error = null }
            .onFailure { error = friendlyError(it) }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "浏览历史", onBack = onBack) }
    ) { padding ->
        when {
            error != null && items.isEmpty() -> ErrorRetry(
                padding = padding,
                message = error ?: "加载失败",
                onRetry = { scope.launch { reload() } }
            )
            loading && items.isEmpty() -> Column {
                repeat(5) { PostSkeleton() }
            }
            items.isEmpty() -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "还没有浏览过帖子",
                    subtitle = "去发现页逛逛，这里会记下你的足迹"
                )
            }
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                items(items.filter { it.post != null }, key = { it.post!!.id }) { item ->
                    val post = item.post!!
                    Text(
                        text = "${TimeUtils.relative(item.viewedAt)} 看过",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    PostCard(
                        post = post,
                        onClick = { onOpenPost(post) },
                        onLike = {
                            // 乐观更新：先变色，失败回滚
                            val original = post
                            runOptimistic(
                                scope = scope,
                                context = context,
                                optimistic = {
                                    items = items.map {
                                        if (it.post?.id == post.id) {
                                            it.copy(
                                                post = it.post?.copy(
                                                    liked = it.post?.liked != true,
                                                    likeCount = (it.post?.likeCount ?: 0) +
                                                            if (it.post?.liked == true) -1 else 1
                                                )
                                            )
                                        } else it
                                    }
                                },
                                onResult = { resp ->
                                    items = items.map {
                                        if (it.post?.id == post.id) {
                                            it.copy(
                                                post = it.post?.copy(
                                                    liked = resp.liked,
                                                    likeCount = resp.likeCount
                                                )
                                            )
                                        } else it
                                    }
                                },
                                rollback = {
                                    items = items.map {
                                        if (it.post?.id == post.id) it.copy(post = original) else it
                                    }
                                },
                                request = { ApiClient.api.toggleLike(post.id) }
                            )
                        }
                    )
                }
            }
        }
    }
}
