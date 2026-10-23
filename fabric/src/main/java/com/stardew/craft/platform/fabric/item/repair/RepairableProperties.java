package com.stardew.craft.platform.fabric.item.repair;

/** State owned by each native Item.Properties instance until its item is constructed. */
public interface RepairableProperties {
    boolean stardewcraft$canRepair();
    void stardewcraft$disableRepair();
}
