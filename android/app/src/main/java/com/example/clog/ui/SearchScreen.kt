package com.example.clog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.GsonSavers
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Community
import com.example.clog.data.api.Post
import com.example.clog.data.api.SearchResponse
import com.example.clog.data.api.User
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/** 旋转屏幕时保留搜索结果数据的 Saver */
private val searchResultSaver = GsonSavers.ofNullable<SearchResponse>(object : TypeToken<SearchResponse>() {}.type)

/** 搜索页：搜帖子 / 用户 */
@Composable
fun SearchScreen(
    padding: PaddingValues,
    onOpenPost: (Post) -> Unit,
    onOpenUser: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var result by rememberSaveable(stateSaver = searchResultSaver) { mutableStateOf<SearchResponse?>(null) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            error = null
            runCatching { ApiClient.api.search(q) }
                .onSuccess { result = it; searched = true }
                .onFailure {
                    // 网络错误如实展示，不能伪装成「没有找到相关内容」
                    error = friendlyError(it)
                    result = null
                    searched = true
                }
        }
    }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索帖子、用户、社区…", fontSize = 14.sp) },
            singleLine = true,
            trailingIcon = {
                Image(
                    painter = painterResource(com.example.clog.R.drawable.ic_search),
                    contentDescription = "搜索",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier
                        .size(24.dp)
                        .clogPressable { doSearch() }
                        .padding(4.dp)
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (!searched) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "输入关键词，搜索帖子与用户",
                    subtitle = "回车或点右侧搜索键"
                )
            }
        } else {
            val r = result
            if (error != null) {
                ErrorRetry(
                    padding = PaddingValues(0.dp),
                    message = error ?: "搜索失败",
                    onRetry = { doSearch() }
                )
            } else if (r == null || (r.posts.isNullOrEmpty() && r.users.isNullOrEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "没有找到相关内容",
                        subtitle = "换个关键词试试"
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    r.users?.takeIf { it.isNotEmpty() }?.let { list ->
                        item { SectionTitle("用户") }
                        items(list, key = { "u${it.id}" }) { u ->
                            UserRow(u) { onOpenUser(u.id) }
                        }
                    }
                    r.posts?.takeIf { it.isNotEmpty() }?.let { list ->
                        item { SectionTitle("帖子") }
                        items(list, key = { "p${it.id}" }) { p ->
                            PostCard(
                                post = p,
                                onClick = { onOpenPost(p) },
                                onLike = {
                                    // 乐观更新：先变色，失败回滚
                                    val original = p
                                    runOptimistic(
                                        scope = scope,
                                        context = context,
                                        optimistic = {
                                            result = result?.copy(
                                                posts = result?.posts?.map {
                                                    if (it.id == p.id) {
                                                        it.copy(
                                                            liked = it.liked != true,
                                                            likeCount = (it.likeCount ?: 0) +
                                                                    if (it.liked == true) -1 else 1
                                                        )
                                                    } else it
                                                }
                                            )
                                        },
                                        onResult = { resp ->
                                            result = result?.copy(
                                                posts = result?.posts?.map {
                                                    if (it.id == p.id) {
                                                        it.copy(liked = resp.liked, likeCount = resp.likeCount)
                                                    } else it
                                                }
                                            )
                                        },
                                        rollback = {
                                            result = result?.copy(
                                                posts = result?.posts?.map {
                                                    if (it.id == p.id) original else it
                                                }
                                            )
                                        },
                                        request = { ApiClient.api.toggleLike(p.id) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun UserRow(u: User, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clogRowClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Avatar(letter = initialOf(u.nickname), size = 34)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = u.nickname,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${u.username}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}
