package com.stardew.craft.platform.fabric.item.transfer;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/** Explicit opt-in for a machine's real transactional mutation implementation. */
public interface TransactionalItemHandler {
    Storage<ItemVariant> stardewcraft$itemStorage();
}
