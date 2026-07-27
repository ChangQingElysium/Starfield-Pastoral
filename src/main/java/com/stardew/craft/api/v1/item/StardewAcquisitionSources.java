package com.stardew.craft.api.v1.item;

import com.stardew.craft.api.v1.internal.item.StardewAcquisitionSourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/** Unified item acquisition-source lookup and addon registration facade. */
public final class StardewAcquisitionSources {
    private StardewAcquisitionSources() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewAcquisitionSourceProvider provider
    ) {
        StardewAcquisitionSourceRegistry.register(
                id, priority, provider);
    }

    public static List<StardewAcquisitionSource> find(ItemStack target) {
        return find(target, null);
    }

    public static List<StardewAcquisitionSource> find(
            ItemStack target,
            @Nullable ServerPlayer player
    ) {
        return StardewAcquisitionSourceRegistry.find(target, player);
    }
}
