package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.port.PortVanillaEnchantmentRules;
import com.stardew.craft.port.PortEnchantments;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): enchanting-table / {@code enchant_with_levels} selection drops candidates incompatible with the
 * last pick; for a StardewCraft item use the 1.21.1 exclusive-set rules ({@code Enchantment.areCompatible}).
 * See {@link PortVanillaEnchantmentRules}.
 */
@Mixin(EnchantmentHelper.class)
public abstract class PortEnchantmentSelectionRulesMixin {
    @WrapOperation(method = "getAvailableEnchantmentResults", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;isDiscoverable()Z"))
    private static boolean stardewcraft$selectionPool(Enchantment enchantment, Operation<Boolean> original) {
        return PortEnchantments.inSelectionPool(enchantment, original.call(enchantment));
    }

    @WrapOperation(method = "getAvailableEnchantmentResults", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;isTreasureOnly()Z"))
    private static boolean stardewcraft$dataPoolAlreadyFiltersTreasure(Enchantment enchantment, Operation<Boolean> original) {
        return !(enchantment instanceof PortEnchantments.DataEnchantment) && original.call(enchantment);
    }

    @WrapOperation(method = "selectEnchantment",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;filterCompatibleEnchantments(Ljava/util/List;Lnet/minecraft/world/item/enchantment/EnchantmentInstance;)V"))
    private static void stardewcraft$exclusiveSet(List<EnchantmentInstance> candidates, EnchantmentInstance chosen,
                                                  Operation<Void> original, @Local(argsOnly = true) ItemStack target) {
        if (PortVanillaEnchantmentRules.isModItem(target)) {
            PortVanillaEnchantmentRules.filterCompatibleEnchantments(target, candidates, chosen);
        } else {
            original.call(candidates, chosen);
        }
    }
}
