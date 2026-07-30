package com.stardew.craft.client.combat;

/**
 * Pure transaction/idempotence state used by the combat-collapse client presentation.
 */
final class CombatCollapseTransactionGate {
    enum StartDecision {
        STARTED,
        REPLACED_ACTIVE,
        DUPLICATE_ACTIVE,
        ALREADY_ACKNOWLEDGED
    }

    private boolean active;
    private boolean acknowledged;
    private long activeTransaction = Long.MIN_VALUE;
    private long lastAcknowledgedTransaction = Long.MIN_VALUE;

    StartDecision begin(long transactionId) {
        if (active && activeTransaction == transactionId) {
            return StartDecision.DUPLICATE_ACTIVE;
        }
        if (lastAcknowledgedTransaction == transactionId) {
            return StartDecision.ALREADY_ACKNOWLEDGED;
        }

        StartDecision result = active ? StartDecision.REPLACED_ACTIVE : StartDecision.STARTED;
        active = true;
        acknowledged = false;
        activeTransaction = transactionId;
        return result;
    }

    boolean acknowledgeOnce() {
        if (!active || acknowledged) {
            return false;
        }
        acknowledged = true;
        lastAcknowledgedTransaction = activeTransaction;
        return true;
    }

    boolean isActive() {
        return active;
    }

    boolean isAcknowledged() {
        return active && acknowledged;
    }

    long activeTransaction() {
        return activeTransaction;
    }

    void finish() {
        active = false;
        acknowledged = false;
        activeTransaction = Long.MIN_VALUE;
    }

    void resetSession() {
        finish();
        lastAcknowledgedTransaction = Long.MIN_VALUE;
    }
}
