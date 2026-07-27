package com.stardew.craft.api.v1.tree;

import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

import javax.annotation.Nullable;

/** Safe runtime operations shared by core commands, items and addon integrations. */
public final class StardewTreeRuntime {
    private StardewTreeRuntime() {
    }

    /** Returns an immutable tree view for any core or registered addon tree part. */
    @Nullable
    public static StardewTreeState inspect(LevelReader level, BlockPos position) {
        return StardewTreeRuntimeRegistry.inspect(level, position);
    }

    /**
     * Advances the tree found at {@code position} by one day after resolving its authoritative
     * root. Returns whether a core manager or addon adapter handled the request.
     */
    public static boolean growOneDay(ServerLevel level, BlockPos position) {
        return StardewTreeRuntimeRegistry.growOneDay(level, position);
    }

    /**
     * Applies the registered runtime rule. The caller still owns item consumption, sound,
     * particles and player messages.
     */
    public static StardewTreeRuntimeAdapter.FertilizerResult fertilize(
            ServerLevel level,
            BlockPos position
    ) {
        return StardewTreeRuntimeRegistry.fertilize(level, position);
    }
}
