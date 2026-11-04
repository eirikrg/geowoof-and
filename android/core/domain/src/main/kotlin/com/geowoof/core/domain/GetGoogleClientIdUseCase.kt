package com.geowoof.core.domain

import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.model.Outcome
import javax.inject.Inject

/**
 * The OAuth client ID to request Google ID tokens with, or `null` when Sign in with Google is
 * unavailable: not configured in this build (docs/decisions/0012) or disabled in the backend.
 */
class GetGoogleClientIdUseCase @Inject constructor(private val config: AppConfig, private val auth: AuthRepository) {
    suspend operator fun invoke(): String? {
        val clientId = config.googleServerClientId?.takeIf { it.isNotBlank() } ?: return null
        // When the backend cannot be reached the button stays: trying it explains the problem.
        val providers = auth.authProviders()
        return if (providers is Outcome.Success && !providers.value.google) null else clientId
    }
}
