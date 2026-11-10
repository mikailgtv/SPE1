package ir.guardianlink.server

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SosStateMachineTest {
    @Test fun `only declared actors and edges are accepted`() {
        assertTrue(SosStateMachine.transition(SosStatus.ACTIVATING,SosStatus.ACTIVE,"system"))
        assertTrue(SosStateMachine.transition(SosStatus.ACTIVE,SosStatus.ACKNOWLEDGED,"parent"))
        assertTrue(SosStateMachine.transition(SosStatus.ACKNOWLEDGED,SosStatus.RESOLVED,"parent"))
        assertTrue(SosStateMachine.transition(SosStatus.ACTIVE,SosStatus.CANCELLED,"device"))
        assertTrue(SosStateMachine.transition(SosStatus.ACTIVE,SosStatus.EXPIRED,"system"))
        assertFalse(SosStateMachine.transition(SosStatus.ACTIVE,SosStatus.RESOLVED,"device"))
        assertFalse(SosStateMachine.transition(SosStatus.RESOLVED,SosStatus.ACTIVE,"system"))
        assertFalse(SosStateMachine.transition(SosStatus.CANCELLED,SosStatus.ACKNOWLEDGED,"parent"))
    }
}
