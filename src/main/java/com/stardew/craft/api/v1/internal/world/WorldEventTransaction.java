package com.stardew.craft.api.v1.internal.world;

import com.stardew.craft.api.v1.world.StardewWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure bounded block transaction used by the world-event runtime and focused tests. */
final class WorldEventTransaction {
    private WorldEventTransaction() {
    }

    static Outcome commit(
            Access access,
            List<StardewWorldEvents.BlockChange> changes
    ) {
        Validation validation = validate(
                access, changes, false, false);
        if (validation != Validation.VALID) {
            return new Outcome(validation.status, 0);
        }
        return apply(access, changes, false);
    }

    static Outcome cleanup(
            Access access,
            List<StardewWorldEvents.BlockChange> changes,
            boolean recovering
    ) {
        Validation validation = validate(
                access, changes, true, recovering);
        if (validation != Validation.VALID) {
            return new Outcome(validation.status, 0);
        }
        List<StardewWorldEvents.BlockChange> pending =
                recovering
                        ? changes.stream()
                                .filter(change -> access.get(
                                        change.position()).equals(
                                        change.replacement()))
                                .toList()
                        : changes;
        return apply(access, pending, true);
    }

    private static Validation validate(
            Access access,
            List<StardewWorldEvents.BlockChange> changes,
            boolean inverse,
            boolean recovering
    ) {
        if (changes == null || changes.isEmpty()
                || changes.size() > StardewWorldEvents.MAX_BLOCK_CHANGES) {
            return Validation.INVALID;
        }
        Set<BlockPos> positions = new HashSet<>();
        for (StardewWorldEvents.BlockChange change : changes) {
            if (change == null
                    || !positions.add(change.position())
                    || change.expected().equals(change.replacement())
                    || change.expected().hasBlockEntity()
                    || change.replacement().hasBlockEntity()
                    || !access.canWrite(change.position())
                    || access.hasBlockEntity(change.position())) {
                return Validation.INVALID;
            }
            BlockState current = access.get(change.position());
            if (recovering) {
                if (!current.equals(change.expected())
                        && !current.equals(change.replacement())) {
                    return Validation.CONFLICT;
                }
            } else if (!current.equals(
                    inverse
                            ? change.replacement()
                            : change.expected())) {
                return Validation.CONFLICT;
            }
        }
        return Validation.VALID;
    }

    private static Outcome apply(
            Access access,
            List<StardewWorldEvents.BlockChange> changes,
            boolean inverse
    ) {
        List<StardewWorldEvents.BlockChange> applied =
                new ArrayList<>();
        for (StardewWorldEvents.BlockChange change : changes) {
            BlockState target = inverse
                    ? change.expected()
                    : change.replacement();
            if (!access.write(change.position(), target)) {
                boolean rollbackFailed = false;
                for (int index = applied.size() - 1;
                     index >= 0; index--) {
                    StardewWorldEvents.BlockChange prior =
                            applied.get(index);
                    BlockState rollback = inverse
                            ? prior.replacement()
                            : prior.expected();
                    if (!access.write(prior.position(), rollback)) {
                        rollbackFailed = true;
                    }
                }
                return new Outcome(
                        rollbackFailed
                                ? StardewWorldEvents.Status
                                        .ROLLBACK_FAILED
                                : StardewWorldEvents.Status
                                        .COMMIT_FAILED,
                        applied.size());
            }
            applied.add(change);
        }
        return new Outcome(
                inverse
                        ? StardewWorldEvents.Status.CLEANED
                        : StardewWorldEvents.Status.COMMITTED,
                applied.size());
    }

    interface Access {
        BlockState get(BlockPos position);

        boolean canWrite(BlockPos position);

        boolean hasBlockEntity(BlockPos position);

        boolean write(BlockPos position, BlockState state);
    }

    record Outcome(
            StardewWorldEvents.Status status,
            int changedBlocks
    ) {
    }

    private enum Validation {
        VALID(null),
        INVALID(StardewWorldEvents.Status.INVALID_PLAN),
        CONFLICT(StardewWorldEvents.Status.CONFLICT);

        private final StardewWorldEvents.Status status;

        Validation(StardewWorldEvents.Status status) {
            this.status = status;
        }
    }
}
