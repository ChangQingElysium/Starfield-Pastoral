package com.stardew.craft.platform.mixin.event.level.growth.mixin;

import com.stardew.craft.platform.mixin.event.level.growth.mixin.BambooGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.BerryGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.CocoaGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.FarmlandTrampleMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.PistonMovementMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.RandomCropGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.TreeFeatureGrowthMixin;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.BonemealEvent;
import com.stardew.craft.platform.fabric.event.level.BlockEvent;
import com.stardew.craft.platform.fabric.event.level.BlockGrowFeatureEvent;
import com.stardew.craft.platform.fabric.event.level.PistonEvent;
import com.stardew.craft.platform.fabric.event.level.block.CropGrowEvent;
import com.stardew.craft.platform.fabric.event.level.growth.BoneMealApplications;
import com.stardew.craft.platform.fabric.event.level.growth.GrowthEvents;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class GrowthDecisionsTest {
    private static final BlockPos POS = new BlockPos(2, 70, -5);
    private final List<Object> listeners = new ArrayList<>();
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @AfterEach void cleanup() { listeners.forEach(FabricEvents.GAME::unregister); }
    private <E extends com.stardew.craft.platform.fabric.event.Event> void listen(Class<E> type, Consumer<E> listener) {
        listeners.add(listener); FabricEvents.GAME.addListener(type, listener);
    }

    @Test void cropDecisionDefaultForceAndVetoNeverConflateCancellation() {
        var bus = new EventBus(); var state = Blocks.WHEAT.defaultBlockState();
        assertTrue(GrowthEvents.canGrow(bus, null, POS, state, true)); assertFalse(GrowthEvents.canGrow(bus, null, POS, state, false));
        Consumer<CropGrowEvent.Pre> force = e -> { assertFalse(e.isCancelable()); assertThrows(IllegalStateException.class, () -> e.setCanceled(true)); e.setResult(CropGrowEvent.Pre.Result.GROW); };
        bus.addListener(CropGrowEvent.Pre.class, force); assertTrue(GrowthEvents.canGrow(bus, null, POS, state, false));
        bus.unregister(force); bus.addListener(CropGrowEvent.Pre.class, e -> e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW));
        assertFalse(GrowthEvents.canGrow(bus, null, POS, state, true));
    }
    @Test void cropPreKeepsImmutablePositionAndNativeState() {
        var mutable = new BlockPos.MutableBlockPos(1, 2, 3); var state = Blocks.WHEAT.defaultBlockState();
        var event = new CropGrowEvent.Pre(null, mutable, state); mutable.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), event.getPos()); assertSame(state, event.getState());
    }
    @Test void fluidCancellationRemainsSourceCompatibleWhileReplacementStateActuallyReachesTheConsumer() {
        var bus = new EventBus(); var original = Blocks.OAK_LOG.defaultBlockState();
        var world = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
                (p, m, a) -> { if (m.getName().equals("getBlockState")) return original; throw new AssertionError(m); });
        Consumer<BlockEvent.FluidPlaceBlockEvent> cancel = e -> { assertSame(original, e.getOriginalState()); e.setCanceled(true); };
        bus.addListener(BlockEvent.FluidPlaceBlockEvent.class, cancel);
        assertSame(Blocks.FIRE.defaultBlockState(), GrowthEvents.fluidState(bus, world, POS, POS.below(), Blocks.FIRE.defaultBlockState()));
        bus.unregister(cancel); bus.addListener(BlockEvent.FluidPlaceBlockEvent.class, e -> e.setNewState(Blocks.STONE.defaultBlockState()));
        assertSame(Blocks.STONE.defaultBlockState(), GrowthEvents.fluidState(bus, world, POS, POS.below(), Blocks.FIRE.defaultBlockState()));
    }
    @Test void cropRandomDrawOccursExactlyOnceEvenWhenListenerForcesOrVetoesTheNativeOutcome() throws Exception {
        var calls = new AtomicInteger(); var events = new AtomicInteger();
        listen(CropGrowEvent.Pre.class, e -> { events.incrementAndGet(); assertEquals(1, calls.get()); e.setResult(CropGrowEvent.Pre.Result.GROW); });
        int result = (int) invoke(new RandomCropGrowthMixin() {}, "stardew$growthRoll", RandomSource.create(1), 10,
                (Operation<Integer>) a -> { calls.incrementAndGet(); return 8; }, Blocks.WHEAT.defaultBlockState(), null, POS, RandomSource.create(2));
        assertEquals(0, result); assertEquals(1, calls.get()); assertEquals(1, events.get());
    }
    @Test void matureCocoaDoesNotDrawOrDispatchAndYoungCocoaDrawsBeforeDispatch() throws Exception {
        var calls = new AtomicInteger(); var events = new AtomicInteger(); listen(CropGrowEvent.Pre.class, e -> events.incrementAndGet());
        Operation<Integer> draw = a -> { calls.incrementAndGet(); return 0; };
        var mixin = new CocoaGrowthMixin() {};
        assertEquals(1, invoke(mixin, "stardew$maturityBeforeRoll", RandomSource.create(1), 5, draw, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, 2), null, POS, RandomSource.create(1)));
        assertEquals(0, calls.get()); assertEquals(0, events.get());
        assertEquals(0, invoke(mixin, "stardew$maturityBeforeRoll", RandomSource.create(1), 5, draw, Blocks.COCOA.defaultBlockState(), null, POS, RandomSource.create(1)));
        assertEquals(1, calls.get()); assertEquals(1, events.get());
    }
    @Test void berryLightGuardRunsBeforeTheDrawAndItsSecondNativeLightCheckDoesNotReadAgain() throws Exception {
        var level = world(Blocks.SWEET_BERRY_BUSH.defaultBlockState()); level.light = 8; var calls = new AtomicInteger(); var events = new AtomicInteger();
        listen(CropGrowEvent.Pre.class, e -> events.incrementAndGet()); Operation<Integer> draw = a -> { calls.incrementAndGet(); return 0; };
        var mixin = new BerryGrowthMixin() {};
        assertEquals(1, invoke(mixin, "stardew$lightBeforeRoll", level.random, 5, draw, level.state, level, POS, level.random));
        assertEquals(0, calls.get()); assertEquals(0, events.get()); assertEquals(1, level.lightReads);
        level.light = 9;
        assertEquals(0, invoke(mixin, "stardew$lightBeforeRoll", level.random, 5, draw, level.state, level, POS, level.random));
        assertEquals(1, calls.get()); assertEquals(1, events.get()); assertEquals(2, level.lightReads);
        assertEquals(9, invoke(mixin, "stardew$alreadyCheckedLight", level, POS, 0, (Operation<Integer>) a -> { fail("second light query"); return 0; }));
        assertEquals(2, level.lightReads);
    }
    @Test void bambooDefersTheOnlyGrowthDrawUntilAfterTheNativeHeightGuard() throws Exception {
        var mixin = new BambooGrowthMixin() {}; var calls = new AtomicInteger();
        assertEquals(0, invoke(mixin, "stardew$deferRoll", RandomSource.create(1), 3, (Operation<Integer>) a -> { fail("premature draw"); return 0; }));
        var random = (RandomSource) Proxy.newProxyInstance(RandomSource.class.getClassLoader(), new Class<?>[]{RandomSource.class},
                (p, m, a) -> { if (m.getName().equals("nextInt")) { assertEquals(3, a[0]); calls.incrementAndGet(); return 2; } throw new AssertionError(m); });
        listen(CropGrowEvent.Pre.class, e -> { assertEquals(1, calls.get()); e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW); });
        invoke(mixin, "stardew$decide", Blocks.BAMBOO, Blocks.BAMBOO.defaultBlockState(), null, POS, random, 15,
                (Operation<Void>) a -> { fail("vetoed bamboo grew"); return null; });
        assertEquals(1, calls.get());
    }
    @Test void canceledMegaTreeDoesNotTryTheNormalFeatureAndSelectedHolderCanBeReplacedOrRemoved() throws Exception {
        var mixin = new TreeFeatureGrowthMixin() {}; var flag = new BoolRef();
        listen(BlockGrowFeatureEvent.class, e -> e.setCanceled(true));
        assertNull(invoke(mixin, "stardew$chosenFeature", null, null, null, POS, Blocks.OAK_SAPLING.defaultBlockState(), RandomSource.create(1), flag));
        assertTrue(flag.get()); var cir = new CallbackInfoReturnable<Boolean>("growTree", true);
        invoke(mixin, "stardew$cancelMegaWithoutFallback", null, null, POS, Blocks.OAK_SAPLING.defaultBlockState(), RandomSource.create(1), cir, flag);
        assertTrue(cir.isCancelled()); assertFalse(cir.getReturnValue());
    }
    @Test void featurePayloadPreservesNullableHolderAndOriginalRandomWithoutDrawing() {
        var random = RandomSource.create(5); var event = new BlockGrowFeatureEvent(null, random, POS, null);
        assertSame(random, event.getRandom()); assertNull(event.getFeature()); assertTrue(event.isCancelable());
        Holder<ConfiguredFeature<?, ?>> marker = Holder.direct(new ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.NO_OP, net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE));
        event.setFeature(marker); assertSame(marker, event.getFeature()); event.setFeature(null); assertNull(event.getFeature());
    }
    @Test void trampleVetoSkipsOnlySoilConversionAndPreservesFallDistanceAndReplacementState() throws Exception {
        var mixin = new FarmlandTrampleMixin() {}; var entity = allocate(net.minecraft.world.entity.item.ItemEntity.class);
        listen(BlockEvent.FarmlandTrampleEvent.class, e -> { assertSame(entity, e.getEntity()); assertEquals(1.75F, e.getFallDistance()); assertSame(Blocks.DIRT.defaultBlockState(), e.getState()); e.setCanceled(true); });
        invoke(mixin, "stardew$beforeTrample", entity, Blocks.FARMLAND.defaultBlockState(), null, POS,
                (Operation<Void>) a -> { fail("canceled farmland became dirt"); return null; }, null, Blocks.FARMLAND.defaultBlockState(), POS, entity, 1.75F);
    }
    @Test void pistonPreCarriesFacingAndMoveTypeAndItsNativeResolverHasTheCorrectExtensionFlag() throws Exception {
        var level = world(Blocks.PISTON.defaultBlockState()); var event = new PistonEvent.Pre(level, POS, Direction.WEST, PistonEvent.PistonMoveType.RETRACT);
        assertSame(Direction.WEST, event.getDirection()); assertEquals(POS.west(), event.getFaceOffsetPos()); assertFalse(event.getPistonMoveType().isExtend);
        var resolver = event.getStructureHelper(); var field = resolver.getClass().getDeclaredField("extending"); field.setAccessible(true); assertFalse(field.getBoolean(resolver));
        assertNotSame(resolver, event.getStructureHelper());
    }
    @Test void canceledPistonPreStopsEitherNativeBranchBeforeItsFirstMutation() throws Exception {
        var level = world(Blocks.PISTON.defaultBlockState()); listen(PistonEvent.Pre.class, e -> e.setCanceled(true)); var mixin = new PistonMovementMixin() {};
        for (var name : List.of("stardew$extend", "stardew$retract")) {
            var cir = new CallbackInfoReturnable<Boolean>("triggerEvent", true);
            invoke(mixin, name, level.state, level, POS, name.endsWith("extend") ? 0 : 1, 0, cir);
            assertTrue(cir.isCancelled()); assertFalse(cir.getReturnValue());
        }
    }
    @Test void bonemealVetoLeavesLiveStackAndPlantUntouchedAndSuccessfulOverrideControlsConsumptionResult() throws Exception {
        var plant = plant(); var level = world(plant); var stack = new ItemStack(Items.BONE_MEAL, 3); var bus = new EventBus();
        Consumer<BonemealEvent> veto = e -> { assertNull(e.getPlayer()); assertSame(stack, e.getStack()); assertTrue(e.isValidBonemealTarget()); e.setCanceled(true); };
        bus.addListener(BonemealEvent.class, veto); assertFalse(BoneMealApplications.apply(bus, null, stack, level, POS)); assertEquals(3, stack.getCount()); assertEquals(0, level.growthCalls);
        bus.unregister(veto); bus.addListener(BonemealEvent.class, e -> e.setSuccessful(true)); assertTrue(BoneMealApplications.apply(bus, null, stack, level, POS)); assertEquals(3, stack.getCount()); assertEquals(0, level.growthCalls);
    }
    @Test void acceptedBoneMealKeepsNativeFailedSuccessRollConsumptionAndSuccessfulGrowthOrder() throws Exception {
        var level = world(plant()); var stack = new ItemStack(Items.BONE_MEAL, 3); var bus = new EventBus();
        level.success = false; assertTrue(BoneMealApplications.apply(bus, null, stack, level, POS)); assertEquals(2, stack.getCount()); assertEquals(0, level.growthCalls);
        level.success = true; assertTrue(BoneMealApplications.apply(bus, null, stack, level, POS)); assertEquals(1, stack.getCount()); assertEquals(1, level.growthCalls);
    }
    @Test void boneMealPlayerScopeRestoresNestedContextsAndCleansUpAfterExceptions() throws Exception {
        var level = world(Blocks.STONE.defaultBlockState()); var stack = new ItemStack(Items.BONE_MEAL, 2); var outer = allocate(net.minecraft.server.level.ServerPlayer.class); var inner = allocate(net.minecraft.server.level.ServerPlayer.class); var seen = new ArrayList<Player>();
        listen(BonemealEvent.class, e -> { seen.add(e.getPlayer()); e.setSuccessful(false); });
        BoneMealApplications.withPlayer(outer, () -> { BoneMealApplications.growCrop(stack, level, POS); assertThrows(IllegalArgumentException.class, () -> BoneMealApplications.withPlayer(inner, () -> { BoneMealApplications.growCrop(stack, level, POS); throw new IllegalArgumentException(); })); BoneMealApplications.growCrop(stack, level, POS); return false; });
        BoneMealApplications.growCrop(stack, level, POS);
        assertEquals(java.util.Arrays.asList(outer, inner, outer, null), seen); assertEquals(2, stack.getCount());
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        var method = java.util.Arrays.stream(target.getClass().getSuperclass().getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow(); method.setAccessible(true);
        try { return method.invoke(target, args); } catch (java.lang.reflect.InvocationTargetException e) { if (e.getCause() instanceof Error error) throw error; if (e.getCause() instanceof Exception ex) throw ex; throw e; }
    }
    private static sun.misc.Unsafe unsafe() throws Exception { var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return (sun.misc.Unsafe) f.get(null); }
    private static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe().allocateInstance(type)); }
    private static TestLevel world(BlockState state) throws Exception { var level = allocate(TestLevel.class); level.state = state; var f = Level.class.getDeclaredField("random"); f.setAccessible(true); f.set(level, RandomSource.create(1)); return level; }
    private static BlockState plant() throws Exception {
        var result = allocate(BlockState.class); var original = Blocks.WHEAT.defaultBlockState();
        for (Class<?> type = BlockState.class; type != Object.class; type = type.getSuperclass()) for (var f : type.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); f.set(result, f.get(original)); }
        var f = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("owner"); f.setAccessible(true); f.set(result, allocate(TestPlant.class)); return result;
    }
    private static final class TestPlant extends Block implements BonemealableBlock {
        TestPlant() { super(Properties.of()); }
        public boolean isValidBonemealTarget(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) { return true; }
        public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return ((TestLevel) level).success; }
        public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) { ((TestLevel) level).growthCalls++; }
    }
    private static final class TestLevel extends ServerLevel {
        BlockState state; int light; int lightReads; boolean success; int growthCalls;
        TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public BlockState getBlockState(BlockPos pos) { return state; }
        @Override public int getRawBrightness(BlockPos pos, int darken) { lightReads++; return light; }
    }
    private static final class BoolRef implements LocalBooleanRef { boolean value; public boolean get() { return value; } public void set(boolean value) { this.value = value; } }
}
