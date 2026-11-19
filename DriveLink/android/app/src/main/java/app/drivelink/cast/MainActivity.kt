package app.drivelink.cast

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.drivelink.cast.service.MirrorForegroundService
import app.drivelink.cast.service.MirrorOverlayService
import app.drivelink.cast.ui.MirrorDashboardScreen
import app.drivelink.cast.ui.MirrorViewModel
import app.drivelink.cast.ui.theme.BydMirrorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MirrorViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled gracefully */ }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, MirrorForegroundService::class.java).apply {
                action = MirrorForegroundService.ACTION_START
                putExtra(MirrorForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MirrorForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH && Settings.canDrawOverlays(this)) {
                startService(Intent(this, MirrorOverlayService::class.java))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialPermissions()

        setContent {
            BydMirrorTheme {
                MirrorDashboardScreen(
                    viewModel = viewModel,
                    onRequestMediaProjection = { requestScreenCapture() },
                    onLaunchOverlay = { launchFloatingOverlay() }
                )
            }
        }
    }

    private fun requestInitialPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun launchFloatingOverlay() {
        if (!BuildConfig.ENABLE_OVERLAY_AND_TOUCH) return
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        startService(Intent(this, MirrorOverlayService::class.java))
    }
}
