package com.stardew.craft.api.v1.internal.world;

import com.stardew.craft.api.v1.world.StardewWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldEventTransactionTest {
    private static final BlockPos FIRST = new BlockPos(1, 2, 3);
    private static final BlockPos SECOND = new BlockPos(2, 2, 3);

    @Test
    void persistentPayloadBudgetUsesEncodedSizeNotCompressionRatio() {
        CompoundTag repetitive = new CompoundTag();
        repetitive.putString("payload", "a".repeat(
                StardewWorldEvents.MAX_PERSISTENT_DATA_BYTES));

        assertFalse(StardewWorldEventRegistry
                .withinPersistentDataBudget(repetitive));
    }

    @Test
    void preflightConflictDoesNotTouchAnyBlock() {
        FakeAccess access = access();
        access.blocks.put(SECOND, Blocks.SAND.defaultBlockState());

        WorldEventTransaction.Outcome outcome =
                WorldEventTransaction.commit(access, changes());

        assertEquals(StardewWorldEvents.Status.CONFLICT,
                outcome.status());
        assertEquals(Blocks.STONE.defaultBlockState(),
                access.get(FIRST));
        assertEquals(Blocks.SAND.defaultBlockState(),
                access.get(SECOND));
        assertEquals(0, access.writeAttempts);
    }

    @Test
    void commitFailureRollsBackEarlierWrites() {
        FakeAccess access = access();
        access.failWhen = (position, state) ->
                position.equals(SECOND)
                        && state.equals(
                        Blocks.DIAMOND_BLOCK.defaultBlockState());

        WorldEventTransaction.Outcome outcome =
                WorldEventTransaction.commit(access, changes());

        assertEquals(StardewWorldEvents.Status.COMMIT_FAILED,
                outcome.status());
        assertEquals(Blocks.STONE.defaultBlockState(),
                access.get(FIRST));
        assertEquals(Blocks.DIRT.defaultBlockState(),
                access.get(SECOND));
    }

    @Test
    void normalCleanupRequiresAndRestoresTheFullReplacementPlan() {
        FakeAccess access = access();
        WorldEventTransaction.Outcome committed =
                WorldEventTransaction.commit(access, changes());
        assertEquals(StardewWorldEvents.Status.COMMITTED,
                committed.status());

        WorldEventTransaction.Outcome cleaned =
                WorldEventTransaction.cleanup(
                        access, changes(), false);

        assertEquals(StardewWorldEvents.Status.CLEANED,
                cleaned.status());
        assertEquals(Blocks.STONE.defaultBlockState(),
                access.get(FIRST));
        assertEquals(Blocks.DIRT.defaultBlockState(),
                access.get(SECOND));
    }

    @Test
    void recoveryCleanupRestoresOnlyChangesStillApplied() {
        FakeAccess access = access();
        access.failWhen = (position, state) ->
                position.equals(SECOND)
                        && state.equals(
                        Blocks.DIAMOND_BLOCK.defaultBlockState())
                        || position.equals(FIRST)
                        && state.equals(
                        Blocks.STONE.defaultBlockState());

        WorldEventTransaction.Outcome failed =
                WorldEventTransaction.commit(access, changes());
        assertEquals(StardewWorldEvents.Status.ROLLBACK_FAILED,
                failed.status());
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(),
                access.get(FIRST));
        assertEquals(Blocks.DIRT.defaultBlockState(),
                access.get(SECOND));

        access.failWhen = (position, state) -> false;
        WorldEventTransaction.Outcome recovered =
                WorldEventTransaction.cleanup(
                        access, changes(), true);

        assertEquals(StardewWorldEvents.Status.CLEANED,
                recovered.status());
        assertEquals(Blocks.STONE.defaultBlockState(),
                access.get(FIRST));
        assertEquals(Blocks.DIRT.defaultBlockState(),
                access.get(SECOND));
    }

    private static List<StardewWorldEvents.BlockChange> changes() {
        return List.of(
                new StardewWorldEvents.BlockChange(
                        FIRST,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.GOLD_BLOCK.defaultBlockState()),
                new StardewWorldEvents.BlockChange(
                        SECOND,
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.DIAMOND_BLOCK.defaultBlockState()));
    }

    private static FakeAccess access() {
        FakeAccess access = new FakeAccess();
        access.blocks.put(FIRST, Blocks.STONE.defaultBlockState());
        access.blocks.put(SECOND, Blocks.DIRT.defaultBlockState());
        return access;
    }

    private static final class FakeAccess
            implements WorldEventTransaction.Access {
        private final Map<BlockPos, BlockState> blocks =
                new HashMap<>();
        private BiPredicate<BlockPos, BlockState> failWhen =
                (position, state) -> false;
        private int writeAttempts;

        @Override
        public BlockState get(BlockPos position) {
            return blocks.getOrDefault(
                    position, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean canWrite(BlockPos position) {
            return true;
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean write(
                BlockPos position,
                BlockState state
        ) {
            writeAttempts++;
            if (failWhen.test(position, state)) {
                return false;
            }
            blocks.put(position, state);
            return true;
        }
    }
}
