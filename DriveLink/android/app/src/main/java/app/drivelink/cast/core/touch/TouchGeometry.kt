package app.drivelink.cast.core.touch

/**
 * Converts normalized coordinates received from the head unit into phone-screen pixels.
 *
 * The receiver's surface and the phone's logical display can be rotated independently, so the
 * mapping is explicit instead of relying on `displayMetrics` alone. Invalid normalized values are
 * rejected, not silently clamped, because a corrupted touch packet must never become a tap at (0,0).
 */
object TouchGeometry {

    data class Point(val x: Float, val y: Float)

    fun toScreen(
        xNorm: Float,
        yNorm: Float,
        widthPx: Int,
        heightPx: Int,
        rotationDegrees: Int = 0
    ): Point? {
        if (!xNorm.isFinite() || !yNorm.isFinite()) return null
        if (xNorm < 0f || xNorm > 1f || yNorm < 0f || yNorm > 1f) return null
        if (widthPx <= 0 || heightPx <= 0) return null

        val rawX = xNorm * widthPx
        val rawY = yNorm * heightPx
        return when (((rotationDegrees % 360) + 360) % 360) {
            0 -> Point(rawX, rawY)
            90 -> Point(heightPx - rawY, rawX)
            180 -> Point(widthPx - rawX, heightPx - rawY)
            270 -> Point(rawY, widthPx - rawX)
            else -> null
        }
    }

    fun strokeDurationMs(downAtMs: Long, upAtMs: Long, minimumMs: Long = 50L): Long {
        if (upAtMs <= downAtMs) return minimumMs
        return (upAtMs - downAtMs).coerceIn(minimumMs, 60_000L)
    }
}
