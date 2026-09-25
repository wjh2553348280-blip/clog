package com.example.clog.data.api

/**
 * 与后端 JSON 一一对应的数据模型。
 * 字段用驼峰命名，Gson 的 LOWER_CASE_WITH_UNDERSCORES 策略会自动映射后端的
 * 下划线命名（如 avatar_emoji → avatarEmoji）。
 */

data class User(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarEmoji: String?,
    val bio: String?,
    val createdAt: String?
) {
    val avatar: String get() = avatarEmoji?.takeIf { it.isNotBlank() } ?: "🐱"
}

data class Community(
    val id: Long,
    val name: String,
    val slug: String?,
    val description: String?,
    val icon: String?,
    val postCount: Int?
)

data class MediaItem(
    val type: String,      // image / video
    val url: String        // /static/xxx.jpg（相对后端地址）
)

data class Post(
    val id: Long,
    val title: String?,
    val content: String,
    val media: List<MediaItem>?,
    val createdAt: String?,
    val likeCount: Int?,
    val commentCount: Int?,
    val collectCount: Int?,
    val viewCount: Int?,
    val liked: Boolean?,
    val collected: Boolean?,
    val author: User?,
    val community: Community?
)

data class Comment(
    val id: Long,
    val content: String,
    val likeCount: Int?,
    val createdAt: String?,
    val liked: Boolean?,
    val user: User?,
    val parentId: Long?,
    val replyCount: Int?,
    val floorNumber: Int?,
    val replies: List<Comment>? = null,
    val hasMoreReplies: Boolean? = null
)

data class HotItem(
    val post: Post?,
    val hotScore: Double?
)

data class AuthResponse(
    val accessToken: String?,
    val tokenType: String?,
    val user: User?
)

data class UserProfile(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarEmoji: String?,
    val bio: String?,
    val createdAt: String?,
    val postCount: Int?,
    val followerCount: Int?,
    val followingCount: Int?,
    val followed: Boolean?
)

data class NotificationItem(
    val id: Long,
    val type: String?,       // like / comment / follow
    val read: Boolean?,
    val createdAt: String?,
    val actor: User?,
    val postId: Long?,
    val commentId: Long?,
    val postSnippet: String?
)

data class NotificationListResponse(
    val items: List<NotificationItem>?,
    val unread: Int?
)

data class SearchResponse(
    val posts: List<Post>?,
    val users: List<User>?,
    val communities: List<Community>?
)

data class HistoryItem(
    val post: Post?,
    val viewedAt: String?
)

// ---------- 请求体 ----------

data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String,
    val avatar_emoji: String = "🐱"
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class CreatePostRequest(
    val title: String,
    val content: String,
    val community_id: Long,
    val media: List<MediaItem> = emptyList()
)

data class CommentRequest(val content: String, val parent_id: Long? = null)

data class ProfileUpdateRequest(
    val nickname: String? = null,
    val avatar_emoji: String? = null,
    val bio: String? = null
)

data class ReportRequest(val reason: String = "")

// ---------- 响应体 ----------

data class LikeResponse(val liked: Boolean?, val likeCount: Int?)

data class FavoriteResponse(val collected: Boolean?, val collectCount: Int?)

data class FollowResponse(val following: Boolean?, val followerCount: Int?)

data class MediaUploadResponse(val type: String?, val url: String?)

data class OkResponse(val ok: Boolean?)
