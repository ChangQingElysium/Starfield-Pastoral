package com.stardew.craft.platform.fabric.event.level;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.player.InteractionTestPlayer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlacementTransactionsTest {
    private final List<Object> listeners = new ArrayList<>();

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void unregister() { listeners.forEach(FabricEvents.GAME::unregister); }

    @Test void singlePlaceUsesPreStateSnapshotAndRestoresTheConsumedStackOnCancellation() throws ReflectiveOperationException {
        var world = new Fixture();
        var pos = new BlockPos(2, 3, 4);
        world.states.put(pos.below(), Blocks.STONE.defaultBlockState());
        var held = new ItemStack(Items.DIRT, 2);
        var player = InteractionTestPlayer.create(held);
        listen(event -> {
            assertSame(player, event.getEntity());
            assertSame(world.level, event.getLevel());
            assertEquals(pos, event.getPos());
            assertSame(Blocks.AIR.defaultBlockState(), event.getBlockSnapshot().getState());
            assertSame(Blocks.DIRT.defaultBlockState(), event.getPlacedBlock());
            assertSame(Blocks.STONE.defaultBlockState(), event.getPlacedAgainst());
            assertEquals(2, held.getCount(), "listeners see the pre-placement stack");
            assertFalse(PlacementTransactions.isCapturing(world.level));
            event.setCanceled(true);
        });
        assertEquals(InteractionResult.FAIL, PlacementTransactions.run(world.level, player, Direction.UP, held, () -> {
            world.set(pos, Blocks.DIRT.defaultBlockState());
            held.shrink(1);
            return InteractionResult.CONSUME;
        }));
        assertSame(Blocks.AIR.defaultBlockState(), world.level.getBlockState(pos));
        assertEquals(2, held.getCount());
        assertFalse(PlacementTransactions.isCapturing(world.level));
        assertFalse(PlacementTransactions.isRestoring(world.level));
    }

    @Test void multiPlacePublishesOneInheritedEventAndRestoresAllPositionsInReverseOrder() throws ReflectiveOperationException {
        var world = new Fixture();
        var first = new BlockPos(1, 2, 3);
        var second = first.above();
        var held = new ItemStack(Items.OAK_DOOR);
        List<BlockEvent.EntityPlaceEvent> events = new ArrayList<>();
        listen(event -> {
            events.add(event);
            var multi = assertInstanceOf(BlockEvent.EntityMultiPlaceEvent.class, event);
            assertEquals(List.of(first, second), multi.getReplacedBlockSnapshots().stream().map(BlockSnapshot::getPos).toList());
            assertThrows(UnsupportedOperationException.class, () -> multi.getReplacedBlockSnapshots().clear());
            event.setCanceled(true);
        });
        assertEquals(InteractionResult.FAIL, PlacementTransactions.run(world.level, InteractionTestPlayer.create(held),
                Direction.UP, held, () -> {
                    world.set(first, Blocks.OAK_DOOR.defaultBlockState());
                    world.set(second, Blocks.OAK_DOOR.defaultBlockState());
                    held.shrink(1);
                    return InteractionResult.SUCCESS;
                }));
        assertEquals(1, events.size());
        assertEquals(List.of(second, first), world.restored);
        assertEquals(1, held.getCount());
        assertTrue(world.level.getBlockState(first).isAir());
        assertTrue(world.level.getBlockState(second).isAir());
    }

    @Test void successfulPlacementRetainsCountAndComponentChangesIncludingConsumptionOfTheLastItem() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.DIRT);
        held.set(DataComponents.CUSTOM_NAME, Component.literal("before"));
        var player = InteractionTestPlayer.create(held);
        listen(event -> {
            assertEquals(1, held.getCount());
            assertEquals("before", held.getHoverName().getString());
            assertNull(held.get(DataComponents.CUSTOM_MODEL_DATA));
        });
        assertEquals(InteractionResult.SUCCESS, PlacementTransactions.run(world.level, player, Direction.UP, held, () -> {
            world.set(BlockPos.ZERO, Blocks.DIRT.defaultBlockState());
            held.set(DataComponents.CUSTOM_NAME, Component.literal("after"));
            held.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(12));
            held.shrink(1);
            return InteractionResult.SUCCESS;
        }));
        assertEquals(0, held.getCount());
        held.setCount(1); // inspect the native component map hidden by an empty count
        assertEquals("after", held.getHoverName().getString());
        assertEquals(new CustomModelData(12), held.get(DataComponents.CUSTOM_MODEL_DATA));
        assertSame(Blocks.DIRT.defaultBlockState(), world.level.getBlockState(BlockPos.ZERO));
    }

    @Test void cancellationRestoresRemovedAndAddedComponentsExactly() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.DIRT);
        held.set(DataComponents.CUSTOM_NAME, Component.literal("before"));
        listen(event -> event.setCanceled(true));
        PlacementTransactions.run(world.level, InteractionTestPlayer.create(held), Direction.UP, held, () -> {
            world.set(BlockPos.ZERO, Blocks.DIRT.defaultBlockState());
            held.remove(DataComponents.CUSTOM_NAME);
            held.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(23));
            held.shrink(1);
            return InteractionResult.CONSUME;
        });
        assertEquals(1, held.getCount());
        assertEquals("before", held.getHoverName().getString());
        assertNull(held.get(DataComponents.CUSTOM_MODEL_DATA));
    }

    @Test void unchangedSetBlockCallsDoNotProducePlaceEvents() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE);
        world.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        listen(event -> fail("unchanged placement"));
        assertEquals(InteractionResult.SUCCESS, PlacementTransactions.run(world.level, InteractionTestPlayer.create(held),
                Direction.UP, held, () -> {
                    assertFalse(world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
                    return InteractionResult.SUCCESS;
                }));
    }

    @Test void failedItemUseDoesNotPublishPlaceEvents() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE);
        listen(event -> fail("failed item use"));
        assertEquals(InteractionResult.FAIL, PlacementTransactions.run(world.level, InteractionTestPlayer.create(held),
                Direction.UP, held, () -> InteractionResult.FAIL));
    }

    @Test void nonConsumingWritesAreRetainedWithoutPublishingAnItemPlaceEvent() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE);
        listen(event -> fail("non-consuming item use"));
        assertEquals(InteractionResult.PASS, PlacementTransactions.run(world.level, InteractionTestPlayer.create(held),
                Direction.UP, held, () -> {
                    world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
                    return InteractionResult.PASS;
                }));
        assertSame(Blocks.STONE.defaultBlockState(), world.level.getBlockState(BlockPos.ZERO));
        assertTrue(world.restored.isEmpty());
        assertFalse(PlacementTransactions.isCapturing(world.level));
    }

    @Test void manualCaptureDefersSecondaryStateUntilTheFinalBlockStatesAreVisible() {
        var world = new Fixture();
        BlockPos first = BlockPos.ZERO;
        BlockPos second = first.east();
        world.set(first, Blocks.STONE.defaultBlockState());
        world.set(second, Blocks.STONE.defaultBlockState());
        Map<BlockPos, Boolean> secondary = new HashMap<>(Map.of(first, true, second, true));
        List<Runnable> deferred = new ArrayList<>();
        Consumer<BlockPos> supportChanged = pos -> {
            if (!world.level.getBlockState(pos).isAir()) return;
            if (PlacementTransactions.isCapturing(world.level)) {
                deferred.add(() -> { if (world.level.getBlockState(pos).isAir()) secondary.remove(pos); });
            } else secondary.remove(pos);
        };
        listen(event -> fail("manual capture must not invent an item placement"));
        try (var capture = PlacementTransactions.beginCapture(world.level)) {
            world.set(first, Blocks.AIR.defaultBlockState());
            supportChanged.accept(first);
            world.set(second, Blocks.AIR.defaultBlockState());
            supportChanged.accept(second);
            assertTrue(secondary.containsKey(first));
            assertTrue(secondary.containsKey(second));
            world.set(first, Blocks.STONE.defaultBlockState());
            assertEquals(3, capture.snapshots().size());
            assertThrows(UnsupportedOperationException.class, () -> capture.snapshots().clear());
        }
        assertFalse(PlacementTransactions.isCapturing(world.level));
        deferred.forEach(Runnable::run);
        assertTrue(secondary.containsKey(first), "restored support keeps its secondary state");
        assertFalse(secondary.containsKey(second), "committed support destruction removes its secondary state");
    }

    @Test void manualRollbackRestoresInReverseAndIsIdempotent() {
        var world = new Fixture();
        var capture = PlacementTransactions.beginCapture(world.level);
        world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        world.set(BlockPos.ZERO.above(), Blocks.DIRT.defaultBlockState());
        capture.rollback();
        capture.rollback();
        capture.close();
        assertEquals(List.of(BlockPos.ZERO.above(), BlockPos.ZERO), world.restored);
        assertTrue(world.level.getBlockState(BlockPos.ZERO).isAir());
        assertTrue(world.level.getBlockState(BlockPos.ZERO.above()).isAir());
        assertFalse(PlacementTransactions.isCapturing(world.level));
    }

    @Test void nestedManualScopesArePerLevelAndRequireReverseCloseOrder() {
        var first = new Fixture();
        var second = new Fixture();
        var outer = PlacementTransactions.beginCapture(first.level);
        var inner = PlacementTransactions.beginCapture(second.level);
        assertTrue(PlacementTransactions.isCapturing(first.level));
        assertTrue(PlacementTransactions.isCapturing(second.level));
        assertThrows(IllegalStateException.class, outer::close);
        first.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        second.set(BlockPos.ZERO, Blocks.DIRT.defaultBlockState());
        assertEquals(1, outer.snapshots().size());
        assertEquals(1, inner.snapshots().size());
        inner.rollback();
        assertTrue(PlacementTransactions.isCapturing(first.level));
        assertFalse(PlacementTransactions.isCapturing(second.level));
        outer.rollback();
        assertFalse(PlacementTransactions.isCapturing(first.level));
    }

    @Test void actionExceptionsRollbackBlocksAndComponentsThenClearTheCaptureScope() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE, 3);
        var player = InteractionTestPlayer.create(held);
        var failure = new IllegalStateException("fixture action");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> PlacementTransactions.run(world.level, player,
                Direction.UP, held, () -> {
                    world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
                    held.setCount(0);
                    throw failure;
                })));
        assertTrue(world.level.getBlockState(BlockPos.ZERO).isAir());
        assertEquals(3, held.getCount());
        assertFalse(PlacementTransactions.isCapturing(world.level));
        assertFalse(PlacementTransactions.isRestoring(world.level));
    }

    @Test void subscriberExceptionsRollbackRatherThanLeavingAPlacedBlock() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE);
        var player = InteractionTestPlayer.create(held);
        listen(event -> { throw new IllegalStateException("fixture subscriber"); });
        assertThrows(IllegalStateException.class, () -> PlacementTransactions.run(world.level, player, Direction.UP, held, () -> {
            world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            held.shrink(1);
            return InteractionResult.SUCCESS;
        }));
        assertTrue(world.level.getBlockState(BlockPos.ZERO).isAir());
        assertEquals(1, held.getCount());
        assertFalse(PlacementTransactions.isCapturing(world.level));
    }

    @Test void placementStartedByASubscriberHasItsOwnTransaction() throws ReflectiveOperationException {
        var world = new Fixture();
        var outerHeld = new ItemStack(Items.STONE);
        var innerHeld = new ItemStack(Items.DIRT);
        var player = InteractionTestPlayer.create(outerHeld);
        BlockPos innerPos = BlockPos.ZERO.above();
        List<BlockPos> events = new ArrayList<>();
        listen(event -> {
            events.add(event.getPos());
            if (event.getPos().equals(BlockPos.ZERO)) {
                assertEquals(InteractionResult.SUCCESS, PlacementTransactions.run(world.level, player, Direction.UP, innerHeld, () -> {
                    world.set(innerPos, Blocks.DIRT.defaultBlockState());
                    innerHeld.shrink(1);
                    return InteractionResult.SUCCESS;
                }));
                event.setCanceled(true);
            }
        });
        assertEquals(InteractionResult.FAIL, PlacementTransactions.run(world.level, player, Direction.UP, outerHeld, () -> {
            world.set(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            outerHeld.shrink(1);
            return InteractionResult.SUCCESS;
        }));
        assertEquals(List.of(BlockPos.ZERO, innerPos), events);
        assertTrue(world.level.getBlockState(BlockPos.ZERO).isAir());
        assertSame(Blocks.DIRT.defaultBlockState(), world.level.getBlockState(innerPos));
        assertEquals(1, outerHeld.getCount());
        assertEquals(0, innerHeld.getCount());
    }

    @Test void snapshotsKeepEntryOrderWhenBlockMutationRecursivelyChangesAnotherPosition() throws ReflectiveOperationException {
        var world = new Fixture();
        var held = new ItemStack(Items.STONE);
        BlockPos innerPos = BlockPos.ZERO.above();
        listen(event -> {
            var multi = assertInstanceOf(BlockEvent.EntityMultiPlaceEvent.class, event);
            assertEquals(List.of(BlockPos.ZERO, innerPos), multi.getReplacedBlockSnapshots().stream().map(BlockSnapshot::getPos).toList());
            event.setCanceled(true);
        });
        PlacementTransactions.run(world.level, InteractionTestPlayer.create(held), Direction.UP, held, () -> {
            BlockSnapshot outer = PlacementTransactions.beforeSet(world.level, BlockPos.ZERO, 3, 512);
            world.set(innerPos, Blocks.DIRT.defaultBlockState());
            world.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            PlacementTransactions.afterSet(outer, true);
            return InteractionResult.SUCCESS;
        });
        assertEquals(List.of(innerPos, BlockPos.ZERO), world.restored);
    }

    @Test void blockEntityDataIsCopiedAndRestoredWithTheOriginalBlock() throws ReflectiveOperationException {
        var world = new Fixture();
        BlockPos pos = new BlockPos(5, 6, 7);
        world.set(pos, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) world.entities.get(pos);
        var named = chest.saveWithFullMetadata(world.level.registryAccess());
        named.putString("CustomName", Component.Serializer.toJson(Component.literal("retained inventory"), world.level.registryAccess()));
        chest.loadWithComponents(named, world.level.registryAccess());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 4));
        var held = new ItemStack(Items.STONE);
        listen(event -> {
            var copy = event.getBlockSnapshot().getBlockEntityTag();
            assertNotNull(copy);
            copy.putString("CustomName", "broken external copy");
            assertNotEquals(copy, event.getBlockSnapshot().getBlockEntityTag());
            event.setCanceled(true);
        });
        PlacementTransactions.run(world.level, InteractionTestPlayer.create(held), Direction.UP, held, () -> {
            world.set(pos, Blocks.STONE.defaultBlockState());
            return InteractionResult.SUCCESS;
        });
        var restored = assertInstanceOf(ChestBlockEntity.class, world.entities.get(pos));
        assertEquals("retained inventory", restored.getName().getString());
        assertEquals(4, restored.getItem(0).getCount());
        assertTrue(restored.getItem(0).is(Items.DIAMOND));
    }

    private void listen(Consumer<BlockEvent.EntityPlaceEvent> listener) {
        listeners.add(listener);
        FabricEvents.GAME.addListener(BlockEvent.EntityPlaceEvent.class, listener);
    }

    /** Only the LevelAccessor storage contract is modeled; no native world or game is created. */
    private static final class Fixture {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        final List<BlockPos> restored = new ArrayList<>();
        final LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getBlockEntity" -> entities.get(args[0]);
                    case "registryAccess" -> RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
                    case "setBlock" -> set((BlockPos) args[0], (BlockState) args[1], (int) args[2], args.length == 4 ? (int) args[3] : 512);
                    case "toString" -> "PlacementFixture";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });

        boolean set(BlockPos pos, BlockState state) { return set(pos, state, 3, 512); }

        boolean set(BlockPos pos, BlockState state, int flags, int recursionLeft) {
            BlockSnapshot snapshot = PlacementTransactions.beforeSet(level, pos, flags, recursionLeft);
            boolean changed = level.getBlockState(pos) != state;
            if (changed) {
                states.put(pos.immutable(), state);
                entities.remove(pos);
                if (state.hasBlockEntity()) entities.put(pos.immutable(), ((EntityBlock) state.getBlock()).newBlockEntity(pos, state));
                if (PlacementTransactions.isRestoring(level)) restored.add(pos.immutable());
            }
            PlacementTransactions.afterSet(snapshot, changed);
            return changed;
        }
    }
}
