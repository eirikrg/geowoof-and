package com.geowoof.feature.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot

/** Types into a field held by a ViewModel and publishes the change, as a frame would in the app. */
internal fun TextFieldState.enter(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}
