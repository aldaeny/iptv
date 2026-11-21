package app.drivelink.cast.service

internal data class TouchPoint(val x: Float, val y: Float) {
    fun pixels(width: Int, height: Int) = TouchPoint(x * (width - 1), y * (height - 1))
}
internal data class ReverseGesture(val points: List<TouchPoint>, val durationMs: Long)

/** Protocol v1 carries one normalized pointer, without frame IDs or sender timing. */
internal class ReverseTouchGesture {
    private val points = mutableListOf<TouchPoint>()
    private var startedAt = 0L

    fun reset() = points.clear()

    fun accept(x: Float, y: Float, action: Byte, now: Long): ReverseGesture? {
        if (action == 3.toByte()) { reset(); return null }
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) {
            reset()
            return null
        }
        val point = TouchPoint(x, y)
        if (points.isNotEmpty() && now - startedAt > 3_000L) reset()
        when (action.toInt()) {
            0 -> { reset(); startedAt = now; points.add(point) }
            2 -> if (points.isNotEmpty() && points.size < 128 && point != points.last()) points.add(point)
            1 -> if (points.isNotEmpty()) {
                if (point != points.last()) points.add(point)
                return ReverseGesture(points.toList(), (now - startedAt).coerceIn(45L, 1_500L)).also { reset() }
            }
        }
        return null
    }
}
