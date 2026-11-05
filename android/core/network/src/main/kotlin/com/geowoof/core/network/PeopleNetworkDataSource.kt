package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkCounts
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPerson
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkPetSearchResult
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkStats
import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Search and what can be seen of other people (docs/api.md, phases 4 and 5). */
@Suppress("TooManyFunctions")
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

    @GET("api/v1/users/{id}/counts")
    suspend fun counts(@Path("id") userId: String): NetworkCounts

    /** [list] is `friends`, `followers` or `following`. */
    @GET("api/v1/users/{id}/{list}")
    suspend fun people(
        @Path("id") userId: String,
        @Path("list") list: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkPerson>

    @GET("api/v1/users/{id}/pets")
    suspend fun pets(@Path("id") userId: String): NetworkPage<NetworkPet>

    @GET("api/v1/users/{id}/walks")
    suspend fun walks(
        @Path("id") userId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkWalk>

    @GET("api/v1/users/{id}/walk-stats")
    suspend fun walkStats(@Path("id") userId: String): NetworkWalkStats

    @GET("api/v1/walks/{id}")
    suspend fun walk(@Path("id") walkId: String): NetworkWalk
}

@Suppress("TooManyFunctions")
interface PeopleNetworkDataSource {
    suspend fun searchUsers(query: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkUserSummary>>

    suspend fun searchPets(query: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPetSearchResult>>

    suspend fun profile(userId: String): Outcome<NetworkProfile>

    suspend fun relationship(userId: String): Outcome<NetworkRelationship>

    suspend fun counts(userId: String): Outcome<NetworkCounts>

    /** [list] is `friends`, `followers` or `following`. */
    suspend fun people(userId: String, list: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPerson>>

    suspend fun pets(userId: String): Outcome<NetworkPage<NetworkPet>>

    suspend fun walks(userId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWalk>>

    suspend fun walkStats(userId: String): Outcome<NetworkWalkStats>

    suspend fun walk(walkId: String): Outcome<NetworkWalk>
}

@Suppress("TooManyFunctions")
internal class RetrofitPeopleNetworkDataSource @Inject constructor(private val api: PeopleApi, private val json: Json) :
    PeopleNetworkDataSource {
    override suspend fun searchUsers(query: String, limit: Int, cursor: String?) =
        apiCall(json) { api.searchUsers(query, limit, cursor) }

    override suspend fun searchPets(query: String, limit: Int, cursor: String?) =
        apiCall(json) { api.searchPets(query, limit, cursor) }

    override suspend fun profile(userId: String) = apiCall(json) { api.profile(userId) }

    override suspend fun relationship(userId: String) = apiCall(json) { api.relationship(userId) }

    override suspend fun counts(userId: String) = apiCall(json) { api.counts(userId) }

    override suspend fun people(userId: String, list: String, limit: Int, cursor: String?) =
        apiCall(json) { api.people(userId, list, limit, cursor) }

    override suspend fun pets(userId: String) = apiCall(json) { api.pets(userId) }

    override suspend fun walks(userId: String, limit: Int, cursor: String?) =
        apiCall(json) { api.walks(userId, limit, cursor) }

    override suspend fun walkStats(userId: String) = apiCall(json) { api.walkStats(userId) }

    override suspend fun walk(walkId: String) = apiCall(json) { api.walk(walkId) }
}
