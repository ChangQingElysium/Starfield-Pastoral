package com.stardew.craft.platform.fabric.item.transfer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** Preflight runs before FAPI invokes any committed close or final-commit callback. */
public final class TransferTransactionGuards {
    private static final ThreadLocal<Map<TransactionContext, Guards>> ACTIVE = ThreadLocal.withInitial(IdentityHashMap::new);
    private TransferTransactionGuards() {}

    public interface Guard {
        void validate();
        default void apply() {}
    }

    /** Business predicates may call addon code; they cannot append reservations during preflight. */
    public static void checkCanReserve(TransactionContext transaction) {
        Transaction outer = transaction.getOpenTransaction(0);
        Guards guards = ACTIVE.get().get(outer);
        if (guards != null && guards.checking) throw new IllegalStateException("Cannot reserve during transaction preflight");
    }

    public static void register(TransactionContext transaction, Guard guard) {
        Transaction outer = transaction.getOpenTransaction(0);
        Guards guards = ACTIVE.get().get(outer);
        if (guards == null) {
            guards = new Guards();
            ACTIVE.get().put(outer, guards);
            outer.addOuterCloseCallback(result -> {
                ACTIVE.get().remove(outer);
                if (ACTIVE.get().isEmpty()) ACTIVE.remove();
            });
        }
        if (guards.checking) throw new IllegalStateException("Cannot reserve during transaction preflight");
        guards.entries.add(guard);
    }

    /** Called only by the pinned FAPI commit HEAD injection, before close(COMMITTED). */
    public static void beforeCommit(Transaction transaction) {
        if (Transaction.getCurrentUnsafe() != transaction) {
            throw new IllegalStateException("Only the current open transaction may commit");
        }
        if (transaction.nestingDepth() != 0) return;
        Guards guards = ACTIVE.get().get(transaction);
        if (guards == null) return;
        if (guards.checking) throw new IllegalStateException("Reentrant transaction commit");
        guards.checking = true;
        try {
            // No apply runs when any guard rejects the transaction.
            for (Guard guard : guards.entries) {
                guard.validate();
                if (Transaction.getCurrentUnsafe() != transaction) {
                    throw new IllegalStateException("Transaction closed during preflight");
                }
            }
            for (Guard guard : guards.entries) guard.apply();
        } catch (RuntimeException failure) {
            // FAPI abort restores every registered snapshot before exposing the conflict.
            if (Transaction.getCurrentUnsafe() == transaction) transaction.abort();
            throw failure;
        } finally {
            guards.checking = false;
        }
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict(String message) { super(message); }
    }

    private static final class Guards {
        private final ArrayList<Guard> entries = new ArrayList<>();
        private boolean checking;
    }
}
