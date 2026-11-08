package com.geowoof.core.ui

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.testing.DrainMainLooperRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The screen goes to the back stack and comes back, as when the person opens something from it. */
@RunWith(AndroidJUnit4::class)
class RefreshOnReturnTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val screen = TestScreen()
    private val changes = MutableStateFlow(0)
    private var reloads = 0
    private var isShown by mutableStateOf(true)

    private fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                ReloadOnReturn(onReload = { reloads++ }, changes = changes, isShown = isShown)
            }
        }
        composeRule.waitForIdle()
    }

    private fun goAway() {
        composeRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.STARTED }
        composeRule.waitForIdle()
    }

    private fun comeBack() {
        composeRule.runOnIdle { screen.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
    }

    private fun change() {
        changes.value += 1
        composeRule.waitForIdle()
    }

    @Test
    fun coming_back_with_nothing_changed_keeps_what_there_is() {
        show()

        goAway()
        comeBack()

        assertEquals(0, reloads)
    }

    @Test
    fun a_change_made_somewhere_else_loads_it_again_once() {
        show()

        goAway()
        change()
        comeBack()
        goAway()
        comeBack()

        assertEquals(1, reloads)
    }

    @Test
    fun a_change_made_on_the_screen_itself_does_not() {
        show()

        change()
        goAway()
        comeBack()

        assertEquals(0, reloads)
    }

    @Test
    fun after_a_while_away_it_loads_again_once() {
        show()

        goAway()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(STALE_MINUTES))
        comeBack()
        goAway()
        comeBack()

        assertEquals(1, reloads)
    }

    @Test
    fun a_tab_shown_again_after_a_change_loads_again() {
        show()

        isShown = false
        composeRule.waitForIdle()
        change()
        assertEquals(0, reloads)
        isShown = true
        composeRule.waitForIdle()

        assertEquals(1, reloads)
    }

    @Test
    fun a_tab_shown_again_without_changes_does_not() {
        show()

        isShown = false
        composeRule.waitForIdle()
        isShown = true
        composeRule.waitForIdle()

        assertEquals(0, reloads)
    }

    @Test
    fun a_tab_not_shown_yet_knows_what_it_loaded_with() {
        isShown = false
        show()

        change()
        isShown = true
        composeRule.waitForIdle()

        assertEquals(1, reloads)
    }

    @Test
    fun a_list_asks_the_server_again() {
        val server = Server()
        val pages = Pager(PagingConfig(pageSize = PAGE_SIZE)) { OnePage(server) }.flow
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                RefreshOnReturn(pages.collectAsLazyPagingItems(), changes)
            }
        }
        waitForLoads(server, 1)

        goAway()
        change()
        comeBack()

        waitForLoads(server, 2)
    }

    private fun waitForLoads(server: Server, count: Int) {
        // Paging loads through the frames of the main looper, whose clock only moves here.
        composeRule.waitUntil(WAIT_MILLIS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
            server.loads == count
        }
        composeRule.waitForIdle()
    }

    private class TestScreen : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private class Server {
        var loads = 0
    }

    /** A refresh makes a new source each time, so the count is kept outside. */
    private class OnePage(private val server: Server) : PagingSource<Int, String>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
            server.loads++
            return LoadResult.Page(listOf("a"), prevKey = null, nextKey = null)
        }

        override fun getRefreshKey(state: PagingState<Int, String>): Int? = null
    }

    private companion object {
        const val STALE_MINUTES = 6L
        const val WAIT_MILLIS = 5_000L
        const val FRAME_MILLIS = 16L
        const val PAGE_SIZE = 10
    }
}
