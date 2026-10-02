package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortEnchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mod data enchantments use the random-loot pool, not the enchanting-table pool. */
@Mixin(EnchantWithLevelsFunction.class)
public abstract class PortLootEnchantmentPoolMixin {
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;IZ)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack stardewcraft$randomLootPool(RandomSource random, ItemStack stack, int level, boolean treasure,
            Operation<ItemStack> original) {
        return PortEnchantments.fromRandomLoot(() -> original.call(random, stack, level, treasure));
    }
}
