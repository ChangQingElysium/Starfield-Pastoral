package com.stardew.craft.api.v1.tree;

import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/** Registration and metadata query facade for core and addon tree types. */
public final class StardewTreeTypes {
    private StardewTreeTypes() {
    }

    /**
     * Registers an addon tree during mod construction.
     *
     * <p>Higher-priority adapters are inspected first when multiple addon adapters recognize the
     * same block. Core tree blocks are always resolved before addon adapters.
     */
    public static void register(
            StardewTreeType type,
            int priority,
            StardewTreeRuntimeAdapter adapter
    ) {
        StardewTreeRuntimeRegistry.register(type, priority, adapter);
    }

    @Nullable
    public static StardewTreeType definition(ResourceLocation typeId) {
        return StardewTreeRuntimeRegistry.definition(typeId);
    }

    /** Returns core and addon definitions in stable ID order. */
    public static List<StardewTreeType> definitions() {
        return StardewTreeRuntimeRegistry.definitions();
    }
}
