package app.drivelink.cast.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import app.drivelink.cast.core.touch.TouchGeometry

/**
 * Optional AccessibilityService enabling reverse touch control from the BYD DiLink display
 * back onto the Android phone screen.
 *
 * Injection is allowed only while the coordinator says a session is streaming. A stale touch from
 * a closed session must never tap the phone's home screen or a banking app.
 */
class BydTouchAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activePath: Path? = null
    private var downAtMs = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var moveCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        mainHandler.post { resetStroke() }
    }

    override fun onDestroy() {
        instance = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    fun injectNormalizedTouch(xNorm: Float, yNorm: Float, action: Byte) {
        if (!injectionAllowed) return
        mainHandler.post {
            if (!injectionAllowed) return@post
            val point = TouchGeometry.toScreen(
                xNorm = xNorm,
                yNorm = yNorm,
                widthPx = screenWidthPx(),
                heightPx = screenHeightPx(),
                rotationDegrees = screenRotationDegrees()
            ) ?: return@post

            when (action.toInt()) {
                0 -> {
                    activePath = Path().apply { moveTo(point.x, point.y) }
                    downAtMs = SystemClock.uptimeMillis()
                    lastX = point.x
                    lastY = point.y
                    moveCount = 0
                }
                2 -> {
                    val path = activePath ?: return@post
                    if (moveCount < MAX_MOVE_POINTS) {
                        path.lineTo(point.x, point.y)
                        moveCount++
                    }
                    lastX = point.x
                    lastY = point.y
                }
                1 -> {
                    val path = activePath ?: return@post
                    if (point.x != lastX || point.y != lastY) {
                        path.lineTo(point.x, point.y)
                    }
                    val duration = TouchGeometry.strokeDurationMs(downAtMs, SystemClock.uptimeMillis())
                    val gesture = GestureDescription.Builder()
                        .addStroke(
                            GestureDescription.StrokeDescription(
                                path,
                                0L,
                                duration
                            )
                        )
                        .build()
                    dispatchGesture(gesture, null, null)
                    resetStroke()
                }
            }
        }
    }

    private fun resetStroke() {
        activePath = null
        downAtMs = 0L
        moveCount = 0
    }

    private fun screenWidthPx(): Int = screenBounds().first

    private fun screenHeightPx(): Int = screenBounds().second

    private fun screenBounds(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun screenRotationDegrees(): Int {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(WindowManager::class.java).defaultDisplay
        }
        return when (display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    companion object {
        private const val MAX_MOVE_POINTS = 512

        @Volatile
        private var instance: BydTouchAccessibilityService? = null

        @Volatile
        private var injectionAllowed = false

        /** Called by the session coordinator; false closes the injection gate immediately. */
        fun setInjectionAllowed(allowed: Boolean) {
            injectionAllowed = allowed
            if (!allowed) {
                instance?.mainHandler?.post { instance?.resetStroke() }
            }
        }

        fun dispatchNormalizedTouch(xNorm: Float, yNorm: Float, action: Byte) {
            if (!injectionAllowed) return
            instance?.injectNormalizedTouch(xNorm, yNorm, action)
        }
    }
}
