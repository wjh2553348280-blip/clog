package com.example.clog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clog.data.AuthStore
import com.example.clog.data.api.ApiClient
import com.example.clog.data.api.LoginRequest
import com.example.clog.data.api.RegisterRequest
import com.example.clog.data.api.User
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** 登录/注册页：登录成功后回调 onAuthed */
@Composable
fun AuthScreen(authStore: AuthStore, onAuthed: (User) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }  // 0=登录 1=注册
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text(
            text = "clog",
            color = MaterialTheme.colorScheme.primary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "一个暖黄色的文字社区",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(36.dp))

        // 登录/注册切换：衬线文字 + 灯色下划线（书房气质，替代默认 Material TabRow）
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuthTab("登录", tab == 0) { tab = 0 }
            AuthTab("注册", tab == 1) { tab = 1 }
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名（字母数字下划线）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（至少 6 位）") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "隐藏" else "显示",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clogPressable { passwordVisible = !passwordVisible }
                        .padding(8.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (tab == 1) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
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
                loading = true
                error = null
                scope.launch {
                    try {
                        val resp = if (tab == 0) {
                            ApiClient.api.login(LoginRequest(username.trim(), password))
                        } else {
                            ApiClient.api.register(
                                RegisterRequest(username.trim(), password, nickname.trim())
                            )
                        }
                        val token = resp.accessToken ?: throw Exception("服务器没有返回 token")
                        val user = resp.user ?: throw Exception("服务器没有返回用户信息")
                        authStore.save(token, user)
                        ApiClient.token = token
                        onAuthed(user)
                    } catch (e: Exception) {
                        error = friendlyError(e)
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading && username.isNotBlank() && password.isNotBlank() &&
                    (tab == 0 || nickname.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (tab == 0) "登录" else "注册并进入", fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { tab = if (tab == 0) 1 else 0 }) {
            Text(
                text = if (tab == 0) "还没有账号？去注册" else "已有账号？去登录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

/** 登录/注册切换标签：衬线文字，选中=灯色 + 细下划线 */
@Composable
private fun AuthTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clogPressable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 28.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** 把网络异常翻译成人话 */
fun friendlyError(e: Throwable): String {
    if (e is HttpException) {
        val detail = runCatching {
            val body = e.response()?.errorBody()?.string()
            if (body != null) {
                org.json.JSONObject(body).optString("detail")
            } else null
        }.getOrNull()
        if (!detail.isNullOrBlank()) return detail
        return when (e.code()) {
            401 -> "未登录或登录已过期"
            403 -> "没有权限"
            404 -> "内容不存在"
            else -> "请求失败（${e.code()}）"
        }
    }
    if (e is java.net.ConnectException || e is java.net.UnknownHostException) {
        return "连不上服务器，请确认后端已启动"
    }
    return e.message ?: "网络错误，请稍后重试"
}
