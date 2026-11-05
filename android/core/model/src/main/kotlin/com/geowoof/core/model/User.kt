package com.geowoof.core.model

import java.time.Instant

/** The signed-in user's account. */
data class User(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val username: String?,
    val displayName: String,
    val hasPassword: Boolean,
    val signInMethods: Set<SignInMethod>,
    val language: String,
    val createdAt: Instant,
    val isPrivate: Boolean = false,
) {
    /** Onboarding ends when the user has chosen a @username. */
    val needsOnboarding: Boolean get() = username == null
}

enum class SignInMethod {
    Password,
    Google,
}

/** Whether there is a session, and for whom. */
sealed interface SessionState {
    /** The stored session is still being read (the splash screen stays visible). */
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: User) : SessionState
}

/** Sign-in methods offered by the backend. */
data class AuthProviders(val google: Boolean)

data class UsernameAvailability(val username: String, val available: Boolean, val reason: UsernameUnavailableReason?)

enum class UsernameUnavailableReason {
    Taken,
    Reserved,
    Invalid,
}
