package com.stardew.craft.item.fish.misc;

import com.stardew.craft.item.IStardewItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * River Jelly
 * 原版(1.6)：+75 能量、+33 生命，Max Energy +30（7m）。
 */
public class RiverJellyItem extends Item implements IStardewItem {
    public static final int ENERGY = 75;
    public static final int HEALTH = 33;
    public static final int MAX_ENERGY_BONUS = 30;
    public static final int BUFF_DURATION_TICKS = 7 * 60 * 20;

    @SuppressWarnings("null")
    public RiverJellyItem(Item.Properties properties) {
        super(properties.food(new FoodProperties.Builder()
                .nutrition(1)
                .saturationMod(0.1f)
                .alwaysEat()
                .build()));
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.misc";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 125;
    }

    @Override
    public boolean isFood() {
        return true;
    }

    @Override
    public int getEnergy(ItemStack stack) {
        return ENERGY;
    }

    @Override
    public int getHealth(ItemStack stack) {
        return HEALTH;
    }

    @SuppressWarnings("null")
    @Override
    public List<Component> getAfterEatTooltipLines(ItemStack stack) {
        @SuppressWarnings("null")
        Component buffName = Component.literal("【")
                .append(Component.translatable("effect.stardewcraft.vigorous"))
                .append(" I】")
                .withStyle(ChatFormatting.GREEN);

        return List.of(
                Component.literal("\uE010 ")
                        .append(buffName)
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("stardewcraft.tooltip.buff.max_energy", MAX_ENERGY_BONUS)
                                .withStyle(ChatFormatting.GRAY))
        );
    }

    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity livingEntity) {
        int health = getHealth(stack);
        int energy = getEnergy(stack);

        @SuppressWarnings("null")
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            // 先应用 Max Energy Buff，确保本次恢复能量能正确 cap 到增益后的上限。
            com.stardew.craft.player.PlayerStardewDataAPI.applyMaxEnergyBuff(serverPlayer, MAX_ENERGY_BONUS, BUFF_DURATION_TICKS);

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
}
