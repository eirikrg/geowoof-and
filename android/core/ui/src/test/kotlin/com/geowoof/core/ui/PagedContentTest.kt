package com.geowoof.core.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.testing.DrainMainLooperRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Each step gives the list the pages and the load states it would have at that moment. */
@RunWith(AndroidJUnit4::class)
class PagedContentTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val pages = MutableStateFlow(PagingData.empty<String>(LOADING))
    private lateinit var items: LazyPagingItems<String>
    private var content: PagedContent? = null

    private fun show() {
        composeRule.setContent {
            items = pages.collectAsLazyPagingItems()
            content = items.pagedContent()
        }
        composeRule.waitForIdle()
    }

    private fun answer(list: List<String>, states: LoadStates) {
        pages.value = PagingData.from(list, states)
        // Paging hands its pages over through the frames of the main looper, whose clock only moves here.
        composeRule.waitUntil(WAIT_MILLIS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
            items.loadState.source == states && items.itemCount == list.size
        }
        composeRule.waitForIdle()
    }

    @Test
    fun before_the_first_page_it_is_loading_and_not_refreshing() {
        show()

        assertEquals(PagedContent.Loading, content)
        assertFalse(items.isRefreshing(content!!))
    }

    @Test
    fun an_empty_answer_is_empty_and_a_refresh_keeps_it_empty() {
        show()
        answer(emptyList(), LOADED)
        assertEquals(PagedContent.Empty, content)

        answer(emptyList(), LOADING)

        assertEquals(PagedContent.Empty, content)
        assertTrue(items.isRefreshing(content!!))
    }

    @Test
    fun a_page_with_items_is_items() {
        show()

        answer(listOf("a", "b"), LOADED)

        assertEquals(PagedContent.Items, content)
    }

    @Test
    fun a_first_load_that_fails_is_an_error() {
        show()

        answer(emptyList(), LoadStates(LoadState.Error(IllegalStateException("offline")), NOT_DONE, NOT_DONE))

        assertEquals(PagedContent.Error, content)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val FRAME_MILLIS = 16L
        val NOT_DONE = LoadState.NotLoading(endOfPaginationReached = false)
        val DONE = LoadState.NotLoading(endOfPaginationReached = true)
        val LOADING = LoadStates(LoadState.Loading, NOT_DONE, NOT_DONE)
        val LOADED = LoadStates(NOT_DONE, DONE, DONE)
    }
}
