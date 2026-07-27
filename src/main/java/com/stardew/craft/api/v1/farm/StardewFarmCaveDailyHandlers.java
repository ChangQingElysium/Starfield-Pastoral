package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmCaveDailyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Ordered addon behavior for a farm cave's daily settlement. */
public final class StardewFarmCaveDailyHandlers {
    private StardewFarmCaveDailyHandlers() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            Handler handler
    ) {
        StardewFarmCaveDailyRegistry.registerHandler(id, priority, handler);
    }

    /**
     * Extends or replaces the default fruit-bat result and declares blocks removed when the farm
     * leaves fruit-bat mode.
     */
    public static void registerFruitProvider(
            ResourceLocation id,
            int priority,
            Supplier<List<Block>> managedFruitBlocks,
            FruitProvider provider
    ) {
        StardewFarmCaveDailyRegistry.registerFruitProvider(
                id, priority, managedFruitBlocks, provider);
    }

    public enum Result {
        PASS,
        SKIP_DEFAULT
    }

    @FunctionalInterface
    public interface Handler {
        Result handle(Context context);
    }

    @FunctionalInterface
    public interface FruitProvider {
        Block resolve(FruitContext context);
    }

    public record Context(
            ServerLevel level,
            StardewFarmSnapshot farm,
            BlockPos caveOrigin,
            RandomSource random
    ) {
        public Context {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
            caveOrigin = Objects.requireNonNull(caveOrigin, "caveOrigin").immutable();
            random = Objects.requireNonNull(random, "random");
        }
    }

    public record FruitContext(
            ServerLevel level,
            StardewFarmSnapshot farm,
            BlockPos caveOrigin,
            RandomSource random,
            Block currentFruit
    ) {
        public FruitContext {
            level = Objects.requireNonNull(level, "level");
            farm = Objects.requireNonNull(farm, "farm");
            caveOrigin = Objects.requireNonNull(caveOrigin, "caveOrigin").immutable();
            random = Objects.requireNonNull(random, "random");
            currentFruit = Objects.requireNonNull(currentFruit, "currentFruit");
        }
    }
}
