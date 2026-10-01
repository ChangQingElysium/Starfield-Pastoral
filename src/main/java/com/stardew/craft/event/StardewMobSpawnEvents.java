package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

/** Suppresses ambient population without filtering summoned or persisted entities on entry. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class StardewMobSpawnEvents {
    private StardewMobSpawnEvents() {}

    static boolean blocksSpawn(ResourceKey<Level> dimension, MobSpawnType reason) {
        if (!ModDimensions.STARDEW_VALLEY.equals(dimension)
                && !ModMiningDimensions.STARDEW_MINING.equals(dimension)) return false;
        return switch (reason) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, PATROL, EVENT -> true;
            default -> false;
        };
    }

    @SubscribeEvent
    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (blocksSpawn(event.getLevel().getLevel().dimension(), event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // Also covers ambient spawners that bypass placement checks, e.g. wandering traders.
        if (blocksSpawn(event.getLevel().getLevel().dimension(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }
}
