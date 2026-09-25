package com.example.clog.data.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层单例。
 * 本机开发时手机通过 `adb reverse tcp:8000 tcp:8000` 访问电脑上的后端，
 * 所以 BASE_URL 用 127.0.0.1。部署到服务器后改成 https://你的域名/。
 */
object ApiClient {

    const val BASE_URL = "http://127.0.0.1:8000/"

    /** 登录成功后由 AuthStore 注入，拦截器自动带上 Authorization 头 */
    var token: String? = null

    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                token?.let { header("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)

    /** 后端返回的媒体地址是 /static/xxx 相对路径，拼成完整 URL */
    fun mediaUrl(path: String): String =
        if (path.startsWith("http")) path else BASE_URL.trimEnd('/') + path
}
