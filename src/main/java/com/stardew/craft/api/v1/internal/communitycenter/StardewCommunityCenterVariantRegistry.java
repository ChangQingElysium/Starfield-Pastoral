package com.stardew.craft.api.v1.internal.communitycenter;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.communitycenter.StardewBundleDefinition;
import com.stardew.craft.api.v1.communitycenter.StardewBundleIngredient;
import com.stardew.craft.api.v1.communitycenter.StardewCommunityCenterVariants;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.data.BundleIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Internal conversion and dispatch bridge for player-specific bundle catalogs. */
public final class StardewCommunityCenterVariantRegistry {
    private static final Map<ResourceLocation, Registered> PROVIDERS = new HashMap<>();
    private static volatile List<Registered> snapshot = List.of();

    private StardewCommunityCenterVariantRegistry() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            StardewCommunityCenterVariants.Provider provider
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.containsKey(id)) {
            throw new IllegalStateException(
                    "Community Center variant provider already registered: " + id);
        }
        PROVIDERS.put(id, new Registered(id, priority, provider));
        ArrayList<Registered> ordered = new ArrayList<>(PROVIDERS.values());
        ordered.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        snapshot = List.copyOf(ordered);
    }

    public static StardewCommunityCenterVariants.Catalog catalog(
            MinecraftServer server,
            UUID playerId
    ) {
        StardewCommunityCenterVariants.Catalog current =
                new StardewCommunityCenterVariants.Catalog(
                        List.of(), toPublic(BundleDataManager.getAllBundles()));
        StardewCommunityCenterVariants.Context context =
                new StardewCommunityCenterVariants.Context(server, playerId);
        for (Registered registered : snapshot) {
            try {
                StardewCommunityCenterVariants.Catalog candidate =
                        registered.provider().apply(context, current);
                if (candidate != null) {
                    validate(candidate);
                    current = candidate;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Community Center variant provider {} failed for {}",
                        registered.id(), playerId, exception);
            }
        }
        return current;
    }

    public static Collection<BundleDefinition> all(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null
                ? BundleDataManager.getAllBundles()
                : toInternal(catalog(server, playerId).definitions());
    }

    public static List<BundleDefinition> area(UUID playerId, int areaId) {
        return all(playerId).stream()
                .filter(definition -> definition.areaId() == areaId)
                .toList();
    }

    @Nullable
    public static BundleDefinition bundle(UUID playerId, int bundleId) {
        for (BundleDefinition definition : all(playerId)) {
            if (definition.bundleId() == bundleId) {
                return definition;
            }
        }
        return null;
    }

    public static List<StardewBundleDefinition> toPublic(
            Collection<BundleDefinition> definitions
    ) {
        return definitions.stream()
                .map(definition -> new StardewBundleDefinition(
                        definition.bundleId(),
                        definition.areaId(),
                        definition.internalName(),
                        definition.displayNameKey(),
                        definition.rewardString(),
                        definition.ingredients().stream()
                                .map(ingredient -> new StardewBundleIngredient(
                                        ingredient.itemId(),
                                        ingredient.sdvId(),
                                        ingredient.category(),
                                        ingredient.stack(),
                                        ingredient.quality()))
                                .toList(),
                        definition.color(),
                        definition.requiredCount()))
                .sorted(Comparator.comparingInt(StardewBundleDefinition::bundleId))
                .toList();
    }

    public static List<BundleDefinition> toInternal(
            Collection<StardewBundleDefinition> definitions
    ) {
        return definitions.stream()
                .map(definition -> new BundleDefinition(
                        definition.bundleId(),
                        definition.areaId(),
                        definition.internalName(),
                        definition.displayNameKey(),
                        definition.rewardDescriptor(),
                        definition.ingredients().stream()
                                .map(ingredient -> new BundleIngredient(
                                        ingredient.itemId(),
                                        ingredient.legacyItemId(),
                                        ingredient.category(),
                                        ingredient.count(),
                                        ingredient.minimumQuality()))
                                .toList(),
                        definition.color(),
                        definition.requiredCount()))
                .toList();
    }

    private static void validate(StardewCommunityCenterVariants.Catalog catalog) {
        LinkedHashMap<Integer, StardewBundleDefinition> unique = new LinkedHashMap<>();
        for (StardewBundleDefinition definition : catalog.definitions()) {
            if (unique.put(definition.bundleId(), definition) != null) {
                throw new IllegalArgumentException(
                        "Duplicate bundle ID in variant catalog: " + definition.bundleId());
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("Variant catalog cannot be empty");
        }
    }

    private record Registered(
            ResourceLocation id,
            int priority,
            StardewCommunityCenterVariants.Provider provider
    ) {
    }
}
