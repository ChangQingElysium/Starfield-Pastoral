package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds")
public abstract class PortEnchantmentTradePriceMixin {
    @WrapOperation(method = "getOffer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;isTreasureOnly()Z"))
    private boolean stardewcraft$doubleTradePrice(Enchantment enchantment, Operation<Boolean> original) {
        return PortEnchantments.doubleTradePrice(enchantment, original.call(enchantment));
    }
}
