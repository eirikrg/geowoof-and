package com.geowoof.core.testing

import com.geowoof.core.model.SignInMethod
import com.geowoof.core.model.User
import java.time.Instant

object TestUsers {
    @Suppress("LongParameterList")
    fun user(
        username: String? = "ana",
        emailVerified: Boolean = true,
        hasPassword: Boolean = true,
        displayName: String = "Ana",
        email: String = "ana@example.com",
        isPrivate: Boolean = false,
    ) = User(
        id = "0193f0c2-6f1e-7cc4-9a2b-3c1d5e7f9a10",
        email = email,
        emailVerified = emailVerified,
        username = username,
        displayName = displayName,
        hasPassword = hasPassword,
        signInMethods = if (hasPassword) setOf(SignInMethod.Password) else setOf(SignInMethod.Google),
        language = "es",
        createdAt = Instant.parse("2026-11-01T10:00:00Z"),
        isPrivate = isPrivate,
    )
}
