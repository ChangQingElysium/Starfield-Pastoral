package com.stardew.craft.platform.fabric.capability;

import static org.junit.jupiter.api.Assertions.*;
import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;

import com.stardew.craft.platform.fabric.item.IItemHandler;
import com.stardew.craft.platform.fabric.item.ItemStackHandler;
import com.stardew.craft.platform.fabric.item.wrapper.InvWrapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegisterCapabilitiesEventTest {
    private static final AtomicInteger NEXT = new AtomicInteger();
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void actualFapiLookupRunsTypedProviderAndPreservesTheSidedContext() throws ReflectiveOperationException {
        var fixture = fixture();
        var capability = new BlockCapability<IItemHandler, Direction>(BlockCapability.Kind.ITEM);
        var inventory = new SimpleContainer(2);
        var handler = new InvWrapper(inventory);
        AtomicReference<Direction> observedSide = new AtomicReference<>();
        fixture.event.registerBlockEntity(capability, BlockEntityType.CHEST, (be, side) -> {
            observedSide.set(side);
            return handler;
        });
        var level = level(new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
        var provider = fixture.items.getProvider(Blocks.CHEST);
        assertNotNull(provider);
        Storage<ItemVariant> storage = provider.find(level, BlockPos.ZERO, level.state, level.blockEntity, Direction.UP);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(5, storage.insert(item(Items.DIAMOND), 5, transaction));
            transaction.commit();
        }
        assertEquals(Direction.UP, observedSide.get());
        assertEquals(5, inventory.getItem(0).getCount());
        assertSame(handler, BlockCapabilities.get(capability, level, BlockPos.ZERO, Direction.DOWN));
        assertEquals(Direction.DOWN, observedSide.get());
    }

    @Test
    void blockAndBlockEntityProvidersComposeInsteadOfDiscardingTheMultiBlockProxy() throws ReflectiveOperationException {
        var fixture = fixture();
        var capability = new BlockCapability<IItemHandler, Direction>(BlockCapability.Kind.ITEM);
        var main = new InvWrapper(new SimpleContainer(1));
        var extension = new InvWrapper(new SimpleContainer(1));
        fixture.event.registerBlockEntity(capability, BlockEntityType.CHEST, (be, side) -> main);
        var firstProvider = fixture.items.getProvider(Blocks.CHEST);
        fixture.event.registerBlock(capability, (world, position, state, be, side) -> extension, Blocks.CHEST);
        assertSame(firstProvider, fixture.items.getProvider(Blocks.CHEST));
        var level = level(null);
        assertSame(extension, capability.getCapability(level, BlockPos.ZERO, Direction.UP));
        level.blockEntity = new ChestBlockEntity(BlockPos.ZERO, level.state);
        assertSame(main, capability.getCapability(level, BlockPos.ZERO, Direction.UP));
    }

    @Test
    void deniedProviderReturnsANonNullEmptyStorageAndExistingViewUpdatesAfterOwnerBinding() throws ReflectiveOperationException {
        var fixture = fixture();
        var capability = new BlockCapability<IItemHandler, Direction>(BlockCapability.Kind.ITEM);
        AtomicReference<IItemHandler> handler = new AtomicReference<>();
        fixture.event.registerBlock(capability, (world, position, state, be, side) -> handler.get(), Blocks.CHEST);
        var level = level(null);
        Storage<ItemVariant> retained = fixture.items.getProvider(Blocks.CHEST)
                .find(level, BlockPos.ZERO, level.state, null, Direction.UP);
        assertNotNull(retained);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, retained.insert(item(Items.DIAMOND), 1, transaction));
        }
        var inventory = new SimpleContainer(1);
        handler.set(new InvWrapper(inventory));
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, retained.insert(item(Items.DIAMOND), 1, transaction));
            transaction.commit();
        }
        assertEquals(1, inventory.getItem(0).getCount());
    }

    @Test
    void opaqueMachineTransactionsFailExplicitlyUntilTheirPlanBridgeExists() throws ReflectiveOperationException {
        var fixture = fixture();
        var capability = new BlockCapability<IItemHandler, Direction>(BlockCapability.Kind.ITEM);
        fixture.event.registerBlock(capability, (world, position, state, be, side) -> new ItemStackHandler(2), Blocks.CHEST);
        var level = level(null);
        var storage = fixture.items.getProvider(Blocks.CHEST).find(level, BlockPos.ZERO, level.state, null, Direction.UP);
        try (var transaction = Transaction.openOuter()) {
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> storage.insert(item(Items.DIAMOND), 1, transaction));
            assertTrue(failure.getMessage().contains("transactional item plan"));
        }
    }

    @Test
    void explicitlyZeroSlotHandlersRemainDisabledInsteadOfAcquiringAMachinePlan() {
        var storage = com.stardew.craft.platform.fabric.item.transfer.ItemHandlerStorage.of(new ItemStackHandler(0));
        assertFalse(storage.iterator().hasNext());
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, storage.insert(item(Items.DIAMOND), 64, transaction));
            assertEquals(0, storage.extract(item(Items.DIAMOND), 64, transaction));
        }
    }

    private static Fixture fixture() {
        int serial = NEXT.incrementAndGet();
        BlockApiLookup<Storage<ItemVariant>, Direction> items = BlockApiLookup.get(
                ResourceLocation.parse("capability_fixture:items_" + serial), Storage.asClass(), Direction.class);
        BlockApiLookup<Storage<FluidVariant>, Direction> fluids = BlockApiLookup.get(
                ResourceLocation.parse("capability_fixture:fluids_" + serial), Storage.asClass(), Direction.class);
        return new Fixture(new RegisterCapabilitiesEvent(items, fluids), items);
    }

    private static TestLevel level(BlockEntity blockEntity) throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        TestLevel level = (TestLevel) allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, TestLevel.class);
        level.state = Blocks.CHEST.defaultBlockState();
        level.blockEntity = blockEntity;
        return level;
    }

    private static final class TestLevel extends ServerLevel {
        private BlockState state;
        private BlockEntity blockEntity;
        private TestLevel() { super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null); }
        @Override public BlockState getBlockState(BlockPos position) { return state; }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return blockEntity; }
    }

    private record Fixture(RegisterCapabilitiesEvent event, BlockApiLookup<Storage<ItemVariant>, Direction> items) {}
}
