package com.stardew.craft.item.crop.summer;

import com.stardew.craft.port.PortItemData;
    import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 蓝莓
 */
public class BlueberryItem extends Item implements IStardewItem {

    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{50, 62, 75, 100};
    private static final int[] ENERGY_BY_QUALITY = new int[]{25, 35, 45, 65};
    private static final int[] HEALTH_BY_QUALITY = new int[]{11, 15, 20, 29};


    @SuppressWarnings("null")
    public BlueberryItem(Item.Properties properties) {
        super(properties
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationMod(0.3f)
                        .alwaysEat()
                        .build())
        );
    }


    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        int quality = QualityHelper.getQuality(stack);
        Component prefix = QualityHelper.getQualityPrefix(quality);
        @SuppressWarnings("null")
        Component baseName = Component.translatable(this.getDescriptionId(stack))
                .withStyle(ChatFormatting.WHITE);

        @SuppressWarnings("null")
        var customData = PortItemData.getOrDefault(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT);
        if (quality != QualityHelper.NORMAL && customData.equals(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT)) {
            PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        }

        return Component.empty().append(prefix).append(baseName);
    }

    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") net.minecraft.world.level.Level level, @SuppressWarnings("null") net.minecraft.world.entity.LivingEntity livingEntity) {
        int quality = QualityHelper.getQuality(stack);
        int health = getHealthRestoration(quality);
        int energy = getEnergyRestoration(quality);

        @SuppressWarnings("null")
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);

        if (!level.isClientSide && livingEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (health > 0) {
                int currentSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int maxSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, Math.min(maxSDHealth, currentSDHealth + health));
            }

            if (energy > 0) {
                com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
            }
        }

        return result;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.crop";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return getSellPrice(QualityHelper.getQuality(stack));
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return getHealthRestoration(QualityHelper.getQuality(stack));
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return getEnergyRestoration(QualityHelper.getQuality(stack));
    }

    public static int getHealthRestoration(int quality) {
        return HEALTH_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }

    public static int getEnergyRestoration(int quality) {
        return ENERGY_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }

    public static int getSellPrice(int quality) {
        return SELL_PRICE_BY_QUALITY[Math.max(0, Math.min(3, quality))];
    }
}
