package com.example.clog.data

import android.content.Context
import com.example.clog.data.api.User
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

/**
 * 登录状态存储：token + 当前用户资料，存手机本地。
 */
class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("clog_auth", Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    fun save(token: String, user: User) {
        prefs.edit()
            .putString("token", token)
            .putString("user", gson.toJson(user))
            .apply()
    }

    fun token(): String? = prefs.getString("token", null)

    fun user(): User? = prefs.getString("user", null)?.let {
        runCatching { gson.fromJson(it, User::class.java) }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
