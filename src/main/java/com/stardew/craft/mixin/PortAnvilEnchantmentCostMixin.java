package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortEnchantments;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * PORT(1.20.1): 1.21 anvils charge {@code Enchantment#getAnvilCost()} per level (halved, min 1, for books);
 * 1.20.1 derives that number from {@link Enchantment.Rarity}. Replace only the four switch results;
 * keep the following book division and minimum untouched, including for arbitrary data-pack costs.
 */
@Mixin(AnvilMenu.class)
public abstract class PortAnvilEnchantmentCostMixin {
    @ModifyConstant(method = "createResult", constant = {
            @Constant(intValue = 1, ordinal = 0), @Constant(intValue = 2, ordinal = 0),
            @Constant(intValue = 4, ordinal = 0), @Constant(intValue = 8, ordinal = 0)},
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getRarity()Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I", ordinal = 1)),
            require = 4, expect = 4, allow = 4)
    private int stardewcraft$dataAnvilCost(int vanilla, @Local(ordinal = 0) Enchantment enchantment) {
        return PortEnchantments.anvilCost(enchantment, vanilla);
    }
}
