package com.stardew.craft.item.artisan;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.quality.QualityHelper;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SmokedFishItem extends Item implements IStardewItem {
    private final Supplier<Item> sourceItem;

    @SuppressWarnings("null")
    public SmokedFishItem(Supplier<Item> sourceItem, Properties properties) {
        super(properties.food(createFoodProperties()));
        this.sourceItem = sourceItem;
    }

    @SuppressWarnings("null")
    private static FoodProperties createFoodProperties() {
        return new FoodProperties.Builder()
            .nutrition(2)
            .saturationModifier(0.3f)
            .alwaysEdible()
            .build();
    }

    public Item getSourceItem() {
        return sourceItem.get();
    }

    @SuppressWarnings("null")
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final com.stardew.craft.client.render.SmokedFishItemRenderer renderer = new com.stardew.craft.client.render.SmokedFishItemRenderer();

            @Override
            public com.stardew.craft.client.render.SmokedFishItemRenderer getCustomRenderer() {
                return renderer;
            }
        });
    }

    @Override
    public String getItemTypeKey() {
        // SDV: 熏鱼 Object.Category = -26 (ArtisanGoods)，享受 Artisan 1.4×。
        // 此外 SDV Object.getPriceAfterMultipliers 对 PreserveType.SmokedFish 额外
        // 叠加 Fisher 1.25× / Angler 1.5×（见下游 ProfessionSellPriceService 特判）。
        return "stardewcraft.type.artisan_goods";
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component prefix = QualityHelper.getQualityPrefix(quality);
        Item source = getSourceItem();
        ItemStack sourceStack = new ItemStack(source);
        Component sourceName = sourceStack.getHoverName();
        Component baseName = Component.translatable("stardewcraft.preserve.smoked_fish.flavored_name", sourceName)
                .withStyle(ChatFormatting.WHITE);

        @SuppressWarnings("null")
        var customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        }
        return Component.empty().append(prefix).append(baseName);
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Item source = getSourceItem();
        ItemStack sourceStack = new ItemStack(source);
        QualityHelper.setQuality(sourceStack, quality);
        int basePrice = StardewItemDataApi.getSellPrice(sourceStack);
        if (basePrice < 0) {
            return basePrice;
        }
        return basePrice * 2;
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        int edibility = getSmokedEdibility(stack);
        return energyFromEdibility(edibility);
    }

    @Override
    public int getHealth(ItemStack stack) {
        int edibility = getSmokedEdibility(stack);
        if (edibility <= -300) {
            return 0;
        }
        int energy = energyFromEdibility(edibility);
        return (int) (energy * 0.45f);
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack,
                                     @SuppressWarnings("null") Level level,
                                     @SuppressWarnings("null") LivingEntity livingEntity) {
        int health = getHealth(stack);
        int energy = getEnergy(stack);

        @SuppressWarnings("null")
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            if (health != 0) {
                int current = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int max = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, Math.max(0, Math.min(max, current + health)));
            }
            if (energy != 0) {
                if (energy > 0) {
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
                } else {
                    com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, -energy);
                }
            }
        }
        return result;
    }

    private int getSmokedEdibility(ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        int baseEdibility = getBaseEdibility(quality);
        int smokedPrice = getSellPrice(stack);
        if (baseEdibility <= -300) {
            return (int) Math.floor(smokedPrice * 0.3f);
        }
        return (int) Math.round(baseEdibility * 1.5f);
    }

    private int getBaseEdibility(int quality) {
        Item source = getSourceItem();
        ItemStack sourceStack = new ItemStack(source);
        QualityHelper.setQuality(sourceStack, quality);
        return StardewItemDataApi.resolve(sourceStack)
                .filter(data -> data.isFood())
                .map(data -> data.edibility())
                .orElse(-300);
    }

    private static int energyFromEdibility(int edibility) {
        if (edibility <= -300) {
            return -300;
        }
        return (int) (edibility * 2.5f);
    }
}
