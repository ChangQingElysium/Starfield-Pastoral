package com.stardew.craft.item.artisan;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public class PreservesItem extends Item implements IStardewItem {
    private static final String TAG_PRESERVE_TYPE = "PreserveType";
    private static final String TAG_SOURCE_ID = "PreserveSourceId";
    private static final String TAG_PRICE = "PreservePrice";
    private static final String TAG_EDIBILITY = "PreserveEdibility";
    private static final String TAG_COLOR = "PreserveColor";
    private static final String TAG_QUALITY = "PreserveQuality";

    private static final int BASE_ROE_PRICE = 30;
    private static final int BASE_ROE_EDIBILITY = 20;
    private static final int BASE_AGED_ROE_EDIBILITY = 40;
    private static final int BASE_CAVIAR_PRICE = 500;
    private static final int BASE_CAVIAR_EDIBILITY = 70;

    private static final int STURGEON_ROE_COLOR = 0x3D372A;

    private final PreserveType preserveType;

    @SuppressWarnings("null")
    public PreservesItem(PreserveType preserveType, Properties properties) {
        super(properties.food(new FoodProperties.Builder()
                .nutrition(2)
                .saturationModifier(0.3f)
                .alwaysEdible()
                .build()));
        this.preserveType = preserveType;
    }

    public PreserveType getPreserveType() {
        return preserveType;
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);

        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            int energy = getEnergy(stack);
            int health = getHealth(stack);

            // 处理生命恢复
            if (health != 0) {
                int currentSDHealth = PlayerStardewDataAPI.getHealth(serverPlayer);
                int maxSDHealth = PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                int newHealth = Math.max(0, Math.min(maxSDHealth, currentSDHealth + health));
                PlayerStardewDataAPI.setHealth(serverPlayer, newHealth);
            }

            // 处理能量恢复
            if (energy != 0) {
                if (energy > 0) {
                    PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
                } else {
                    PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
                }
            }
        }

        return result;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.artisan_goods";
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        if (preserveType == PreserveType.CAVIAR) {
            return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);
        }

        ResourceLocation sourceId = getSourceItemId(stack);
        if (sourceId == null) {
            return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);
        }

        Item sourceItem = BuiltInRegistries.ITEM.get(sourceId);
        ItemStack sourceStack = new ItemStack(sourceItem);
        Component sourceName = sourceStack.getHoverName();

        return switch (preserveType) {
            case JELLY -> Component.translatable("stardewcraft.preserve.jelly.flavored_name", sourceName)
                    .withStyle(ChatFormatting.WHITE);
            case PICKLES -> Component.translatable("stardewcraft.preserve.pickles.flavored_name", sourceName)
                    .withStyle(ChatFormatting.WHITE);
            case ROE -> Component.translatable("stardewcraft.preserve.roe.flavored_name", sourceName)
                    .withStyle(ChatFormatting.WHITE);
            case AGED_ROE -> Component.translatable("stardewcraft.preserve.aged_roe.flavored_name", sourceName)
                    .withStyle(ChatFormatting.WHITE);
            case DRIED_FRUIT -> Component.translatable("stardewcraft.preserve.dried_fruit.flavored_name", sourceName)
                .withStyle(ChatFormatting.WHITE);
            case DRIED_MUSHROOMS -> Component.translatable("stardewcraft.preserve.dried_mushrooms.flavored_name", sourceName)
                .withStyle(ChatFormatting.WHITE);
            case CAVIAR -> Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);
        };
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int price = getIntTag(stack, TAG_PRICE, getBasePrice());
        if (preserveType == PreserveType.DRIED_FRUIT || preserveType == PreserveType.DRIED_MUSHROOMS) {
            int quality = getIntTag(stack, TAG_QUALITY, QualityHelper.NORMAL);
            float multiplier = QualityHelper.getPriceMultiplier(quality);
            price = (int) Math.floor(price * multiplier);
        }
        return price;
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        int edibility = getIntTag(stack, TAG_EDIBILITY, getBaseEdibility());
        return energyFromEdibility(edibility);
    }

    @Override
    public int getHealth(ItemStack stack) {
        int edibility = getIntTag(stack, TAG_EDIBILITY, getBaseEdibility());
        if (edibility <= -300) {
            return 0;
        }
        int energy = energyFromEdibility(edibility);
        return (int) (energy * 0.45f);
    }

    public int getColor(ItemStack stack) {
        int resolved = resolveColorFromSource(stack);
        if (resolved >= 0) {
            return resolved;
        }
        return getIntTag(stack, TAG_COLOR, -1);
    }

    @SuppressWarnings("null")
    private int resolveColorFromSource(ItemStack stack) {
        if (preserveType == PreserveType.CAVIAR) {
            return -1;
        }
        ResourceLocation sourceId = getSourceItemId(stack);
        if (sourceId == null) {
            return -1;
        }
        if ((preserveType == PreserveType.ROE || preserveType == PreserveType.AGED_ROE)
                && "sturgeon".equals(sourceId.getPath())) {
            return STURGEON_ROE_COLOR;
        }
        return PreservesIngredientDataManager.getData(sourceId)
                .map(PreservesIngredientDataManager.IngredientData::getColorRgb)
                .filter(color -> color >= 0)
                .orElse(-1);
    }

    private int getBasePrice() {
        return switch (preserveType) {
            case JELLY -> 160;
            case PICKLES -> 100;
            case ROE -> BASE_ROE_PRICE;
            case AGED_ROE -> 100;
            case CAVIAR -> BASE_CAVIAR_PRICE;
            case DRIED_FRUIT, DRIED_MUSHROOMS -> 25;
        };
    }

    private int getBaseEdibility() {
        return switch (preserveType) {
            case JELLY -> -300;
            case PICKLES -> -300;
            case ROE -> BASE_ROE_EDIBILITY;
            case AGED_ROE -> BASE_AGED_ROE_EDIBILITY;
            case CAVIAR -> BASE_CAVIAR_EDIBILITY;
            case DRIED_FRUIT, DRIED_MUSHROOMS -> -300;
        };
    }

    @SuppressWarnings("null")
    public static ItemStack createFlavored(PreserveType type, ItemStack ingredient, ItemStack resultStack) {
        if (ingredient == null || ingredient.isEmpty()) {
            return resultStack;
        }

        PreservesIngredientDataManager.IngredientData data = PreservesIngredientDataManager
                .getData(ingredient).orElse(null);
        int ingredientPrice = getIngredientPrice(ingredient, data);
        int ingredientEdibility = getIngredientEdibility(ingredient, data);
        int ingredientColor = getIngredientColor(ingredient, data, type);
        ResourceLocation sourceId = getSourceIdFromIngredient(ingredient, type);

        int price;
        int edibility;

        switch (type) {
            case JELLY -> {
                price = ingredientPrice * 2 + 50;
                edibility = computePreserveEdibility(ingredientEdibility, ingredientPrice, 2.0f, 0.2f);
            }
            case PICKLES -> {
                price = ingredientPrice * 2 + 50;
                edibility = computePreserveEdibility(ingredientEdibility, ingredientPrice, 1.75f, 0.25f);
            }
            case ROE -> {
                price = BASE_ROE_PRICE + ingredientPrice / 2;
                edibility = BASE_ROE_EDIBILITY;
            }
            case AGED_ROE -> {
                int roePrice = BASE_ROE_PRICE + ingredientPrice / 2;
                price = roePrice * 2;
                edibility = BASE_AGED_ROE_EDIBILITY;
            }
            case CAVIAR -> {
                price = BASE_CAVIAR_PRICE;
                edibility = BASE_CAVIAR_EDIBILITY;
            }
            case DRIED_FRUIT -> {
                price = (int) ((ingredientPrice * 5) * 1.5f) + 25;
                edibility = computePreserveEdibility(ingredientEdibility, ingredientPrice, 3.0f, 0.5f);
            }
            case DRIED_MUSHROOMS -> {
                price = (int) ((ingredientPrice * 5) * 1.5f) + 25;
                edibility = ingredientEdibility * 3;
            }
            default -> {
                price = ingredientPrice;
                edibility = ingredientEdibility;
            }
        }

        CompoundTag tag = getOrCreateTag(resultStack);
        tag.putString(TAG_PRESERVE_TYPE, type.name());
        if (sourceId != null) {
            tag.putString(TAG_SOURCE_ID, sourceId.toString());
        }
        tag.putInt(TAG_PRICE, price);
        tag.putInt(TAG_EDIBILITY, edibility);
        if (type != PreserveType.CAVIAR) {
            tag.putInt(TAG_COLOR, ingredientColor);
        }
        if (type == PreserveType.DRIED_FRUIT || type == PreserveType.DRIED_MUSHROOMS) {
            tag.putInt(TAG_QUALITY, QualityHelper.getQuality(ingredient));
        }
        applyTag(resultStack, tag);

        return resultStack;
    }

    private static int computePreserveEdibility(int ingredientEdibility, int ingredientPrice, float multiplier, float priceFallbackMultiplier) {
        if (ingredientEdibility > 0) {
            return (int) (ingredientEdibility * multiplier);
        }
        if (ingredientEdibility == -300) {
            return (int) (ingredientPrice * priceFallbackMultiplier);
        }
        return ingredientEdibility;
    }

    private static int getIngredientPrice(ItemStack ingredient, PreservesIngredientDataManager.IngredientData data) {
        if (data != null) {
            return data.price;
        }
        int price = StardewItemDataApi.getSellPrice(ingredient);
        if (price >= 0) return price;
        StardewCraft.LOGGER.warn("Missing preserves ingredient price data for {}", ingredient.getItem());
        return 0;
    }

    private static int getIngredientEdibility(ItemStack ingredient, PreservesIngredientDataManager.IngredientData data) {
        if (data != null) {
            return data.edibility;
        }
        var metadata = StardewItemDataApi.resolve(ingredient).orElse(null);
        if (metadata != null) return metadata.edibility();
        StardewCraft.LOGGER.warn("Missing preserves ingredient edibility data for {}", ingredient.getItem());
        return -300;
    }

    @SuppressWarnings("null")
    private static int getIngredientColor(ItemStack ingredient, PreservesIngredientDataManager.IngredientData data, PreserveType type) {
        if (type == PreserveType.ROE || type == PreserveType.AGED_ROE) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
            if (id != null && "sturgeon".equals(id.getPath())) {
                return STURGEON_ROE_COLOR;
            }
        }

        int color = -1;
        if (data != null) {
            color = data.getColorRgb();
        }
        if (color < 0) {
            color = switch (type) {
                case JELLY -> 0xFF0000;
                case PICKLES -> 0x00FF00;
                case ROE, AGED_ROE -> 0xFF8000;
                default -> 0xFFFFFF;
            };
        }
        return color & 0xFFFFFF;
    }

    @SuppressWarnings("null")
    private static ResourceLocation getSourceIdFromIngredient(ItemStack ingredient, PreserveType type) {
        if (type == PreserveType.AGED_ROE && ingredient.getItem() instanceof PreservesItem preserveItem) {
            PreserveType ingredientType = preserveItem.getPreserveType();
            if (ingredientType == PreserveType.ROE) {
                ResourceLocation source = PreservesItem.getSourceItemId(ingredient);
                if (source != null) {
                    return source;
                }
            }
        }
        return BuiltInRegistries.ITEM.getKey(ingredient.getItem());
    }

    public static ResourceLocation getSourceItemId(ItemStack stack) {
        String raw = getStringTag(stack, TAG_SOURCE_ID, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    public static String getSubtypeKey(ItemStack stack) {
        if (!(stack.getItem() instanceof PreservesItem preservesItem)) {
            return "";
        }
        ResourceLocation sourceId = getSourceItemId(stack);
        String sourceKey = sourceId == null ? "" : sourceId.toString();
        if (preservesItem.getPreserveType() == PreserveType.DRIED_FRUIT
                || preservesItem.getPreserveType() == PreserveType.DRIED_MUSHROOMS) {
            int quality = getIntTag(stack, TAG_QUALITY, QualityHelper.NORMAL);
            return preservesItem.getPreserveType().name() + ":" + sourceKey + ":q=" + quality;
        }
        return preservesItem.getPreserveType().name() + ":" + sourceKey;
    }

    private static int energyFromEdibility(int edibility) {
        if (edibility <= -300) {
            return -300;
        }
        return (int) (edibility * 2.5f);
    }

    @SuppressWarnings("null")
    private static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    @SuppressWarnings("null")
    private static void applyTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @SuppressWarnings("null")
    private static int getIntTag(ItemStack stack, String key, int fallback) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(key)) {
            return fallback;
        }
        return tag.getInt(key);
    }

    @SuppressWarnings("null")
    private static String getStringTag(ItemStack stack, String key, String fallback) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(key)) {
            return fallback;
        }
        return tag.getString(key);
    }

    public static Optional<PreserveType> getTypeFromStack(ItemStack stack) {
        if (!(stack.getItem() instanceof PreservesItem item)) {
            return Optional.empty();
        }
        return Optional.of(item.getPreserveType());
    }
}
