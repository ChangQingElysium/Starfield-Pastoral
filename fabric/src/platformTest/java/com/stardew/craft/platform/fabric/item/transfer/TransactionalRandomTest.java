package com.stardew.craft.platform.fabric.item.transfer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;

class TransactionalRandomTest {
    @Test
    void nativePrimitiveAdapterPreservesLongBooleanForkAndPositionalForkDrawOrder() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(714);
        var expected = new LegacyRandomSource(714);
        AtomicLong nativeSeed = seed(source);
        try (var outer = Transaction.openOuter()) {
            RandomSource stream = TransactionalRandom.stream(nativeSeed, outer);
            for (int i = 0; i < 100; i++) {
                assertEquals(expected.nextInt(), stream.nextInt());
                assertEquals(expected.nextLong(), stream.nextLong());
                assertEquals(expected.nextBoolean(), stream.nextBoolean());
                assertEquals(expected.nextFloat(), stream.nextFloat());
                assertEquals(expected.nextDouble(), stream.nextDouble());
                assertEquals(expected.fork().nextLong(), stream.fork().nextLong());
                assertEquals(expected.forkPositional().at(3, 7, 9).nextLong(), stream.forkPositional().at(3, 7, 9).nextLong());
            }
            commit(outer);
        }
        assertEquals(seed(expected).get(), nativeSeed.get());
    }

    @Test
    void readOnlyForecastSpendsNeitherNativeNorExistingShadowDraws() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(32);
        var expected = new LegacyRandomSource(32);
        AtomicLong nativeSeed = seed(source);
        long initial = nativeSeed.get();
        assertEquals(expected.nextInt(7), TransactionalRandom.preview(nativeSeed).nextInt(7));
        assertEquals(initial, nativeSeed.get());
        expected = new LegacyRandomSource(32);
        try (var outer = Transaction.openOuter()) {
            RandomSource stream = TransactionalRandom.stream(nativeSeed, outer);
            assertEquals(expected.nextInt(7), stream.nextInt(7));
            long next = expected.nextLong();
            assertEquals(next, TransactionalRandom.preview(nativeSeed).nextLong());
            assertEquals(next, TransactionalRandom.preview(nativeSeed).nextLong());
            assertEquals(next, stream.nextLong());
        }
        assertEquals(initial, nativeSeed.get());
    }

    @Test
    void cropPrimitiveAdaptersExplicitlyRejectReseedingAndGaussianCacheOperations() throws ReflectiveOperationException {
        AtomicLong nativeSeed = seed(new LegacyRandomSource(13));
        long initial = nativeSeed.get();
        try (var outer = Transaction.openOuter()) {
            RandomSource stream = TransactionalRandom.stream(nativeSeed, outer);
            assertThrows(UnsupportedOperationException.class, () -> stream.setSeed(8));
            assertThrows(UnsupportedOperationException.class, stream::nextGaussian);
            assertThrows(UnsupportedOperationException.class, TransactionalRandom.preview(nativeSeed)::nextGaussian);
            commit(outer);
        }
        assertEquals(initial, nativeSeed.get());
    }

    @Test
    void boundedStreamMatchesTheNativeAlgorithmIncludingRejectionSampling() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(123456789);
        var reference = new LegacyRandomSource(123456789);
        AtomicLong seed = seed(source);
        long original = seed.get();
        try (var transaction = Transaction.openOuter()) {
            for (int i = 0; i < 400; i++) {
                for (int bound : new int[] {1, 2, 3, 999, Integer.MAX_VALUE, 1_073_741_825}) {
                    assertEquals(reference.nextInt(bound), TransactionalRandom.nextInt(seed, bound, transaction));
                }
            }
            assertEquals(original, seed.get());
            commit(transaction);
        }
        assertEquals(seed(reference).get(), seed.get());
        assertEquals(reference.nextInt(), source.nextInt());
    }

    @Test
    void mixedIntegerFloatAndDoubleRollsMatchTheExactNativeBitStream() throws ReflectiveOperationException {
        var random = new LegacyRandomSource(4_294_967_297L);
        var expected = new LegacyRandomSource(4_294_967_297L);
        AtomicLong seed = seed(random);
        long initial = seed.get();
        try (var outer = Transaction.openOuter()) {
            for (int i = 0; i < 1000; i++) {
                assertEquals(expected.nextInt(4), TransactionalRandom.nextInt(seed, 4, outer));
                assertEquals(expected.nextDouble(), TransactionalRandom.nextDouble(seed, outer));
                assertEquals(expected.nextFloat(), TransactionalRandom.nextFloat(seed, outer));
                assertEquals(expected.nextInt(999), TransactionalRandom.nextInt(seed, 999, outer));
            }
            assertEquals(initial, seed.get());
            commit(outer);
        }
        assertEquals(seed(expected).get(), seed.get());
        assertEquals(expected.nextLong(), random.nextLong());
    }

    @Test
    void nestedAbortRestoresEveryPrimitiveUsedByProbabilityRecipes() throws ReflectiveOperationException {
        var random = new LegacyRandomSource(91);
        var expected = new LegacyRandomSource(91);
        AtomicLong seed = seed(random);
        try (var outer = Transaction.openOuter()) {
            assertEquals(expected.nextFloat(), TransactionalRandom.nextFloat(seed, outer));
            try (var inner = outer.openNested()) {
                TransactionalRandom.nextDouble(seed, inner);
                TransactionalRandom.nextInt(seed, 4, inner);
            }
            assertEquals(expected.nextDouble(), TransactionalRandom.nextDouble(seed, outer));
            commit(outer);
        }
        assertEquals(expected.nextLong(), random.nextLong());
    }

    @Test
    void twoMachinesShareTheSameStreamAndNestedAbortReturnsOnlyItsOwnRolls() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(99);
        var reference = new LegacyRandomSource(99);
        AtomicLong seed = seed(source);
        long original = seed.get();
        try (var outer = Transaction.openOuter()) {
            assertEquals(reference.nextInt(999), TransactionalRandom.nextInt(seed, 999, outer));
            int second = reference.nextInt(999);
            try (var inner = outer.openNested()) {
                assertEquals(second, TransactionalRandom.nextInt(seed, 999, inner));
                commit(inner);
            }
            assertEquals(reference.nextInt(999), TransactionalRandom.nextInt(seed, 999, outer));
            try (var inner = outer.openNested()) {
                TransactionalRandom.nextInt(seed, 999, inner);
            }
            assertEquals(reference.nextInt(999), TransactionalRandom.nextInt(seed, 999, outer));
            assertEquals(original, seed.get());
            commit(outer);
        }
        assertEquals(seed(reference).get(), seed.get());
    }

    @Test
    void outerAbortCancelsCommittedNestedRolls() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(55);
        AtomicLong seed = seed(source);
        long original = seed.get();
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                TransactionalRandom.nextInt(seed, 999, inner);
                commit(inner);
            }
        }
        assertEquals(original, seed.get());
    }

    @Test
    void ordinaryDrawConflictAbortsBeforeAnyFinalCommitAndNeverRewindsTheSource() throws ReflectiveOperationException {
        var source = new LegacyRandomSource(5);
        AtomicLong seed = seed(source);
        int[] committed = {0};
        try (var outer = Transaction.openOuter()) {
            TransactionalRandom.nextInt(seed, 999, outer);
            outer.addOuterCloseCallback(result -> { if (result.wasCommitted()) committed[0]++; });
            source.nextInt();
            long afterExternalDraw = seed.get();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
            assertEquals(afterExternalDraw, seed.get());
            assertEquals(0, committed[0]);
        }
        assertNull(Transaction.getCurrentUnsafe());
    }

    @Test
    void anotherRejectedGuardPreventsEveryRandomSourceFromAdvancing() throws ReflectiveOperationException {
        AtomicLong first = seed(new LegacyRandomSource(1));
        AtomicLong second = seed(new LegacyRandomSource(2));
        long a = first.get(), b = second.get();
        try (var outer = Transaction.openOuter()) {
            TransactionalRandom.nextInt(first, 999, outer);
            TransactionalRandom.nextInt(second, 999, outer);
            TransferTransactionGuards.register(outer, () -> { throw new TransferTransactionGuards.Conflict("fixture"); });
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(a, first.get());
        assertEquals(b, second.get());
    }

    @Test
    void committingANonCurrentOuterOrReenteringPreflightCannotApplySeeds() throws ReflectiveOperationException {
        AtomicLong seed = seed(new LegacyRandomSource(10));
        long original = seed.get();
        try (var outer = Transaction.openOuter()) {
            TransactionalRandom.nextInt(seed, 999, outer);
            try (var inner = outer.openNested()) {
                assertThrows(IllegalStateException.class, () -> TransferTransactionGuards.beforeCommit(outer));
            }
            TransferTransactionGuards.register(outer, () -> TransferTransactionGuards.beforeCommit(outer));
            assertThrows(IllegalStateException.class, () -> commit(outer));
        }
        assertEquals(original, seed.get());
        assertNull(Transaction.getCurrentUnsafe());
    }

    @Test
    void abortingFromPreflightCannotApplyTheReservedRandomStream() throws ReflectiveOperationException {
        AtomicLong seed = seed(new LegacyRandomSource(11));
        long initial = seed.get();
        try (var outer = Transaction.openOuter()) {
            TransactionalRandom.nextInt(seed, 999, outer);
            TransferTransactionGuards.register(outer, outer::abort);
            assertThrows(IllegalStateException.class, () -> commit(outer));
        }
        assertEquals(initial, seed.get());
        assertNull(Transaction.getCurrentUnsafe());
    }

    @Test
    void unsupportedCustomRandomSourcesHaveAnExplicitBoundary() {
        try (var outer = Transaction.openOuter()) {
            assertThrows(UnsupportedOperationException.class, () -> TransactionalRandom.nextInt(RandomSource.createThreadSafe(), 5, outer));
        }
    }

    @Test
    void businessPreflightCannotAppendAnotherDrawToAnAlreadyActiveLedger() throws ReflectiveOperationException {
        AtomicLong source = seed(new LegacyRandomSource(9));
        long initial = source.get();
        try (var transaction = Transaction.openOuter()) {
            TransactionalRandom.nextInt(source, 9, transaction);
            TransferTransactionGuards.register(transaction, () -> TransactionalRandom.nextInt(source, 9, transaction));
            assertThrows(IllegalStateException.class, () -> commit(transaction));
        }
        assertEquals(initial, source.get());
    }

    static AtomicLong seed(LegacyRandomSource source) throws ReflectiveOperationException {
        var field = LegacyRandomSource.class.getDeclaredField("seed");
        if (!field.trySetAccessible()) throw new IllegalStateException("Native seed fixture unavailable");
        return (AtomicLong) field.get(source);
    }

    /** The plain JVM does not apply Mixins: call the exact HEAD hook, then native commit. */
    static void commit(Transaction transaction) {
        TransferTransactionGuards.beforeCommit(transaction);
        transaction.commit();
    }
}
