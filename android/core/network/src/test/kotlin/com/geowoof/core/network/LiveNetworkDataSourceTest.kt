package com.geowoof.core.network

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkLiveEvent
import com.geowoof.core.network.realtime.HubCallException
import com.geowoof.core.network.realtime.HubClient
import com.geowoof.core.network.realtime.RealtimeConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** What each method sends and how it reads what comes back: the contract in docs/realtime.md. */
class LiveNetworkDataSourceTest {
    private val client = RecordingHubClient()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun TestScope.source(): SignalRLiveNetworkDataSource {
        val connection = RealtimeConnection(
            newClient = { client },
            events = listOf(SignalRLiveNetworkDataSource.WALKER_UPDATED, SignalRLiveNetworkDataSource.WALKER_LEFT),
            scope = backgroundScope,
        )
        val source = SignalRLiveNetworkDataSource(connection, json)
        val done = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.whileOpen { done.await() } }
        runCurrent()
        return source
    }

    @Test
    fun looking_at_an_area_sends_its_limits_and_reads_who_is_there() = runTest {
        client.answer = """
            {"tooWide":false,"walkers":[{"userId":"bea-id","username":"bea","displayName":"Bea",
            "avatar":{"assetId":"a","thumbUrl":"t","mediumUrl":"m"},
            "pets":[{"id":"mishi-id","name":"Mishi","avatar":null}],
            "latitude":40.4,"longitude":-3.6,"updatedAt":"2026-11-05T10:00:00Z","somethingNew":1}]}
        """.trimIndent()

        val found = (source().subscribeViewport(41.0, 40.0, -3.0, -4.0) as Outcome.Success).value

        assertEquals(
            "SubscribeViewport" to mapOf("north" to 41.0, "south" to 40.0, "east" to -3.0, "west" to -4.0),
            client.calls.single(),
        )
        assertEquals(false, found.tooWide)
        assertEquals("bea", found.walkers.single().username)
        assertEquals("Mishi", found.walkers.single().pets.single().name)
        assertEquals("t", found.walkers.single().avatar?.thumbUrl)
    }

    @Test
    fun an_area_too_wide_says_so() = runTest {
        client.answer = """{"tooWide":true,"walkers":[]}"""

        val found = (source().subscribeViewport(41.0, 40.0, -3.0, -4.0) as Outcome.Success).value

        assertTrue(found.tooWide)
    }

    @Test
    fun starting_sends_the_walk_and_its_pets_and_reads_what_is_applied() = runTest {
        client.answer = """{"visibility":"FriendsOnly"}"""

        val answer = source().startSharing("walk-id", listOf("luna", "toby"))

        assertEquals(Outcome.Success("FriendsOnly"), answer)
        assertEquals(
            "StartSharing" to mapOf("walkId" to "walk-id", "petIds" to listOf("luna", "toby")),
            client.calls.single(),
        )
    }

    @Test
    fun a_position_says_when_it_was_recorded_in_utc() = runTest {
        val answer = source().updatePosition(40.4, -3.6, 8.5f, Instant.parse("2026-11-05T10:00:00Z"))

        assertEquals(Outcome.Success(Unit), answer)
        assertEquals(
            "UpdatePosition" to mapOf(
                "latitude" to 40.4,
                "longitude" to -3.6,
                "accuracyM" to 8.5f,
                "recordedAt" to "2026-11-05T10:00:00Z",
            ),
            client.calls.single(),
        )
    }

    @Test
    fun what_the_server_rejects_keeps_its_code() = runTest {
        client.failure = HubCallException("live.not_sharing")

        val answer = source().stopSharing()

        val error = (answer as Outcome.Failure).error as AppError.Api
        assertEquals("live.not_sharing", error.code)
    }

    @Test
    fun without_a_connection_it_is_a_network_problem() = runTest {
        val connection = RealtimeConnection(newClient = { client }, events = emptyList(), scope = backgroundScope)

        val answer = SignalRLiveNetworkDataSource(connection, json).stopSharing()

        assertEquals(Outcome.Failure(AppError.Network), answer)
    }

    @Test
    fun a_call_that_never_answers_ends_as_a_network_problem() = runTest {
        client.hangs = true
        val source = source()
        val answers = mutableListOf<Outcome<Unit>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { answers += source.stopSharing() }

        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(listOf<Outcome<Unit>>(Outcome.Failure(AppError.Network)), answers)
    }

    @Test
    fun an_answer_it_cannot_read_is_not_a_crash() = runTest {
        client.answer = """{"nothing":"useful"}"""

        val answer = source().startSharing("walk-id", listOf("luna"))

        assertTrue((answer as Outcome.Failure).error is AppError.Unexpected)
    }

    @Test
    fun the_events_arrive_read_and_what_it_does_not_understand_is_ignored() = runTest {
        val source = source()
        val events = mutableListOf<NetworkLiveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.events.collect { events += it } }
        runCurrent()

        client.receive(SignalRLiveNetworkDataSource.WALKER_LEFT, """{"nonsense":true}""")
        client.receive(
            SignalRLiveNetworkDataSource.WALKER_UPDATED,
            """
            {"userId":"bea-id","username":"bea","displayName":"Bea","pets":[],
            "latitude":1.5,"longitude":2.5,"updatedAt":"2026-11-05T10:00:00Z"}
            """.trimIndent(),
        )
        client.receive(SignalRLiveNetworkDataSource.WALKER_LEFT, """{"userId":"bea-id"}""")
        runCurrent()

        assertEquals(2, events.size)
        assertEquals(1.5, (events[0] as NetworkLiveEvent.WalkerUpdated).walker.latitude, 0.0)
        assertEquals(NetworkLiveEvent.WalkerLeft("bea-id"), events[1])
    }
}

private class RecordingHubClient : HubClient {
    private val handlers = mutableMapOf<String, (String) -> Unit>()

    val calls = mutableListOf<Pair<String, Any?>>()
    var answer = "{}"
    var failure: Exception? = null
    var hangs = false

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun invokeForResult(method: String, argument: Any): String {
        calls += method to argument
        failure?.let { throw it }
        return answer
    }

    override suspend fun invoke(method: String, argument: Any?) {
        calls += method to argument
        if (hangs) awaitCancellation()
        failure?.let { throw it }
    }

    override fun on(event: String, handler: (String) -> Unit) {
        handlers[event] = handler
    }

    override fun onClosed(handler: () -> Unit) = Unit

    fun receive(event: String, json: String) = handlers.getValue(event)(json)
}
