package app.drivelink.cast.core.capability

import app.drivelink.cast.BuildConfig

/**
 * Every capability the app can advertise must carry one of these states.
 *
 * [UNSUPPORTED] and [NOT_VERIFIED] are deliberately different: the first means the platform or
 * hardware cannot do it at all, the second means code exists but no executable evidence exists.
 * Never turn "compiles" into "verified".
 */
enum class CapabilityState {
    AVAILABLE,
    REQUIRES_PERMISSION,
    DEGRADED,
    UNSUPPORTED,
    NOT_VERIFIED,
    BLOCKED_BY_PLATFORM,
    BLOCKED_BY_HARDWARE,
    FAILED;

    /** True only for states that may be presented to the user as working today. */
    val assertsUsable: Boolean
        get() = this == AVAILABLE || this == DEGRADED
}

enum class CapabilityId {
    BYD_WMP_TRANSPORT,
    WIFI_DIRECT,
    MIRACAST,
    SCREEN_MIRRORING,
    REVERSE_TOUCH,
    AUDIO_STREAMING,
    HEVC_ENCODING,
    CARPLAY,
    ANDROID_AUTO,
    USB_VIDEO,
    BLUETOOTH_VIDEO
}

/**
 * A capability is intentionally impossible to construct without evidence. This forces every new
 * feature to say what was actually tested instead of silently claiming support.
 */
data class Capability(
    val id: CapabilityId,
    val state: CapabilityState,
    val evidence: String,
    val notes: String = ""
) {
    init {
        require(evidence.isNotBlank()) { "Capability $id must declare evidence" }
        require(!(state.assertsUsable && evidence.contains("BLOCKED", ignoreCase = true))) {
            "Capability $id cannot be usable while its evidence is blocked"
        }
    }
}

object CapabilityRegistry {

    /** Last green native build/test run for the code in this tree. Never a device-test claim. */
    const val CI_BASELINE_EVIDENCE =
        "GitHub Actions run 45056352610 (commit 7c2aaea): Android, Receiver, iOS and Go builds + unit tests passed. No device/vehicle test."

    fun forThisBuild(): List<Capability> = listOf(
        Capability(
            id = CapabilityId.BYD_WMP_TRANSPORT,
            state = CapabilityState.AVAILABLE,
            evidence = CI_BASELINE_EVIDENCE
        ),
        Capability(
            id = CapabilityId.WIFI_DIRECT,
            state = CapabilityState.NOT_VERIFIED,
            evidence = "Adapter exists; no physical WFD group-owner test recorded."
        ),
        Capability(
            id = CapabilityId.MIRACAST,
            state = CapabilityState.NOT_VERIFIED,
            evidence = "Sink selection exists; no vehicle Miracast session recorded."
        ),
        Capability(
            id = CapabilityId.SCREEN_MIRRORING,
            state = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                CapabilityState.REQUIRES_PERMISSION
            } else {
                CapabilityState.AVAILABLE
            },
            evidence = "MediaProjection foreground service; fullTouch requires overlay consent.",
            notes = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                "Overlay and accessibility consent are required for reverse touch."
            } else {
                "Standard flavor mirrors without overlay permission."
            }
        ),
        Capability(
            id = CapabilityId.REVERSE_TOUCH,
            state = if (BuildConfig.ENABLE_OVERLAY_AND_TOUCH) {
                CapabilityState.REQUIRES_PERMISSION
            } else {
                CapabilityState.UNSUPPORTED
            },
            evidence = "Implemented in fullTouch only; physical injection test is still pending.",
            notes = "Geometry and long-press duration are covered by JVM tests; device receipt is not."
        ),
        Capability(
            id = CapabilityId.AUDIO_STREAMING,
            state = CapabilityState.NOT_VERIFIED,
            evidence = "AAC capture path exists; no end-to-end audio test on a head unit."
        ),
        Capability(
            id = CapabilityId.HEVC_ENCODING,
            state = CapabilityState.NOT_VERIFIED,
            evidence = "Capability negotiation exists; no H.265 hardware encode/decode pair recorded."
        ),
        Capability(
            id = CapabilityId.CARPLAY,
            state = CapabilityState.UNSUPPORTED,
            evidence = "No CarPlay entitlement, MFi hardware, or CarPlay code in this repository.",
            notes = "Out of scope without Apple entitlement approval."
        ),
        Capability(
            id = CapabilityId.ANDROID_AUTO,
            state = CapabilityState.UNSUPPORTED,
            evidence = "No Android Auto projection category, desktop head-unit protocol, or code."
        ),
        Capability(
            id = CapabilityId.USB_VIDEO,
            state = CapabilityState.UNSUPPORTED,
            evidence = "No USB/ADB/AOA video transport implementation."
        ),
        Capability(
            id = CapabilityId.BLUETOOTH_VIDEO,
            state = CapabilityState.UNSUPPORTED,
            evidence = "No Bluetooth video transport; Bluetooth is not used for media."
        )
    )

    fun byId(id: CapabilityId): Capability? = forThisBuild().firstOrNull { it.id == id }
}
