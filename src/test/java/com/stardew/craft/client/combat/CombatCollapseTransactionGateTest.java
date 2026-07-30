package com.stardew.craft.client.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatCollapseTransactionGateTest {
    @Test
    void duplicateStartDoesNotRestartAnActiveCollapse() {
        CombatCollapseTransactionGate gate = new CombatCollapseTransactionGate();
        assertEquals(CombatCollapseTransactionGate.StartDecision.STARTED, gate.begin(41L));
        assertEquals(CombatCollapseTransactionGate.StartDecision.DUPLICATE_ACTIVE, gate.begin(41L));
        assertEquals(41L, gate.activeTransaction());
    }

    @Test
    void acknowledgementCanOnlyBeEmittedOnce() {
        CombatCollapseTransactionGate gate = new CombatCollapseTransactionGate();
        gate.begin(42L);
        assertTrue(gate.acknowledgeOnce());
        assertFalse(gate.acknowledgeOnce());
        assertTrue(gate.isAcknowledged());
    }

    @Test
    void acknowledgedTransactionCannotRestartUntilReconnectReset() {
        CombatCollapseTransactionGate gate = new CombatCollapseTransactionGate();
        gate.begin(43L);
        gate.acknowledgeOnce();
        gate.finish();
        assertEquals(
            CombatCollapseTransactionGate.StartDecision.ALREADY_ACKNOWLEDGED,
            gate.begin(43L)
        );

        gate.resetSession();
        assertEquals(CombatCollapseTransactionGate.StartDecision.STARTED, gate.begin(43L));
    }

    @Test
    void genuinelyNewTransactionReplacesAnActiveOne() {
        CombatCollapseTransactionGate gate = new CombatCollapseTransactionGate();
        gate.begin(44L);
        assertEquals(
            CombatCollapseTransactionGate.StartDecision.REPLACED_ACTIVE,
            gate.begin(45L)
        );
        assertEquals(45L, gate.activeTransaction());
        assertFalse(gate.isAcknowledged());
    }

    @Test
    void delayedAcknowledgedPacketCannotReplaceANewerActiveTransaction() {
        CombatCollapseTransactionGate gate = new CombatCollapseTransactionGate();
        gate.begin(46L);
        gate.acknowledgeOnce();
        gate.finish();
        gate.begin(47L);

        assertEquals(
            CombatCollapseTransactionGate.StartDecision.ALREADY_ACKNOWLEDGED,
            gate.begin(46L)
        );
        assertEquals(47L, gate.activeTransaction());
    }
}
