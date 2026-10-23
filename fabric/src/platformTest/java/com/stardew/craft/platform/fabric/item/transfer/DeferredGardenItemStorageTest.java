package com.stardew.craft.platform.fabric.item.transfer;

import static com.stardew.craft.platform.fabric.item.transfer.NativeTransferVariants.item;
import static com.stardew.craft.platform.fabric.item.transfer.TransactionalRandomTest.commit;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeferredGardenItemStorageTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void stagedFertilizerPlantAndGrowthAbortWithoutWorldOrRandomWrites() throws ReflectiveOperationException {
        var pot = new Pot(); long initial = pot.random.get();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, pot.storage.insert(item(Items.DIAMOND), 1, transaction));
            assertEquals(1, pot.storage.insert(item(Items.WHEAT_SEEDS), 1, transaction));
            assertEquals(1, pot.storage.insert(item(Items.BONE_MEAL), 1, transaction));
            assertTrue(pot.world.crop.isAir()); assertFalse(pot.world.fertilizer);
            assertEquals(initial, pot.random.get()); assertEquals(0, pot.writes);
        }
        assertTrue(pot.world.crop.isAir()); assertEquals(initial, pot.random.get()); assertEquals(0, pot.writes);
    }

    @Test void growthHarvestAndReplantCanShareOneTransactionWithoutAnOpaqueQuota() throws ReflectiveOperationException {
        var pot = new Pot();
        try (var transaction = Transaction.openOuter()) {
            assertEquals(1, pot.storage.insert(item(Items.WHEAT_SEEDS), 1, transaction));
            for (int i = 0; i < 4; i++) pot.storage.insert(item(Items.BONE_MEAL), 1, transaction);
            ItemVariant product = pot.storage.getSlot(1).getResource();
            long amount = pot.storage.getSlot(1).getAmount();
            assertTrue(amount > 0); assertFalse(product.isBlank());
            assertEquals(amount, pot.storage.extract(product, Long.MAX_VALUE, transaction));
            assertEquals(1, pot.storage.insert(item(Items.WHEAT_SEEDS), 1, transaction));
            assertTrue(pot.world.crop.isAir());
            commit(transaction);
        }
        assertTrue(pot.world.crop.is(Blocks.WHEAT));
        assertEquals(0, ((CropBlock) Blocks.WHEAT).getAge(pot.world.crop));
        assertTrue(pot.writes >= 5);
    }

    @Test void repeatedForecastsDoNotSpendTheShadowOrLoseRealComponentEquality() throws ReflectiveOperationException {
        var pot = new Pot(); pot.mature();
        long initial = pot.random.get();
        ItemVariant first = pot.storage.getSlot(1).getResource();
        long amount = pot.storage.getSlot(1).getAmount();
        assertEquals(first, pot.storage.getSlot(1).getResource());
        assertEquals(initial, pot.random.get()); assertEquals(0, pot.writes);
        try (var transaction = Transaction.openOuter()) {
            assertEquals(0, pot.storage.extract(item(Items.WHEAT), amount, transaction));
            assertEquals(first, pot.storage.getSlot(1).getResource());
            assertEquals(amount, pot.storage.extract(first, amount, transaction));
        }
        assertEquals(initial, pot.random.get()); assertEquals(0, pot.writes);
        assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(pot.world.crop));
    }

    @Test void twoGardensUseOneLevelRandomStreamAndNestedAbortRestoresTheFirstPlan() throws ReflectiveOperationException {
        var first = new Pot(); var second = new Pot(first.random); first.mature(); second.mature();
        try (var outer = Transaction.openOuter()) {
            ItemVariant initial = first.storage.getSlot(1).getResource();
            try (var nested = outer.openNested()) {
                assertTrue(first.storage.extract(initial, 64, nested) > 0);
                assertTrue(second.storage.extract(second.storage.getSlot(1).getResource(), 64, nested) > 0);
            }
            assertEquals(initial, first.storage.getSlot(1).getResource());
            assertTrue(first.storage.extract(initial, 64, outer) > 0);
            assertTrue(second.storage.extract(second.storage.getSlot(1).getResource(), 64, outer) > 0);
            commit(outer);
        }
        assertTrue(first.world.crop.isAir()); assertTrue(second.world.crop.isAir());
        assertEquals(1, first.writes); assertEquals(1, second.writes);
    }

    @Test void changedCropRejectsBeforeDestinationWriteAndKeepsTheExternalEdit() throws ReflectiveOperationException {
        var pot = new Pot(); pot.mature();
        var destination = new SimpleContainer(1);
        var storage = ContainerItemStorage.of(destination);
        long initial = pot.random.get();
        try (var transaction = Transaction.openOuter()) {
            ItemVariant product = pot.storage.getSlot(1).getResource();
            long count = pot.storage.extract(product, 64, transaction);
            assertEquals(count, storage.insert(product, count, transaction));
            pot.world = new State(Blocks.STONE.defaultBlockState(), false, ItemStack.EMPTY);
            assertThrows(TransferTransactionGuards.Conflict.class, () -> commit(transaction));
        }
        assertTrue(pot.world.crop.is(Blocks.STONE)); assertTrue(destination.isEmpty());
        assertEquals(initial, pot.random.get()); assertEquals(0, pot.writes);
    }

    private record State(BlockState crop, boolean fertilizer, ItemStack output) {
        private State { output = output.copy(); }
    }
    /** Exercises the real participant/native RNG/native crop plans; world services still require headless acceptance. */
    private static final class Pot implements DeferredGardenItemStorage.Garden<State> {
        private State world = new State(Blocks.AIR.defaultBlockState(), false, ItemStack.EMPTY);
        private final AtomicLong random;
        private int writes;
        private final DeferredGardenItemStorage<State> storage = new DeferredGardenItemStorage<>(this);
        private Pot() throws ReflectiveOperationException { this(TransactionalRandomTest.seed(new LegacyRandomSource(5))); }
        private Pot(AtomicLong random) { this.random = random; }
        private void mature() { world = new State(((CropBlock) Blocks.WHEAT).getStateForAge(7), false, ItemStack.EMPTY); }
        @Override public boolean available() { return true; }
        @Override public State read() { return world; }
        @Override public boolean matches(State previous) {
            return previous.crop == world.crop && previous.fertilizer == world.fertilizer && ItemStack.matches(previous.output, world.output);
        }
        private ItemStack harvest(State state, net.minecraft.util.RandomSource source) {
            if (!(state.crop.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state.crop)) return ItemStack.EMPTY;
            int quality = source.nextInt(4); int count = source.nextInt(3) + 1;
            ItemStack output = new ItemStack(Items.WHEAT, count);
            output.set(DataComponents.CUSTOM_NAME, Component.literal("Native quality fixture " + quality));
            return output;
        }
        @Override public ItemStack preview(State state) {
            return state.output.isEmpty() ? harvest(state, TransactionalRandom.preview(random)) : state.output.copy();
        }
        @Override public Optional<DeferredGardenItemStorage.Step<State>> insert(State state, ItemStack offered, TransactionContext transaction) {
            State next;
            if (offered.is(Items.DIAMOND) && !state.fertilizer) next = new State(state.crop, true, state.output);
            else if (offered.is(Items.WHEAT_SEEDS) && state.crop.isAir()) next = new State(Blocks.WHEAT.defaultBlockState(), state.fertilizer, state.output);
            else if (offered.is(Items.BONE_MEAL) && state.crop.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state.crop))
                next = new State(NativeCropPlans.bonemeal(state.crop, TransactionalRandom.stream(random, transaction)), state.fertilizer, state.output);
            else return Optional.empty();
            return Optional.of(new DeferredGardenItemStorage.Step<>(next, 1, () -> true, () -> { world = next; writes++; }));
        }
        @Override public Optional<DeferredGardenItemStorage.Step<State>> extract(State state, ItemVariant wanted, long amount, TransactionContext transaction) {
            boolean harvesting = state.output.isEmpty();
            ItemStack output = harvesting ? harvest(state, TransactionalRandom.stream(random, transaction)) : state.output;
            if (!wanted.matches(output)) return Optional.empty();
            int count = (int) Math.min(amount, output.getCount());
            State next = new State(harvesting ? Blocks.AIR.defaultBlockState() : state.crop, state.fertilizer,
                    output.copyWithCount(output.getCount() - count));
            return Optional.of(new DeferredGardenItemStorage.Step<>(next, count, () -> true, () -> { world = next; writes++; }));
        }
    }
}
