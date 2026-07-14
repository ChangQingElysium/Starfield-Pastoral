package com.stardew.craft.cutscene.data;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;

/** Shared server/client location rules for enter-area cutscene triggers. */
public final class CutsceneTriggerLocations {
    private static final Map<String, ResourceKey<Level>> DIMENSIONS = Map.ofEntries(
            Map.entry("beach", ModDimensions.STARDEW_VALLEY),
            Map.entry("town", ModDimensions.STARDEW_VALLEY),
            Map.entry("farm", ModDimensions.STARDEW_VALLEY),
            Map.entry("forest", ModDimensions.STARDEW_VALLEY),
            Map.entry("mountain", ModDimensions.STARDEW_VALLEY),
            Map.entry("trailer", ModDimensions.STARDEW_VALLEY),
            Map.entry("seedshop", ModDimensions.STARDEW_VALLEY),
            Map.entry("saloon", ModDimensions.STARDEW_VALLEY),
            Map.entry("sciencehouse", ModDimensions.STARDEW_VALLEY),
            Map.entry("haleyhouse", ModDimensions.STARDEW_VALLEY),
            Map.entry("joshhouse", ModDimensions.STARDEW_VALLEY),
            Map.entry("samhouse", ModDimensions.STARDEW_VALLEY),
            Map.entry("1_willow_lane", ModDimensions.STARDEW_VALLEY),
            Map.entry("animalshop", ModDimensions.STARDEW_VALLEY),
            Map.entry("marnie_ranch", ModDimensions.STARDEW_VALLEY),
            Map.entry("marnieranch", ModDimensions.STARDEW_VALLEY),
            Map.entry("museum", ModDimensions.STARDEW_VALLEY),
            Map.entry("archaeologyhouse", ModDimensions.STARDEW_VALLEY),
            Map.entry("mine", ModMiningDimensions.STARDEW_MINING)
    );
    private static final Set<String> WHOLE_DIMENSION = Set.of("mine");

    private CutsceneTriggerLocations() {
    }

    public static ResourceKey<Level> dimensionFor(String location) {
        return DIMENSIONS.get(location);
    }

    public static boolean isWholeDimension(String location) {
        return WHOLE_DIMENSION.contains(location);
    }

    public static boolean contains(Player player, EventTrigger trigger) {
        if (player == null || trigger == null || trigger.location() == null) {
            return false;
        }
        ResourceKey<Level> expectedDimension = dimensionFor(trigger.location());
        if (expectedDimension == null || player.level().dimension() != expectedDimension) {
            return false;
        }
        double[] min = trigger.areaMin();
        double[] max = trigger.areaMax();
        if (min == null || max == null) {
            return isWholeDimension(trigger.location());
        }
        if (min.length < 3 || max.length < 3) {
            return false;
        }
        return player.getX() >= min[0] && player.getX() <= max[0]
                && player.getY() >= min[1] && player.getY() <= max[1]
                && player.getZ() >= min[2] && player.getZ() <= max[2];
    }
}
