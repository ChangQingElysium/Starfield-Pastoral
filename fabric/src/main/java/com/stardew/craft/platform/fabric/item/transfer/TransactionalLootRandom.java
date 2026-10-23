package com.stardew.craft.platform.fabric.item.transfer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/** Shared vanilla named-loot sequences, without mutating RandomSequences during planning or reads. */
public final class TransactionalLootRandom {
    private static final ThreadLocal<Map<Object, Map<ResourceLocation, Ledger>>> ACTIVE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private TransactionalLootRandom() {}

    public static RandomSource stream(RandomSequences sequences, ResourceLocation id, TransactionContext transaction) {
        return stream(nativeStore(sequences), id, transaction);
    }
    public static RandomSource preview(RandomSequences sequences, ResourceLocation id) {
        return preview(nativeStore(sequences), id);
    }
    record Defaults(long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequence create(ResourceLocation id) {
            return new RandomSequence((includeWorldSeed ? worldSeed : 0L) ^ (long) salt,
                    includeSequenceId ? Optional.of(id) : Optional.empty());
        }
    }
    /** Plain-JVM tests provide native field access because they do not apply Mixins. */
    interface Store {
        Object identity();
        Defaults defaults();
        RandomSequence get(ResourceLocation id);
        void install(ResourceLocation id, RandomSequence sequence);
        void ensureWritable(XoroshiroRandomSource source);
        void advance(XoroshiroRandomSource original, XoroshiroRandomSource prepared);
        void dirty();
    }
    private static Store nativeStore(RandomSequences sequences) {
        if (!(sequences instanceof RandomSequencesState state)) {
            throw new UnsupportedOperationException("Named loot transactions require the native RandomSequences state Mixin");
        }
        return new NativeStore(sequences, state);
    }
    private record NativeStore(RandomSequences sequences, RandomSequencesState state) implements Store {
        @Override public Object identity() { return sequences; }
        @Override public Defaults defaults() {
            return new Defaults(state.stardewcraft$worldSeed(), state.stardewcraft$salt(),
                    state.stardewcraft$includeWorldSeed(), state.stardewcraft$includeSequenceId());
        }
        @Override public RandomSequence get(ResourceLocation id) { return state.stardewcraft$sequences().get(id); }
        @Override public void install(ResourceLocation id, RandomSequence sequence) { state.stardewcraft$sequences().put(id, sequence); }
        @Override public void ensureWritable(XoroshiroRandomSource source) {
            if (!(source instanceof XoroshiroRandomState)) {
                throw new UnsupportedOperationException("Named loot transactions require the native Xoroshiro state Mixin");
            }
        }
        @Override public void advance(XoroshiroRandomSource original, XoroshiroRandomSource prepared) {
            ((XoroshiroRandomState) original).stardewcraft$generator(((XoroshiroRandomState) prepared).stardewcraft$generator());
        }
        @Override public void dirty() { sequences.setDirty(); }
    }
    private static XoroshiroRandomSource source(RandomSequence sequence) {
        RandomSource source = sequence.random();
        if (source.getClass() != XoroshiroRandomSource.class) {
            throw new UnsupportedOperationException("Unsupported named-loot source " + source.getClass().getName());
        }
        return (XoroshiroRandomSource) source;
    }
    private static Tag encode(XoroshiroRandomSource source) {
        return XoroshiroRandomSource.CODEC.encodeStart(NbtOps.INSTANCE, source).getOrThrow();
    }
    private static XoroshiroRandomSource decode(Tag state) {
        return XoroshiroRandomSource.CODEC.parse(NbtOps.INSTANCE, state).getOrThrow();
    }
    static RandomSource stream(Store store, ResourceLocation id, TransactionContext transaction) {
        Map<ResourceLocation, Ledger> byId = ACTIVE.get().computeIfAbsent(store.identity(), ignored -> new HashMap<>());
        Ledger ledger = byId.get(id);
        if (ledger == null) {
            ledger = new Ledger(store, id);
            byId.put(id, ledger);
            transaction.addOuterCloseCallback(result -> {
                Map<Object, Map<ResourceLocation, Ledger>> active = ACTIVE.get();
                Map<ResourceLocation, Ledger> sequences = active.get(store.identity());
                if (sequences != null) {
                    sequences.remove(id);
                    if (sequences.isEmpty()) active.remove(store.identity());
                }
                if (active.isEmpty()) ACTIVE.remove();
            });
            TransferTransactionGuards.register(transaction, ledger);
        }
        Ledger shared = ledger;
        return new PrimitiveSource(() -> {
            shared.prepare(transaction);
            return shared.shadow;
        });
    }
    static RandomSource preview(Store store, ResourceLocation id) {
        Map<Object, Map<ResourceLocation, Ledger>> active = ACTIVE.get();
        Map<ResourceLocation, Ledger> byId = active.get(store.identity());
        Ledger ledger = byId == null ? null : byId.get(id);
        XoroshiroRandomSource original;
        if (ledger != null && ledger.active) original = ledger.shadow;
        else {
            RandomSequence sequence = store.get(id);
            original = source(sequence == null ? store.defaults().create(id) : sequence);
        }
        XoroshiroRandomSource copied = decode(encode(original));
        if (active.isEmpty()) ACTIVE.remove();
        return new PrimitiveSource(() -> copied);
    }
    private record PrimitiveSource(Supplier<XoroshiroRandomSource> source) implements RandomSource {
        @Override public int nextInt() { return source.get().nextInt(); }
        @Override public int nextInt(int bound) { return source.get().nextInt(bound); }
        @Override public long nextLong() { return source.get().nextLong(); }
        @Override public boolean nextBoolean() { return source.get().nextBoolean(); }
        @Override public float nextFloat() { return source.get().nextFloat(); }
        @Override public double nextDouble() { return source.get().nextDouble(); }
        @Override public RandomSource fork() { return source.get().fork(); }
        @Override public PositionalRandomFactory forkPositional() { return source.get().forkPositional(); }
        @Override public void consumeCount(int count) { source.get().consumeCount(count); }
        @Override public void setSeed(long seed) {
            throw new UnsupportedOperationException("Loot plans cannot reseed a shared native random sequence");
        }
        @Override public double nextGaussian() {
            throw new UnsupportedOperationException("Native loot primitives do not reserve the shared Gaussian cache");
        }
    }
    private static final class Ledger extends SnapshotParticipant<State> implements TransferTransactionGuards.Guard {
        private final Store store;
        private final ResourceLocation id;
        private boolean active;
        private RandomSequence original;
        private Defaults defaults;
        private Tag initial;
        private XoroshiroRandomSource shadow;
        private XoroshiroRandomSource prepared;
        private RandomSequence created;
        private Ledger(Store store, ResourceLocation id) { this.store = store; this.id = id; }
        private void prepare(TransactionContext transaction) {
            TransferTransactionGuards.checkCanReserve(transaction);
        updateSnapshots(transaction);
            if (active) return;
            original = store.get(id);
            defaults = store.defaults();
            XoroshiroRandomSource current = source(original == null ? defaults.create(id) : original);
            store.ensureWritable(current);
            initial = encode(current);
            shadow = decode(initial);
            store.ensureWritable(shadow);
            active = true;
        }
        @Override protected State createSnapshot() {
            return new State(active, original, defaults, initial, shadow == null ? null : encode(shadow));
        }
        @Override protected void readSnapshot(State state) {
            active = state.active; original = state.original; defaults = state.defaults; initial = state.initial;
            shadow = state.shadow == null ? null : decode(state.shadow);
            prepared = null; created = null;
        }
        @Override public void validate() {
            if (!active) return;
            RandomSequence current = store.get(id);
            if (current != original || (original == null ? !defaults.equals(store.defaults())
                    : !initial.equals(encode(source(current))))) {
                throw new TransferTransactionGuards.Conflict("Vanilla loot sequence changed outside its open transaction: " + id);
            }
            // Complete all codec work before any Guard.apply may write seeds or SavedData.
            prepared = decode(encode(shadow));
            store.ensureWritable(prepared);
            if (original == null) created = new RandomSequence(prepared);
        }
        @Override public void apply() {
            if (!active) return;
            if (original == null) store.install(id, created);
            else store.advance(source(original), prepared);
            store.dirty();
        }
    }
    private record State(boolean active, RandomSequence original, Defaults defaults, Tag initial, Tag shadow) {}
}
