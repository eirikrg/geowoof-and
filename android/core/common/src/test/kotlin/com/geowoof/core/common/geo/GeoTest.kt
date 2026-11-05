package com.geowoof.core.common.geo

import com.geowoof.core.common.UuidV7
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class GeoTest {
    @Test
    fun the_vectors_shared_with_the_server_are_encoded_and_decoded() {
        for (vector in TestFixtures.polylineVectors) {
            assertEquals(vector.name, vector.encoded, PolylineCodec.encode(vector.points))

            val decoded = checkNotNull(PolylineCodec.decode(vector.encoded)) { vector.name }
            assertEquals(vector.name, vector.points.size, decoded.size)
            vector.points.zip(decoded).forEach { (expected, actual) ->
                assertEquals(vector.name, expected.latitude, actual.latitude, TOLERANCE)
                assertEquals(vector.name, expected.longitude, actual.longitude, TOLERANCE)
            }
        }
    }

    @Test
    fun text_that_is_not_a_polyline_is_rejected() {
        // Another chunk announced that never arrives, a latitude alone, below the alphabet,
        // and a value that never ends.
        listOf("_", "_p~iF", "  ", "~".repeat(20)).forEach { assertNull(it, PolylineCodec.decode(it)) }
    }

    @Test
    fun the_distance_between_two_points_is_geodesic() {
        assertEquals(111_195.0, GeoMath.distanceMeters(GeoPoint(40.0, -3.0), GeoPoint(41.0, -3.0)), 1.0)
        assertEquals(85_179.0, GeoMath.distanceMeters(GeoPoint(40.0, -3.0), GeoPoint(40.0, -2.0)), 10.0)
        assertEquals(0.0, GeoMath.distanceMeters(GeoPoint(40.0, -3.0), GeoPoint(40.0, -3.0)), 0.0)
    }

    @Test
    fun an_id_is_a_version_7_uuid_that_starts_with_the_time() {
        val time = 1_793_000_000_000L

        val id = UUID.fromString(UuidV7.generate(time))

        assertEquals(7, id.version())
        assertEquals(2, id.variant())
        assertEquals(time, id.mostSignificantBits ushr 16)
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
