package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve data-driven weights, including values outside 1.20.1's four Rarity constants. */
@Mixin(EnchantmentInstance.class)
public abstract class PortEnchantmentWeightMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;getWeight()I"),
            require = 1, expect = 1, allow = 1)
    private static int stardewcraft$dataWeight(Enchantment.Rarity rarity, Operation<Integer> original,
            @Local(argsOnly = true) Enchantment enchantment) {
        return PortEnchantments.weight(enchantment, original.call(rarity));
    }
}
