package com.stardew.craft.item.catalog;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.item.StardewQualityItem;
import com.stardew.craft.item.artisan.ArtisanDrinkItem;
import com.stardew.craft.item.artisan.DehydratorIngredientHelper;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesCropTypeHelper;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.artisan.SmokedFishItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;

public final class StardewItemDisplayStacks {
    private StardewItemDisplayStacks() {
    }

    public static List<ItemStack> stacksForItem(Item item) {
        if (isHiddenBaseItem(item)) {
            return List.of();
        }

        Integer colorCount = getFlowerColorVariantCount(item);
        if (colorCount != null && colorCount > 0) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int color = 0; color < colorCount; color++) {
                addQualitySet(stacks, item, color);
            }
            return stacks;
        }

        if (usesQualityDisplay(item)) {
            List<ItemStack> stacks = new ArrayList<>();
            addQualitySet(stacks, item, null);
            return stacks;
        }

        return List.of(new ItemStack(item));
    }

    public static List<ItemStack> preserveVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : StardewItemCatalog.visibleItems()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
            if (typeKey.isBlank()) continue;
            if (isPreserveCropIngredient(typeKey)) {
                PreserveType preserveType = PreservesCropTypeHelper.getCropPreserveType(id);
                if (preserveType != null) {
                    stacks.add(buildPreserveVariant(preserveType, item, getPreserveBaseItem(preserveType)));
                }
            } else if (isPreserveFishIngredient(typeKey)) {
                stacks.add(buildPreserveVariant(PreserveType.ROE, item, ModItems.ROE.get()));
                stacks.add(buildPreserveVariant(PreserveType.AGED_ROE, item, ModItems.AGED_ROE.get()));
                if (isSturgeon(id)) {
                    stacks.add(buildPreserveVariant(PreserveType.CAVIAR, item, ModItems.CAVIAR.get()));
                }
            }

            if (isDehydratorFruitIngredient(typeKey, id)) {
                stacks.add(buildPreserveVariant(PreserveType.DRIED_FRUIT, item, ModItems.DRIED_FRUIT.get()));
            } else if (isDehydratorMushroomIngredient(id)) {
                stacks.add(buildPreserveVariant(PreserveType.DRIED_MUSHROOMS, item, ModItems.DRIED_MUSHROOMS.get()));
            }
        }
        return stacks;
    }

    public static List<ItemStack> specificBaitVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        int variantIndex = 0;
        for (Item item : StardewItemCatalog.specificBaitTargetItems()) {
            ItemStack fishStack = new ItemStack(item);
            if (fishStack.isEmpty()) {
                continue;
            }
            ItemStack baitStack = SpecificBaitItem.createForFish(fishStack, 1);
            SpecificBaitItem.applyCreativeVariantMarker(baitStack, variantIndex);
            stacks.add(baitStack);
            variantIndex++;
        }
        return stacks;
    }

    public static List<ItemStack> junimoNoteAreaVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int area = 0; area <= 6; area++) {
            ItemStack stack = new ItemStack(ModItems.JUNIMO_NOTE.get());
            stack.set(DataComponents.BLOCK_STATE,
                    new net.minecraft.world.item.component.BlockItemStateProperties(
                            java.util.Map.of("area", String.valueOf(area))));
            stacks.add(stack);
        }
        return stacks;
    }

    public static boolean isHiddenBaseItem(Item item) {
        return item == ModItems.TARGETED_BAIT.get()
                || item == ModItems.JELLY.get()
                || item == ModItems.PICKLES.get()
                || item == ModItems.ROE.get()
                || item == ModItems.AGED_ROE.get()
                || item == ModItems.CAVIAR.get()
                || item == ModItems.DRIED_FRUIT.get()
                || item == ModItems.DRIED_MUSHROOMS.get()
                || item == ModItems.JUNIMO_NOTE.get();
    }

    public static boolean isSpecificBaitTargetType(String typeKey) {
        return "stardewcraft.type.fish".equals(typeKey)
                || "stardewcraft.type.crabpot".equals(typeKey)
                || "stardewcraft.type.legendary_fish".equals(typeKey);
    }

    public static Integer getFlowerColor(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var tag = customData.copyTag();
        if (tag.contains("FlowerColor")) {
            return tag.getInt("FlowerColor");
        }
        return null;
    }

    private static boolean usesQualityDisplay(Item item) {
        String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
        if (typeKey.isBlank()) {
            return false;
        }
        if (item instanceof StardewQualityItem qualityItem && qualityItem.supportsQuality()) {
            return true;
        }
        if (item instanceof ArtisanDrinkItem drink && drink.supportsQuality()) {
            return true;
        }
        if (item instanceof SmokedFishItem) {
            return true;
        }
        return "stardewcraft.type.crop".equals(typeKey)
                || "stardewcraft.type.crop_seed".equals(typeKey)
                || "stardewcraft.type.fruit".equals(typeKey)
                || "stardewcraft.type.forage".equals(typeKey)
                || "stardewcraft.type.fish".equals(typeKey)
                || "stardewcraft.type.crabpot".equals(typeKey)
                || "stardewcraft.type.legendary_fish".equals(typeKey)
                || "stardewcraft.type.animal_product".equals(typeKey)
                || "stardewcraft.type.artisan_animal_quality".equals(typeKey)
                || "stardewcraft.type.artifact_quality".equals(typeKey);
    }

    private static void addQualitySet(List<ItemStack> stacks, Item item, Integer flowerColor) {
        stacks.add(createVariant(item, QualityHelper.NORMAL, flowerColor));
        stacks.add(createVariant(item, QualityHelper.SILVER, flowerColor));
        stacks.add(createVariant(item, QualityHelper.GOLD, flowerColor));
        stacks.add(createVariant(item, QualityHelper.IRIDIUM, flowerColor));
    }

    private static ItemStack createVariant(Item item, int quality, Integer flowerColor) {
        ItemStack stack = new ItemStack(item);
        QualityHelper.setQuality(stack, quality);
        if (flowerColor != null) {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            var tag = customData.copyTag();
            tag.putInt("FlowerColor", Math.max(0, flowerColor));
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(100 + (quality * 10) + Math.max(0, flowerColor)));
        }
        return stack;
    }

    private static Integer getFlowerColorVariantCount(Item item) {
        if (item == ModItems.BLUE_JAZZ.get()) return 6;
        if (item == ModItems.POPPY.get()) return 3;
        if (item == ModItems.TULIP.get()) return 5;
        if (item == ModItems.SUMMER_SPANGLE.get()) return 6;
        if (item == ModItems.FAIRY_ROSE.get()) return 6;
        return null;
    }

    private static boolean isPreserveCropIngredient(String typeKey) {
        return "stardewcraft.type.crop".equals(typeKey);
    }

    private static boolean isPreserveFishIngredient(String typeKey) {
        return isSpecificBaitTargetType(typeKey);
    }

    private static boolean isDehydratorFruitIngredient(String typeKey, ResourceLocation id) {
        if (!"stardewcraft.type.crop".equals(typeKey)) {
            return false;
        }
        if (isGrape(id)) {
            return false;
        }
        return DehydratorIngredientHelper.isFruitCrop(id);
    }

    private static boolean isDehydratorMushroomIngredient(ResourceLocation id) {
        return DehydratorIngredientHelper.isMushroom(id);
    }

    private static boolean isSturgeon(ResourceLocation id) {
        return id != null && "sturgeon".equals(id.getPath());
    }

    private static boolean isGrape(ResourceLocation id) {
        return id != null && "grape".equals(id.getPath());
    }

    private static ItemStack buildPreserveVariant(PreserveType type, Item ingredient, Item baseItem) {
        ItemStack ingredientStack = new ItemStack(ingredient);
        ItemStack resultStack = new ItemStack(baseItem);
        PreservesItem.createFlavored(type, ingredientStack, resultStack);
        return resultStack;
    }

    private static Item getPreserveBaseItem(PreserveType type) {
        return switch (type) {
            case JELLY -> ModItems.JELLY.get();
            case PICKLES -> ModItems.PICKLES.get();
            case ROE -> ModItems.ROE.get();
            case AGED_ROE -> ModItems.AGED_ROE.get();
            case CAVIAR -> ModItems.CAVIAR.get();
            case DRIED_FRUIT -> ModItems.DRIED_FRUIT.get();
            case DRIED_MUSHROOMS -> ModItems.DRIED_MUSHROOMS.get();
        };
    }
}
