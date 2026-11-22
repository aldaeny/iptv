package app.drivelink.cast.core.streaming

import app.drivelink.cast.core.model.DisplayProfile

/**
 * What can change without tearing down MediaCodec/VirtualDisplay.
 *
 * Resolution and codec changes still require a full restart; bitrate and frame-rate changes are
 * applied through `MediaCodec.setParameters`, which is fast enough to keep a live session.
 */
data class ReconfigurationPlan(
    val requiresRestart: Boolean,
    val canApplyLive: Boolean,
    val bitrateMbps: Int,
    val targetFps: Int
)

object EncoderReconfiguration {

    fun plan(current: DisplayProfile, desired: DisplayProfile): ReconfigurationPlan {
        val restart = current.baseWidth != desired.baseWidth ||
            current.baseHeight != desired.baseHeight ||
            current.codec != desired.codec
        return ReconfigurationPlan(
            requiresRestart = restart,
            canApplyLive = !restart && (
                current.bitrateMbps != desired.bitrateMbps ||
                    current.targetFps != desired.targetFps
                ),
            bitrateMbps = desired.bitrateMbps,
            targetFps = desired.targetFps
        )
    }
}

/**
 * Drops encoded frames above the negotiated target instead of letting a 120 Hz panel flood the
 * link. Keyframes and codec-config buffers are never dropped, otherwise the decoder would never
 * recover after loss.
 */
class FrameRateGovernor(initialTargetFps: Int) {

    @Volatile
    var targetFps: Int = initialTargetFps.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
        }

    private var lastEmitNs: Long = 0L

    @Synchronized
    fun shouldEmit(nowNs: Long, isKeyFrame: Boolean): Boolean {
        if (isKeyFrame) {
            lastEmitNs = nowNs
            return true
        }
        val intervalNs = 1_000_000_000L / targetFps
        if (lastEmitNs == 0L || nowNs - lastEmitNs >= intervalNs) {
            lastEmitNs = nowNs
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        lastEmitNs = 0L
    }
}
