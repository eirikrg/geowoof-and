package com.geowoof.core.network

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkProblem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Runs an API call and turns every failure into an [AppError]; exceptions never escape. */
internal suspend fun <T> apiCall(json: Json, call: suspend () -> T): Outcome<T> = try {
    Outcome.Success(call())
} catch (exception: HttpException) {
    Outcome.Failure(exception.toAppError(json))
} catch (@Suppress("SwallowedException") exception: IOException) {
    Outcome.Failure(AppError.Network)
} catch (exception: SerializationException) {
    Outcome.Failure(AppError.Unexpected(exception.message))
}

internal const val UNAUTHORIZED_CODE = "auth.unauthorized"

internal fun HttpException.toAppError(json: Json): AppError {
    val status = code()
    val problem = response()?.errorBody()?.string()?.let { body ->
        runCatching { json.decodeFromString<NetworkProblem>(body) }.getOrNull()
    }
    val code = problem?.code ?: fallbackCode(status)
    if (status == HTTP_UNAUTHORIZED && code == UNAUTHORIZED_CODE) {
        // The authenticator could not refresh the session: it is over.
        return AppError.SessionExpired
    }

    return AppError.Api(
        status = status,
        code = code,
        fieldErrors = problem?.errors.orEmpty(),
        retryAfterSeconds = response()?.headers()?.get("Retry-After")?.toLongOrNull(),
    )
}

/** Same defaults as the backend for responses without a ProblemDetails body. */
private fun fallbackCode(status: Int): String = when (status) {
    HTTP_BAD_REQUEST -> "request.invalid"
    HTTP_UNAUTHORIZED -> UNAUTHORIZED_CODE
    HTTP_FORBIDDEN -> "auth.forbidden"
    HTTP_NOT_FOUND -> "request.not_found"
    HTTP_CONFLICT -> "request.conflict"
    HTTP_TOO_MANY_REQUESTS -> "rate_limit.exceeded"
    HTTP_UNAVAILABLE -> "server.unavailable"
    in HTTP_SERVER_ERRORS -> "server.internal_error"
    else -> "request.failed"
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_UNAVAILABLE = 503
private val HTTP_SERVER_ERRORS = 500..599
