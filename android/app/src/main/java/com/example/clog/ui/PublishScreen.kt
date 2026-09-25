package com.example.clog.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.Community
import com.example.clog.data.api.CreatePostRequest
import com.example.clog.data.api.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 发布页：写短帖 + 选社区 + 选图/视频 → 上传媒体 → 发布 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    onPublished: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var communities by remember { mutableStateOf<List<Community>>(emptyList()) }
    var communityId by remember { mutableStateOf<Long?>(null) }
    var communityError by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun loadCommunities() {
        communityError = null
        scope.launch {
            runCatching { ApiClient.api.communities() }.onSuccess { list ->
                communities = list
                if (communityId == null && list.isNotEmpty()) communityId = list.first().id
            }.onFailure { communityError = friendlyError(it) }
        }
    }

    LaunchedEffect(Unit) { loadCommunities() }

    // 有草稿时，返回键和顶栏 ← 都要先确认
    val hasDraft = title.isNotBlank() || content.isNotBlank() || imageUris.isNotEmpty() || videoUri != null
    fun requestBack() {
        if (hasDraft && !publishing) confirmDiscard = true else onBack()
    }
    BackHandler { requestBack() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> imageUris = uris }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) videoUri = uri }

    // 发布页输入框：只保留底部横线（透明容器 + 下划线指示器）
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
    )
    // 缩略图按 70dp 目标尺寸解码（≈2.75x 密度下的像素值），不解码 4000px 原图
    val thumbPx = (70 * context.resources.displayMetrics.density).toInt()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ClogBackTopBar(title = "发布", onBack = { requestBack() }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 主题：最上面，20 字以内（下划线式输入框）
            TextField(
                value = title,
                onValueChange = { if (it.length <= 20) title = it },
                placeholder = { Text("主题（20 字以内）", fontSize = 14.sp) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${title.length}/20",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(modifier = Modifier.height(14.dp))
            // 正文（下划线式输入框）
            TextField(
                value = content,
                onValueChange = { if (it.length <= 2000) content = it },
                placeholder = { Text("正文…", fontSize = 14.sp) },
                minLines = 7,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${content.length}/2000",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 底部：添加图片/视频（纯文字选项）
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MediaOption(
                    label = "＋ 图片",
                    subtitle = if (imageUris.isEmpty()) "" else "已选 ${imageUris.size}/9",
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                MediaOption(
                    label = "＋ 视频",
                    subtitle = if (videoUri == null) "" else "已选 1",
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )
            }
            if (imageUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    imageUris.forEach { uri ->
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(thumbPx) // 只约束宽度，保持比例；预览用，不影响上传原图
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            // 右上角删除角标
                            Text(
                                text = "×",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(bottomStart = 6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clogPressable { imageUris = imageUris.filterNot { it == uri } }
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            videoUri?.let { v ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        VideoThumb(uri = v)
                        Text(
                            text = "×",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(bottomStart = 6.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clogPressable { videoUri = null }
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "视频",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            communityError?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { loadCommunities() }) { Text("重试", fontSize = 13.sp) }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Button(
                onClick = {
                    val cid = communityId
                    if (cid == null) {
                        error = "请先选择社区"
                        return@Button
                    }
                    publishing = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                // 1. 逐个上传媒体
                                val media = mutableListOf<MediaItem>()
                                imageUris.forEach { uri ->
                                    val resp = uploadMedia(context, uri)
                                    resp.url?.let { media.add(MediaItem("image", it)) }
                                }
                                videoUri?.let { uri ->
                                    val resp = uploadMedia(context, uri)
                                    resp.url?.let { media.add(MediaItem("video", it)) }
                                }
                                // 2. 发布帖子
                                ApiClient.api.createPost(
                                    CreatePostRequest(
                                        title = title.trim(),
                                        content = content.trim(),
                                        community_id = cid,
                                        media = media
                                    )
                                )
                            }
                        }
                        publishing = false
                        result.onSuccess {
                            toast(context, "发布成功")
                            onPublished()
                        }
                            .onFailure { e -> error = friendlyError(e) }
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank() && !publishing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (publishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("发布", fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "放弃发布",
            message = "草稿还没发布，离开后内容会丢失。确定放弃吗？",
            confirmLabel = "放弃",
            danger = true,
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

/** 把相册选的文件上传到后端，返回 {type, url}（流式上传：64KB 缓冲，不把整个文件读进内存） */
private suspend fun uploadMedia(context: android.content.Context, uri: Uri) =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = "${System.currentTimeMillis()}-${uri.lastPathSegment ?: "file"}"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val body = object : okhttp3.RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: okio.BufferedSink) {
                resolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                    }
                } ?: throw java.io.IOException("无法读取所选文件")
            }
        }
        val part = MultipartBody.Part.createFormData("file", name, body)
        ApiClient.api.uploadMedia(part)
    }

/** 发布页底部的媒体选项：纯文字，无底色 */
@Composable
private fun MediaOption(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clogPressable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

/** 视频首帧缩略图（MediaMetadataRetriever 是系统 API，无新依赖；缩到 140px 避免大图） */
@Composable
private fun VideoThumb(uri: Uri) {
    val ctx = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ctx, uri)
                    val frame = retriever.getFrameAtTime(1_000_000)
                    val target = 140
                    if (frame != null && (frame.width > target || frame.height > target)) {
                        val scale = target.toFloat() / maxOf(frame.width, frame.height)
                        android.graphics.Bitmap.createScaledBitmap(
                            frame,
                            (frame.width * scale).toInt(),
                            (frame.height * scale).toInt(),
                            true
                        )
                    } else frame
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "视频预览",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
    }
}
