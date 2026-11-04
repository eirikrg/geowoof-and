package com.geowoof.core.common

/**
 * Build-time configuration provided by `:app` (BuildConfig). External keys are optional
 * (docs/decisions/0012): when [googleServerClientId] is blank, Sign in with Google is hidden.
 */
data class AppConfig(val apiBaseUrl: String, val googleServerClientId: String?, val logHttpTraffic: Boolean) {
    val isGoogleSignInConfigured: Boolean get() = !googleServerClientId.isNullOrBlank()
}
