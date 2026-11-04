package com.geowoof.core.network.auth

import com.geowoof.core.network.model.NetworkSession

/**
 * Where the network layer reads and updates the session tokens. Implemented in `:core:data`
 * (backed by the encrypted session store), so this module does not depend on storage.
 */
interface SessionTokenProvider {
    /** Current access token, read synchronously by the interceptor. */
    fun accessToken(): String?

    fun refreshToken(): String?

    suspend fun onTokensRefreshed(session: NetworkSession)

    /** The session cannot be refreshed any more: sign the user out locally. */
    suspend fun onSessionExpired()
}
