package com.stardew.craft.platform.fabric.item.transfer;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/** A utility machine exposes one stable storage to all its handler/proxy views. */
public interface TransactionalMachineAccess {
    Storage<ItemVariant> stardewcraft$automationStorage();
}
