package com.example.clog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.GsonSavers
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.NotificationItem
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/** 旋转屏幕时保留通知列表数据的 Saver */
private val notificationListSaver = GsonSavers.ofType<List<NotificationItem>>(object : TypeToken<List<NotificationItem>>() {}.type)

/** 通知中心：赞/评/关注聚合（PDF 的「通知」Tab） */
@Composable
fun NotificationScreen(
    padding: PaddingValues,
    refreshKey: Int = 0,
    onOpenPost: (Long) -> Unit
) {
    var items by rememberSaveable(stateSaver = notificationListSaver) { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { ApiClient.api.notifications() }.onSuccess { resp ->
            items = resp.items ?: emptyList()
            unread = resp.unread ?: 0
            loaded = true
            error = null
        }.onFailure {
            error = friendlyError(it)
        }
    }

    LaunchedEffect(refreshKey) { reload() }

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "通知",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            if (unread > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($unread 条未读)",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (unread > 0) {
                TextButton(onClick = {
                    // 乐观更新：立即清零，失败回滚并提示
                    val originalItems = items
                    val originalUnread = unread
                    items = items.map { it.copy(read = true) }
                    unread = 0
                    scope.launch {
                        runCatching { ApiClient.api.readAllNotifications() }.onFailure {
                            items = originalItems
                            unread = originalUnread
                            toast(context, friendlyError(it))
                        }
                    }
                }) {
                    Text("全部已读", fontSize = 13.sp)
                }
            }
        }
        if (error != null && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "加载失败",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = { scope.launch { reload() } }) { Text("重试") }
                }
            }
        } else if (!loaded) {
            Column {
                repeat(6) { PostSkeleton() }
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "还没有通知"
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { n ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clogRowClickable(enabled = n.postId != null) {
                                n.postId?.let { onOpenPost(it) }
                                // 本地先标记已读，服务器侧由「全部已读」同步
                                if (n.read != true) {
                                    items = items.map { if (it.id == n.id) it.copy(read = true) else it }
                                    unread = (unread - 1).coerceAtLeast(0)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Avatar(letter = initialOf(n.actor?.nickname), size = 36)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = n.actor?.nickname ?: "有人",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (n.type) {
                                        "like" -> "赞了你的帖子"
                                        "comment" -> "评论了你的帖子"
                                        "follow" -> "关注了你"
                                        else -> "提到了你"
                                    },
                                    fontSize = 14.sp
                                )
                            }
                            n.postSnippet?.let { snippet ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "“$snippet”",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 未读红点（不显示时间小字）
                        if (n.read != true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
