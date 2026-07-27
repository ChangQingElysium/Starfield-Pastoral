package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewArtisanResolverRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Runtime resolvers for special artisan-machine behavior not expressible by recipe JSON alone. */
public final class StardewArtisanResolvers {
    private StardewArtisanResolvers() {
    }

    public static void registerCaskAgingRate(
            ResourceLocation id,
            int priority,
            CaskAgingRateResolver resolver
    ) {
        StardewArtisanResolverRegistry.registerCask(id, priority, resolver);
    }

    public static void registerSmokedOutput(
            ResourceLocation id,
            int priority,
            SmokedOutputResolver resolver
    ) {
        StardewArtisanResolverRegistry.registerSmoked(id, priority, resolver);
    }

    public static void registerSeedMakerOutput(
            ResourceLocation id,
            int priority,
            SeedMakerOutputResolver resolver
    ) {
        StardewArtisanResolverRegistry.registerSeedMaker(id, priority, resolver);
    }

    @FunctionalInterface
    public interface CaskAgingRateResolver {
        /** Returns a positive rate, or {@code null} to pass to the next resolver. */
        Float resolve(ItemStack input);
    }

    @FunctionalInterface
    public interface SmokedOutputResolver {
        /** Returns a non-empty output, or {@code null}/{@link ItemStack#EMPTY} to pass. */
        ItemStack resolve(ItemStack input);
    }

    @FunctionalInterface
    public interface SeedMakerOutputResolver {
        SeedResult resolve(Item input);
    }

    public enum SeedDecision {
        PASS,
        OUTPUT,
        DENY
    }

    public record SeedResult(SeedDecision decision, Item output) {
        public SeedResult {
            decision = Objects.requireNonNull(decision, "decision");
            if (decision == SeedDecision.OUTPUT && output == null) {
                throw new IllegalArgumentException("OUTPUT seed result requires an item");
            }
            if (decision != SeedDecision.OUTPUT && output != null) {
                throw new IllegalArgumentException("Only OUTPUT seed results may contain an item");
            }
        }

        public static SeedResult pass() {
            return new SeedResult(SeedDecision.PASS, null);
        }

        public static SeedResult output(Item item) {
            return new SeedResult(SeedDecision.OUTPUT, Objects.requireNonNull(item, "item"));
        }

        public static SeedResult deny() {
            return new SeedResult(SeedDecision.DENY, null);
        }
    }
}
