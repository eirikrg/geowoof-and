package com.geowoof.core.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult

    data object Cancelled : GoogleIdTokenResult

    data class Failed(val reason: String?) : GoogleIdTokenResult
}

/** Gets a Google ID token with Credential Manager ("Sign in with Google" button flow). */
class GoogleIdTokenRequester(private val context: Context, private val serverClientId: String) {
    suspend fun request(): GoogleIdTokenResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleIdTokenResult.Failed("Unexpected credential type ${credential.type}")
            }
        } catch (@Suppress("SwallowedException") exception: GetCredentialCancellationException) {
            GoogleIdTokenResult.Cancelled
        } catch (exception: GetCredentialException) {
            GoogleIdTokenResult.Failed(exception.message)
        } catch (exception: GoogleIdTokenParsingException) {
            GoogleIdTokenResult.Failed(exception.message)
        }
    }
}

/** `null` when Google sign-in is not configured in this build (docs/decisions/0012). */
@Composable
fun rememberGoogleIdTokenRequester(serverClientId: String?): GoogleIdTokenRequester? {
    val context = LocalContext.current
    return remember(context, serverClientId) {
        serverClientId?.takeIf { it.isNotBlank() }?.let { GoogleIdTokenRequester(context, it) }
    }
}

/** Starts Sign in with Google and reports an ID token, or that no credential could be obtained. */
@Stable
class GoogleSignInLauncher internal constructor(
    private val scope: CoroutineScope,
    private val requester: GoogleIdTokenRequester,
    private val onIdToken: State<(String) -> Unit>,
    private val onUnavailable: State<() -> Unit>,
) {
    /** Cancelling the account sheet reports nothing. */
    fun launch() {
        scope.launch {
            when (val result = requester.request()) {
                is GoogleIdTokenResult.Success -> onIdToken.value(result.idToken)

                GoogleIdTokenResult.Cancelled -> Unit

                is GoogleIdTokenResult.Failed -> {
                    Log.w(TAG, "Sign in with Google failed: ${result.reason}")
                    onUnavailable.value()
                }
            }
        }
    }
}

/** `null` when Sign in with Google is unavailable ([serverClientId] is null or blank). */
@Composable
fun rememberGoogleSignInLauncher(
    serverClientId: String?,
    onIdToken: (String) -> Unit,
    onUnavailable: () -> Unit,
): GoogleSignInLauncher? {
    val requester = rememberGoogleIdTokenRequester(serverClientId)
    val scope = rememberCoroutineScope()
    val currentOnIdToken = rememberUpdatedState(onIdToken)
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    return remember(requester, scope) {
        requester?.let { GoogleSignInLauncher(scope, it, currentOnIdToken, currentOnUnavailable) }
    }
}

/**
 * "Continue with Google" button. It follows Material 3; the official Google-branded button is
 * in docs/backlog.md.
 */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, loading: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.core_ui_continue_with_google))
        }
    }
}

private const val TAG = "GoogleSignIn"
