package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmDailyTaskRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.Objects;

/** Ordered daily maintenance tasks run once for each active initialized farm. */
public final class StardewFarmDailyTasks {
    private StardewFarmDailyTasks() {
    }

    public static void register(ResourceLocation id, int priority, Task task) {
        StardewFarmDailyTaskRegistry.register(id, priority, task);
    }

    @FunctionalInterface
    public interface Task {
        void run(Context context) throws Exception;
    }

    public record Context(
            ServerLevel level,
            StardewFarmSnapshot farm,
            int absoluteDay,
            int season,
            int dayOfSeason,
            RandomSource random
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
            random = Objects.requireNonNull(random, "random");
        }
    }
}
