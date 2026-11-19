package app.drivelink.cast.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Optional AccessibilityService enabling reverse touch control from the BYD DiLink display
 * back onto the Android phone screen.
 */
class BydTouchAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activePath: Path? = null
    private var lastX = 0f
    private var lastY = 0f
    private var moveCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        mainHandler.post {
            activePath = null
            moveCount = 0
        }
    }

    override fun onDestroy() {
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun injectNormalizedTouch(xNorm: Float, yNorm: Float, action: Byte) {
        mainHandler.post {
            val metrics = resources.displayMetrics
            val x = xNorm.coerceIn(0f, 1f) * metrics.widthPixels
            val y = yNorm.coerceIn(0f, 1f) * metrics.heightPixels

            when (action.toInt()) {
                0 -> { // DOWN
                    activePath = Path().apply { moveTo(x, y) }
                    lastX = x
                    lastY = y
                    moveCount = 0
                }
                2 -> { // MOVE
                    if (moveCount < MAX_MOVE_POINTS) {
                        activePath?.lineTo(x, y)
                        moveCount++
                    }
                    lastX = x
                    lastY = y
                }
                1 -> { // UP -> dispatch tap or swipe gesture
                    val path = activePath ?: Path().apply { moveTo(x, y) }
                    if (x != lastX || y != lastY) {
                        path.lineTo(x, y)
                    }
                    val gesture = GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0L, 45L))
                        .build()
                    dispatchGesture(gesture, null, null)
                    activePath = null
                    moveCount = 0
                }
            }
        }
    }

    companion object {
        private const val MAX_MOVE_POINTS = 128

        @Volatile
        private var instance: BydTouchAccessibilityService? = null

        fun dispatchNormalizedTouch(xNorm: Float, yNorm: Float, action: Byte) {
            instance?.injectNormalizedTouch(xNorm, yNorm, action)
        }
    }
}
