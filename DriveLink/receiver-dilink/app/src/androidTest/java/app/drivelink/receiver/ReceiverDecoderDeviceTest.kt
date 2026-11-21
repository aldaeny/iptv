package app.drivelink.receiver

import android.graphics.SurfaceTexture
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import app.drivelink.receiver.decoder.VideoAudioDecoder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class ReceiverDecoderDeviceTest {
    @Test
    fun rendersAnAvcFrameBeforeAndAfterSurfaceRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frame = instrumentation.context.assets.open("blue-idr.h264").use { it.readBytes() }
        val callbackThread = HandlerThread("frame-callbacks").apply { start() }
        val decoder = VideoAudioDecoder()
        try {
            repeat(2) {
                val texture = SurfaceTexture(false)
                val surface = Surface(texture)
                val rendered = CountDownLatch(1)
                try {
                    texture.setDefaultBufferSize(320, 240)
                    texture.setOnFrameAvailableListener({ rendered.countDown() }, Handler(callbackThread.looper))
                    // Model surfaceCreated followed by surfaceChanged with unchanged parameters.
                    repeat(2) { decoder.configureVideo(surface, 320, 240, MediaFormat.MIMETYPE_VIDEO_AVC) }
                    val deadline = System.nanoTime() + 5_000_000_000L
                    var ptsUs = 0L
                    while (rendered.count > 0 && System.nanoTime() < deadline) {
                        decoder.decodeVideoFrame(frame, ptsUs)
                        ptsUs += 33_333L
                        Thread.sleep(33L)
                    }
                    assertTrue("No decoded frame reached the surface on attempt ${it + 1}", rendered.count == 0L)
                } finally {
                    decoder.releaseVideo()
                    surface.release()
                    texture.release()
                }
            }
        } finally {
            callbackThread.quitSafely()
            callbackThread.join()
        }
    }
}
