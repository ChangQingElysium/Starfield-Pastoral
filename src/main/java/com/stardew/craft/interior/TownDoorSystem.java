package com.stardew.craft.interior;

import com.stardew.craft.interior.door.TownDoorRuntime;
import com.stardew.craft.interior.door.TownDoorDefinitions;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;

/** Bootstrap for the project-native town door connections. */
public final class TownDoorSystem {
    private TownDoorSystem() {}

    public static void register(IEventBus bus) {
        TownDoorRuntime.register(bus);
    }

    public static boolean replacesLegacy(ResourceKey<Level> dimension, String target) {
        return ModDimensions.STARDEW_VALLEY.equals(dimension)
                && TownDoorDefinitions.replacesLegacy(dimension, target);
    }
}
