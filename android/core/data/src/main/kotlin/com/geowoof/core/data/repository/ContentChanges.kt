package com.geowoof.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Something that changes what other screens show, which came from the server before it changed. */
enum class ContentChange {
    /** Following or unfollowing someone, a request, a follower removed, a block or an unblock. */
    Relationships,

    /** A post published, deleted, reposted or saved. */
    Posts,

    /** The name, photo, bio or username of the person, or whether their profile is private. */
    Profile,

    /** A pet added, edited or deleted. */
    Pets,
}

/**
 * Says what has just changed to the screens that show it from the server, so that they load it again when they
 * come back. The repositories tell it what the person does, and the pushes what others do with them. Each change
 * is counted: a screen keeps the count it last saw, and a different one means that it has to load again.
 */
@Singleton
class ContentChanges @Inject constructor() {
    private val counts = MutableStateFlow(emptyMap<ContentChange, Int>())

    fun notify(change: ContentChange) = counts.update { it + (change to (it[change] ?: 0) + 1) }

    /** How many of these have happened, which only goes up. It gives the current count at once. */
    fun of(vararg changes: ContentChange): Flow<Int> =
        counts.map { current -> changes.sumOf { current[it] ?: 0 } }.distinctUntilChanged()
}
