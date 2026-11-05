package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.FriendRequestBody
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkFriend
import com.geowoof.core.network.model.NetworkFriendRequest
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkReport
import com.geowoof.core.network.model.ReportBody
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Friends, blocks and reports (docs/api.md, phase 4). */
internal interface SocialApi {
    @POST("api/v1/friend-requests")
    suspend fun sendRequest(@Body body: FriendRequestBody): NetworkFriendRequest

    @GET("api/v1/friend-requests/received")
    suspend fun receivedRequests(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkFriendRequest>

    @GET("api/v1/friend-requests/sent")
    suspend fun sentRequests(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkFriendRequest>

    @POST("api/v1/friend-requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") id: String): NetworkFriendRequest

    @POST("api/v1/friend-requests/{id}/reject")
    suspend fun rejectRequest(@Path("id") id: String)

    @DELETE("api/v1/friend-requests/{id}")
    suspend fun cancelRequest(@Path("id") id: String)

    @GET("api/v1/friends")
    suspend fun friends(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkFriend>

    @DELETE("api/v1/friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: String)

    @GET("api/v1/blocks")
    suspend fun blocks(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkBlock>

    @PUT("api/v1/blocks/{userId}")
    suspend fun block(@Path("userId") userId: String)

    @DELETE("api/v1/blocks/{userId}")
    suspend fun unblock(@Path("userId") userId: String)

    @POST("api/v1/reports")
    suspend fun report(@Body body: ReportBody): NetworkReport
}

interface SocialNetworkDataSource {
    suspend fun sendRequest(userId: String): Outcome<NetworkFriendRequest>

    suspend fun receivedRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFriendRequest>>

    suspend fun sentRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFriendRequest>>

    suspend fun acceptRequest(id: String): Outcome<NetworkFriendRequest>

    suspend fun rejectRequest(id: String): Outcome<Unit>

    suspend fun cancelRequest(id: String): Outcome<Unit>

    suspend fun friends(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFriend>>

    suspend fun removeFriend(userId: String): Outcome<Unit>

    suspend fun blocks(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkBlock>>

    suspend fun block(userId: String): Outcome<Unit>

    suspend fun unblock(userId: String): Outcome<Unit>

    suspend fun report(body: ReportBody): Outcome<NetworkReport>
}

internal class RetrofitSocialNetworkDataSource @Inject constructor(private val api: SocialApi, private val json: Json) :
    SocialNetworkDataSource {
    override suspend fun sendRequest(userId: String) = apiCall(json) { api.sendRequest(FriendRequestBody(userId)) }

    override suspend fun receivedRequests(limit: Int, cursor: String?) =
        apiCall(json) { api.receivedRequests(limit, cursor) }

    override suspend fun sentRequests(limit: Int, cursor: String?) = apiCall(json) { api.sentRequests(limit, cursor) }

    override suspend fun acceptRequest(id: String) = apiCall(json) { api.acceptRequest(id) }

    override suspend fun rejectRequest(id: String) = apiCall(json) { api.rejectRequest(id) }

    override suspend fun cancelRequest(id: String) = apiCall(json) { api.cancelRequest(id) }

    override suspend fun friends(limit: Int, cursor: String?) = apiCall(json) { api.friends(limit, cursor) }

    override suspend fun removeFriend(userId: String) = apiCall(json) { api.removeFriend(userId) }

    override suspend fun blocks(limit: Int, cursor: String?) = apiCall(json) { api.blocks(limit, cursor) }

    override suspend fun block(userId: String) = apiCall(json) { api.block(userId) }

    override suspend fun unblock(userId: String) = apiCall(json) { api.unblock(userId) }

    override suspend fun report(body: ReportBody) = apiCall(json) { api.report(body) }
}
