package com.example.clog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.GsonSavers
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Post
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 旋转屏幕时保留帖子列表数据的 Saver */
private val postListSaver = GsonSavers.ofType<List<Post>>(object : TypeToken<List<Post>>() {}.type)

/**
 * 通用帖子列表：下拉刷新 + 滚到底自动加载下一页 + 首屏失败重试。
 * loadPage 由调用方提供（推荐流/社区流/关注流都用它）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostList(
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
    loadPage: suspend (page: Int) -> List<Post>,
    onPostClick: (Post) -> Unit,
    emptyTitle: String = "这里还没有帖子",
    emptySubtitle: String = "点中间的 ＋，写下第一句吧"
) {
    var posts by rememberSaveable(stateSaver = postListSaver) { mutableStateOf<List<Post>>(emptyList()) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    // 仅下拉手势触发 refreshing（顶部指示器）；自动加载走 autoLoading（骨架屏，无指示器）
    var refreshing by remember { mutableStateOf(false) }
    var autoLoading by remember { mutableStateOf(posts.isEmpty()) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load(pageNum: Int, append: Boolean) {
        val newPosts = loadPage(pageNum)
        posts = if (append) posts + newPosts else newPosts
        page = pageNum
    }

    // refreshKey 变化时刷新：无内容→骨架静默加载；已有内容→后台静默替换，不打断浏览
    LaunchedEffect(refreshKey) {
        error = null
        if (posts.isEmpty()) {
            autoLoading = true
            try {
                load(1, append = false)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                autoLoading = false
            }
        } else {
            runCatching { load(1, append = false) }
            // 静默刷新失败不打扰用户，保留现有内容
        }
    }

    // 滚到底部自动加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= posts.size - 3 && posts.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !loadingMore && !refreshing && !autoLoading) {
            loadingMore = true
            try {
                load(page + 1, append = true)
            } catch (_: Exception) {
                // 加载更多失败不打扰用户，下次滚动再试
            } finally {
                loadingMore = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (error != null && posts.isEmpty()) {
            // 首屏失败：统一错误态 + 重试
            ErrorRetry(
                padding = PaddingValues(0.dp),
                message = error ?: "加载失败",
                onRetry = {
                    scope.launch {
                        error = null
                        autoLoading = true
                        try {
                            load(1, append = false)
                        } catch (e: Exception) {
                            error = friendlyError(e)
                        } finally {
                            autoLoading = false
                        }
                    }
                }
            )
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        val started = System.currentTimeMillis()
                        try {
                            load(1, append = false)
                        } catch (e: Exception) {
                            error = friendlyError(e)
                        } finally {
                            // 至少悬停 600ms：等新内容刷新好，指示器再返回
                            val elapsed = System.currentTimeMillis() - started
                            if (elapsed < 600) delay(600 - elapsed)
                            refreshing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (posts.isEmpty() && autoLoading) {
                    // 骨架屏：保持卡片结构稳定
                    Column {
                        repeat(6) { PostSkeleton() }
                    }
                } else if (posts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = emptyTitle,
                            subtitle = emptySubtitle
                        )
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                onClick = { onPostClick(post) },
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
                        if (loadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 首页：推荐流（热度排序）／关注流（已关注用户的新帖）切换 */
@Composable
fun FeedScreen(
    padding: PaddingValues,
    refreshKey: Int = 0,
    onPostClick: (Post) -> Unit
) {
    // 0=推荐 1=关注（旋转屏幕不丢）
    var mode by rememberSaveable { mutableIntStateOf(0) }
    // 两个列表各自保持状态：切换不重建、不闪加载
    val modeStateHolder = rememberSaveableStateHolder()
    Column(modifier = Modifier.padding(padding)) {
        // 分段切换：两栏各占一半、各自居中，中间竖线分隔；衬线文字，选中亮灯色
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FeedTab("推荐", mode == 0) { mode = 0 }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FeedTab("关注", mode == 1) { mode = 1 }
            }
        }
        modeStateHolder.SaveableStateProvider(mode) {
            when (mode) {
                0 -> PostList(
                    refreshKey = refreshKey,
                    loadPage = { page -> ApiClient.api.feed(page) },
                    onPostClick = onPostClick
                )
                else -> PostList(
                    refreshKey = refreshKey,
                    loadPage = { page -> ApiClient.api.followingFeed(page) },
                    onPostClick = onPostClick,
                    emptyTitle = "还没有关注的人",
                    emptySubtitle = "去别人的主页点 ＋ 关注，这里会出现他们的帖子"
                )
            }
        }
    }
}

@Composable
private fun FeedTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clogPressable(onClick = onClick)
            .padding(vertical = 6.dp)
    )
}
