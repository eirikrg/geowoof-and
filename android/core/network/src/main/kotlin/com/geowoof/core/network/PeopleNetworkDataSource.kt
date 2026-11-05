package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkPetSearchResult
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.NetworkWalk
import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Search and what can be seen of other people (docs/api.md, phase 4). */
internal interface PeopleApi {
    @GET("api/v1/search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkUserSummary>

    @GET("api/v1/search/pets")
    suspend fun searchPets(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkPetSearchResult>

    @GET("api/v1/users/{id}")
    suspend fun profile(@Path("id") userId: String): NetworkProfile

    @GET("api/v1/users/{id}/relationship")
    suspend fun relationship(@Path("id") userId: String): NetworkRelationship

    @GET("api/v1/users/{id}/pets")
    suspend fun pets(@Path("id") userId: String): NetworkPage<NetworkPet>

    @GET("api/v1/users/{id}/walks")
    suspend fun walks(
        @Path("id") userId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkWalk>

    @GET("api/v1/walks/{id}")
    suspend fun walk(@Path("id") walkId: String): NetworkWalk
}

interface PeopleNetworkDataSource {
    suspend fun searchUsers(query: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkUserSummary>>

    suspend fun searchPets(query: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPetSearchResult>>

    suspend fun profile(userId: String): Outcome<NetworkProfile>

    suspend fun relationship(userId: String): Outcome<NetworkRelationship>

    suspend fun pets(userId: String): Outcome<NetworkPage<NetworkPet>>

    suspend fun walks(userId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>>

    suspend fun walk(walkId: String): Outcome<NetworkWalk>
}

internal class RetrofitPeopleNetworkDataSource @Inject constructor(private val api: PeopleApi, private val json: Json) :
    PeopleNetworkDataSource {
    override suspend fun searchUsers(query: String, limit: Int, cursor: String?) =
        apiCall(json) { api.searchUsers(query, limit, cursor) }

    override suspend fun searchPets(query: String, limit: Int, cursor: String?) =
        apiCall(json) { api.searchPets(query, limit, cursor) }

    override suspend fun profile(userId: String) = apiCall(json) { api.profile(userId) }

    override suspend fun relationship(userId: String) = apiCall(json) { api.relationship(userId) }

    override suspend fun pets(userId: String) = apiCall(json) { api.pets(userId) }

    override suspend fun walks(userId: String, limit: Int, cursor: String?) =
        apiCall(json) { api.walks(userId, limit, cursor) }

    override suspend fun walk(walkId: String) = apiCall(json) { api.walk(walkId) }
}
