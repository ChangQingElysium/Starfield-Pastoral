package com.stardew.craft.platform.fabric.item.transfer;

import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

class TransactionalLootRandomTest {
    private static final ResourceLocation WHEAT = ResourceLocation.withDefaultNamespace("blocks/wheat");
    private static final ResourceLocation CARROTS = ResourceLocation.withDefaultNamespace("blocks/carrots");

    @Test void forecastsDoNotCreateNamedSequencesOrDirtyTheNativeSavedData() {
        var store = new NativeStore();
        RandomSource expected = store.defaults().create(WHEAT).random();
        long next = expected.nextLong();
        assertEquals(next, TransactionalLootRandom.preview(store, WHEAT).nextLong());
        assertEquals(next, TransactionalLootRandom.preview(store, WHEAT).nextLong());
        assertNull(store.get(WHEAT)); assertFalse(store.owner.isDirty()); assertEquals(0, store.writes);
    }

    @Test void firstCreationAndTwoViewsOfOneSequenceUseOneOrderedNativeStream() {
        var store = new NativeStore();
        RandomSource expected = store.defaults().create(WHEAT).random();
        try (var outer = Transaction.openOuter()) {
            RandomSource first = TransactionalLootRandom.stream(store, WHEAT, outer);
            RandomSource second = TransactionalLootRandom.stream(store.alias(), WHEAT, outer);
            for (int i = 0; i < 100; i++) {
                assertEquals(expected.nextInt(1_073_741_825), first.nextInt(1_073_741_825));
                assertEquals(expected.nextBoolean(), second.nextBoolean());
                assertEquals(expected.nextFloat(), first.nextFloat());
                assertEquals(expected.nextDouble(), second.nextDouble());
                assertEquals(expected.fork().nextLong(), first.fork().nextLong());
                assertEquals(expected.forkPositional().at(2, 9, 3).nextLong(), second.forkPositional().at(2, 9, 3).nextLong());
            }
            assertNull(store.get(WHEAT)); assertFalse(store.owner.isDirty());
            commit(outer);
        }
        assertEquals(expected.nextLong(), store.get(WHEAT).random().nextLong());
        assertTrue(store.owner.isDirty()); assertEquals(1, store.writes);
    }

    @Test void nestedAbortRestoresTheShadowAndAnOuterAbortCreatesNothing() {
        var store = new NativeStore();
        RandomSource expected = store.defaults().create(WHEAT).random();
        try (var outer = Transaction.openOuter()) {
            assertEquals(expected.nextLong(), TransactionalLootRandom.stream(store, WHEAT, outer).nextLong());
            try (var nested = outer.openNested()) {
                TransactionalLootRandom.stream(store, WHEAT, nested).nextDouble();
                TransactionalLootRandom.stream(store, CARROTS, nested).nextInt();
            }
            long next = expected.nextLong();
            assertEquals(next, TransactionalLootRandom.preview(store, WHEAT).nextLong());
            assertEquals(next, TransactionalLootRandom.stream(store, WHEAT, outer).nextLong());
            commit(outer);
        }
        assertNull(store.get(CARROTS)); assertEquals(1, store.writes);
        var aborted = new NativeStore();
        try (var outer = Transaction.openOuter()) { TransactionalLootRandom.stream(aborted, WHEAT, outer).nextLong(); }
        assertNull(aborted.get(WHEAT)); assertFalse(aborted.owner.isDirty());
    }

    @Test void ordinaryNativeDrawCannotBeRewoundAndAbortsOtherSequenceWrites() {
        var store = new NativeStore();
        store.owner.get(WHEAT).nextLong();
        RandomSequence original = store.get(WHEAT);
        try (var outer = Transaction.openOuter()) {
            TransactionalLootRandom.stream(store, WHEAT, outer).nextInt();
            TransactionalLootRandom.stream(store, CARROTS, outer).nextInt();
            store.owner.get(WHEAT).nextDouble();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertSame(original, store.get(WHEAT)); assertNull(store.get(CARROTS)); assertEquals(0, store.writes);
    }

    @Test void resetOrDefaultsChangesRejectBeforeAnySequenceInstallation() {
        var store = new NativeStore();
        try (var outer = Transaction.openOuter()) {
            TransactionalLootRandom.stream(store, WHEAT, outer).nextLong();
            store.defaults = new TransactionalLootRandom.Defaults(17, 7, false, false);
            store.owner.setSeedDefaults(7, false, false);
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertNull(store.get(WHEAT));
        store.owner.get(WHEAT).nextInt();
        try (var outer = Transaction.openOuter()) {
            TransactionalLootRandom.stream(store, WHEAT, outer).nextLong();
            store.owner.reset(WHEAT);
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(0, store.writes);
    }

    @Test void existingSourceIdentityAndItsCachedGaussianArePreservedAtCommit() {
        var store = new NativeStore();
        RandomSource expected = store.defaults().create(WHEAT).random();
        RandomSource nativeSource = store.owner.get(WHEAT);
        assertEquals(expected.nextGaussian(), nativeSource.nextGaussian());
        RandomSequence original = store.get(WHEAT);
        RandomSource underlying = original.random();
        try (var outer = Transaction.openOuter()) {
            assertEquals(expected.nextInt(), TransactionalLootRandom.stream(store, WHEAT, outer).nextInt());
            commit(outer);
        }
        assertSame(original, store.get(WHEAT)); assertSame(underlying, store.get(WHEAT).random());
        assertEquals(expected.nextGaussian(), nativeSource.nextGaussian());
        assertEquals(expected.nextLong(), nativeSource.nextLong());
    }

    @Test void namedConflictAlsoAbortsLegacyWorldRngBeforeAnyGuardApplies() throws ReflectiveOperationException {
        var store = new NativeStore();
        AtomicLong legacy = TransactionalRandomTest.seed(new LegacyRandomSource(8));
        long initial = legacy.get();
        try (var outer = Transaction.openOuter()) {
            TransactionalRandom.nextInt(legacy, 8, outer);
            TransactionalLootRandom.stream(store, WHEAT, outer).nextInt();
            store.owner.get(WHEAT).nextLong();
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(outer));
        }
        assertEquals(initial, legacy.get()); assertEquals(0, store.writes);
    }

    @Test void preflightCannotAppendMoreLootDrawsToAnActiveLedger() {
        var store = new NativeStore();
        try (var outer = Transaction.openOuter()) {
            RandomSource stream = TransactionalLootRandom.stream(store, WHEAT, outer);
            stream.nextLong();
            TransferTransactionGuards.register(outer, stream::nextLong);
            assertThrows(IllegalStateException.class, () -> commit(outer));
        }
        assertNull(store.get(WHEAT)); assertFalse(store.owner.isDirty());
    }

    @Test void unsupportedSharedGaussianAndReseedingDoNotInstallASequence() {
        var store = new NativeStore();
        try (var outer = Transaction.openOuter()) {
            RandomSource stream = TransactionalLootRandom.stream(store, WHEAT, outer);
            assertThrows(UnsupportedOperationException.class, stream::nextGaussian);
            assertThrows(UnsupportedOperationException.class, () -> stream.setSeed(8));
            commit(outer);
        }
        assertNull(store.get(WHEAT)); assertFalse(store.owner.isDirty());
    }

    /** Real native RandomSequences/Xoroshiro/codec; reflection replaces only the two runtime accessors. */
    private static final class NativeStore implements TransactionalLootRandom.Store {
        private static final Field MAP = field(RandomSequences.class, "sequences");
        private static final Field GENERATOR = field(XoroshiroRandomSource.class, "randomNumberGenerator");
        private final RandomSequences owner;
        private TransactionalLootRandom.Defaults defaults;
        private int writes;
        private NativeStore() { this(new RandomSequences(17), new TransactionalLootRandom.Defaults(17, 0, true, true)); }
        private NativeStore(RandomSequences owner, TransactionalLootRandom.Defaults defaults) { this.owner = owner; this.defaults = defaults; }
        private NativeStore alias() { return new NativeStore(owner, defaults); }
        @Override public Object identity() { return owner; }
        @Override public TransactionalLootRandom.Defaults defaults() { return defaults; }
        @SuppressWarnings("unchecked") private Map<ResourceLocation, RandomSequence> map() {
            try { return (Map<ResourceLocation, RandomSequence>) MAP.get(owner); }
            catch (IllegalAccessException exception) { throw new AssertionError(exception); }
        }
        @Override public RandomSequence get(ResourceLocation id) { return map().get(id); }
        @Override public void install(ResourceLocation id, RandomSequence sequence) { map().put(id, sequence); }
        @Override public void ensureWritable(XoroshiroRandomSource source) { assertTrue(GENERATOR.canAccess(source)); }
        @Override public void advance(XoroshiroRandomSource original, XoroshiroRandomSource prepared) {
            try { GENERATOR.set(original, GENERATOR.get(prepared)); }
            catch (IllegalAccessException exception) { throw new AssertionError(exception); }
        }
        @Override public void dirty() { owner.setDirty(); writes++; }
        private static Field field(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
                if (!field.trySetAccessible()) throw new AssertionError("Native fixture field unavailable: " + name);
                return field;
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
    }
}
