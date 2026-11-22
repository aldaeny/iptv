package app.drivelink.cast.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Explicit session phases. The UI-facing [app.drivelink.cast.core.model.ConnectionState] still
 * carries device/profile details; this machine owns the legal ordering and the single-flight rules.
 */
enum class SessionPhase {
    Idle,
    Discovering,
    Connecting,
    Handshaking,
    AwaitingApproval,
    Streaming,
    Reconnecting,
    Stopping,
    Failed;

    val isBusy: Boolean
        get() = this == Discovering || this == Connecting || this == Handshaking ||
            this == Reconnecting || this == Stopping

    val canAcceptNewConnection: Boolean
        get() = this == Idle || this == Failed
}

data class SessionSnapshot(
    val phase: SessionPhase = SessionPhase.Idle,
    val generation: Long = 0L,
    val reason: String = ""
)

/**
 * Thread-safe, generation-aware session gate.
 *
 * Generation increments on every accepted transition. Asynchronous callbacks capture the value
 * they started with and are ignored when a newer session has already begun — this is what prevents
 * a stale connect/reconnect coroutine from resurrecting a session the user cancelled.
 */
class SessionStateMachine {

    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    val phase: SessionPhase
        get() = _snapshot.value.phase

    @Synchronized
    fun move(next: SessionPhase, reason: String = ""): Boolean {
        val current = _snapshot.value.phase
        if (current == next) return true
        if (!isAllowed(current, next)) return false
        _snapshot.value = SessionSnapshot(
            phase = next,
            generation = _snapshot.value.generation + 1L,
            reason = reason
        )
        return true
    }

    /**
     * Miracast is a platform sink with no BYD-WMP handshake; it is the one transport that may enter
     * Streaming without an approval phase. Kept explicit so the normal path cannot use it.
     */
    @Synchronized
    fun enterExternalStreaming(reason: String): Long {
        val nextGeneration = _snapshot.value.generation + 1L
        _snapshot.value = SessionSnapshot(SessionPhase.Streaming, nextGeneration, reason)
        return nextGeneration
    }

    /** Forceful reset for a user-initiated stop; never used by network callbacks. */
    @Synchronized
    fun reset(reason: String = "reset"): Long {
        val nextGeneration = _snapshot.value.generation + 1L
        _snapshot.value = SessionSnapshot(SessionPhase.Idle, nextGeneration, reason)
        return nextGeneration
    }

    fun isCurrent(generation: Long): Boolean = _snapshot.value.generation == generation

    companion object {
        private val allowed: Map<SessionPhase, Set<SessionPhase>> = mapOf(
            SessionPhase.Idle to setOf(
                SessionPhase.Discovering,
                SessionPhase.Connecting,
                SessionPhase.Failed
            ),
            SessionPhase.Discovering to setOf(
                SessionPhase.Connecting,
                SessionPhase.Idle,
                SessionPhase.Failed
            ),
            SessionPhase.Connecting to setOf(
                SessionPhase.Handshaking,
                SessionPhase.Failed,
                SessionPhase.Stopping
            ),
            SessionPhase.Handshaking to setOf(
                SessionPhase.AwaitingApproval,
                SessionPhase.Failed,
                SessionPhase.Stopping
            ),
            SessionPhase.AwaitingApproval to setOf(
                SessionPhase.Streaming,
                SessionPhase.Failed,
                SessionPhase.Stopping
            ),
            SessionPhase.Streaming to setOf(
                SessionPhase.Reconnecting,
                SessionPhase.Stopping,
                SessionPhase.Failed
            ),
            SessionPhase.Reconnecting to setOf(
                SessionPhase.Connecting,
                SessionPhase.Failed,
                SessionPhase.Stopping
            ),
            SessionPhase.Stopping to setOf(SessionPhase.Idle, SessionPhase.Failed),
            SessionPhase.Failed to setOf(
                SessionPhase.Idle,
                SessionPhase.Discovering,
                SessionPhase.Connecting
            )
        )

        fun isAllowed(from: SessionPhase, to: SessionPhase): Boolean =
            from == to || allowed[from]?.contains(to) == true
    }
}
