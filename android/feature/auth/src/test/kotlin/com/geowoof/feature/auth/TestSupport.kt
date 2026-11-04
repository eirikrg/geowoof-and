package com.geowoof.feature.auth

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.domain.GetGoogleClientIdUseCase

/** Types into a field held by a ViewModel and publishes the change, as a frame would in the app. */
internal fun TextFieldState.enter(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}

internal fun googleClientId(auth: AuthRepository, clientId: String? = null) = GetGoogleClientIdUseCase(
    AppConfig(apiBaseUrl = "http://localhost/", googleServerClientId = clientId, logHttpTraffic = false),
    auth,
)
