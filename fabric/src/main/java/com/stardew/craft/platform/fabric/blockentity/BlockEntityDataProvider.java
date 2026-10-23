package com.stardew.craft.platform.fabric.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Explicit update hooks for the mod's BEs; empty snapshots may clear custom state. */
public interface BlockEntityDataProvider {
    default void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        ((BlockEntity) this).loadWithComponents(tag, registries);
    }

    default void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        if (!packet.getTag().isEmpty()) ((BlockEntity) this).loadWithComponents(packet.getTag(), registries);
    }
}
