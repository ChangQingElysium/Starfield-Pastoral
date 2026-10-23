package com.stardew.craft.platform.mixin.event.enchant.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents;
import com.stardew.craft.platform.fabric.event.enchant.GrindstoneExperience;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneUpdateMixin implements GrindstoneExperience {
    @Unique private int stardewcraft$experience = -1;
    @Override public int stardewcraft$grindstoneExperience() { return stardewcraft$experience; }

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/GrindstoneMenu;computeResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack stardewcraft$place(GrindstoneMenu menu, ItemStack top, ItemStack bottom, Operation<ItemStack> original) {
        var result = FabricEnchantingEvents.place(top, bottom);
        stardewcraft$experience = result.experience();
        return result.vanilla() ? original.call(menu, top, bottom) : result.output();
    }
}
