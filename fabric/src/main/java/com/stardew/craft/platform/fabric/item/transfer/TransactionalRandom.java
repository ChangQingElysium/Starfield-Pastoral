package com.stardew.craft.platform.fabric.item.transfer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/** Shared native recipe-roll stream; aborted rolls never advance Level.random. */
public final class TransactionalRandom {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long MASK = (1L << 48) - 1;
    private static final ThreadLocal<Map<AtomicLong, Ledger>> STREAMS = ThreadLocal.withInitial(IdentityHashMap::new);
    private TransactionalRandom() {}

    public static int nextInt(RandomSource source, int bound, TransactionContext transaction) {
        return nextInt(nativeSeed(source), bound, transaction);
    }
    public static float nextFloat(RandomSource source, TransactionContext transaction) {
        return nextFloat(nativeSeed(source), transaction);
    }
    public static double nextDouble(RandomSource source, TransactionContext transaction) {
        return nextDouble(nativeSeed(source), transaction);
    }
    /** Native bit primitives for existing crop/mixed-seed algorithms; all draws share the same ledger. */
    public static RandomSource stream(RandomSource source, TransactionContext transaction) {
        return stream(nativeSeed(source), transaction);
    }
    /** A disposable read-only forecast. It spends neither the real nor the transaction's RNG. */
    public static RandomSource preview(RandomSource source) { return preview(nativeSeed(source)); }
    static RandomSource stream(AtomicLong seed, TransactionContext transaction) {
        return new PrimitiveSource(bits -> {
            Ledger ledger = ledger(seed, transaction);
            ledger.prepare(transaction);
            return ledger.next(bits);
        });
    }
    static RandomSource preview(AtomicLong seed) {
        Ledger ledger = STREAMS.get().get(seed);
        AtomicLong copy = new AtomicLong(ledger != null && ledger.active ? ledger.shadow : seed.get());
        if (STREAMS.get().isEmpty()) STREAMS.remove();
        return new PrimitiveSource(bits -> {
            long value = copy.updateAndGet(previous -> (previous * MULTIPLIER + 11) & MASK);
            return (int) (value >>> (48 - bits));
        });
    }
    private record PrimitiveSource(IntUnaryOperator bits) implements BitRandomSource {
        @Override public int next(int size) {
            if (size < 1 || size > 32) throw new IllegalArgumentException("Random bit width must be 1..32");
            return bits.applyAsInt(size);
        }
        @Override public RandomSource fork() { return new LegacyRandomSource(nextLong()); }
        @Override public PositionalRandomFactory forkPositional() {
            return new LegacyRandomSource.LegacyPositionalRandomFactory(nextLong());
        }
        @Override public void setSeed(long seed) {
            throw new UnsupportedOperationException("A crop transaction cannot reseed the shared world random source");
        }
        @Override public double nextGaussian() {
            throw new UnsupportedOperationException("Gaussian cache reservations are outside the native crop/loot primitive contract");
        }
    }
    private static AtomicLong nativeSeed(RandomSource source) {
        if (source.getClass() != LegacyRandomSource.class || !(source instanceof LegacyRandomState state)) {
            throw new UnsupportedOperationException("Transactional recipe RNG requires the native LegacyRandomSource and its state Mixin: " + source.getClass().getName());
        }
        return state.stardewcraft$randomSeed();
    }

    // Also used with the real seed of a native source by the no-Mixin JVM fixture.
    static int nextInt(AtomicLong seed, int bound, TransactionContext transaction) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return ledger(seed, transaction).draw(bound, transaction);
    }
    static float nextFloat(AtomicLong seed, TransactionContext transaction) {
        Ledger ledger = ledger(seed, transaction);
        ledger.prepare(transaction);
        return ledger.next(24) * 0x1.0p-24f;
    }
    static double nextDouble(AtomicLong seed, TransactionContext transaction) {
        Ledger ledger = ledger(seed, transaction);
        ledger.prepare(transaction);
        return (((long) ledger.next(26) << 27) + ledger.next(27)) * 0x1.0p-53;
    }
    private static Ledger ledger(AtomicLong seed, TransactionContext transaction) {
        Ledger ledger = STREAMS.get().get(seed);
        if (ledger == null) {
            ledger = new Ledger(seed);
            STREAMS.get().put(seed, ledger);
            transaction.addOuterCloseCallback(result -> {
                STREAMS.get().remove(seed);
                if (STREAMS.get().isEmpty()) STREAMS.remove();
            });
            TransferTransactionGuards.register(transaction, ledger);
        }
        return ledger;
    }

    private static final class Ledger extends SnapshotParticipant<State> implements TransferTransactionGuards.Guard {
        private final AtomicLong source;
        private boolean active;
        private long initial;
        private long shadow;
        private Ledger(AtomicLong source) { this.source = source; }
        private void prepare(TransactionContext transaction) {
            TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
            if (!active) { initial = shadow = source.get(); active = true; }
        }
        private int draw(int bound, TransactionContext transaction) {
            prepare(transaction);
            int bits = next(31);
            if ((bound & -bound) == bound) return (int) ((bound * (long) bits) >> 31);
            int value = bits % bound;
            while (bits - value + (bound - 1) < 0) { bits = next(31); value = bits % bound; }
            return value;
        }
        private int next(int bits) {
            shadow = (shadow * MULTIPLIER + 11) & MASK;
            return (int) (shadow >>> (48 - bits));
        }
        @Override protected State createSnapshot() { return new State(active, initial, shadow); }
        @Override protected void readSnapshot(State state) { active = state.active; initial = state.initial; shadow = state.shadow; }
        @Override public void validate() {
            if (active && source.get() != initial) throw new TransferTransactionGuards.Conflict("World RNG advanced outside its open recipe transaction");
        }
        @Override public void apply() {
            // Native LegacyRandomSource belongs to this server thread. All sources
            // were validated first; no gameplay callback runs between check and set.
            if (active) source.set(shadow);
        }
    }
    private record State(boolean active, long initial, long shadow) {}
}
