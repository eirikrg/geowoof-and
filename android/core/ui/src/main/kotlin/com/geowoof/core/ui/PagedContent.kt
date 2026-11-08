package com.geowoof.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/** What a paged list has to show: each of these has its own look, and only one at a time. */
enum class PagedContent { Loading, Error, Empty, Items }

/**
 * Tells a list that has not loaded yet from an empty one. Only the first load is [PagedContent.Loading]:
 * a later refresh keeps what was there, also an empty list, so that it does not go from empty to a
 * skeleton and back each time it comes back to the screen or is pulled down. A different [key], such as
 * a new search, is a new list, and loads again from the start.
 */
@Composable
fun LazyPagingItems<*>.pagedContent(key: Any? = null): PagedContent {
    var hasLoaded by remember(this, key) { mutableStateOf(false) }
    LaunchedEffect(this, key) {
        var sawLoading = false
        snapshotFlow { loadState }.collect { states ->
            when (states.refresh) {
                is LoadState.Loading -> sawLoading = true

                is LoadState.Error -> hasLoaded = true

                // Before the first page, a list can also say NotLoading: it only counts after a load.
                is LoadState.NotLoading -> if (sawLoading || states.append.endOfPaginationReached) hasLoaded = true
            }
        }
    }
    return when {
        itemCount > 0 -> PagedContent.Items
        !hasLoaded -> PagedContent.Loading
        loadState.refresh is LoadState.Error -> PagedContent.Error
        else -> PagedContent.Empty
    }
}

/** For a pull to refresh: a refresh of what is already on the screen, empty or not, and not the first load. */
fun LazyPagingItems<*>.isRefreshing(content: PagedContent): Boolean =
    loadState.refresh is LoadState.Loading && content != PagedContent.Loading
