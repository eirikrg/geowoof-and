package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkStats
import com.geowoof.core.network.model.WalkBody
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Walks endpoints. */
internal interface WalksApi {
    @PUT("api/v1/walks/{id}")
    suspend fun putWalk(@Path("id") id: String, @Body body: WalkBody): NetworkWalk

    @GET("api/v1/walks/{id}")
    suspend fun walk(@Path("id") id: String): NetworkWalk

    @DELETE("api/v1/walks/{id}")
    suspend fun deleteWalk(@Path("id") id: String)

    @GET("api/v1/me/walks")
    suspend fun myWalks(@Query("limit") limit: Int, @Query("cursor") cursor: String?): NetworkPage<NetworkWalk>

    @GET("api/v1/pets/{id}/walks")
    suspend fun petWalks(
        @Path("id") petId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkWalk>

    @GET("api/v1/me/walk-stats")
    suspend fun myStats(): NetworkWalkStats

    @GET("api/v1/pets/{id}/walk-stats")
    suspend fun petStats(@Path("id") petId: String): NetworkWalkStats
}

interface WalksNetworkDataSource {
    suspend fun putWalk(id: String, body: WalkBody): Outcome<NetworkWalk>

    suspend fun walk(id: String): Outcome<NetworkWalk>

    suspend fun deleteWalk(id: String): Outcome<Unit>

    suspend fun myWalks(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>>

    suspend fun petWalks(petId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>>

    suspend fun myStats(): Outcome<NetworkWalkStats>

    suspend fun petStats(petId: String): Outcome<NetworkWalkStats>
}

internal class RetrofitWalksNetworkDataSource @Inject constructor(private val api: WalksApi, private val json: Json) :
    WalksNetworkDataSource {
    override suspend fun putWalk(id: String, body: WalkBody) = apiCall(json) { api.putWalk(id, body) }

    override suspend fun walk(id: String) = apiCall(json) { api.walk(id) }

    override suspend fun deleteWalk(id: String) = apiCall(json) { api.deleteWalk(id) }

    override suspend fun myWalks(limit: Int, cursor: String?) = apiCall(json) { api.myWalks(limit, cursor) }

    override suspend fun petWalks(petId: String, limit: Int, cursor: String?) =
        apiCall(json) { api.petWalks(petId, limit, cursor) }

    override suspend fun myStats() = apiCall(json) { api.myStats() }

    override suspend fun petStats(petId: String) = apiCall(json) { api.petStats(petId) }
}
