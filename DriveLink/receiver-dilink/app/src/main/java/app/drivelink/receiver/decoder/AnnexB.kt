package app.drivelink.receiver.decoder

/** Config prepended to an IDR is still a picture: never mark the whole access unit as CSD. */
internal fun isCodecConfigOnly(data: ByteArray, mimeType: String): Boolean {
    var found = false
    var zeros = 0
    for (i in data.indices) {
        val value = data[i].toInt() and 0xff
        if (value == 1 && zeros >= 2) {
            if (i == data.lastIndex) return false
            val header = data[i + 1].toInt() and 0xff
            val config = if (mimeType == "video/hevc") (header ushr 1 and 63) in 32..34
                         else (header and 31) in 7..8
            if (!config) return false
            found = true
        }
        zeros = if (value == 0) zeros + 1 else 0
    }
    return found
}
