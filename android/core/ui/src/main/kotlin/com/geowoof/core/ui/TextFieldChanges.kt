package com.geowoof.core.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

/** Text typed into a field held by a ViewModel, from the first change on (not the initial text). */
fun TextFieldState.textChanges(): Flow<String> = snapshotFlow { text.toString() }.drop(1)
