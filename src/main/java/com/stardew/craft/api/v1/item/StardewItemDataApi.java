package com.stardew.craft.api.v1.item;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.data.StardewDataMaps;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Stable entry point for resolving Stardew metadata on arbitrary items. */
public final class StardewItemDataApi {
    private static final Map<ResourceLocation, RegisteredProvider> PROVIDERS = new HashMap<>();
    private static volatile List<RegisteredProvider> providerSnapshot = List.of();

    private StardewItemDataApi() {
    }

    /**
     * Registers a stack-sensitive metadata provider.
     *
     * <p>Higher priorities run first. Equal priorities are ordered by provider
     * ID so resolution does not depend on mod loading order.
     *
     * @throws IllegalStateException when the provider ID is already registered
     */
    public static synchronized void registerProvider(
            ResourceLocation id,
            int priority,
            StardewItemDataProvider provider
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.containsKey(id)) {
            throw new IllegalStateException("Stardew item data provider already registered: " + id);
        }
        PROVIDERS.put(id, new RegisteredProvider(id, priority, provider));
        ArrayList<RegisteredProvider> sorted = new ArrayList<>(PROVIDERS.values());
        sorted.sort(Comparator.comparingInt(RegisteredProvider::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        providerSnapshot = List.copyOf(sorted);
    }

    public static void registerProvider(ResourceLocation id, StardewItemDataProvider provider) {
        registerProvider(id, 0, provider);
    }

    /** Resolves provider data first, then Data Map data, then the legacy adapter. */
    public static Optional<StardewItemData> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<StardewItemData> external = resolveExternal(stack);
        if (external.isPresent()) {
            return external;
        }
        if (stack.getItem() instanceof IStardewItem legacy) {
            String typeKey = legacy.getItemTypeKey();
            return Optional.of(new StardewItemData(
                    legacyCategory(typeKey),
                    legacy.getBaseSellPrice(stack),
                    legacy.getEdibility(stack),
                    legacy.getEnergy(stack),
                    legacy.getHealth(stack),
                    "stardewcraft.type.hidden".equals(typeKey)
            ));
        }
        return Optional.empty();
    }

    /** Returns the current stack sell price, including Stardew quality where applicable. */
    public static int getSellPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        Optional<StardewItemData> external = resolveExternal(stack);
        if (external.isPresent()) {
            int basePrice = external.get().baseSellPrice();
            if (basePrice < 0) {
                return -1;
            }
            return Math.round(basePrice * QualityHelper.getPriceMultiplier(QualityHelper.getQuality(stack)));
        }
        if (stack.getItem() instanceof IStardewItem legacy) {
            return legacy.getSellPrice(stack);
        }
        return -1;
    }

    /**
     * Transitional type key used by legacy gameplay consumers.
     *
     * <p>Built-in categories retain their historical translation-key form
     * ({@code stardewcraft.type.crop}); addon categories retain their full
     * namespaced ID.
     */
    public static String getTypeKey(ItemStack stack) {
        return resolve(stack).map(StardewItemData::category).map(category -> {
            if (StardewCraft.MODID.equals(category.getNamespace())) {
                return "stardewcraft.type." + category.getPath();
            }
            return category.toString();
        }).orElse("");
    }

    public static boolean isCategory(ItemStack stack, ResourceLocation category) {
        Objects.requireNonNull(category, "category");
        return resolve(stack).map(StardewItemData::category).filter(category::equals).isPresent();
    }

    private static Optional<StardewItemData> resolveExternal(ItemStack stack) {
        Optional<StardewItemData> provided = resolveProvider(stack);
        if (provided.isPresent()) {
            return provided;
        }
        return Optional.ofNullable(dataMapValue(stack));
    }

    private static Optional<StardewItemData> resolveProvider(ItemStack stack) {
        for (RegisteredProvider entry : providerSnapshot) {
            try {
                Optional<StardewItemData> resolved = entry.provider().resolve(stack);
                if (resolved != null && resolved.isPresent()) {
                    return resolved;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("Stardew item data provider {} failed", entry.id(), exception);
            }
        }
        return Optional.empty();
    }

    private static StardewItemData dataMapValue(ItemStack stack) {
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(StardewDataMaps.ITEM_DATA);
    }

    private static ResourceLocation legacyCategory(String typeKey) {
        if (typeKey == null || typeKey.isBlank()) {
            return StardewItemData.UNKNOWN_CATEGORY;
        }
        String prefix = "stardewcraft.type.";
        if (typeKey.startsWith(prefix) && typeKey.length() > prefix.length()) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, typeKey.substring(prefix.length()));
        }
        ResourceLocation parsed = ResourceLocation.tryParse(typeKey);
        return parsed != null ? parsed : StardewItemData.UNKNOWN_CATEGORY;
    }

    private record RegisteredProvider(
            ResourceLocation id,
            int priority,
            StardewItemDataProvider provider
    ) {
    }
}
