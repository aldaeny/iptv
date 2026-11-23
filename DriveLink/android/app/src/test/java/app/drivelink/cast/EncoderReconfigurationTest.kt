package app.drivelink.cast

import app.drivelink.cast.core.model.DisplayProfile
import app.drivelink.cast.core.model.VideoCodec
import app.drivelink.cast.core.streaming.EncoderReconfiguration
import app.drivelink.cast.core.streaming.FrameRateGovernor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoderReconfigurationTest {

    @Test
    fun bitrateAndFpsChangesApplyWithoutRestart() {
        val base = DisplayProfile.DILINK_12_8_FHD
        val desired = base.copy(bitrateMbps = 18, targetFps = 30)
        val plan = EncoderReconfiguration.plan(base, desired)
        assertFalse(plan.requiresRestart)
        assertTrue(plan.canApplyLive)
        assertEquals(18, plan.bitrateMbps)
        assertEquals(30, plan.targetFps)
    }

    @Test
    fun resolutionOrCodecChangeRequiresRestart() {
        val base = DisplayProfile.DILINK_12_8_FHD
        assertTrue(EncoderReconfiguration.plan(base, DisplayProfile.DILINK_10_1_HD).requiresRestart)
        assertTrue(
            EncoderReconfiguration.plan(base, base.copy(codec = VideoCodec.H265)).requiresRestart
        )
    }

    @Test
    fun identicalProfileNeedsNoWork() {
        val base = DisplayProfile.DILINK_15_6_FHD
        val plan = EncoderReconfiguration.plan(base, base)
        assertFalse(plan.requiresRestart)
        assertFalse(plan.canApplyLive)
    }

    @Test
    fun governorEmitsAtTargetRate() {
        val governor = FrameRateGovernor(60)
        val oneSecondNs = 1_000_000_000L
        val frameNs = oneSecondNs / 120L
        var emitted = 0
        for (frame in 0 until 120) {
            if (governor.shouldEmit(frame * frameNs, isKeyFrame = false)) emitted++
        }
        assertEquals(60, emitted)
    }

    @Test
    fun governorNeverDropsKeyFrames() {
        val governor = FrameRateGovernor(1)
        assertTrue(governor.shouldEmit(0L, isKeyFrame = false))
        assertTrue(governor.shouldEmit(1_000_000_000L, isKeyFrame = false))
        assertTrue(governor.shouldEmit(2_000_000_000L, isKeyFrame = true))
    }

    @Test
    fun governorSurvivesZeroFps() {
        val governor = FrameRateGovernor(0)
        assertEquals(1, governor.targetFps)
        assertTrue(governor.shouldEmit(0L, isKeyFrame = false))
    }
}
