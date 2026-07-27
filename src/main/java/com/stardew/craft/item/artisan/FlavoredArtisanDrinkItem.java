package com.stardew.craft.item.artisan;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/**
 * A single source-aware item implementation for Stardew's dynamically flavored
 * Wine and Juice objects.
 */
public final class FlavoredArtisanDrinkItem extends ArtisanDrinkItem {
    private static final String TAG_TYPE = "FlavorType";
    private static final String TAG_SOURCE_ID = "FlavorSourceId";
    private static final String TAG_PRICE = "FlavorPrice";
    private static final String TAG_EDIBILITY = "FlavorEdibility";
    private static final String TAG_COLOR = "FlavorColor";

    private static final int DEFAULT_WINE_PRICE = 400;
    private static final int DEFAULT_WINE_ENERGY = 50;
    private static final int DEFAULT_WINE_HEALTH = 22;
    private static final int DEFAULT_JUICE_PRICE = 150;
    private static final int DEFAULT_JUICE_ENERGY = 75;
    private static final int DEFAULT_JUICE_HEALTH = 33;

    private final PreserveType flavorType;
    @Nullable
    private final ResourceLocation legacySourceId;

    public FlavoredArtisanDrinkItem(PreserveType flavorType, Item.Properties properties) {
        this(flavorType, null, properties);
    }

    /**
     * Compatibility constructor for a retired source-specific registry ID.
     * New outputs must use the source-aware {@code wine}/{@code juice} stacks.
     */
    public FlavoredArtisanDrinkItem(
            PreserveType flavorType,
            @Nullable ResourceLocation legacySourceId,
            Item.Properties properties
    ) {
        super(defaultPrice(flavorType), defaultEnergy(flavorType), defaultHealth(flavorType),
                flavorType == PreserveType.WINE ? -1 : 0,
                flavorType == PreserveType.WINE ? 30 * 20 : 0,
                flavorType == PreserveType.WINE,
                properties);
        if (flavorType != PreserveType.WINE && flavorType != PreserveType.JUICE) {
            throw new IllegalArgumentException("Flavored drink type must be WINE or JUICE");
        }
        this.flavorType = flavorType;
        this.legacySourceId = legacySourceId;
    }

    public PreserveType getFlavorType() {
        return flavorType;
    }

    public boolean isLegacyCompatibilityItem() {
        return legacySourceId != null;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int basePrice = getIntTag(stack, TAG_PRICE, fallbackValues().price());
        if (flavorType != PreserveType.WINE) {
            return basePrice;
        }
        return (int) Math.floor(basePrice
                * QualityHelper.getPriceMultiplier(QualityHelper.getQuality(stack)));
    }

    @Override
    public int getBaseSellPrice(ItemStack stack) {
        return getIntTag(stack, TAG_PRICE, fallbackValues().price());
    }

    @Override
    public int getEnergy(ItemStack stack) {
        int edibility = getEdibility(stack);
        int quality = flavorType == PreserveType.WINE
                ? stardewQualityValue(QualityHelper.getQuality(stack))
                : 0;
        return (int) Math.ceil(edibility * 2.5D) + quality * edibility;
    }

    @Override
    public int getHealth(ItemStack stack) {
        if (getEdibility(stack) < 0) {
            return 0;
        }
        return (int) Math.floor(getEnergy(stack) * 0.45D);
    }

    @Override
    public int getEdibility(ItemStack stack) {
        return getIntTag(stack, TAG_EDIBILITY, fallbackValues().edibility());
    }

    public int getColor(ItemStack stack) {
        ResourceLocation sourceId = getSourceItemId(stack);
        if (sourceId != null) {
            int currentColor = PreservesIngredientDataManager.getData(sourceId)
                    .map(PreservesIngredientDataManager.IngredientData::getColorRgb)
                    .orElse(-1);
            if (currentColor >= 0) {
                return currentColor;
            }
        }
        return getIntTag(stack, TAG_COLOR, defaultColor(flavorType));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation sourceId = getSourceItemId(stack);
        if (sourceId == null || !BuiltInRegistries.ITEM.containsKey(sourceId)) {
            return super.getName(stack);
        }

        Component sourceName = new ItemStack(BuiltInRegistries.ITEM.get(sourceId)).getHoverName();
        String translationKey = flavorType == PreserveType.WINE
                ? "stardewcraft.preserve.wine.flavored_name"
                : "stardewcraft.preserve.juice.flavored_name";
        Component baseName = Component.translatable(translationKey, sourceName)
                .withStyle(ChatFormatting.WHITE);

        if (flavorType != PreserveType.WINE) {
            return baseName;
        }
        int quality = QualityHelper.getQuality(stack);
        QualityHelper.ensureQualityModelData(stack);
        return quality == QualityHelper.NORMAL
                ? baseName
                : Component.empty().append(QualityHelper.getQualityPrefix(quality)).append(baseName);
    }

    public static ItemStack createFlavored(
            PreserveType type,
            ItemStack ingredient,
            ItemStack resultStack
    ) {
        if (ingredient == null || ingredient.isEmpty() || resultStack.isEmpty()) {
            return resultStack;
        }
        if (!(resultStack.getItem() instanceof FlavoredArtisanDrinkItem drink)
                || drink.flavorType != type) {
            StardewCraft.LOGGER.warn("Cannot apply {} flavor data to {}", type, resultStack.getItem());
            return resultStack;
        }

        ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        PreservesIngredientDataManager.IngredientData ingredientData =
                PreservesIngredientDataManager.getData(ingredient).orElse(null);
        int ingredientPrice = ingredientData != null
                ? ingredientData.price
                : StardewItemDataApi.resolve(ingredient)
                        .map(data -> data.baseSellPrice())
                        .filter(price -> price >= 0)
                        .orElse(0);
        int ingredientEdibility = ingredientData != null
                ? ingredientData.edibility
                : StardewItemDataApi.resolve(ingredient)
                        .map(data -> data.edibility())
                        .orElse(-300);
        FlavorValues values = computeFlavorValues(type, ingredientPrice, ingredientEdibility);
        int color = ingredientData == null ? -1 : ingredientData.getColorRgb();
        if (color < 0) {
            color = defaultColor(type);
        }

        CompoundTag tag = getOrCreateTag(resultStack);
        tag.putString(TAG_TYPE, type.name());
        tag.putString(TAG_SOURCE_ID, sourceId.toString());
        tag.putInt(TAG_PRICE, values.price());
        tag.putInt(TAG_EDIBILITY, values.edibility());
        tag.putInt(TAG_COLOR, color);
        resultStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        QualityHelper.setQuality(resultStack, QualityHelper.NORMAL);
        return resultStack;
    }

    public static ResourceLocation getSourceItemId(ItemStack stack) {
        String raw = getStringTag(stack, TAG_SOURCE_ID);
        ResourceLocation taggedSource = raw == null || raw.isBlank()
                ? null
                : ResourceLocation.tryParse(raw);
        if (taggedSource != null) {
            return taggedSource;
        }
        return stack.getItem() instanceof FlavoredArtisanDrinkItem drink
                ? drink.legacySourceId
                : null;
    }

    public static String getSubtypeKey(ItemStack stack) {
        if (!(stack.getItem() instanceof FlavoredArtisanDrinkItem drink)) {
            return "";
        }
        ResourceLocation sourceId = getSourceItemId(stack);
        return drink.flavorType.name() + ":" + (sourceId == null ? "" : sourceId);
    }

    private static int stardewQualityValue(int quality) {
        return switch (quality) {
            case QualityHelper.SILVER -> 1;
            case QualityHelper.GOLD -> 2;
            case QualityHelper.IRIDIUM -> 4;
            default -> 0;
        };
    }

    private static int defaultPrice(PreserveType type) {
        return type == PreserveType.WINE ? DEFAULT_WINE_PRICE : DEFAULT_JUICE_PRICE;
    }

    private static int defaultEnergy(PreserveType type) {
        return type == PreserveType.WINE ? DEFAULT_WINE_ENERGY : DEFAULT_JUICE_ENERGY;
    }

    private static int defaultHealth(PreserveType type) {
        return type == PreserveType.WINE ? DEFAULT_WINE_HEALTH : DEFAULT_JUICE_HEALTH;
    }

    private static int defaultEdibility(PreserveType type) {
        return type == PreserveType.WINE ? 20 : 30;
    }

    private static int defaultColor(PreserveType type) {
        return type == PreserveType.WINE ? 0x7329B5 : 0x0A8F00;
    }

    private FlavorValues fallbackValues() {
        if (legacySourceId == null) {
            return new FlavorValues(defaultPrice(flavorType), defaultEdibility(flavorType));
        }
        return PreservesIngredientDataManager.getData(legacySourceId)
                .map(data -> computeFlavorValues(flavorType, data.price, data.edibility))
                .orElseGet(() -> new FlavorValues(defaultPrice(flavorType), defaultEdibility(flavorType)));
    }

    private static FlavorValues computeFlavorValues(
            PreserveType type,
            int ingredientPrice,
            int ingredientEdibility
    ) {
        if (type == PreserveType.WINE) {
            int edibility = ingredientEdibility > 0
                    ? (int) (ingredientEdibility * 1.75F)
                    : ingredientEdibility == -300
                            ? (int) (ingredientPrice * 0.1F)
                            : ingredientEdibility;
            return new FlavorValues(ingredientPrice * 3, edibility);
        }

        int edibility = ingredientEdibility > 0
                ? ingredientEdibility * 2
                : ingredientEdibility == -300
                        ? (int) (ingredientPrice * 0.4F)
                        : ingredientEdibility;
        return new FlavorValues((int) Math.floor(ingredientPrice * 2.25D), edibility);
    }

    private record FlavorValues(int price, int edibility) {
    }

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static int getIntTag(ItemStack stack, String key, int fallback) {
        CompoundTag tag = getOrCreateTag(stack);
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }

    private static String getStringTag(ItemStack stack, String key) {
        CompoundTag tag = getOrCreateTag(stack);
        return tag.contains(key) ? tag.getString(key) : null;
    }
}
