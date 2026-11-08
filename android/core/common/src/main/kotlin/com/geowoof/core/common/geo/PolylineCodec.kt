package com.geowoof.core.common.geo

import com.geowoof.core.model.GeoPoint
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Google's encoded polyline algorithm with precision 5 (about 1.1 m). The server has its own
 * implementation: both are tested with the same vectors, in polyline-vectors.json.
 */
object PolylineCodec {
    private const val FACTOR = 1e5
    private const val ASCII_OFFSET = 63
    private const val CHUNK_BITS = 5
    private const val CONTINUATION_BIT = 0x20
    private const val CHUNK_MASK = 0x1F
    private const val MAX_SHIFT = 30
    private const val ROUNDING = 0.5

    fun encode(points: List<GeoPoint>): String = buildString {
        var previousLat = 0L
        var previousLon = 0L
        for (point in points) {
            val lat = point.latitude.toE5()
            val lon = point.longitude.toE5()
            appendValue(lat - previousLat)
            appendValue(lon - previousLon)
            previousLat = lat
            previousLon = lon
        }
    }

    /** `null` when the text is not a valid polyline. */
    fun decode(encoded: String): List<GeoPoint>? {
        val points = mutableListOf<GeoPoint>()
        val reader = Reader(encoded)
        var lat = 0L
        var lon = 0L
        while (reader.hasMore) {
            val deltaLat = reader.nextValue()
            val deltaLon = deltaLat?.let { reader.nextValue() }
            if (deltaLon == null) return null
            lat += deltaLat
            lon += deltaLon
            points += GeoPoint(lat / FACTOR, lon / FACTOR)
        }

        return points
    }

    /** Rounds half away from zero, as the server does. */
    private fun Double.toE5(): Long {
        val scaled = this * FACTOR
        return (if (scaled >= 0) floor(scaled + ROUNDING) else ceil(scaled - ROUNDING)).toLong()
    }

    private fun StringBuilder.appendValue(value: Long) {
        var remaining = if (value < 0) (value shl 1).inv() else value shl 1
        while (remaining >= CONTINUATION_BIT) {
            append(((CONTINUATION_BIT or (remaining and CHUNK_MASK.toLong()).toInt()) + ASCII_OFFSET).toChar())
            remaining = remaining shr CHUNK_BITS
        }

        append((remaining.toInt() + ASCII_OFFSET).toChar())
    }

    private class Reader(private val text: String) {
        private var index = 0

        val hasMore: Boolean get() = index < text.length

        fun nextValue(): Long? {
            var result = 0L
            var shift = 0
            var chunk = nextChunk()
            while (chunk != null && shift <= MAX_SHIFT) {
                result = result or ((chunk and CHUNK_MASK).toLong() shl shift)
                if (chunk < CONTINUATION_BIT) return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
                shift += CHUNK_BITS
                chunk = nextChunk()
            }

            return null
        }

        private fun nextChunk(): Int? = text.getOrNull(index++)?.code?.minus(ASCII_OFFSET)
            ?.takeIf { it in 0..(CONTINUATION_BIT + CHUNK_MASK) }
    }
}
