package com.byd.mirror.core.model

enum class DisplayOrientation {
    LANDSCAPE,
    PORTRAIT
}

enum class VideoCodec(val mimeType: String) {
    H264("video/avc"),
    H265("video/hevc")
}

data class DisplayProfile(
    val id: String,
    val label: String,
    val baseWidth: Int,
    val baseHeight: Int,
    val targetFps: Int,
    val bitrateMbps: Int,
    val codec: VideoCodec
) {
    fun dimensionsFor(orientation: DisplayOrientation): Pair<Int, Int> {
        return when (orientation) {
            DisplayOrientation.LANDSCAPE -> maxOf(baseWidth, baseHeight) to minOf(baseWidth, baseHeight)
            DisplayOrientation.PORTRAIT -> minOf(baseWidth, baseHeight) to maxOf(baseWidth, baseHeight)
        }
    }

    companion object {
        val DILINK_15_6_FHD = DisplayProfile(
            id = "dilink_15_6_fhd",
            label = "BYD DiLink 15.6\" FHD",
            baseWidth = 1920,
            baseHeight = 1080,
            targetFps = 60,
            bitrateMbps = 14,
            codec = VideoCodec.H265
        )

        val DILINK_12_8_FHD = DisplayProfile(
            id = "dilink_12_8_fhd",
            label = "BYD DiLink 12.8\" FHD",
            baseWidth = 1920,
            baseHeight = 1080,
            targetFps = 60,
            bitrateMbps = 12,
            codec = VideoCodec.H264
        )

        val DILINK_10_1_HD = DisplayProfile(
            id = "dilink_10_1_hd",
            label = "BYD DiLink 10.1\" HD",
            baseWidth = 1280,
            baseHeight = 720,
            targetFps = 30,
            bitrateMbps = 6,
            codec = VideoCodec.H264
        )
    }
}
