package com.geowoof.core.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How long what came from the server is taken as it is, if the person has changed none of it. */
val StaleAfter: Duration = 5.minutes

/** For what only others change: it only gets old. */
val NoChanges: Flow<Int> = flowOf(0)

/**
 * Loads a list again when it comes back to the screen, if it may be different: the person has changed something
 * that it shows ([changes] is not what it was), or it loaded more than [staleAfter] ago. If not, it stays as it was,
 * also where it was scrolled to, as when coming back from one of its posts. What changes on this same screen does
 * not count, because the screen already shows it. [isShown] is for a list in a tab: being shown again is also
 * coming back. [changes] has to be the same object in each composition, such as a property of the ViewModel.
 */
@Composable
fun RefreshOnReturn(
    items: LazyPagingItems<*>,
    changes: Flow<Int> = NoChanges,
    isShown: Boolean = true,
    staleAfter: Duration = StaleAfter,
) {
    var loadedAt by rememberSaveable { mutableLongStateOf(now()) }
    // Pulling it down or trying again also loads it.
    LaunchedEffect(items) {
        var isLoading = false
        snapshotFlow { items.loadState.refresh }.collect { refresh ->
            when (refresh) {
                is LoadState.Loading -> isLoading = true

                is LoadState.NotLoading -> if (isLoading) {
                    loadedAt = now()
                    isLoading = false
                }

                // It is tried again on the next return, whenever it was.
                is LoadState.Error -> isLoading = false
            }
        }
    }
    OnReturn(changes, isShown, staleAfter, loadedAt = { loadedAt }, reload = items::refresh)
}

/** The same as [RefreshOnReturn], for what is not a paged list, such as a post. */
@Composable
fun ReloadOnReturn(
    onReload: () -> Unit,
    changes: Flow<Int> = NoChanges,
    isShown: Boolean = true,
    staleAfter: Duration = StaleAfter,
) {
    var loadedAt by rememberSaveable { mutableLongStateOf(now()) }
    OnReturn(changes, isShown, staleAfter, loadedAt = { loadedAt }) {
        onReload()
        loadedAt = now()
    }
}

@Composable
private fun OnReturn(
    changes: Flow<Int>,
    isShown: Boolean,
    staleAfter: Duration,
    loadedAt: () -> Long,
    reload: () -> Unit,
) {
    // It survives going to the back stack and switching tabs, which is when the person changes something.
    var seen by rememberSaveable { mutableIntStateOf(NOT_SEEN) }
    val currentLoadedAt by rememberUpdatedState(loadedAt)
    val currentReload by rememberUpdatedState(reload)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, changes, isShown, staleAfter) {
        // What it shows now was loaded with the count of now, also for a tab that is not shown yet.
        if (seen == NOT_SEEN) seen = changes.first()
        if (!isShown) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var isReturn = true
            changes.collect { count ->
                if (isReturn) {
                    val isOld = now() - currentLoadedAt() > staleAfter.inWholeMilliseconds
                    if (count != seen || isOld) currentReload()
                    isReturn = false
                }
                seen = count
            }
        }
    }
}

private fun now() = SystemClock.elapsedRealtime()

private const val NOT_SEEN = -1
