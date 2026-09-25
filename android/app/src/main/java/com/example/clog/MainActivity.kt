package com.example.clog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.AuthStore
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Post
import com.example.clog.data.api.User
import com.example.clog.ui.AuthScreen
import com.example.clog.ui.Avatar
import com.example.clog.ui.ClogBackTopBar
import com.example.clog.ui.FavoritesScreen
import com.example.clog.ui.clogPressable
import com.example.clog.ui.clogRowClickable
import com.example.clog.ui.FeedScreen
import com.example.clog.ui.HistoryScreen
import com.example.clog.ui.NotificationScreen
import com.example.clog.ui.PostDetailScreen
import com.example.clog.ui.ProfileScreen
import com.example.clog.ui.PublishScreen
import com.example.clog.ui.SearchScreen
import com.example.clog.ui.SplashScreen
import com.example.clog.ui.UserProfileScreen
import com.example.clog.ui.initialOf
import com.example.clog.ui.theme.ClogTheme
import com.example.clog.ui.theme.PaperInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 底部三 Tab：发现｜创作｜通知（纯文字） */
private enum class MainTab { Discover, Create, Notifications }

/** 页面栈里的页面：返回 = 出栈，回到上一个页面 */
private sealed class Page {
    data object Profile : Page()
    data object Search : Page()
    data object Publish : Page()
    data object Favorites : Page()
    data object History : Page()
    data class UserProfile(val userId: Long) : Page()
    data class PostDetail(val postId: Long) : Page()
}

/** 页面栈的保存器：旋转屏幕后导航栈不丢 */
private val PageSaver = listSaver<SnapshotStateList<Page>, String>(
    save = { list ->
        list.map {
            when (it) {
                Page.Profile -> "profile"
                Page.Search -> "search"
                Page.Publish -> "publish"
                Page.Favorites -> "fav"
                Page.History -> "hist"
                is Page.UserProfile -> "user:${it.userId}"
                is Page.PostDetail -> "post:${it.postId}"
            }
        }
    },
    restore = { strings ->
        strings.map { s ->
            when {
                s == "profile" -> Page.Profile
                s == "search" -> Page.Search
                s == "publish" -> Page.Publish
                s == "fav" -> Page.Favorites
                s == "hist" -> Page.History
                s.startsWith("user:") -> Page.UserProfile(s.removePrefix("user:").toLong())
                else -> Page.PostDetail(s.removePrefix("post:").toLong())
            }
        }.toMutableStateList()
    }
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClogTheme {
                // 旋转屏幕不重放开屏（saveable），开屏与主界面交叉淡化
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(400),
                    label = "splash"
                ) { splash ->
                    if (splash) {
                        SplashScreen(onFinished = { showSplash = false })
                    } else {
                        ClogApp()
                    }
                }
            }
        }
    }
}

/** clog 的「大脑」：登录态 + 三 Tab 主界面 + 页面栈驱动的覆盖层 + 左侧边栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClogApp() {
    val context = LocalContext.current
    val authStore = remember { AuthStore(context) }

    var me by remember { mutableStateOf(authStore.user()) }
    // 旋转屏幕时保持 Tab 与导航栈
    var tab by rememberSaveable { mutableStateOf(MainTab.Discover) }
    var feedRefreshKey by rememberSaveable { mutableIntStateOf(0) }
    var pageStack by rememberSaveable(stateSaver = PageSaver) {
        mutableStateOf(mutableStateListOf<Page>())
    }
    // 渲染栈：按栈顺序渲染覆盖层（含退出动画中的页），旋转屏幕后由 pageStack 重建
    val renderStack = remember { mutableStateListOf<Page>() }
    // 保持各 Tab 的 rememberSaveable 状态：切换回来不重新加载、不闪骨架
    val tabStateHolder = rememberSaveableStateHolder()

    // 进页面压栈，返回出栈——无论在哪一层，返回都回到上一页
    fun push(page: Page) { pageStack.add(page) }
    fun pop() { if (pageStack.isNotEmpty()) pageStack.removeAt(pageStack.lastIndex) }
    fun clearStack() { pageStack.clear() }

    // 启动时恢复 token，之后所有请求自动带认证头
    remember { ApiClient.token = authStore.token() }

    // 未登录：登录/注册页
    if (me == null) {
        AuthScreen(
            authStore = authStore,
            onAuthed = { user ->
                me = user
                feedRefreshKey++
            }
        )
        return
    }

    val currentUser = me!!
    val scope = rememberCoroutineScope()

    // —— 左侧边栏：3/4 屏宽，打开时主内容被推向右侧、只露出 1/4；仅发现主界面可打开 ——
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp
    // 动画值只保存在 State 里，只在 graphicsLayer 的绘制期 lambda 内读取——
    // 不触发组合期重组，也不触发布局，每帧只重绘图层（修复实测 51% 掉帧）
    val drawerOffsetAnim = animateDpAsState(
        targetValue = if (drawerOpen) 0.dp else -drawerWidth,
        animationSpec = tween(220),
        label = "drawerOffset"
    )
    val contentOffsetAnim = animateDpAsState(
        targetValue = if (drawerOpen) drawerWidth else 0.dp,
        animationSpec = tween(220),
        label = "contentOffset"
    )
    // 遮罩：侧边栏打开时主界面变暗（30% 黑），与推拉同步淡入淡出，制造视觉深度
    val scrimAlpha by animateFloatAsState(
        targetValue = if (drawerOpen) 0.3f else 0f,
        animationSpec = tween(220),
        label = "scrimAlpha"
    )
    val density = LocalDensity.current
    val edgePx = with(density) { 30.dp.toPx() }
    val openEnabled = tab == MainTab.Discover && pageStack.isEmpty()
    val drawerOpenNow by rememberUpdatedState(drawerOpen)
    val openEnabledNow by rememberUpdatedState(openEnabled)

    // 系统返回键：弹出栈顶页面。发布页内部自带草稿确认（注册更晚，优先处理）。
    BackHandler(enabled = pageStack.isNotEmpty()) { pop() }
    // 侧边栏打开时，返回键先关侧边栏
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容：侧边栏打开时整体右移；从屏幕左缘向右滑可呼出侧边栏
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = contentOffsetAnim.value.roundToPx().toFloat()
                }
                .pointerInput(Unit) {
                    var startX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { startX = it.x },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0 && startX < edgePx && !drawerOpenNow && openEnabledNow) {
                                drawerOpen = true
                            } else if (dragAmount < 0 && drawerOpenNow) {
                                drawerOpen = false
                            }
                        }
                    )
                }
        ) {
            // —— 主界面：三 Tab，始终保持在组合中（覆盖层盖上时状态不丢） ——
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ClogTopBar(
                        me = currentUser,
                        onSearch = { push(Page.Search) },
                        // 仅发现主界面：点头像呼出侧边栏
                        onAvatarClick = {
                            if (tab == MainTab.Discover && pageStack.isEmpty()) drawerOpen = true
                        }
                    )
                },
                bottomBar = {
                    ClogNavBar(currentTab = tab, onSelect = { tab = it })
                },
                // 发布：中央悬浮灯色圆钮（不再挤在底栏里）
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { push(Page.Publish) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "发布",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { innerPadding ->
                tabStateHolder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        MainTab.Discover -> FeedScreen(
                            padding = innerPadding,
                            refreshKey = feedRefreshKey,
                            onPostClick = { push(Page.PostDetail(it.id)) }
                        )
                        MainTab.Create -> Unit // 创作走 FAB，不占 Tab
                        MainTab.Notifications -> NotificationScreen(
                            padding = innerPadding,
                            refreshKey = feedRefreshKey,
                            onOpenPost = { push(Page.PostDetail(it)) }
                        )
                    }
                }
            }

            // —— 覆盖层：按页面栈顺序渲染（后进的在上面），保持进出场动画 ——

            // 渲染栈同步：栈里新增的页按顺序挂上；被弹出的页等退出动画播完再移除
            LaunchedEffect(pageStack.toList()) {
                val snapshot = pageStack.toList()
                for (i in snapshot.indices) {
                    if (renderStack.getOrNull(i) != snapshot[i]) {
                        if (i < renderStack.size) renderStack[i] = snapshot[i]
                        else renderStack.add(snapshot[i])
                    }
                }
                val extras = renderStack.drop(snapshot.size)
                if (extras.isNotEmpty()) {
                    delay(200) // 等退出动画播完再移除
                    extras.forEach { renderStack.remove(it) }
                }
            }

            renderStack.forEach { page ->
                AnimatedVisibility(
                    visible = page in pageStack,
                    // 瞬间进入：点击后立即呈现目标页面（骨架屏兜底），不闪烁上一页内容；
                    // 返回时保留短暂淡出，自然不拖沓
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(160))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (page) {
                            Page.Profile -> ProfilePage(
                                me = currentUser,
                                refreshKey = feedRefreshKey,
                                onBack = { pop() },
                                onLogout = {
                                    clearStack()
                                    authStore.clear()
                                    ApiClient.token = null
                                    me = null
                                },
                                onUserUpdated = { updated ->
                                    me = updated
                                    authStore.token()?.let { t -> authStore.save(t, updated) }
                                },
                                onOpenPost = { push(Page.PostDetail(it.id)) },
                                onOpenFavorites = { push(Page.Favorites) }
                            )
                            Page.Search -> SearchHost(
                                onBack = { pop() },
                                onOpenPost = { push(Page.PostDetail(it.id)) },
                                onOpenUser = { push(Page.UserProfile(it)) }
                            )
                            Page.Publish -> PublishScreen(
                                onPublished = {
                                    clearStack()
                                    tab = MainTab.Discover
                                    feedRefreshKey++
                                },
                                onBack = { pop() }
                            )
                            Page.Favorites -> FavoritesScreen(
                                me = currentUser,
                                onBack = { pop() },
                                onOpenPost = { push(Page.PostDetail(it.id)) }
                            )
                            Page.History -> HistoryScreen(
                                onBack = { pop() },
                                onOpenPost = { push(Page.PostDetail(it.id)) }
                            )
                            is Page.UserProfile -> UserProfileScreen(
                                userId = page.userId,
                                onBack = { pop() },
                                onOpenPost = { push(Page.PostDetail(it.id)) }
                            )
                            is Page.PostDetail -> PostDetailScreen(
                                postId = page.postId,
                                me = currentUser,
                                onBack = { pop() },
                                onDeleted = {
                                    pop()
                                    feedRefreshKey++
                                },
                                onOpenUser = { push(Page.UserProfile(it)) }
                            )
                        }
                    }
                }
            }
        }
        // 侧边栏打开时：半透明黑遮罩压在内容上（深度感），点击即关闭
        if (drawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clogRowClickable { drawerOpen = false }
            )
        }

        // 侧边栏面板：3/4 屏宽，右边缘画一条过渡竖线
        Box(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = drawerOffsetAnim.value.roundToPx().toFloat()
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            ClogDrawer(
                me = currentUser,
                onProfile = {
                    drawerOpen = false
                    push(Page.Profile)
                },
                onFavorites = {
                    drawerOpen = false
                    push(Page.Favorites)
                },
                onHistory = {
                    drawerOpen = false
                    push(Page.History)
                }
            )
        }
    }
}

/** 左侧边栏：头像+昵称（个人主页入口）在左上角，下面是收藏/浏览历史 */
@Composable
private fun ClogDrawer(
    me: User,
    onProfile: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        // 个人主页入口：头像在上，昵称与用户名在头像下面
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clogRowClickable { onProfile() }
                .padding(vertical = 10.dp)
        ) {
            Avatar(letter = initialOf(me.nickname), size = 48)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = me.nickname,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = "@${me.username}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
        Spacer(modifier = Modifier.height(6.dp))
        DrawerItem("收藏") { onFavorites() }
        DrawerItem("浏览历史") { onHistory() }
    }
}

@Composable
private fun DrawerItem(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clogRowClickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(text = label, fontSize = 15.sp)
    }
}

/** 我的主页（独立页面，带返回键） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePage(
    me: User,
    refreshKey: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenFavorites: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "我的", onBack = onBack) }
    ) { padding ->
        ProfileScreen(
            padding = padding,
            me = me,
            refreshKey = refreshKey,
            onLogout = onLogout,
            onUserUpdated = onUserUpdated,
            onOpenPost = onOpenPost,
            onOpenFavorites = onOpenFavorites
        )
    }
}

/** 顶部栏：我的头像（左上角，点开侧边栏）+ 搜索入口 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClogTopBar(me: User, onSearch: () -> Unit, onAvatarClick: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        // clip 必须在 clickable 之前：涟漪被裁成圆形，与头像形状一致
                        .clip(CircleShape)
                        .clogPressable(onClick = onAvatarClick)
                        .padding(2.dp)
                ) {
                    Avatar(letter = initialOf(me.nickname), size = 32)
                }
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Image(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "搜索",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/** 底部导航：图标 + 文字两项（发现/通知），选中亮灯色、无胶囊指示器；发布是中央悬浮 FAB */
@Composable
private fun ClogNavBar(currentTab: MainTab, onSelect: (MainTab) -> Unit) {
    Column {
        // 顶部分隔线：底栏与信息流之间的纸页分界
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavTab(
                label = "发现",
                icon = Icons.Outlined.Search,
                selected = currentTab == MainTab.Discover
            ) { onSelect(MainTab.Discover) }
            NavTab(
                label = "通知",
                icon = Icons.Outlined.Notifications,
                selected = currentTab == MainTab.Notifications
            ) { onSelect(MainTab.Notifications) }
        }
    }
}

@Composable
private fun RowScope.NavTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
        icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** 搜索页壳：带返回键的顶栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHost(
    onBack: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onOpenUser: (Long) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "搜索", onBack = onBack) }
    ) { innerPadding ->
        SearchScreen(
            padding = innerPadding,
            onOpenPost = onOpenPost,
            onOpenUser = onOpenUser
        )
    }
}
