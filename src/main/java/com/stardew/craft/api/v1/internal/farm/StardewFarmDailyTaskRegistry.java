package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.farm.StardewFarmDailyTasks;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Core daily-task dispatch bridge. Not part of the public compatibility surface. */
public final class StardewFarmDailyTaskRegistry {
    private static final OrderedExtensionRegistry<
            StardewFarmDailyTasks.Task> TASKS =
            new OrderedExtensionRegistry<>(
                    new ResourceLocation(
                            StardewCraft.MODID, "farm/daily_task"));

    private StardewFarmDailyTaskRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFarmDailyTasks.Task task
    ) {
        TASKS.register(id, priority, task);
    }

    public static void runActiveFarms(
            ServerLevel level,
            int absoluteDay,
            int season,
            int dayOfSeason
    ) {
        FarmInstanceRegistry farmRegistry = FarmInstanceRegistry.get(level.getServer());
        Set<UUID> online = level.getServer().getPlayerList().getPlayers().stream()
                .map(player -> player.getUUID()).collect(java.util.stream.Collectors.toSet());
        Set<UUID> processedFarms = new HashSet<>();
        for (FarmInstance farm : farmRegistry.getAllFarms()) {
            if (!farm.isInitialized() || farm.getAllFarmers().stream().noneMatch(online::contains)
                    || !processedFarms.add(farm.getInstanceId())) continue;
            run(new StardewFarmDailyTasks.Context(
                    level,
                    StardewFarmSnapshots.from(farm),
                    absoluteDay,
                    season,
                    dayOfSeason,
                    level.getRandom()
            ));
        }
    }

    public static void run(StardewFarmDailyTasks.Context context) {
        for (var registered : TASKS.entries()) {
            try {
                TASKS.invokeCheckedVoid(
                        registered,
                        task -> task.run(context));
            } catch (Exception exception) {
                StardewCraft.LOGGER.error(
                        "Stardew farm daily task {} failed for farm {}",
                        registered.id(),
                        context.farm().ownerUuid(),
                        exception
                );
            }
        }
    }
}
