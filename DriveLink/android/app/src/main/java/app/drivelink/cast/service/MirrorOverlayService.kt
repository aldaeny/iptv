package app.drivelink.cast.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.drivelink.cast.core.session.MirrorSessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating control panel rendered over other apps (SYSTEM_ALERT_WINDOW)
 * allowing one-tap orientation switching (Portrait <-> Landscape) and disconnection.
 */
class MirrorOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 180
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6111827"))
                cornerRadius = 48f
                setStroke(2, Color.parseColor("#00D4FF"))
            }
        }

        val statusBadge = TextView(this).apply {
            text = "BYD • LIVE"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            setPadding(0, 0, 18, 0)
        }

        val rotateBtn = Button(this).apply {
            text = "أفقي/عمودي"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setOnClickListener {
                MirrorSessionCoordinator.toggleOrientation()
            }
        }

        val stopBtn = Button(this).apply {
            text = "إنهاء"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#DC2626"))
            setOnClickListener {
                serviceScope.launch {
                    MirrorSessionCoordinator.disconnect()
                }
                stopService(Intent(this@MirrorOverlayService, MirrorForegroundService::class.java))
                stopSelf()
            }
        }

        container.addView(statusBadge)
        container.addView(rotateBtn)
        container.addView(stopBtn)

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        overlayView = container
        windowManager.addView(container, params)
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
