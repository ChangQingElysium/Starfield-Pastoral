package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.api.v1.world.StardewLocations;
import com.stardew.craft.festival.FestivalDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Shared authoritative location checks for festival actions. */
public final class StardewFestivalAccess {
    private StardewFestivalAccess() {
    }

    public static boolean isAtFestivalLocation(
            ServerPlayer player,
            FestivalDefinition definition
    ) {
        if (player == null || definition == null) {
            return false;
        }
        if (definition.locationKey().isBlank()) {
            return true;
        }
        ResourceLocation expected = StardewLocations.resolveId(
                definition.locationKey()).orElse(null);
        if (expected == null) {
            ResourceLocation explicit = ResourceLocation.tryParse(
                    definition.locationKey());
            return explicit == null
                    || definition.locationKey().indexOf(':') < 0
                    || explicit.getNamespace().equals("stardewcraft");
        }
        return StardewLocations.find(
                        player.level(), player.blockPosition())
                .map(location -> StardewLocations.isWithin(
                        location.id(), expected))
                .orElse(false);
    }
}
