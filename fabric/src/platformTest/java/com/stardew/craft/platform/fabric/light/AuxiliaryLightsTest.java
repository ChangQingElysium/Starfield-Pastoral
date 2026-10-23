package com.stardew.craft.platform.fabric.light;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuxiliaryLightsTest {
    private static final ChunkPos CHUNK = new ChunkPos(-2, 5);
    private static final BlockPos A = new BlockPos(-30, 24, 82), B = A.above(), C = A.above(2);
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void changesClampRememberImmutablePositionsAndOnlyInvalidateWhenTheEffectiveLightChanges() {
        var changes = new ArrayList<ChunkAuxiliaryLightManager.Change>();
        var manager = new ChunkAuxiliaryLightManager(CHUNK, changes::add);
        var mutable = A.mutable();
        manager.setLightAt(mutable, 20); mutable.move(0, 1, 0);
        manager.setLightAt(A, 15); manager.setLightAt(A, -4); manager.removeLightAt(A);
        assertEquals(List.of(new ChunkAuxiliaryLightManager.Change(A, 0, 15),
                new ChunkAuxiliaryLightManager.Change(A, 15, 0)), changes);
        assertEquals(0, manager.getLightAt(A)); assertEquals(0, manager.getLightAt(B));
    }

    @Test void retainedSnapshotsAreImmutableAndRemainValidAfterNewWrites() {
        var manager = manager(); manager.setLightAt(A, 15); var before = manager.snapshot();
        var renderRegion = new AuxiliaryLightSnapshot(before);
        manager.setLightAt(A, 0); manager.setLightAt(B, 9);
        assertEquals((byte) 15, before.get(A.asLong())); assertFalse(before.containsKey(B.asLong()));
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        assertEquals(15, renderRegion.getLightAt(A)); assertEquals(0, renderRegion.getLightAt(B));
        assertThrows(UnsupportedOperationException.class, () -> renderRegion.setLightAt(A, 0));
    }

    @Test void concurrentReadersObserveCompleteFullSnapshotsWithoutIntermediateClearOrPartialData() throws Exception {
        var manager = manager(); manager.apply(Map.of(A.asLong(), (byte) 1, B.asLong(), (byte) 1), true);
        var failure = new AtomicReference<Throwable>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    byte value = (byte) (i % 15 + 1); manager.apply(Map.of(A.asLong(), value, B.asLong(), value), true);
                }
            });
            var reader = pool.submit(() -> {
                try {
                    for (int i = 0; i < 3000; i++) {
                        var snapshot = manager.snapshot();
                        assertEquals(2, snapshot.size()); assertEquals(snapshot.get(A.asLong()), snapshot.get(B.asLong()));
                    }
                } catch (Throwable error) { failure.set(error); }
            });
            writer.get(); reader.get();
        }
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test void orderedFullAndDeltaSyncRemovesSourcesAndInvalidatesTheirOldAndNewPositions() {
        var changes = new ArrayList<ChunkAuxiliaryLightManager.Change>();
        var manager = new ChunkAuxiliaryLightManager(CHUNK, changes::add);
        manager.apply(Map.of(A.asLong(), (byte) 15, B.asLong(), (byte) 9), true);
        manager.apply(Map.of(A.asLong(), (byte) 0, C.asLong(), (byte) 8), false);
        assertEquals(Map.of(B.asLong(), (byte) 9, C.asLong(), (byte) 8), manager.snapshot());
        manager.apply(Map.of(), true);
        assertTrue(manager.snapshot().isEmpty()); assertEquals(6, changes.size());
        assertTrue(changes.contains(new ChunkAuxiliaryLightManager.Change(A, 15, 0)));
        assertTrue(changes.contains(new ChunkAuxiliaryLightManager.Change(B, 9, 0)));
    }

    @Test void saveReloadAndLegacyMigrationRetainEmissionAndAnEmptyNewCacheOverridesOldData() {
        var original = manager(); original.setLightAt(A, 15); original.setLightAt(B, 8);
        var tag = new CompoundTag(); tag.put(ChunkAuxiliaryLightManager.LEGACY_NBT_KEY, original.save());
        var loaded = manager(); loaded.loadChunkTag(tag); assertEquals(original.snapshot(), loaded.snapshot());
        loaded.removeLightAt(A); loaded.saveInto(tag);
        assertFalse(tag.contains(ChunkAuxiliaryLightManager.LEGACY_NBT_KEY));
        var reopened = manager(); reopened.loadChunkTag(tag); assertEquals(0, reopened.getLightAt(A)); assertEquals(8, reopened.getLightAt(B));
        tag.put(ChunkAuxiliaryLightManager.LEGACY_NBT_KEY, original.save());
        tag.put(ChunkAuxiliaryLightManager.NBT_KEY, new ListTag());
        var empty = manager(); empty.loadChunkTag(tag); assertTrue(empty.snapshot().isEmpty());
    }

    @Test void nbtReadHasNoWorldSideEffectsAndDuplicateLastZeroReallyExtinguishesItsSource() {
        var manager = new ChunkAuxiliaryLightManager(CHUNK, change -> fail("chunk deserialization notified a live world"));
        var input = new ListTag();
        var lit = new CompoundTag(); lit.putLong("pos", A.asLong()); lit.putByte("level", (byte) 15); input.add(lit);
        var dark = lit.copy(); dark.putByte("level", (byte) 0); input.add(dark);
        manager.load(input); assertEquals(0, manager.getLightAt(A));
    }

    @Test void wrongChunkAndInvalidNetworkLevelsAreRejectedWithoutPublishingPartialUpdates() {
        var manager = manager(); manager.setLightAt(A, 9); var original = manager.snapshot();
        assertThrows(IllegalArgumentException.class, () -> manager.setLightAt(A.offset(16, 0, 0), 15));
        assertThrows(IllegalArgumentException.class, () -> manager.apply(Map.of(A.asLong(), (byte) 16), false));
        assertThrows(IllegalArgumentException.class, () -> manager.apply(Map.of(A.asLong(), (byte) 0), true));
        assertThrows(IllegalArgumentException.class, () -> new LightSyncPayload(DIMENSION, CHUNK, false, Map.of(A.offset(16, 0, 0).asLong(), (byte) 15)));
        assertEquals(original, manager.snapshot());
    }

    @Test void fullAndDeltaPayloadCodecRoundTripsWithoutLosingZeroRemovalsOrDimension() {
        for (var payload : List.of(new LightSyncPayload(DIMENSION, CHUNK, true, Map.of(A.asLong(), (byte) 15)),
                new LightSyncPayload(DIMENSION, CHUNK, false, Map.of(A.asLong(), (byte) 0, B.asLong(), (byte) 7)))) {
            var buffer = buffer();
            try { LightSyncPayload.CODEC.encode(buffer, payload); assertEquals(payload, LightSyncPayload.CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
            finally { buffer.release(); }
        }
    }

    @Test void malformedPayloadCountsAndDuplicatePositionsFailBeforeCacheMutation() {
        for (int count : new int[]{-1, 256 * 4096 + 1, 2}) {
            var buffer = buffer();
            try {
                buffer.writeResourceLocation(DIMENSION); buffer.writeInt(CHUNK.x); buffer.writeInt(CHUNK.z); buffer.writeBoolean(false); buffer.writeVarInt(count);
                if (count == 2) { buffer.writeLong(A.asLong()); buffer.writeByte(3); buffer.writeLong(A.asLong()); buffer.writeByte(4); }
                assertThrows(IllegalArgumentException.class, () -> LightSyncPayload.CODEC.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void nativeChunkPacketComesFirstAndSyncFramesStayInTheirOrderInTheSameBundle() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var nativeChunk = (ClientboundLevelChunkWithLightPacket) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ClientboundLevelChunkWithLightPacket.class);
        var full = new LightSyncPayload(DIMENSION, CHUNK, true, Map.of(A.asLong(), (byte) 15));
        var delta = new LightSyncPayload(DIMENSION, CHUNK, false, Map.of(A.asLong(), (byte) 0));
        var bundle = AuxiliaryLights.bundlePackets(nativeChunk, List.of(full, delta));
        var packets = new ArrayList<Packet<?>>(); bundle.subPackets().forEach(packets::add);
        assertEquals(3, packets.size()); assertSame(nativeChunk, packets.getFirst());
        assertSame(full, ((ClientboundCustomPayloadPacket) packets.get(1)).payload());
        assertSame(delta, ((ClientboundCustomPayloadPacket) packets.get(2)).payload());
    }

    @Test void contextualProviderReadsOnlyTheAuxiliarySnapshotAndStateOnlyProviderHasARealStaticValue() throws Exception {
        var manager = manager(); manager.setLightAt(A, 15);
        var view = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class, AuxiliaryLightView.class},
                (proxy, method, args) -> { if (method.getName().equals("stardewcraft$auxiliaryLights")) return manager; throw new AssertionError("lighting read live data: " + method); });
        var dynamic = isolatedState(DynamicTemplate.class, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertTrue(LightEmissionHooks.dynamic(dynamic));
        assertEquals(0, LightEmissionHooks.staticEmission(dynamic, 0));
        assertEquals(15, LightEmissionHooks.getLightEmission(dynamic, view, A));
        manager.removeLightAt(A); assertEquals(0, LightEmissionHooks.getLightEmission(dynamic, view, A));
        var vanillaLamp = net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState();
        var lamp = isolatedState(StateLamp.class, vanillaLamp);
        assertFalse(LightEmissionHooks.dynamic(lamp));
        assertEquals(0, LightEmissionHooks.staticEmission(lamp, 0));
        assertEquals(15, LightEmissionHooks.staticEmission(isolatedState(StateLamp.class,
                vanillaLamp.setValue(BlockStateProperties.LIT, true)), 0));
    }

    private static ChunkAuxiliaryLightManager manager() { return new ChunkAuxiliaryLightManager(CHUNK, change -> {}); }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)); }

    /** Isolated immutable state fixtures avoid mutating the already-frozen vanilla block registry. */
    private static BlockState isolatedState(Class<? extends Block> provider, BlockState source) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var result = (BlockState) unsafe.allocateInstance(BlockState.class);
        for (Class<?> type = BlockState.class; type != Object.class; type = type.getSuperclass()) {
            for (var member : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(member.getModifiers())) continue;
                member.setAccessible(true); member.set(result, member.get(source));
            }
        }
        var owner = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("owner");
        owner.setAccessible(true); owner.set(result, unsafe.allocateInstance(provider));
        return result;
    }

    private static final class StateLamp extends Block implements LightEmissionProvider {
        StateLamp() { super(Properties.of()); registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false)); }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.LIT); }
        @Override public int getLightEmission(BlockState state, BlockGetter view, BlockPos pos) { return state.getValue(BlockStateProperties.LIT) ? 15 : 0; }
    }

    private static final class DynamicTemplate extends Block implements LightEmissionProvider {
        DynamicTemplate() { super(Properties.of()); }
        @Override public boolean hasDynamicLightEmission(BlockState state) { return true; }
        @Override public int getLightEmission(BlockState state, BlockGetter view, BlockPos pos) {
            var lights = AuxiliaryLights.getManager(view, pos); return lights == null ? 0 : lights.getLightAt(pos);
        }
    }
}
