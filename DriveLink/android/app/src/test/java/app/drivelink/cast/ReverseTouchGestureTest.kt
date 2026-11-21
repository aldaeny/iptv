package app.drivelink.cast

import app.drivelink.cast.service.ReverseTouchGesture
import app.drivelink.cast.service.TouchPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseTouchGestureTest {
    @Test fun ignoresOrphanDuplicateCancelledAndExpiredEvents() {
        val touch = ReverseTouchGesture()
        assertNull(touch.accept(.5f, .5f, 1, 0))
        touch.accept(.5f, .5f, 0, 10)
        touch.accept(.5f, .5f, 3, 11)
        assertNull(touch.accept(.5f, .5f, 1, 12))
        touch.accept(.5f, .5f, 0, 20)
        assertNull(touch.accept(.5f, .5f, 1, 3_021))
        touch.accept(.5f, .5f, 0, 4_000)
        assertNotNull(touch.accept(.5f, .5f, 1, 4_100))
        assertNull(touch.accept(.5f, .5f, 1, 4_101))
    }

    @Test fun preservesDurationAndMapsEdgesWithoutSwappingAxes() {
        val touch = ReverseTouchGesture()
        touch.accept(0f, 1f, 0, 0)
        val gesture = checkNotNull(touch.accept(1f, 0f, 1, 700))
        assertEquals(700L, gesture.durationMs)
        assertEquals(TouchPoint(0f, 1919f), gesture.points.first().pixels(1080, 1920))
        assertEquals(TouchPoint(1919f, 0f), gesture.points.last().pixels(1920, 1080))
    }
}
