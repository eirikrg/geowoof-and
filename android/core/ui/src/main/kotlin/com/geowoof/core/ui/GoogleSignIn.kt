package com.geowoof.core.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.geowoof.core.designsystem.component.MinButtonHeight
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
 * The "Continue with Google" button, with the colors, the padding and the logo that Google's branding
 * guidelines ask for in each theme. The font is the system one (docs/backlog.md).
 */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, loading: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val isDark = isThemeDark()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinButtonHeight),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isDark) GoogleDarkFill else Color.White,
            contentColor = if (isDark) GoogleDarkText else GoogleLightText,
        ),
        border = BorderStroke(1.dp, if (isDark) GoogleDarkStroke else GoogleLightStroke),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                painter = painterResource(
                    if (isDark) R.drawable.core_ui_google_g_dark else R.drawable.core_ui_google_g_light,
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.core_ui_continue_with_google), style = GoogleLabel)
        }
    }
}

private val GoogleDarkFill = Color(0xFF131314)
private val GoogleDarkText = Color(0xFFE3E3E3)
private val GoogleDarkStroke = Color(0xFF8E918F)
private val GoogleLightText = Color(0xFF1F1F1F)
private val GoogleLightStroke = Color(0xFF747775)
private val GoogleLabel = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

private const val TAG = "GoogleSignIn"
