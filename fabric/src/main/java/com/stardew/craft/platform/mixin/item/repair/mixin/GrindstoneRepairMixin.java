package com.stardew.craft.platform.mixin.item.repair.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.item.repair.ItemRepairs;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneRepairMixin {
    @WrapOperation(method = "mergeItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isDamageableItem()Z", ordinal = 0))
    private boolean stardewcraft$repairable(ItemStack input, Operation<Boolean> original) {
        return original.call(input) && ItemRepairs.isRepairable(input);
    }

    @WrapOperation(method = "mergeItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void stardewcraft$preserveUnrepairableDamage(ItemStack output, int damage, Operation<Void> original,
            ItemStack input, ItemStack additional) {
        original.call(output, ItemRepairs.isRepairable(additional) ? damage : input.getDamageValue());
    }
}
