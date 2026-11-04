package com.geowoof.core.domain

import com.geowoof.core.common.AppConfig
import com.geowoof.core.model.AppError
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.Outcome
import com.geowoof.core.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetGoogleClientIdUseCaseTest {
    private val auth = FakeAuthRepository()

    private fun useCase(clientId: String?) = GetGoogleClientIdUseCase(
        AppConfig(apiBaseUrl = "http://localhost/", googleServerClientId = clientId, logHttpTraffic = false),
        auth,
    )

    @Test
    fun is_unavailable_without_a_client_id_in_the_build() = runTest {
        auth.providers = Outcome.Success(AuthProviders(google = true))

        assertNull(useCase(clientId = null)())
        assertNull(useCase(clientId = " ")())
    }

    @Test
    fun is_unavailable_when_the_backend_disables_google() = runTest {
        auth.providers = Outcome.Success(AuthProviders(google = false))

        assertNull(useCase(clientId = "web-client")())
    }

    @Test
    fun returns_the_client_id_when_both_sides_support_google() = runTest {
        auth.providers = Outcome.Success(AuthProviders(google = true))

        assertEquals("web-client", useCase(clientId = "web-client")())
    }

    @Test
    fun keeps_google_when_the_backend_cannot_be_reached() = runTest {
        auth.providers = Outcome.Failure(AppError.Network)

        assertEquals("web-client", useCase(clientId = "web-client")())
    }
}
