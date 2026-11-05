package com.geowoof.core.common

import java.security.SecureRandom
import java.util.UUID

/**
 * Identifiers generated on the device, so that repeating an upload never duplicates anything.
 * Version 7 starts with the time, like every id of the API (docs/api.md).
 */
object UuidV7 {
    private const val TIMESTAMP_SHIFT = 16
    private const val VERSION_BITS = 0x7000L
    private const val RANDOM_A_MASK = 0x0FFFL
    private const val VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL
    private const val VARIANT_BITS = Long.MIN_VALUE

    private val random = SecureRandom()

    fun generate(timeEpochMs: Long): String {
        val mostSignificant = (timeEpochMs shl TIMESTAMP_SHIFT) or VERSION_BITS or (random.nextLong() and RANDOM_A_MASK)
        val leastSignificant = (random.nextLong() and VARIANT_MASK) or VARIANT_BITS
        return UUID(mostSignificant, leastSignificant).toString()
    }
}
