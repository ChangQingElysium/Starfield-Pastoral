package com.stardew.craft.platform.mixin.event.enchant.mixin;


import com.stardew.craft.platform.fabric.event.enchant.FabricEnchantingEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilUpdateMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;
    @Shadow private String itemName;
    @Shadow private int repairItemCountCost;
    protected AnvilUpdateMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }
    @Inject(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V", ordinal = 0, shift = At.Shift.AFTER), cancellable = true)
    private void stardewcraft$anvilUpdate(CallbackInfo callback) {
        if (!FabricEnchantingEvents.anvil(inputSlots.getItem(0), inputSlots.getItem(1), itemName, player,
                output -> resultSlots.setItem(0, output), cost::set, value -> repairItemCountCost = value)) callback.cancel();
    }
}
