package com.geowoof.core.testing

import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** The files of docs/fixtures, shared with the tests of the backend. */
object TestFixtures {
    data class PolylineVector(val name: String, val encoded: String, val points: List<GeoPoint>)

    val polylineVectors: List<PolylineVector> by lazy {
        Json.parseToJsonElement(text("polyline-vectors.json")).jsonArray.map { element ->
            val vector = element.jsonObject
            PolylineVector(
                name = vector.getValue("name").jsonPrimitive.content,
                encoded = vector.getValue("encoded").jsonPrimitive.content,
                points = vector.getValue("points").jsonArray.map { point ->
                    val (latitude, longitude) = point.jsonArray.map { it.jsonPrimitive.double }
                    GeoPoint(latitude, longitude)
                },
            )
        }
    }

    /** The positions of a simulated trace, one list per `trkseg`. The accuracy travels in `hdop`. */
    fun gpx(name: String): List<List<LocationFix>> = TRACK_SEGMENT.findAll(text("$name.gpx")).map { segment ->
        TRACK_POINT.findAll(segment.value).map { point ->
            val (latitude, longitude, time) = point.destructured
            LocationFix(
                position = GeoPoint(latitude.toDouble(), longitude.toDouble()),
                accuracyM = point.groupValues.last().toFloat(),
                timeEpochMs = Instant.parse(time).toEpochMilli(),
            )
        }.toList()
    }.toList()

    private fun text(file: String): String =
        checkNotNull(TestFixtures::class.java.getResourceAsStream("/$file")) { "Missing fixture $file" }
            .bufferedReader()
            .use { it.readText() }

    private val TRACK_SEGMENT = Regex("<trkseg>.*?</trkseg>", RegexOption.DOT_MATCHES_ALL)
    private val TRACK_POINT = Regex(
        "<trkpt lat=\"([^\"]+)\" lon=\"([^\"]+)\"><time>([^<]+)</time><hdop>([^<]+)</hdop></trkpt>",
    )
}
