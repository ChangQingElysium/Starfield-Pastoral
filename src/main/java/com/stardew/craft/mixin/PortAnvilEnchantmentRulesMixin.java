package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortVanillaEnchantmentRules;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): 1.21.1 anvils check {@code Enchantment.areCompatible} (exclusive-set tags); for a StardewCraft
 * item in the left slot use those rules for vanilla enchantment pairs (1.20.1 class rules differ: Looting/Luck of
 * the Sea vs Silk Touch, Impaling vs Sharpness/Smite/Bane). See {@link PortVanillaEnchantmentRules}.
 */
@Mixin(AnvilMenu.class)
public abstract class PortAnvilEnchantmentRulesMixin {
    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;isCompatibleWith(Lnet/minecraft/world/item/enchantment/Enchantment;)Z"))
    private boolean stardewcraft$exclusiveSet(Enchantment added, Enchantment existing, Operation<Boolean> original,
                                              @Local(ordinal = 0) ItemStack target) {
        return PortVanillaEnchantmentRules.isModItem(target)
                ? PortVanillaEnchantmentRules.areCompatible(target, added, existing)
                : original.call(added, existing);
    }
}
