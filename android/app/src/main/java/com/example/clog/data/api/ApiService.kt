package com.example.clog.data.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** clog 后端全部接口（与 ~/clog-server/app/routers 一一对应） */
interface ApiService {

    // ---------- 认证 ----------
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    // ---------- 社区 ----------
    @GET("communities")
    suspend fun communities(): List<Community>

    // ---------- 信息流 ----------
    @GET("feed")
    suspend fun feed(@Query("page") page: Int = 1, @Query("size") size: Int = 20): List<Post>

    @GET("hot")
    suspend fun hot(): List<HotItem>

    @GET("feed/community/{id}")
    suspend fun communityFeed(@Path("id") id: Long, @Query("page") page: Int = 1, @Query("size") size: Int = 20): List<Post>

    @GET("feed/following")
    suspend fun followingFeed(@Query("page") page: Int = 1, @Query("size") size: Int = 20): List<Post>

    // ---------- 帖子 ----------
    @POST("posts")
    suspend fun createPost(@Body body: CreatePostRequest): Post

    @GET("posts/{id}")
    suspend fun postDetail(@Path("id") id: Long): Post

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: Long): OkResponse

    @POST("posts/{id}/report")
    suspend fun reportPost(@Path("id") id: Long, @Body body: ReportRequest): OkResponse

    // ---------- 互动 ----------
    @POST("posts/{id}/like")
    suspend fun toggleLike(@Path("id") id: Long): LikeResponse

    @POST("posts/{id}/favorite")
    suspend fun toggleFavorite(@Path("id") id: Long): FavoriteResponse

    @GET("posts/{id}/comments")
    suspend fun comments(
        @Path("id") id: Long,
        @Query("sort") sort: String = "hot",
        @Query("author_only") authorOnly: Boolean = false
    ): List<Comment>

    @GET("comments/{id}/replies")
    suspend fun commentReplies(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50
    ): List<Comment>

    @POST("posts/{id}/comments")
    suspend fun addComment(@Path("id") id: Long, @Body body: CommentRequest): Comment

    @DELETE("comments/{id}")
    suspend fun deleteComment(@Path("id") id: Long): OkResponse

    @POST("comments/{id}/like")
    suspend fun toggleCommentLike(@Path("id") id: Long): LikeResponse

    @POST("users/{id}/follow")
    suspend fun toggleFollow(@Path("id") id: Long): FollowResponse

    // ---------- 用户 ----------
    @GET("users/{id}")
    suspend fun userProfile(@Path("id") id: Long): UserProfile

    @PUT("users/me")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): User

    @GET("users/{id}/posts")
    suspend fun userPosts(@Path("id") id: Long): List<Post>

    @GET("users/{id}/favorites")
    suspend fun userFavorites(@Path("id") id: Long): List<Post>

    @GET("users/me/history")
    suspend fun myHistory(): List<HistoryItem>

    @GET("users/{id}/followers")
    suspend fun followers(@Path("id") id: Long): List<User>

    @GET("users/{id}/following")
    suspend fun following(@Path("id") id: Long): List<User>

    // ---------- 通知 ----------
    @GET("notifications")
    suspend fun notifications(): NotificationListResponse

    @POST("notifications/read-all")
    suspend fun readAllNotifications(): OkResponse

    // ---------- 搜索 ----------
    @GET("search")
    suspend fun search(@Query("q") q: String): SearchResponse

    // ---------- 媒体上传 ----------
    @Multipart
    @POST("upload/media")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): MediaUploadResponse
}
