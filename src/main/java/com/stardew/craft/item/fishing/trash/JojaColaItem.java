package com.stardew.craft.item.fishing.trash;

import com.stardew.craft.item.IStardewItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Joja Cola
 * 钓鱼垃圾，但可饮用。
 * 原版(1.6)：+13 能量、+5 生命，并提供短暂移速增益。
 */
public class JojaColaItem extends Item implements IStardewItem {
    public static final int ENERGY = 13;
    public static final int HEALTH = 5;
    public static final int SPEED_BONUS = 1;
    public static final int SPEED_DURATION_TICKS = 21 * 20;

    @SuppressWarnings("null")
    public JojaColaItem(Item.Properties properties) {
        super(properties.food(new FoodProperties.Builder()
                .nutrition(1)
                .saturationModifier(0.1f)
                .alwaysEdible()
                .build()));
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.trash";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 25;
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
                .append(Component.translatable("effect.stardewcraft.speed"))
                .append(" I】")
                .withStyle(ChatFormatting.BLUE);

        return List.of(
                Component.literal("\uE013 ")
                        .append(buffName)
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable("stardewcraft.tooltip.buff.speed", SPEED_BONUS)
                                .withStyle(ChatFormatting.GRAY))
        );
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack finishUsingItem(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity livingEntity) {
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

            // Speed +1 for 21s
            serverPlayer.addEffect(new MobEffectInstance(com.stardew.craft.effect.ModMobEffects.SPEED.get(), SPEED_DURATION_TICKS, SPEED_BONUS - 1));
        }
        return result;
    }
}
