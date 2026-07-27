package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/** Registration and metadata query facade for addon crop runtime types. */
public final class StardewCropTypes {
    private StardewCropTypes() {
    }

    /**
     * Registers an addon crop during mod construction.
     *
     * <p>Higher-priority adapters are inspected first. Core crop blocks are always resolved before
     * addon adapters.
     */
    public static void register(
            StardewCropType type,
            int priority,
            StardewCropRuntimeAdapter adapter
    ) {
        StardewCropRuntimeRegistry.register(type, priority, adapter);
    }

    @Nullable
    public static StardewCropType definition(ResourceLocation typeId) {
        return StardewCropRuntimeRegistry.definition(typeId);
    }

    /** Returns addon definitions in stable ID order. */
    public static List<StardewCropType> definitions() {
        return StardewCropRuntimeRegistry.definitions();
    }
}
