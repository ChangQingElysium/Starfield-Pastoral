package com.stardew.craft.api.v1.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Runtime bridge for addon-owned tree blocks and persistence.
 *
 * <p>{@link #inspect} must be side-safe and read-only. Mutating methods are invoked only on the
 * logical server. The addon remains responsible for persisting its growth state and daily index.
 */
public interface StardewTreeRuntimeAdapter {
    /**
     * Recognizes the tree part at {@code position}, or returns {@code null}.
     *
     * <p>The returned type ID must equal the ID used when this adapter was registered.
     */
    @Nullable
    StardewTreeState inspect(LevelReader level, BlockPos position);

    /** Advances a verified tree by one in-game day. */
    default boolean growOneDay(ServerLevel level, StardewTreeState tree) {
        return false;
    }

    /**
     * Applies tree fertilizer without consuming the held item or spawning feedback effects.
     * Those transaction details remain owned by StardewCraft.
     */
    default FertilizerResult fertilize(ServerLevel level, StardewTreeState tree) {
        return FertilizerResult.PASS;
    }

    /** Resolves a new tapper cycle for a verified mature tree. */
    @Nullable
    default TapperCycle resolveTapperCycle(
            ServerLevel level,
            StardewTreeState tree,
            BlockPos supportPosition
    ) {
        return null;
    }

    enum FertilizerResult {
        PASS,
        APPLIED,
        ALREADY_APPLIED,
        MATURE,
        CANNOT_APPLY
    }

    /**
     * One tapper output and its number of nights until the product is ready.
     *
     * <p>The stack is copied on construction and access.
     */
    record TapperCycle(ItemStack output, int daysUntilReady) {
        public TapperCycle {
            output = Objects.requireNonNull(output, "output").copy();
            if (output.isEmpty()) {
                throw new IllegalArgumentException("Tapper output cannot be empty");
            }
            if (daysUntilReady <= 0) {
                throw new IllegalArgumentException("Tapper cycle days must be positive");
            }
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }
}
