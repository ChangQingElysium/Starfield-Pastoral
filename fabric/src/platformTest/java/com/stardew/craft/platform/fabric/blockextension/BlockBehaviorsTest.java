package com.stardew.craft.platform.fabric.blockextension;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockBehaviorsTest {
    private static final BlockPos POS = new BlockPos(-3, 42, 8);
    private static final ThreadLocal<List<Call>> CALLS = ThreadLocal.withInitial(ArrayList::new);
    private record Call(String property, BlockState state, BlockGetter level, BlockPos pos, Object subject) {}

    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test void contextualOverridesReceiveTheOriginalStatePositionViewAndSubject() throws Exception {
        var state = isolatedState(ContextualBlock.class, Blocks.STONE.defaultBlockState());
        var level = view(Blocks.AIR.defaultBlockState());
        var entity = allocate(ItemEntity.class); var explosion = allocate(Explosion.class);
        CALLS.get().clear();
        assertSame(SoundType.METAL, BlockBehaviors.getSoundType(state, level, POS, entity));
        assertEquals(0.91F, BlockBehaviors.getFriction(state, level, POS, entity));
        assertEquals(25F, BlockBehaviors.getExplosionResistance(state, level, POS, explosion));
        assertSame(MapColor.GOLD, BlockBehaviors.resolveMapColor(state, level, POS, MapColor.STONE));
        assertTrue(BlockBehaviors.isLadder(state, level, POS, null));
        assertEquals(List.of(new Call("sound", state, level, POS, entity), new Call("friction", state, level, POS, entity),
                new Call("resistance", state, level, POS, explosion), new Call("map", state, level, POS, MapColor.STONE),
                new Call("ladder", state, level, POS, null)), CALLS.get());
    }

    @Test void materialLikeProviderChangesAllContextualPropertiesTogether() throws Exception {
        var state = isolatedState(MaterialProxyBlock.class, Blocks.STONE.defaultBlockState());
        var wood = view(Blocks.OAK_PLANKS.defaultBlockState()); var ice = view(Blocks.ICE.defaultBlockState());
        assertSame(SoundType.WOOD, BlockBehaviors.getSoundType(state, wood, POS, null));
        assertSame(SoundType.GLASS, BlockBehaviors.getSoundType(state, ice, POS, null));
        assertEquals(Blocks.OAK_PLANKS.getFriction(), BlockBehaviors.getFriction(state, wood, POS, null));
        assertEquals(Blocks.ICE.getFriction(), BlockBehaviors.getFriction(state, ice, POS, null));
        assertEquals(Blocks.OAK_PLANKS.getExplosionResistance(), BlockBehaviors.getExplosionResistance(state, wood, POS, null));
        assertEquals(Blocks.ICE.getExplosionResistance(), BlockBehaviors.getExplosionResistance(state, ice, POS, null));
        assertSame(Blocks.OAK_PLANKS.defaultBlockState().getMapColor(wood, POS), BlockBehaviors.resolveMapColor(state, wood, POS, MapColor.NONE));
        assertSame(Blocks.ICE.defaultBlockState().getMapColor(ice, POS), BlockBehaviors.resolveMapColor(state, ice, POS, MapColor.NONE));
    }

    @Test void vanillaFallbackPreservesTheExistingNativeResultWithoutReadingWorldOrEntity() {
        var stone = Blocks.STONE.defaultBlockState();
        assertSame(SoundType.WOOD, BlockBehaviors.resolveSound(stone, null, null, null, SoundType.WOOD));
        assertSame(MapColor.GOLD, BlockBehaviors.resolveMapColor(stone, null, null, MapColor.GOLD));
        assertEquals(123.5F, BlockBehaviors.resolveFriction(Blocks.STONE, null, null, 123.5F));
        assertEquals(45F, BlockBehaviors.resolveExplosionResistance(stone, null, null, null, 45F));
        assertSame(stone.getSoundType(), BlockBehaviors.getSoundType(stone, null, POS, null));
        assertEquals(Blocks.STONE.getFriction(), BlockBehaviors.getFriction(stone, null, POS, null));
    }

    @Test void defaultProviderRetainsNativePropertiesAndTheCallerSuppliedMapColor() {
        var defaults = new BlockBehaviorProvider() {};
        var state = Blocks.ICE.defaultBlockState();
        assertSame(state.getSoundType(), defaults.getSoundType(state, null, POS, null));
        assertEquals(Blocks.ICE.getFriction(), defaults.getFriction(state, null, POS, null));
        assertEquals(Blocks.ICE.getExplosionResistance(), defaults.getExplosionResistance(state, null, POS, null));
        assertSame(MapColor.GOLD, defaults.getMapColor(state, null, POS, MapColor.GOLD));
    }

    @Test void invalidNonNullContractsFailAtTheProviderBoundary() throws Exception {
        var state = isolatedState(NullPropertiesBlock.class, Blocks.STONE.defaultBlockState());
        assertThrows(NullPointerException.class, () -> BlockBehaviors.getSoundType(state, null, POS, null));
        assertThrows(NullPointerException.class, () -> BlockBehaviors.resolveSound(state, null, POS, null, SoundType.STONE));
        assertThrows(NullPointerException.class, () -> BlockBehaviors.resolveMapColor(state, null, POS, MapColor.STONE));
    }

    @Test void openTrapdoorsRequireMatchingFacingButRecognizeAnyLadderSubclass() throws Exception {
        var trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH);
        var subclassLadder = isolatedState(CustomLadder.class, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        var matching = view(subclassLadder);
        assertFalse(BlockBehaviors.vanillaLadder(trapdoor.setValue(TrapDoorBlock.OPEN, false), matching, POS));
        assertTrue(BlockBehaviors.vanillaLadder(trapdoor.setValue(TrapDoorBlock.OPEN, true), matching, POS));
        assertFalse(BlockBehaviors.vanillaLadder(trapdoor.setValue(TrapDoorBlock.OPEN, true),
                view(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)), POS));
        assertFalse(BlockBehaviors.vanillaLadder(trapdoor.setValue(TrapDoorBlock.OPEN, true), view(Blocks.STONE.defaultBlockState()), POS));
    }

    @Test void blockAndStateOverloadsReachTheSameProviderWithoutReplacingItsIdentity() throws Exception {
        var state = isolatedState(ContextualBlock.class, Blocks.STONE.defaultBlockState()); var level = view(Blocks.AIR.defaultBlockState());
        CALLS.get().clear();
        assertSame(SoundType.METAL, BlockBehaviors.getSoundType(state.getBlock(), state, level, POS, null));
        assertTrue(BlockBehaviors.isLadder(state.getBlock(), state, level, POS, null));
        assertTrue(CALLS.get().stream().allMatch(call -> call.state == state && call.pos == POS && call.level == level));
    }

    @Test void combinationSoundsRetainBothImmutablePositionsAndAreScopedToTheEntity() throws Exception {
        var first = allocate(ItemEntity.class); var other = allocate(ItemEntity.class); var mutable = POS.mutable();
        assertNull(StepSoundContext.primary(first));
        StepSoundContext.run(first, mutable, POS.above(), () -> {
            mutable.move(9, 0, 0);
            assertEquals(POS, StepSoundContext.primary(first)); assertEquals(POS.above(), StepSoundContext.secondary(first));
            assertNull(StepSoundContext.primary(other)); assertNull(StepSoundContext.secondary(other));
        });
        assertNull(StepSoundContext.primary(first)); assertNull(StepSoundContext.secondary(first));
    }

    @Test void nestedSoundScopesRestoreTheOuterPositionEvenWhenTheInnerActionFails() throws Exception {
        var entity = allocate(ItemEntity.class);
        StepSoundContext.run(entity, POS, POS.above(), () -> {
            assertThrows(IllegalStateException.class, () -> StepSoundContext.run(entity, POS.below(), POS.below(2), () -> {
                assertEquals(POS.below(), StepSoundContext.primary(entity)); throw new IllegalStateException("test failure");
            }));
            assertEquals(POS, StepSoundContext.primary(entity)); assertEquals(POS.above(), StepSoundContext.secondary(entity));
        });
        assertNull(StepSoundContext.primary(entity));
        assertThrows(IllegalStateException.class, () -> StepSoundContext.run(entity, POS, POS, () -> { throw new IllegalStateException("test failure"); }));
        assertNull(StepSoundContext.primary(entity));
    }

    @Test void concurrentSoundScopesNeverLeakBetweenThreads() throws Exception {
        var entity = allocate(ItemEntity.class);
        try (var pool = Executors.newSingleThreadExecutor()) {
            StepSoundContext.run(entity, POS, POS, () -> {
                try {
                    pool.submit(() -> {
                        assertNull(StepSoundContext.primary(entity));
                        StepSoundContext.run(entity, POS.below(), POS.below(), () -> assertEquals(POS.below(), StepSoundContext.primary(entity)));
                        assertNull(StepSoundContext.primary(entity));
                    }).get();
                } catch (Exception error) { throw new AssertionError(error); }
                assertEquals(POS, StepSoundContext.primary(entity));
            });
        }
    }

    @Test void materialRenderDataReadsTheCapturedAttachmentWithoutLookingUpALiveBlockEntity() {
        var property = new io.github.fabricators_of_create.porting_lib.models.data.ModelProperty<BlockState>();
        var wood = Blocks.OAK_PLANKS.defaultBlockState(); var ice = Blocks.ICE.defaultBlockState();
        var oldData = io.github.fabricators_of_create.porting_lib.models.data.ModelData.builder().with(property, wood).build();
        var current = new java.util.concurrent.atomic.AtomicReference<Object>(oldData);
        var view = snapshotView(current);
        assertSame(wood, BlockMaterialSnapshots.material(view, POS, property, () -> fail("unexpected fallback")));
        current.set(io.github.fabricators_of_create.porting_lib.models.data.ModelData.builder().with(property, ice).build());
        assertSame(ice, BlockMaterialSnapshots.material(view, POS, property, () -> fail("unexpected fallback")));
        assertSame(wood, oldData.get(property));
    }

    @Test void absentOrDifferentAttachmentsUseTheDefaultMaterialWithoutQueryingTheLiveWorld() {
        var property = new io.github.fabricators_of_create.porting_lib.models.data.ModelProperty<BlockState>();
        var current = new java.util.concurrent.atomic.AtomicReference<Object>(); var view = snapshotView(current);
        var fallback = Blocks.OAK_PLANKS.defaultBlockState();
        for (Object attachment : new Object[]{null, "another mods attachment", io.github.fabricators_of_create.porting_lib.models.data.ModelData.EMPTY}) {
            current.set(attachment);
            assertSame(fallback, BlockMaterialSnapshots.material(view, POS, property, () -> fallback));
        }
    }

    private static net.fabricmc.fabric.api.blockview.v2.FabricBlockView snapshotView(java.util.concurrent.atomic.AtomicReference<Object> attachment) {
        return (net.fabricmc.fabric.api.blockview.v2.FabricBlockView) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(),
                new Class<?>[]{BlockGetter.class, net.fabricmc.fabric.api.blockview.v2.FabricBlockView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockEntityRenderData")) { assertSame(POS, args[0]); return attachment.get(); }
                    throw new AssertionError("render material read live world: " + method);
                });
    }

    private static LevelReader view(BlockState material) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, (proxy, method, args) -> {
            if (method.getName().equals("getBlockState")) return material;
            throw new AssertionError("unexpected live world access: " + method);
        });
    }
    private static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe().allocateInstance(type)); }
    private static sun.misc.Unsafe unsafe() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (sun.misc.Unsafe) field.get(null);
    }
    /** Isolated states preserve native cached properties without modifying the frozen vanilla registry. */
    private static BlockState isolatedState(Class<? extends Block> provider, BlockState source) throws Exception {
        var result = allocate(BlockState.class);
        for (Class<?> type = BlockState.class; type != Object.class; type = type.getSuperclass()) for (var field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) { field.setAccessible(true); field.set(result, field.get(source)); }
        }
        var owner = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("owner"); owner.setAccessible(true);
        owner.set(result, allocate(provider)); return result;
    }
    private static class ContextualBlock extends Block implements BlockBehaviorProvider {
        ContextualBlock() { super(Properties.of()); }
        private void record(String property, BlockState state, BlockGetter view, BlockPos pos, Object subject) { CALLS.get().add(new Call(property, state, view, pos, subject)); }
        @Override public SoundType getSoundType(BlockState state, LevelReader view, BlockPos pos, Entity entity) { record("sound", state, view, pos, entity); return SoundType.METAL; }
        @Override public float getFriction(BlockState state, LevelReader view, BlockPos pos, Entity entity) { record("friction", state, view, pos, entity); return 0.91F; }
        @Override public float getExplosionResistance(BlockState state, BlockGetter view, BlockPos pos, Explosion explosion) { record("resistance", state, view, pos, explosion); return 25F; }
        @Override public MapColor getMapColor(BlockState state, BlockGetter view, BlockPos pos, MapColor color) { record("map", state, view, pos, color); return MapColor.GOLD; }
        @Override public boolean isLadder(BlockState state, LevelReader view, BlockPos pos, LivingEntity entity) { record("ladder", state, view, pos, entity); return true; }
    }
    private static final class NullPropertiesBlock extends Block implements BlockBehaviorProvider {
        NullPropertiesBlock() { super(Properties.of()); }
        @Override public SoundType getSoundType(BlockState state, LevelReader view, BlockPos pos, Entity entity) { return null; }
        @Override public MapColor getMapColor(BlockState state, BlockGetter view, BlockPos pos, MapColor color) { return null; }
    }
    private static final class MaterialProxyBlock extends Block implements BlockBehaviorProvider {
        MaterialProxyBlock() { super(Properties.of()); }
        @Override public SoundType getSoundType(BlockState state, LevelReader view, BlockPos pos, Entity entity) { return BlockBehaviors.getSoundType(view.getBlockState(pos), view, pos, entity); }
        @Override public float getFriction(BlockState state, LevelReader view, BlockPos pos, Entity entity) { return BlockBehaviors.getFriction(view.getBlockState(pos), view, pos, entity); }
        @Override public float getExplosionResistance(BlockState state, BlockGetter view, BlockPos pos, Explosion explosion) { return BlockBehaviors.getExplosionResistance(view.getBlockState(pos), view, pos, explosion); }
        @Override public MapColor getMapColor(BlockState state, BlockGetter view, BlockPos pos, MapColor color) { return view.getBlockState(pos).getMapColor(view, pos); }
    }
    private static final class CustomLadder extends LadderBlock { CustomLadder() { super(Properties.of()); } }
}
