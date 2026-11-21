package app.drivelink.cast.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong

/** Optional, user-enabled single-pointer reverse control. */
class BydTouchAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val assembler = ReverseTouchGesture()
    private val pending = ArrayDeque<ReverseGesture>()
    private var dispatching = false
    private var generation = -1L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = clearPendingTouch()

    override fun onDestroy() {
        instance = null
        clearPendingTouch()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun injectNormalizedTouch(x: Float, y: Float, action: Byte) {
        val epoch = sessionEpoch.get()
        val receivedAt = SystemClock.uptimeMillis()
        mainHandler.post {
            if (epoch != sessionEpoch.get()) return@post
            if (epoch != generation) {
                assembler.reset()
                pending.clear()
                generation = epoch
            }
            if (SystemClock.uptimeMillis() - receivedAt > 500L) {
                assembler.reset()
                return@post
            }
            assembler.accept(x, y, action, receivedAt)?.let {
                if (pending.size < 4) pending.addLast(it)
                dispatchNext()
            }
        }
    }

    private fun dispatchNext() {
        if (dispatching || generation != sessionEpoch.get()) return
        val gesture = pending.removeFirstOrNull() ?: return
        val metrics = resources.displayMetrics
        val bounds = if (Build.VERSION.SDK_INT >= 30) {
            getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
        } else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        if (width <= 0 || height <= 0) { pending.clear(); return }
        val path = Path()
        gesture.points.forEachIndexed { index, point ->
            val pixel = point.pixels(width, height)
            val x = pixel.x + (bounds?.left ?: 0)
            val y = pixel.y + (bounds?.top ?: 0)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        dispatching = true
        val dispatched = dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, gesture.durationMs)).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = finishGesture()
                override fun onCancelled(gestureDescription: GestureDescription) = finishGesture()
            }, mainHandler
        )
        if (!dispatched) finishGesture()
    }

    private fun finishGesture() {
        dispatching = false
        dispatchNext()
    }

    companion object {
        private val sessionEpoch = AtomicLong()
        @Volatile private var instance: BydTouchAccessibilityService? = null

        fun clearPendingTouch() { sessionEpoch.incrementAndGet() }

        fun dispatchNormalizedTouch(xNorm: Float, yNorm: Float, action: Byte) {
            instance?.injectNormalizedTouch(xNorm, yNorm, action)
        }
    }
}
