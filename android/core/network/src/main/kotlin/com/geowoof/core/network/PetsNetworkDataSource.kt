package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.CompleteHealthEventBody
import com.geowoof.core.network.model.HealthEventBody
import com.geowoof.core.network.model.NetworkHealthCompletion
import com.geowoof.core.network.model.NetworkHealthEvent
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkWeightEntry
import com.geowoof.core.network.model.PetBody
import com.geowoof.core.network.model.WeightBody
import kotlinx.serialization.json.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject

/** Pets endpoints. */
internal interface PetsApi {
    @GET("api/v1/pets")
    suspend fun myPets(): NetworkPage<NetworkPet>

    @GET("api/v1/pets/{id}")
    suspend fun pet(@Path("id") id: String): NetworkPet

    @POST("api/v1/pets")
    suspend fun createPet(@Body body: PetBody): NetworkPet

    @PUT("api/v1/pets/{id}")
    suspend fun updatePet(@Path("id") id: String, @Body body: PetBody): NetworkPet

    @DELETE("api/v1/pets/{id}")
    suspend fun deletePet(@Path("id") id: String)

    @GET("api/v1/pets/{id}/weights")
    suspend fun weights(
        @Path("id") petId: String,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): NetworkPage<NetworkWeightEntry>

    @POST("api/v1/pets/{id}/weights")
    suspend fun addWeight(@Path("id") petId: String, @Body body: WeightBody): NetworkWeightEntry

    @PUT("api/v1/pets/{id}/weights/{weightId}")
    suspend fun updateWeight(
        @Path("id") petId: String,
        @Path("weightId") weightId: String,
        @Body body: WeightBody,
    ): NetworkWeightEntry

    @DELETE("api/v1/pets/{id}/weights/{weightId}")
    suspend fun deleteWeight(@Path("id") petId: String, @Path("weightId") weightId: String)

    @GET("api/v1/pets/{id}/health")
    suspend fun health(@Path("id") petId: String): NetworkPage<NetworkHealthEvent>

    @PUT("api/v1/pets/{id}/health/{eventId}")
    suspend fun putHealthEvent(
        @Path("id") petId: String,
        @Path("eventId") eventId: String,
        @Body body: HealthEventBody,
    ): NetworkHealthEvent

    @POST("api/v1/pets/{id}/health/{eventId}/complete")
    suspend fun completeHealthEvent(
        @Path("id") petId: String,
        @Path("eventId") eventId: String,
        @Body body: CompleteHealthEventBody,
    ): NetworkHealthCompletion

    @DELETE("api/v1/pets/{id}/health/{eventId}")
    suspend fun deleteHealthEvent(@Path("id") petId: String, @Path("eventId") eventId: String)
}

interface PetsNetworkDataSource {
    suspend fun myPets(): Outcome<NetworkPage<NetworkPet>>

    suspend fun pet(id: String): Outcome<NetworkPet>

    suspend fun createPet(body: PetBody): Outcome<NetworkPet>

    suspend fun updatePet(id: String, body: PetBody): Outcome<NetworkPet>

    suspend fun deletePet(id: String): Outcome<Unit>

    suspend fun weights(petId: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkWeightEntry>>

    suspend fun addWeight(petId: String, body: WeightBody): Outcome<NetworkWeightEntry>

    suspend fun updateWeight(petId: String, weightId: String, body: WeightBody): Outcome<NetworkWeightEntry>

    suspend fun deleteWeight(petId: String, weightId: String): Outcome<Unit>

    /** All the health of a pet: it is not paged. */
    suspend fun health(petId: String): Outcome<NetworkPage<NetworkHealthEvent>>

    /** Creates it with that identifier, or replaces it whole. */
    suspend fun putHealthEvent(petId: String, eventId: String, body: HealthEventBody): Outcome<NetworkHealthEvent>

    suspend fun completeHealthEvent(petId: String, eventId: String, doneOn: String): Outcome<NetworkHealthCompletion>

    suspend fun deleteHealthEvent(petId: String, eventId: String): Outcome<Unit>
}

internal class RetrofitPetsNetworkDataSource @Inject constructor(private val api: PetsApi, private val json: Json) :
    PetsNetworkDataSource {
    override suspend fun myPets() = apiCall(json) { api.myPets() }

    override suspend fun pet(id: String) = apiCall(json) { api.pet(id) }

    override suspend fun createPet(body: PetBody) = apiCall(json) { api.createPet(body) }

    override suspend fun updatePet(id: String, body: PetBody) = apiCall(json) { api.updatePet(id, body) }

    override suspend fun deletePet(id: String) = apiCall(json) { api.deletePet(id) }

    override suspend fun weights(petId: String, limit: Int, cursor: String?) =
        apiCall(json) { api.weights(petId, limit, cursor) }

    override suspend fun addWeight(petId: String, body: WeightBody) = apiCall(json) { api.addWeight(petId, body) }

    override suspend fun updateWeight(petId: String, weightId: String, body: WeightBody) =
        apiCall(json) { api.updateWeight(petId, weightId, body) }

    override suspend fun deleteWeight(petId: String, weightId: String) =
        apiCall(json) { api.deleteWeight(petId, weightId) }

    override suspend fun health(petId: String) = apiCall(json) { api.health(petId) }

    override suspend fun putHealthEvent(petId: String, eventId: String, body: HealthEventBody) =
        apiCall(json) { api.putHealthEvent(petId, eventId, body) }

    override suspend fun completeHealthEvent(petId: String, eventId: String, doneOn: String) =
        apiCall(json) { api.completeHealthEvent(petId, eventId, CompleteHealthEventBody(doneOn)) }

    override suspend fun deleteHealthEvent(petId: String, eventId: String) =
        apiCall(json) { api.deleteHealthEvent(petId, eventId) }
}
