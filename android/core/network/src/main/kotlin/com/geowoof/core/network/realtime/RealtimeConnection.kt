package com.geowoof.core.network.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

enum class RealtimeState {
    Disconnected,
    Connecting,
    Connected,
}

/** The answer of the server to a call it rejected: [code] is part of the contract (docs/realtime.md). */
internal class HubCallException(val code: String) : Exception(code)

/** The little that is asked of a SignalR connection, so that what surrounds it can be tested without one. */
internal interface HubClient {
    /** Fails if it cannot connect. */
    suspend fun start()

    suspend fun stop()

    /** The answer as JSON. */
    suspend fun invokeForResult(method: String, argument: Any): String

    suspend fun invoke(method: String, argument: Any? = null)

    fun on(event: String, handler: (json: String) -> Unit)

    /** When it has closed for any reason. */
    fun onClosed(handler: () -> Unit)
}

internal data class HubMessage(val event: String, val payload: String)

/**
 * One connection to a hub, open while someone needs it: when it closes it comes back with a fresh
 * token, waiting longer each time and a different amount on each device, so that a server that
 * restarts does not get everyone back at the same moment.
 *
 * Whoever listens has to ask again for what they want after a reconnection: a new connection on
 * the server knows nothing of the previous one. [state] says when.
 */
internal class RealtimeConnection(
    private val newClient: () -> HubClient,
    private val events: List<String>,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val mutableState = MutableStateFlow(RealtimeState.Disconnected)
    private val mutableMessages =
        MutableSharedFlow<HubMessage>(
            extraBufferCapacity = MESSAGES_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val lock = Any()
    private var users = 0
    private var loop: Job? = null

    @Volatile
    private var client: HubClient? = null

    val state: StateFlow<RealtimeState> = mutableState.asStateFlow()

    val messages: SharedFlow<HubMessage> = mutableMessages.asSharedFlow()

    /** The connection stays open at least as long as [block] takes. */
    suspend fun <T> whileOpen(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    suspend fun invokeForResult(method: String, argument: Any): String = connected().invokeForResult(method, argument)

    suspend fun invoke(method: String, argument: Any? = null) = connected().invoke(method, argument)

    /** There is no queue: what is live is worthless by the time the connection comes back. */
    private fun connected(): HubClient = client ?: throw IOException("Not connected")

    private fun acquire() = synchronized(lock) {
        users++
        if (users == 1) {
            val previous = loop
            loop = scope.launch {
                previous?.join()
                keepConnected()
            }
        }
    }

    private fun release() = synchronized(lock) {
        users--
        if (users == 0) loop?.cancel()
    }

    private suspend fun keepConnected() {
        var failures = 0
        try {
            while (true) {
                mutableState.value = RealtimeState.Connecting
                failures = if (connectUntilClosed()) 0 else failures + 1
                mutableState.value = RealtimeState.Connecting
                delay(nextDelayMs(failures))
            }
        } finally {
            mutableState.value = RealtimeState.Disconnected
        }
    }

    /**
     * @return whether it lasted. One that the server closes at once did not really connect, and
     * trying again right away would only repeat it: the wait has to keep growing.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun connectUntilClosed(): Boolean {
        val next = newClient()
        val closed = CompletableDeferred<Unit>()
        next.onClosed { closed.complete(Unit) }
        events.forEach { event -> next.on(event) { json -> mutableMessages.tryEmit(HubMessage(event, json)) } }
        var hasLasted = false
        try {
            next.start()
            client = next
            mutableState.value = RealtimeState.Connected
            hasLasted = withTimeoutOrNull(LASTED_AFTER_MS) { closed.await() } == null
            closed.await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Whatever it was, the next attempt starts from scratch.
        } finally {
            client = null
            withContext(NonCancellable) { stopQuietly(next) }
        }
        return hasLasted
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun stopQuietly(hub: HubClient) {
        try {
            withTimeoutOrNull(STOP_TIMEOUT_MS) { hub.stop() }
        } catch (exception: Exception) {
            // It is being discarded.
        }
    }

    private fun nextDelayMs(failures: Int): Long {
        val longest = min(MAX_DELAY_MS, MIN_DELAY_MS shl min(failures, MAX_DOUBLINGS))
        return longest / 2 + random.nextLong(longest / 2 + 1)
    }

    private companion object {
        const val MESSAGES_BUFFER = 64
        const val MIN_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 60_000L
        const val MAX_DOUBLINGS = 6
        const val LASTED_AFTER_MS = 15_000L
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
