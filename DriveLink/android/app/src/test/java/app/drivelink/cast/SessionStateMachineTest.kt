package app.drivelink.cast

import app.drivelink.cast.core.session.SessionPhase
import app.drivelink.cast.core.session.SessionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {

    @Test
    fun happyPathReachesStreaming() {
        val machine = SessionStateMachine()
        assertTrue(machine.move(SessionPhase.Discovering))
        assertTrue(machine.move(SessionPhase.Connecting))
        assertTrue(machine.move(SessionPhase.Handshaking))
        assertTrue(machine.move(SessionPhase.AwaitingApproval))
        assertTrue(machine.move(SessionPhase.Streaming))
        assertEquals(SessionPhase.Streaming, machine.phase)
    }

    @Test
    fun cannotStreamWithoutApproval() {
        val machine = SessionStateMachine()
        machine.move(SessionPhase.Discovering)
        machine.move(SessionPhase.Connecting)
        machine.move(SessionPhase.Handshaking)
        assertFalse(machine.move(SessionPhase.Streaming))
        assertEquals(SessionPhase.Handshaking, machine.phase)
    }

    @Test
    fun staleGenerationIsRejected() {
        val machine = SessionStateMachine()
        machine.move(SessionPhase.Connecting)
        val generation = machine.snapshot.value.generation
        machine.reset("user cancelled")
        assertFalse(machine.isCurrent(generation))
        assertEquals(SessionPhase.Idle, machine.phase)
    }

    @Test
    fun stoppingCannotJumpBackToStreaming() {
        val machine = SessionStateMachine()
        machine.move(SessionPhase.Discovering)
        machine.move(SessionPhase.Connecting)
        machine.move(SessionPhase.Handshaking)
        machine.move(SessionPhase.AwaitingApproval)
        machine.move(SessionPhase.Streaming)
        machine.move(SessionPhase.Stopping)
        assertFalse(machine.move(SessionPhase.Streaming))
        assertTrue(machine.move(SessionPhase.Idle))
    }

    @Test
    fun generationChangesOnEveryAcceptedMove() {
        val machine = SessionStateMachine()
        val first = machine.snapshot.value.generation
        machine.move(SessionPhase.Discovering)
        val second = machine.snapshot.value.generation
        assertNotEquals(first, second)
        machine.move(SessionPhase.Discovering)
        assertEquals(second, machine.snapshot.value.generation)
    }

    @Test
    fun onlyIdleAndFailedAcceptNewConnection() {
        assertTrue(SessionPhase.Idle.canAcceptNewConnection)
        assertTrue(SessionPhase.Failed.canAcceptNewConnection)
        assertFalse(SessionPhase.Streaming.canAcceptNewConnection)
        assertFalse(SessionPhase.AwaitingApproval.canAcceptNewConnection)
    }
}
