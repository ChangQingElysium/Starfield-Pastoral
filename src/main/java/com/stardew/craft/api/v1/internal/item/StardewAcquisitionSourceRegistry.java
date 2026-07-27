package com.stardew.craft.api.v1.internal.item;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.item.StardewAcquisitionContext;
import com.stardew.craft.api.v1.item.StardewAcquisitionSource;
import com.stardew.craft.api.v1.item.StardewAcquisitionSourceProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered, failure-isolated acquisition-source composition. */
public final class StardewAcquisitionSourceRegistry {
    private static final OrderedExtensionRegistry<
            StardewAcquisitionSourceProvider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "item/acquisition_source"));

    static {
        registerCore("shops", CoreAcquisitionSourceProviders::shops);
        registerCore(
                "crafting", CoreAcquisitionSourceProviders::crafting);
        registerCore("cooking", CoreAcquisitionSourceProviders::cooking);
        registerCore("machines", CoreAcquisitionSourceProviders::machines);
        registerCore("crops", CoreAcquisitionSourceProviders::crops);
        registerCore(
                "progress_rewards",
                CoreAcquisitionSourceProviders::progressRewards);
    }

    private StardewAcquisitionSourceRegistry() {
    }

    /** Forces core provider and diagnostic registration during API bootstrap. */
    public static void bootstrap() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewAcquisitionSourceProvider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static List<StardewAcquisitionSource> find(
            ItemStack target,
            ServerPlayer player
    ) {
        if (target == null || target.isEmpty()) {
            return List.of();
        }
        ResourceLocation targetId =
                BuiltInRegistries.ITEM.getKey(target.getItem());
        StardewAcquisitionContext context =
                new StardewAcquisitionContext(
                        target, targetId, player);
        Map<SourceKey, StardewAcquisitionSource> combined =
                new LinkedHashMap<>();
        for (var registered : PROVIDERS.entries()) {
            List<StardewAcquisitionSource> sources;
            try {
                sources = PROVIDERS.invoke(
                        registered,
                        provider -> provider.findSources(context));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew acquisition provider {} failed for {}",
                        registered.id(), targetId, exception);
                continue;
            }
            if (sources == null) {
                continue;
            }
            for (StardewAcquisitionSource source : sources) {
                if (source == null
                        || !targetId.equals(source.itemId())) {
                    continue;
                }
                combined.putIfAbsent(
                        new SourceKey(source.kind(), source.sourceId()),
                        source);
            }
        }
        return List.copyOf(combined.values());
    }

    private static void registerCore(
            String path,
            StardewAcquisitionSourceProvider provider
    ) {
        PROVIDERS.register(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "core/" + path),
                -1000,
                provider);
    }

    private record SourceKey(
            StardewAcquisitionSource.Kind kind,
            ResourceLocation sourceId
    ) {
    }
}
