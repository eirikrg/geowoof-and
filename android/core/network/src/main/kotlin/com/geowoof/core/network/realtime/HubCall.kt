package com.geowoof.core.network.realtime

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import java.io.IOException

private const val NO_HTTP_STATUS = 0
private const val CALL_TIMEOUT_MS = 10_000L

/** Runs a call to a hub and turns every failure into an [AppError]. It never waits long, and nothing queues. */
internal suspend fun <T> hubCall(call: suspend () -> T): Outcome<T> = try {
    Outcome.Success(withTimeout(CALL_TIMEOUT_MS) { call() })
} catch (exception: HubCallException) {
    Outcome.Failure(AppError.Api(status = NO_HTTP_STATUS, code = exception.code))
} catch (@Suppress("SwallowedException") exception: IOException) {
    Outcome.Failure(AppError.Network)
} catch (@Suppress("SwallowedException") exception: TimeoutCancellationException) {
    Outcome.Failure(AppError.Network)
} catch (exception: SerializationException) {
    Outcome.Failure(AppError.Unexpected(exception.message))
}
