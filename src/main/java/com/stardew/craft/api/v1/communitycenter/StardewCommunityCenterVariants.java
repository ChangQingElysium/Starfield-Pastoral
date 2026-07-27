package com.stardew.craft.api.v1.communitycenter;

import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Ordered player-specific bundle catalog transformations. */
public final class StardewCommunityCenterVariants {
    private StardewCommunityCenterVariants() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewCommunityCenterVariantRegistry.register(id, priority, provider);
    }

    public static Catalog catalog(MinecraftServer server, UUID playerId) {
        return StardewCommunityCenterVariantRegistry.catalog(server, playerId);
    }

    @FunctionalInterface
    public interface Provider {
        /**
         * Returns a replacement/transformed catalog or {@code null} to pass.
         *
         * <p>Providers receive the validated output of earlier providers.
         */
        Catalog apply(Context context, Catalog current);
    }

    public record Context(MinecraftServer server, UUID playerId) {
        public Context {
            server = Objects.requireNonNull(server, "server");
            playerId = Objects.requireNonNull(playerId, "playerId");
        }
    }

    public record Catalog(
            List<ResourceLocation> variantIds,
            List<StardewBundleDefinition> definitions
    ) {
        public Catalog {
            variantIds = List.copyOf(variantIds);
            definitions = List.copyOf(definitions);
        }

        public Catalog withVariant(
                ResourceLocation variantId,
                List<StardewBundleDefinition> replacementDefinitions
        ) {
            ArrayList<ResourceLocation> variants = new ArrayList<>(variantIds);
            variants.add(Objects.requireNonNull(variantId, "variantId"));
            return new Catalog(variants, replacementDefinitions);
        }
    }
}
