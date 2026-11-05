package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkFollowRequest
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkRelationship
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

/** Following, blocks and reports (docs/api.md, phases 4 and 5). */
internal interface SocialApi {
    @PUT("api/v1/users/{id}/follow")
    suspend fun follow(@Path("id") userId: String): NetworkRelationship

    @DELETE("api/v1/users/{id}/follow")
    suspend fun unfollow(@Path("id") userId: String): NetworkRelationship

    @DELETE("api/v1/users/{id}/follower")
    suspend fun removeFollower(@Path("id") userId: String): NetworkRelationship

    @GET("api/v1/follow-requests/received")
    suspend fun receivedRequests(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkFollowRequest>

    @GET("api/v1/follow-requests/sent")
    suspend fun sentRequests(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkFollowRequest>

    @POST("api/v1/follow-requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") id: String): NetworkRelationship

    @POST("api/v1/follow-requests/{id}/reject")
    suspend fun rejectRequest(@Path("id") id: String)

    @DELETE("api/v1/follow-requests/{id}")
    suspend fun cancelRequest(@Path("id") id: String)

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
    suspend fun follow(userId: String): Outcome<NetworkRelationship>

    suspend fun unfollow(userId: String): Outcome<NetworkRelationship>

    suspend fun removeFollower(userId: String): Outcome<NetworkRelationship>

    suspend fun receivedRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFollowRequest>>

    suspend fun sentRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFollowRequest>>

    suspend fun acceptRequest(id: String): Outcome<NetworkRelationship>

    suspend fun rejectRequest(id: String): Outcome<Unit>

    suspend fun cancelRequest(id: String): Outcome<Unit>

    suspend fun blocks(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkBlock>>

    suspend fun block(userId: String): Outcome<Unit>

    suspend fun unblock(userId: String): Outcome<Unit>

    suspend fun report(body: ReportBody): Outcome<NetworkReport>
}

internal class RetrofitSocialNetworkDataSource @Inject constructor(private val api: SocialApi, private val json: Json) :
    SocialNetworkDataSource {
    override suspend fun follow(userId: String) = apiCall(json) { api.follow(userId) }

    override suspend fun unfollow(userId: String) = apiCall(json) { api.unfollow(userId) }

    override suspend fun removeFollower(userId: String) = apiCall(json) { api.removeFollower(userId) }

    override suspend fun receivedRequests(limit: Int, cursor: String?) =
        apiCall(json) { api.receivedRequests(limit, cursor) }

    override suspend fun sentRequests(limit: Int, cursor: String?) = apiCall(json) { api.sentRequests(limit, cursor) }

    override suspend fun acceptRequest(id: String) = apiCall(json) { api.acceptRequest(id) }

    override suspend fun rejectRequest(id: String) = apiCall(json) { api.rejectRequest(id) }

    override suspend fun cancelRequest(id: String) = apiCall(json) { api.cancelRequest(id) }

    override suspend fun blocks(limit: Int, cursor: String?) = apiCall(json) { api.blocks(limit, cursor) }

    override suspend fun block(userId: String) = apiCall(json) { api.block(userId) }

    override suspend fun unblock(userId: String) = apiCall(json) { api.unblock(userId) }

    override suspend fun report(body: ReportBody) = apiCall(json) { api.report(body) }
}
