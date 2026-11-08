package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.ProfileBody
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import javax.inject.Inject

/** Profile endpoints. */
internal interface ProfileApi {
    @GET("api/v1/me/profile")
    suspend fun myProfile(): NetworkProfile

    @PUT("api/v1/me/profile")
    suspend fun replaceMyProfile(@Body body: ProfileBody): NetworkProfile
}

interface ProfileNetworkDataSource {
    suspend fun myProfile(): Outcome<NetworkProfile>

    suspend fun replaceMyProfile(body: ProfileBody): Outcome<NetworkProfile>
}

internal class RetrofitProfileNetworkDataSource @Inject constructor(
    private val api: ProfileApi,
    private val json: Json,
) : ProfileNetworkDataSource {
    override suspend fun myProfile() = apiCall(json) { api.myProfile() }

    override suspend fun replaceMyProfile(body: ProfileBody) = apiCall(json) { api.replaceMyProfile(body) }
}
