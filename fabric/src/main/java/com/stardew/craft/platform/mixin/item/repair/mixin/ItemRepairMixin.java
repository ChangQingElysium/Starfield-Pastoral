package com.stardew.craft.platform.mixin.item.repair.mixin;


import com.stardew.craft.platform.fabric.item.repair.RepairableItem;
import com.stardew.craft.platform.fabric.item.repair.RepairableProperties;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class ItemRepairMixin implements RepairableItem {
    @Unique private boolean stardewcraft$canRepair;
    @Override public boolean stardewcraft$canRepair() { return stardewcraft$canRepair; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void stardewcraft$repairFlag(Item.Properties properties, CallbackInfo callback) {
        stardewcraft$canRepair = ((RepairableProperties) properties).stardewcraft$canRepair();
    }
}
