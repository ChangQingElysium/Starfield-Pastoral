package com.stardew.craft.item.artisan;

import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Artisan drink item with configurable restore values and optional speed effect.
 */
public class ArtisanDrinkItem extends Item implements IStardewItem {
    private final int sellPrice;
    private final int energy;
    private final int health;
    private final int speedBonus;
    private final int speedDurationTicks;
    private final boolean supportsQuality;

    @SuppressWarnings("null")
    public ArtisanDrinkItem(int sellPrice, int energy, int health, int speedBonus, int speedDurationTicks, Item.Properties properties) {
        this(sellPrice, energy, health, speedBonus, speedDurationTicks, false, properties);
    }

    @SuppressWarnings("null")
    public ArtisanDrinkItem(int sellPrice, int energy, int health, int speedBonus, int speedDurationTicks, boolean supportsQuality, Item.Properties properties) {
        super(properties.food(new FoodProperties.Builder()
                .nutrition(1)
                .saturationModifier(0.1f)
                .alwaysEdible()
                .build()));
        this.sellPrice = sellPrice;
        this.energy = energy;
        this.health = health;
        this.speedBonus = speedBonus;
        this.speedDurationTicks = speedDurationTicks;
        this.supportsQuality = supportsQuality;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.artisan_goods";
    }

    public boolean supportsQuality() {
        return supportsQuality;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        if (!supportsQuality) {
            return sellPrice;
        }
        int quality = QualityHelper.getQuality(stack);
        return (int) Math.floor(sellPrice * QualityHelper.getPriceMultiplier(quality));
    }

    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        if (!supportsQuality) {
            return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);
        }

        int quality = QualityHelper.getQuality(stack);
        Component baseName = Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.WHITE);

        QualityHelper.ensureQualityModelData(stack);

        if (quality == QualityHelper.NORMAL) {
            return baseName;
        }

        return Component.empty().append(QualityHelper.getQualityPrefix(quality)).append(baseName);
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @SuppressWarnings("null")
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return energy;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return health;
    }

    @SuppressWarnings("null")
    @Override
    public List<Component> getAfterEatTooltipLines(ItemStack stack) {
        if (speedBonus == 0) {
            return List.of();
        }

        @SuppressWarnings("null")
        Component buffName = Component.literal("【")
                .append(Component.translatable("effect.stardewcraft.speed"))
                .append(Component.literal(" I】"))
                .withStyle(speedBonus > 0 ? ChatFormatting.BLUE : ChatFormatting.RED);

        net.minecraft.network.chat.MutableComponent valueText = speedBonus > 0
            ? Component.translatable("stardewcraft.tooltip.buff.speed", speedBonus)
            : Component.translatable("stardewcraft.tooltip.buff.speed_down", speedBonus);

        return List.of(
                Component.literal("\uE013 ")
                        .append(buffName)
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(valueText.withStyle(ChatFormatting.GRAY))
        );
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity livingEntity) {
        @SuppressWarnings("null")
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            int restoredHealth = getHealth(stack);
            int restoredEnergy = getEnergy(stack);
            if (restoredHealth != 0) {
                int current = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int max = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer,
                        Math.max(0, Math.min(max, current + restoredHealth)));
            }
            if (restoredEnergy != 0) {
                if (restoredEnergy > 0) {
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, restoredEnergy);
                } else {
                    com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, -restoredEnergy);
                }
            }

            if (speedBonus != 0 && speedDurationTicks > 0) {
                if (speedBonus > 0) {
                    serverPlayer.addEffect(new MobEffectInstance(ModMobEffects.SPEED, speedDurationTicks, speedBonus - 1));
                } else {
                    int amplifier = Math.max(0, Math.abs(speedBonus) - 1);
                    serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, speedDurationTicks, amplifier));
                }
            }
        }
        return result;
    }
}
