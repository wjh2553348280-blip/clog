package com.example.clog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.GsonSavers
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Post
import com.example.clog.data.api.ProfileUpdateRequest
import com.example.clog.data.api.User
import com.example.clog.data.api.UserProfile
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/** 旋转屏幕时保留用户资料/帖子列表数据的 Saver */
private val userProfileSaver = GsonSavers.ofNullable<UserProfile>(object : TypeToken<UserProfile>() {}.type)
private val userPostsSaver = GsonSavers.ofType<List<Post>>(object : TypeToken<List<Post>>() {}.type)

/** 我的主页（PDF 的「我的」Tab） */
@Composable
fun ProfileScreen(
    padding: PaddingValues,
    me: User,
    refreshKey: Int = 0,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenFavorites: () -> Unit
) {
    var profile by rememberSaveable(stateSaver = userProfileSaver) { mutableStateOf<UserProfile?>(null) }
    var myPosts by rememberSaveable(stateSaver = userPostsSaver) { mutableStateOf<List<Post>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        runCatching { ApiClient.api.userProfile(me.id) }
            .onSuccess { profile = it }
            .onFailure { toast(context, friendlyError(it)) }
        runCatching { ApiClient.api.userPosts(me.id) }
            .onSuccess { myPosts = it }
            .onFailure { toast(context, friendlyError(it)) }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Avatar(letter = initialOf(me.nickname), size = 88)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = me.nickname,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(
            text = "@${me.username}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        me.bio?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        // 资料加载完成前不显示统计，避免「0 粉丝」误导
        if (profile != null) {
            Row {
                StatItem(value = profile?.postCount ?: myPosts.size, label = "帖子")
                Spacer(modifier = Modifier.width(44.dp))
                StatItem(value = profile?.followingCount ?: 0, label = "关注")
                Spacer(modifier = Modifier.width(44.dp))
                StatItem(value = profile?.followerCount ?: 0, label = "粉丝")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        Row {
            OutlinedButton(onClick = { editing = true }) {
                Text("编辑资料")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onOpenFavorites) {
                Text("我的收藏")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "我的帖子",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (myPosts.isEmpty()) {
            EmptyState(
                title = "还没有发过帖子",
                subtitle = "点中间的 ＋，写下第一句吧",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        } else {
            myPosts.forEach { post ->
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
                                myPosts = myPosts.map {
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
                                myPosts = myPosts.map {
                                    if (it.id == post.id) {
                                        it.copy(liked = resp.liked, likeCount = resp.likeCount)
                                    } else it
                                }
                            },
                            rollback = {
                                myPosts = myPosts.map { if (it.id == post.id) original else it }
                            },
                            request = { ApiClient.api.toggleLike(post.id) }
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { confirmLogout = true }) {
            Text(
                text = "退出登录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (editing) {
        EditProfileDialog(
            me = me,
            error = editError,
            onSave = { nickname, bio ->
                editError = null
                scope.launch {
                    runCatching {
                        ApiClient.api.updateProfile(ProfileUpdateRequest(nickname, null, bio))
                    }.onSuccess { updated ->
                        onUserUpdated(updated)
                        editing = false
                    }.onFailure { e ->
                        // 保存失败：对话框保持打开，展示原因
                        editError = friendlyError(e)
                    }
                }
            },
            onCancel = {
                editing = false
                editError = null
            }
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "确定要退出当前账号吗？",
            confirmLabel = "退出",
            danger = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false }
        )
    }
}

@Composable
private fun StatItem(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 数字大而重（数据优先），标签小而轻——对比强烈的层级
        Text(
            text = formatCount(value),
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

/** 编辑资料对话框 */
@Composable
private fun EditProfileDialog(
    me: User,
    error: String?,
    onSave: (nickname: String, bio: String) -> Unit,
    onCancel: () -> Unit
) {
    var nickname by remember { mutableStateOf(me.nickname) }
    var bio by remember { mutableStateOf(me.bio ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("编辑资料") },
        text = {
            Column {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 140) bio = it },
                    label = { Text("简介") }
                )
                error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nickname.trim(), bio.trim()) },
                enabled = nickname.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

/** 他人主页（从搜索/评论进入） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    var profile by rememberSaveable(stateSaver = userProfileSaver) { mutableStateOf<UserProfile?>(null) }
    var posts by rememberSaveable(stateSaver = userPostsSaver) { mutableStateOf<List<Post>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId) {
        runCatching { ApiClient.api.userProfile(userId) }.onSuccess { profile = it }
        runCatching { ApiClient.api.userPosts(userId) }.onSuccess { posts = it }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "主页", onBack = onBack) }
    ) { padding ->
        val p = profile
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Avatar(letter = initialOf(p?.nickname), size = 88)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = p?.nickname ?: "加载中…",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = p?.username?.let { "@$it" } ?: "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            p?.bio?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row {
                StatItem(value = p?.postCount ?: 0, label = "帖子")
                Spacer(modifier = Modifier.width(44.dp))
                StatItem(value = p?.followingCount ?: 0, label = "关注")
                Spacer(modifier = Modifier.width(44.dp))
                StatItem(value = p?.followerCount ?: 0, label = "粉丝")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    // 乐观更新关注：按钮立即切换，失败回滚
                    val original = p
                    runOptimistic(
                        scope = scope,
                        context = context,
                        optimistic = {
                            profile = profile?.copy(
                                followed = profile?.followed != true,
                                followerCount = (profile?.followerCount ?: 0) +
                                        if (profile?.followed == true) -1 else 1
                            )
                        },
                        onResult = { resp ->
                            profile = profile?.copy(
                                followed = resp.following,
                                followerCount = resp.followerCount
                            )
                        },
                        rollback = { profile = original },
                        request = { ApiClient.api.toggleFollow(userId) }
                    )
                },
                enabled = p != null
            ) {
                Text(if (p?.followed == true) "已关注 ✓" else "＋ 关注")
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (posts.isEmpty()) {
                EmptyState(
                    title = "TA 还没有发过帖子",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp)
                )
            } else {
                posts.forEach { post ->
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
