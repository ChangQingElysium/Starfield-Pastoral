package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortEnchantments;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): 1.21 anvils charge {@code Enchantment#getAnvilCost()} per level (halved, min 1, for books);
 * 1.20.1 derives that number from {@link Enchantment.Rarity} (1/2/4/8, same halving). For the mod's
 * code-registered data enchantments, answer the rarity whose anvil cost equals the JSON {@code anvil_cost}.
 */
@Mixin(AnvilMenu.class)
public abstract class PortAnvilEnchantmentCostMixin {
    @WrapOperation(method = "createResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getRarity()Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;"))
    private Enchantment.Rarity stardewcraft$dataAnvilCost(Enchantment enchantment, Operation<Enchantment.Rarity> original) {
        Enchantment.Rarity rarity = PortEnchantments.anvilRarity(enchantment);
        return rarity != null ? rarity : original.call(enchantment);
    }
}
