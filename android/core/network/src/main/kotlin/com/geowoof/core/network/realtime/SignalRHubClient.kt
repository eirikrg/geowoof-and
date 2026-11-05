package com.geowoof.core.network.realtime

import android.annotation.SuppressLint
import android.util.Log
import com.geowoof.core.network.auth.SessionTokenProvider
import com.geowoof.core.network.auth.TokenAuthenticator
import com.google.gson.JsonElement
import com.microsoft.signalr.Action1
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubException
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The official client. The token comes from the session each time it connects, renewed first if
 * it has little left, and the authenticator of the API deals with any other 401. The arguments go
 * as maps and the answers come back as JSON, so that Gson does not need to reflect on anything of
 * ours.
 */
internal class SignalRHubClient(
    url: String,
    tokens: SessionTokenProvider,
    private val authenticator: TokenAuthenticator,
) : HubClient {
    private val hub = HubConnectionBuilder.create(url)
        .withAccessTokenProvider(Single.defer { Single.just(tokens.accessToken().orEmpty()) })
        .setHttpClientBuilderCallback { builder -> builder.authenticator(authenticator) }
        .build()

    override suspend fun start() = guarded {
        authenticator.renewIfExpiring()
        hub.start().awaitCompletion()
    }

    override suspend fun stop() = guarded { hub.stop().awaitCompletion() }

    override suspend fun invokeForResult(method: String, argument: Any): String = guarded {
        hub.invoke(JsonElement::class.java, method, argument).awaitResult().toString()
    }

    override suspend fun invoke(method: String, argument: Any?) = guarded {
        val call = if (argument == null) hub.invoke(method) else hub.invoke(method, argument)
        call.awaitCompletion()
    }

    override fun on(event: String, handler: (String) -> Unit) {
        hub.on(event, Action1<JsonElement> { payload -> handler(payload.toString()) }, JsonElement::class.java)
    }

    override fun onClosed(handler: () -> Unit) {
        hub.onClosed { handler() }
    }

    /** What the server rejected says its code, and anything else counts as a problem of the connection. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun <T> guarded(call: suspend () -> T): T = try {
        call()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: HubException) {
        throw HubCallException(exception.message.orEmpty().substringAfterLast(CODE_SEPARATOR))
    } catch (exception: Exception) {
        throw IOException(exception)
    }

    // Cancelling does not dispose them: an error that arrives later would have nowhere to go.
    @SuppressLint("CheckResult")
    private suspend fun Completable.awaitCompletion() = suspendCancellableCoroutine { continuation ->
        subscribe({ continuation.resume(Unit) }, { continuation.resumeWithException(it) })
    }

    @SuppressLint("CheckResult")
    private suspend fun <T : Any> Single<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        subscribe({ continuation.resume(it) }, { continuation.resumeWithException(it) })
    }

    private companion object {
        const val CODE_SEPARATOR = ": "
        const val TAG = "SignalRHubClient"

        init {
            // By default this would end the app, and a walk with it, for an error of a connection that was over.
            RxJavaPlugins.setErrorHandler { error ->
                val cause = if (error is UndeliverableException) error.cause else error
                Log.w(TAG, "An error of the connection arrived too late: ${cause?.javaClass?.simpleName}")
            }
        }
    }
}
