package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.CommentBody
import com.geowoof.core.network.model.NetworkComment
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPost
import com.geowoof.core.network.model.NetworkPostLike
import com.geowoof.core.network.model.NetworkPostRepost
import com.geowoof.core.network.model.NetworkTimelineItem
import com.geowoof.core.network.model.PostBody
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Posts, what people do with them and the lists that show them. */
@Suppress("TooManyFunctions")
internal interface PostsApi {
    @PUT("api/v1/posts/{id}")
    suspend fun putPost(@Path("id") id: String, @Body body: PostBody): NetworkPost

    @GET("api/v1/posts/{id}")
    suspend fun post(@Path("id") id: String): NetworkPost

    @DELETE("api/v1/posts/{id}")
    suspend fun deletePost(@Path("id") id: String)

    @PUT("api/v1/posts/{id}/likes")
    suspend fun like(@Path("id") id: String): NetworkPostLike

    @DELETE("api/v1/posts/{id}/likes")
    suspend fun unlike(@Path("id") id: String): NetworkPostLike

    @GET("api/v1/posts/{id}/comments")
    suspend fun comments(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkComment>

    @POST("api/v1/posts/{id}/comments")
    suspend fun addComment(@Path("id") id: String, @Body body: CommentBody): NetworkComment

    @DELETE("api/v1/posts/{id}/comments/{commentId}")
    suspend fun deleteComment(@Path("id") id: String, @Path("commentId") commentId: String)

    @PUT("api/v1/posts/{id}/repost")
    suspend fun repost(@Path("id") id: String): NetworkPostRepost

    @DELETE("api/v1/posts/{id}/repost")
    suspend fun undoRepost(@Path("id") id: String): NetworkPostRepost

    @PUT("api/v1/posts/{id}/save")
    suspend fun save(@Path("id") id: String)

    @DELETE("api/v1/posts/{id}/save")
    suspend fun unsave(@Path("id") id: String)

    @GET("api/v1/me/saved-posts")
    suspend fun savedPosts(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkPost>

    @GET("api/v1/feed")
    suspend fun feed(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkTimelineItem>

    @GET("api/v1/explore")
    suspend fun explore(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkPost>

    @GET("api/v1/users/{id}/posts")
    suspend fun userPosts(
        @Path("id") userId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkTimelineItem>
}

@Suppress("TooManyFunctions")
interface PostsNetworkDataSource {
    suspend fun putPost(id: String, body: PostBody): Outcome<NetworkPost>

    suspend fun post(id: String): Outcome<NetworkPost>

    suspend fun deletePost(id: String): Outcome<Unit>

    suspend fun setLiked(id: String, liked: Boolean): Outcome<NetworkPostLike>

    suspend fun comments(id: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkComment>>

    suspend fun addComment(id: String, text: String): Outcome<NetworkComment>

    suspend fun deleteComment(id: String, commentId: String): Outcome<Unit>

    suspend fun setReposted(id: String, reposted: Boolean): Outcome<NetworkPostRepost>

    suspend fun setSaved(id: String, saved: Boolean): Outcome<Unit>

    suspend fun savedPosts(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPost>>

    suspend fun feed(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkTimelineItem>>

    suspend fun explore(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPost>>

    suspend fun userPosts(userId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkTimelineItem>>
}

@Suppress("TooManyFunctions")
internal class RetrofitPostsNetworkDataSource @Inject constructor(private val api: PostsApi, private val json: Json) :
    PostsNetworkDataSource {
    override suspend fun putPost(id: String, body: PostBody) = apiCall(json) { api.putPost(id, body) }

    override suspend fun post(id: String) = apiCall(json) { api.post(id) }

    override suspend fun deletePost(id: String) = apiCall(json) { api.deletePost(id) }

    override suspend fun setLiked(id: String, liked: Boolean) =
        apiCall(json) { if (liked) api.like(id) else api.unlike(id) }

    override suspend fun comments(id: String, limit: Int, cursor: String?) =
        apiCall(json) { api.comments(id, limit, cursor) }

    override suspend fun addComment(id: String, text: String) = apiCall(json) { api.addComment(id, CommentBody(text)) }

    override suspend fun deleteComment(id: String, commentId: String) =
        apiCall(json) { api.deleteComment(id, commentId) }

    override suspend fun setReposted(id: String, reposted: Boolean) =
        apiCall(json) { if (reposted) api.repost(id) else api.undoRepost(id) }

    override suspend fun setSaved(id: String, saved: Boolean) =
        apiCall(json) { if (saved) api.save(id) else api.unsave(id) }

    override suspend fun savedPosts(limit: Int, cursor: String?) = apiCall(json) { api.savedPosts(limit, cursor) }

    override suspend fun feed(limit: Int, cursor: String?) = apiCall(json) { api.feed(limit, cursor) }

    override suspend fun explore(limit: Int, cursor: String?) = apiCall(json) { api.explore(limit, cursor) }

    override suspend fun userPosts(userId: String, limit: Int, cursor: String?) =
        apiCall(json) { api.userPosts(userId, limit, cursor) }
}
