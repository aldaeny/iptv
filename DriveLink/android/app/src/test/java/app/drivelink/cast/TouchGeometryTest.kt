package app.drivelink.cast

import app.drivelink.cast.core.touch.TouchGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchGeometryTest {

    @Test
    fun mapsZeroRotation() {
        val point = TouchGeometry.toScreen(0.1f, 0.2f, 1000, 2000, 0)
        assertEquals(100f, point!!.x, 0.01f)
        assertEquals(400f, point.y, 0.01f)
    }

    @Test
    fun mapsAllRotations() {
        val p90 = TouchGeometry.toScreen(0.1f, 0.2f, 1000, 2000, 90)!!
        val p180 = TouchGeometry.toScreen(0.1f, 0.2f, 1000, 2000, 180)!!
        val p270 = TouchGeometry.toScreen(0.1f, 0.2f, 1000, 2000, 270)!!
        assertEquals(1600f, p90.x, 0.01f); assertEquals(100f, p90.y, 0.01f)
        assertEquals(900f, p180.x, 0.01f); assertEquals(1600f, p180.y, 0.01f)
        assertEquals(400f, p270.x, 0.01f); assertEquals(900f, p270.y, 0.01f)
    }

    @Test
    fun rejectsOutOfRangeAndInvalidNumbers() {
        assertNull(TouchGeometry.toScreen(1.01f, 0.5f, 1000, 2000))
        assertNull(TouchGeometry.toScreen(-0.01f, 0.5f, 1000, 2000))
        assertNull(TouchGeometry.toScreen(Float.NaN, 0.5f, 1000, 2000))
        assertNull(TouchGeometry.toScreen(0.5f, 0.5f, 0, 2000))
    }

    @Test
    fun strokeDurationUsesRealElapsedTime() {
        assertEquals(180L, TouchGeometry.strokeDurationMs(1_000L, 1_180L))
        assertEquals(50L, TouchGeometry.strokeDurationMs(1_000L, 1_000L))
        assertEquals(50L, TouchGeometry.strokeDurationMs(1_000L, 900L))
    }
}
