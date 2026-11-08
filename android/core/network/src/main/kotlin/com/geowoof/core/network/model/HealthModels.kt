package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

/** How often it comes back: `unit` is Days, Weeks, Months or Years. */
@Serializable
data class NetworkHealthRepeat(val every: Int, val unit: String, val until: String? = null)

/** Dates are `yyyy-MM-dd` and the time `HH:mm`, local to the owner. Enums come as strings. */
@Serializable
data class NetworkHealthEvent(
    val id: String,
    val petId: String,
    val type: String,
    val title: String,
    val dueOn: String,
    val dueAt: String? = null,
    val doneOn: String? = null,
    val repeat: NetworkHealthRepeat? = null,
    val reminder: String = "None",
    val notes: String? = null,
    val updatedAt: String,
)

/** The whole event: what is left out is cleared. */
@Serializable
data class HealthEventBody(
    val type: String,
    val title: String,
    val dueOn: String,
    val dueAt: String?,
    val doneOn: String?,
    val repeat: NetworkHealthRepeat?,
    val reminder: String,
    val notes: String?,
)

@Serializable
data class CompleteHealthEventBody(val doneOn: String)

@Serializable
data class NetworkHealthCompletion(val done: NetworkHealthEvent, val next: NetworkHealthEvent? = null)
