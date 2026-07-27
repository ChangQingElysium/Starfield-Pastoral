package com.stardew.craft.api.v1.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/** Read-only resolver for one add-on-owned progress domain. */
@FunctionalInterface
public interface StardewProgressProvider {
    @Nullable
    StardewProgressSnapshot inspect(ServerPlayer player, ResourceLocation entryId);

    /**
     * Entries owned by this provider and safe to expose in catalogs.
     * Providers that only support direct lookup may keep the empty default.
     */
    default Collection<ResourceLocation> entries(ServerPlayer player) {
        return List.of();
    }
}
