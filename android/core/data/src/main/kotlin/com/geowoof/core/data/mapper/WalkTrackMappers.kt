package com.geowoof.core.data.mapper

import com.geowoof.core.common.geo.PolylineCodec
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.network.model.NetworkWalkSegment
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val SegmentsSerializer = ListSerializer(NetworkWalkSegment.serializer())

internal fun List<NetworkWalkSegment>.toTrackJson(): String = Json.encodeToString(SegmentsSerializer, this)

internal fun String.toNetworkSegments(): List<NetworkWalkSegment> = Json.decodeFromString(SegmentsSerializer, this)

/** The route of a saved walk. A segment that cannot be decoded is left out: the rest can still be shown. */
internal fun String.toRouteSegments(): List<RouteSegment> = toNetworkSegments().toRouteSegments()

@JvmName("networkSegmentsToRoute")
internal fun List<NetworkWalkSegment>.toRouteSegments(): List<RouteSegment> = mapNotNull { segment ->
    val positions = PolylineCodec.decode(segment.polyline)
    positions?.takeIf { it.size == segment.timeOffsetsMs.size }?.let {
        RouteSegment(it.zip(segment.timeOffsetsMs) { position, offset -> RoutePoint(position, offset) })
    }
}
