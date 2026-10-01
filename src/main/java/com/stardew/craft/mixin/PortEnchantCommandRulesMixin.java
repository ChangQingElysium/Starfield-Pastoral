package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortVanillaEnchantmentRules;
import java.util.Collection;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): {@code /enchant} on a StardewCraft item checks compatibility with the 1.21.1 exclusive-set rules
 * (applicability already goes through {@code Enchantment#canEnchant}). See {@link PortVanillaEnchantmentRules}.
 */
@Mixin(EnchantCommand.class)
public abstract class PortEnchantCommandRulesMixin {
    @WrapOperation(method = "enchant",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;isEnchantmentCompatible(Ljava/util/Collection;Lnet/minecraft/world/item/enchantment/Enchantment;)Z"))
    private static boolean stardewcraft$exclusiveSet(Collection<Enchantment> existing, Enchantment added,
                                                     Operation<Boolean> original, @Local(ordinal = 0) ItemStack target) {
        return PortVanillaEnchantmentRules.isModItem(target)
                ? PortVanillaEnchantmentRules.isEnchantmentCompatible(target, existing, added)
                : original.call(existing, added);
    }
}
