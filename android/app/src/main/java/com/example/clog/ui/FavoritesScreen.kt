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
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Post
import com.example.clog.data.api.User
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/** 旋转屏幕时保留收藏列表数据的 Saver */
private val favoritesSaver = GsonSavers.ofType<List<Post>>(object : TypeToken<List<Post>>() {}.type)

/** 收藏页：侧边栏与个人主页共用入口 */
@Composable
fun FavoritesScreen(
    me: User,
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    var posts by rememberSaveable(stateSaver = favoritesSaver) { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        runCatching { ApiClient.api.userFavorites(me.id) }
            .onSuccess { posts = it; error = null }
            .onFailure { error = friendlyError(it) }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "我的收藏", onBack = onBack) }
    ) { padding ->
        when {
            error != null && posts.isEmpty() -> ErrorRetry(
                padding = padding,
                message = error ?: "加载失败",
                onRetry = { scope.launch { reload() } }
            )
            loading && posts.isEmpty() -> Column {
                repeat(5) { PostSkeleton() }
            }
            posts.isEmpty() -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "还没有收藏过帖子",
                    subtitle = "看到喜欢的帖子，点 ☆ 收藏"
                )
            }
            else -> LazyColumn(modifier = Modifier.padding(padding)) {
                items(posts, key = { it.id }) { post ->
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
                                    posts = posts.map {
                                        if (it.id == post.id) {
                                            it.copy(
                                                liked = it.liked != true,
                                                likeCount = (it.likeCount ?: 0) +
                                                        if (it.liked == true) -1 else 1
                                            )
                                        } else it
                                    }
                                },
                                onResult = { resp ->
                                    posts = posts.map {
                                        if (it.id == post.id) {
                                            it.copy(liked = resp.liked, likeCount = resp.likeCount)
                                        } else it
                                    }
                                },
                                rollback = {
                                    posts = posts.map { if (it.id == post.id) original else it }
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
