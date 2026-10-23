package com.stardew.craft.platform.mixin.item.repair.mixin;


import com.stardew.craft.platform.fabric.item.repair.RepairableProperties;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Item.Properties.class)
public abstract class ItemRepairPropertiesMixin implements RepairableProperties {
    @Unique private boolean stardewcraft$canRepair = true;
    @Override public boolean stardewcraft$canRepair() { return stardewcraft$canRepair; }
    @Override public void stardewcraft$disableRepair() { stardewcraft$canRepair = false; }
}
