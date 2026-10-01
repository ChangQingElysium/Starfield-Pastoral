package com.stardew.craft.port;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

/** 1.21.1 block entity data packet semantics on 1.20.1. */
public final class PortBlockEntityPackets {
    private PortBlockEntityPackets() {}

    /**
     * 1.21.1 {@code ClientboundBlockEntityDataPacket#getTag()} is never null: an empty update tag travels as an empty
     * compound (TRUSTED_COMPOUND_TAG). 1.20.1 stores an empty update tag as {@code null} (also on the wire), so mod
     * handlers that treat the packet as a full snapshot must see an empty compound instead.
     */
    public static CompoundTag tag(ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        return tag != null ? tag : new CompoundTag();
    }
}
