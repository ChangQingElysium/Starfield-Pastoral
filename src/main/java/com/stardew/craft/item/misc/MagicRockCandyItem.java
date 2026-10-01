package com.stardew.craft.item.misc;

import com.stardew.craft.item.cooking.CookingDishItem;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MagicRockCandyItem extends CookingDishItem {
    public MagicRockCandyItem(Properties properties) {
        super("stardewcraft.type.special", 5000, 200, List.of(
            new DishBuff(BuffType.MINING, 2, 720 * 20),
            new DishBuff(BuffType.LUCK, 5, 720 * 20),
            new DishBuff(BuffType.SPEED, 1, 720 * 20),
            new DishBuff(BuffType.DEFENSE, 5, 720 * 20),
            new DishBuff(BuffType.ATTACK, 5, 720 * 20)
        ), properties);
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        return GalaxySoulItem.prismaticText(Component.translatable(getDescriptionId(stack)).getString(), true, 0.58F);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
            @javax.annotation.Nullable net.minecraft.world.level.Level level,
            @Nonnull List<Component> tooltipComponents,
            @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(GalaxySoulItem.prismaticText(
            Component.translatable("stardewcraft.item.magic_rock_candy.tooltip.flavor").getString(), false, 0.00F));
        tooltipComponents.add(GalaxySoulItem.prismaticText(
            Component.translatable("stardewcraft.item.magic_rock_candy.tooltip.effect").getString(), true, 0.18F));
        tooltipComponents.add(GalaxySoulItem.prismaticText(
            Component.translatable("stardewcraft.item.magic_rock_candy.tooltip.special").getString(), false, 0.36F));
    }
}
