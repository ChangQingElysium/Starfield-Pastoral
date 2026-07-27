package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.machine.StardewArtisanResolvers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Core special-machine dispatch bridge. Not part of the public compatibility surface. */
public final class StardewArtisanResolverRegistry {
    private static final ResolverSet<StardewArtisanResolvers.CaskAgingRateResolver> CASK =
            new ResolverSet<>("cask aging-rate");
    private static final ResolverSet<StardewArtisanResolvers.SmokedOutputResolver> SMOKED =
            new ResolverSet<>("smoked output");
    private static final ResolverSet<StardewArtisanResolvers.SeedMakerOutputResolver> SEED_MAKER =
            new ResolverSet<>("seed-maker output");

    private StardewArtisanResolverRegistry() {
    }

    public static void registerCask(
            ResourceLocation id,
            int priority,
            StardewArtisanResolvers.CaskAgingRateResolver resolver
    ) {
        CASK.register(id, priority, resolver);
    }

    public static void registerSmoked(
            ResourceLocation id,
            int priority,
            StardewArtisanResolvers.SmokedOutputResolver resolver
    ) {
        SMOKED.register(id, priority, resolver);
    }

    public static void registerSeedMaker(
            ResourceLocation id,
            int priority,
            StardewArtisanResolvers.SeedMakerOutputResolver resolver
    ) {
        SEED_MAKER.register(id, priority, resolver);
    }

    @Nullable
    public static Float resolveCaskAgingRate(ItemStack input) {
        for (Registered<StardewArtisanResolvers.CaskAgingRateResolver> registered
                : CASK.snapshot()) {
            try {
                Float rate = registered.resolver().resolve(input.copy());
                if (rate != null) {
                    if (rate > 0.0F && Float.isFinite(rate)) {
                        return rate;
                    }
                    StardewCraft.LOGGER.error(
                            "Stardew {} resolver {} returned invalid rate {}",
                            CASK.label(), registered.id(), rate);
                }
            } catch (RuntimeException exception) {
                logFailure(CASK.label(), registered.id(), input, exception);
            }
        }
        return null;
    }

    public static ItemStack resolveSmokedOutput(ItemStack input) {
        for (Registered<StardewArtisanResolvers.SmokedOutputResolver> registered
                : SMOKED.snapshot()) {
            try {
                ItemStack output = registered.resolver().resolve(input.copy());
                if (output != null && !output.isEmpty()) {
                    return output.copy();
                }
            } catch (RuntimeException exception) {
                logFailure(SMOKED.label(), registered.id(), input, exception);
            }
        }
        return ItemStack.EMPTY;
    }

    public static StardewArtisanResolvers.SeedResult resolveSeedMaker(Item input) {
        for (Registered<StardewArtisanResolvers.SeedMakerOutputResolver> registered
                : SEED_MAKER.snapshot()) {
            try {
                StardewArtisanResolvers.SeedResult result =
                        registered.resolver().resolve(input);
                if (result != null
                        && result.decision() != StardewArtisanResolvers.SeedDecision.PASS) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew {} resolver {} failed for {}",
                        SEED_MAKER.label(), registered.id(), input, exception);
            }
        }
        return StardewArtisanResolvers.SeedResult.pass();
    }

    private static void logFailure(
            String label,
            ResourceLocation id,
            ItemStack input,
            RuntimeException exception
    ) {
        StardewCraft.LOGGER.error(
                "Stardew {} resolver {} failed for {}",
                label, id, input.getItem(), exception);
    }

    private record Registered<T>(ResourceLocation id, int priority, T resolver) {
    }

    private static final class ResolverSet<T> {
        private final String label;
        private final Map<ResourceLocation, Registered<T>> registrations = new HashMap<>();
        private volatile List<Registered<T>> snapshot = List.of();

        private ResolverSet(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private List<Registered<T>> snapshot() {
            return snapshot;
        }

        private synchronized void register(ResourceLocation id, int priority, T resolver) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(resolver, "resolver");
            if (registrations.containsKey(id)) {
                throw new IllegalStateException(
                        "Stardew " + label + " resolver already registered: " + id);
            }
            registrations.put(id, new Registered<>(id, priority, resolver));
            ArrayList<Registered<T>> ordered = new ArrayList<>(registrations.values());
            ordered.sort(Comparator.comparingInt(Registered<T>::priority).reversed()
                    .thenComparing(value -> value.id().toString()));
            snapshot = List.copyOf(ordered);
        }
    }
}
