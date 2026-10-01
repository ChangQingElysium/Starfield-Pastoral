package com.stardew.craft.item.crop.spring;

    import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * 花椰菜
 */
public class CauliflowerItem extends Item implements IStardewItem {

    private static final int[] SELL_PRICE_BY_QUALITY = new int[]{175, 218, 262, 350};
    private static final int[] ENERGY_BY_QUALITY = new int[]{75, 105, 135, 195};
    private static final int[] HEALTH_BY_QUALITY = new int[]{33, 47, 60, 87};


    public CauliflowerItem(Item.Properties properties) {
        super(properties.food(Objects.requireNonNull(new FoodProperties.Builder()
            .nutrition(2)
            .saturationMod(0.3f)
            .alwaysEat()
            .build(), "food")));
    }


    @SuppressWarnings("null")
    @Override
        public @Nonnull Component getName(@Nonnull ItemStack stack) {
        ItemStack safeStack = Objects.requireNonNull(stack, "stack");
        int quality = QualityHelper.getQuality(safeStack);
        Component prefix = Objects.requireNonNull(QualityHelper.getQualityPrefix(quality), "prefix");
        String descId = Objects.requireNonNull(this.getDescriptionId(safeStack), "descId");
        Component baseName = Component.translatable(descId)
                .withStyle(ChatFormatting.WHITE);

        var customModelDataType = Objects.requireNonNull(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, "CUSTOM_MODEL_DATA");
        var defaultData = Objects.requireNonNull(com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData.DEFAULT, "DEFAULT_MODEL_DATA");
        var customData = com.stardew.craft.port.PortItemData.getOrDefault(safeStack, customModelDataType, defaultData);
        if (quality != QualityHelper.NORMAL && customData.equals(defaultData)) {
            com.stardew.craft.port.PortItemData.set(safeStack, customModelDataType,
                    new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        if (quality == QualityHelper.NORMAL) {
            return Objects.requireNonNull(baseName, "baseName");
        }

        @SuppressWarnings("null")
        MutableComponent qualityName = Component.empty().append(prefix).append(baseName);
        return qualityName;
    }

    @Override
    public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull net.minecraft.world.level.Level level, @Nonnull net.minecraft.world.entity.LivingEntity livingEntity) {
        ItemStack safeStack = Objects.requireNonNull(stack, "stack");
        var safeLevel = Objects.requireNonNull(level, "level");
        var safeEntity = Objects.requireNonNull(livingEntity, "livingEntity");
        int quality = QualityHelper.getQuality(safeStack);
        int health = getHealthRestoration(quality);
        int energy = getEnergyRestoration(quality);

        ItemStack result = super.finishUsingItem(safeStack, safeLevel, safeEntity);

        if (!safeLevel.isClientSide && safeEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (health > 0) {
                int currentSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int maxSDHealth = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, Math.min(maxSDHealth, currentSDHealth + health));
            }

            if (energy > 0) {
                com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, energy);
            }
        }

        return Objects.requireNonNull(result, "result");
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
