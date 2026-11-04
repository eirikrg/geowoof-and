package com.geowoof.core.model

/** Result of an operation that can fail with an [AppError]. Errors never reach the UI as exceptions. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = also {
    if (it is Outcome.Success) action(it.value)
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> = also {
    if (it is Outcome.Failure) action(it.error)
}

/** Why an operation failed, in terms the UI can explain to the user. */
sealed interface AppError {
    /** No connection, timeout or an unreachable server. */
    data object Network : AppError

    /** The session ended (expired, revoked or deleted elsewhere); the user must sign in again. */
    data object SessionExpired : AppError

    /**
     * The API rejected the request with a ProblemDetails response (docs/api.md): [code] is stable
     * (for example `auth.invalid_credentials`) and [fieldErrors] lists invalid fields by name.
     */
    data class Api(
        val status: Int,
        val code: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryAfterSeconds: Long? = null,
    ) : AppError

    /**
     * The device could not provide a sign-in credential: no Google account, Google Play services
     * unavailable or a provider error. Cancelling the sign-in sheet is not an error.
     */
    data object CredentialUnavailable : AppError

    data class Unexpected(val message: String?) : AppError
}

/** The API error code, if any. */
val AppError.code: String? get() = (this as? AppError.Api)?.code
