package com.geowoof.core.network.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

/** A hub without a network: the test decides when it connects, fails or closes. */
private class FakeHubClient(private val failsToStart: Boolean) : HubClient {
    private var closedHandler: () -> Unit = {}
    private val handlers = mutableMapOf<String, (String) -> Unit>()

    var isStarted = false
        private set
    var isStopped = false
        private set
    val invocations = mutableListOf<String>()

    override suspend fun start() {
        if (failsToStart) throw IOException("No network")
        isStarted = true
    }

    override suspend fun stop() {
        isStopped = true
    }

    override suspend fun invokeForResult(method: String, argument: Any): String {
        invocations += method
        return "{}"
    }

    override suspend fun invoke(method: String, argument: Any?) {
        invocations += method
    }

    override fun on(event: String, handler: (String) -> Unit) {
        handlers[event] = handler
    }

    override fun onClosed(handler: () -> Unit) {
        closedHandler = handler
    }

    fun close() = closedHandler()

    fun receive(event: String, json: String) = handlers.getValue(event)(json)
}

/** Its delays always land on the longest wait, so that the tests can tell how long it is. */
private object LongestDelay : Random() {
    override fun nextBits(bitCount: Int) = 0

    override fun nextLong(until: Long) = until - 1
}

class RealtimeConnectionTest {
    private val clients = mutableListOf<FakeHubClient>()

    /** How many of the next attempts cannot connect. */
    private var failures = 0

    private fun TestScope.connection() = RealtimeConnection(
        newClient = {
            FakeHubClient(failsToStart = failures-- > 0).also { clients += it }
        },
        events = listOf("WalkerUpdated"),
        scope = backgroundScope,
        random = LongestDelay,
    )

    /** Uses the connection until the returned one completes. */
    private fun TestScope.use(connection: RealtimeConnection): CompletableDeferred<Unit> {
        val done = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.whileOpen { done.await() } }
        return done
    }

    @Test
    fun it_does_nothing_until_someone_needs_it() = runTest {
        val connection = connection()
        runCurrent()

        assertEquals(RealtimeState.Disconnected, connection.state.value)
        assertTrue(clients.isEmpty())
    }

    @Test
    fun it_connects_while_someone_needs_it_and_closes_after_the_last_one() = runTest {
        val connection = connection()

        val first = use(connection)
        val second = use(connection)
        runCurrent()
        assertEquals(RealtimeState.Connected, connection.state.value)
        assertEquals(1, clients.size)

        first.complete(Unit)
        runCurrent()
        assertEquals(RealtimeState.Connected, connection.state.value)

        second.complete(Unit)
        runCurrent()
        assertEquals(RealtimeState.Disconnected, connection.state.value)
        assertTrue(clients.single().isStopped)
    }

    @Test
    fun it_opens_again_when_someone_needs_it_again() = runTest {
        val connection = connection()
        val first = use(connection)
        runCurrent()
        first.complete(Unit)
        runCurrent()

        use(connection)
        runCurrent()

        assertEquals(RealtimeState.Connected, connection.state.value)
        assertEquals(2, clients.size)
    }

    @Test
    fun when_it_closes_it_comes_back_with_a_new_connection() = runTest {
        val connection = connection()
        use(connection)
        runCurrent()

        clients.single().close()
        runCurrent()
        assertEquals(RealtimeState.Connecting, connection.state.value)
        assertEquals(1, clients.size)

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(RealtimeState.Connected, connection.state.value)
        assertEquals(2, clients.size)
        assertTrue(clients.first().isStopped)
    }

    @Test
    fun it_waits_longer_each_time_it_fails_up_to_a_limit() = runTest {
        failures = 10
        val connection = connection()
        use(connection)
        runCurrent()
        assertEquals(1, clients.size)

        // The first failure waits 2 s, and each one after it twice as long.
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, clients.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, clients.size)

        advanceTimeBy(3_990)
        runCurrent()
        assertEquals(2, clients.size)
        advanceTimeBy(20)
        runCurrent()
        assertEquals(3, clients.size)

        // However many there are, it never waits more than a minute.
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(11, clients.size)
        assertEquals(RealtimeState.Connected, connection.state.value)
    }

    @Test
    fun connecting_again_starts_counting_from_the_beginning() = runTest {
        failures = 2
        val connection = connection()
        use(connection)
        advanceTimeBy(6_100)
        runCurrent()
        assertEquals(RealtimeState.Connected, connection.state.value)
        val connected = clients.size

        clients.last().close()
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(connected + 1, clients.size)
    }

    @Test
    fun different_devices_wait_different_times() = runTest {
        val waits = listOf(Random(1), Random(2), Random(3)).map { random ->
            val attempts = mutableListOf<Long>()
            val connection = RealtimeConnection(
                newClient = {
                    attempts += testScheduler.currentTime
                    FakeHubClient(failsToStart = true)
                },
                events = emptyList(),
                scope = backgroundScope,
                random = random,
            )
            val done = use(connection)
            advanceTimeBy(30_000)
            runCurrent()
            done.complete(Unit)
            runCurrent()
            attempts.zipWithNext { first, second -> second - first }.first()
        }

        assertEquals(3, waits.distinct().size)
        assertTrue(waits.all { it in 1_000..2_000 })
    }

    @Test
    fun what_arrives_goes_to_whoever_listens() = runTest {
        val connection = connection()
        val received = mutableListOf<HubMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { connection.messages.toList(received) }
        use(connection)
        runCurrent()

        clients.single().receive("WalkerUpdated", """{"a":1}""")
        runCurrent()

        assertEquals(listOf(HubMessage("WalkerUpdated", """{"a":1}""")), received)
    }

    @Test
    fun a_call_goes_through_the_connection_that_is_open() = runTest {
        val connection = connection()
        use(connection)
        runCurrent()

        connection.invoke("StopSharing")
        connection.invokeForResult("SubscribeViewport", mapOf("north" to 1.0))

        assertEquals(listOf("StopSharing", "SubscribeViewport"), clients.single().invocations)
    }

    @Test
    fun without_a_connection_a_call_fails_at_once_and_nothing_waits_for_it_to_come_back() = runTest {
        val connection = connection()

        val failure = runCatching { connection.invoke("StopSharing") }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertFalse(clients.any { it.invocations.isNotEmpty() })
    }

    @Test
    fun a_call_during_a_reconnection_fails_at_once_too() = runTest {
        val connection = connection()
        use(connection)
        runCurrent()
        clients.single().close()
        runCurrent()

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { connection.invoke("StopSharing") }
        }
    }
}
