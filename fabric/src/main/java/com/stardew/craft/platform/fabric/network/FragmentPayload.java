package com.stardew.craft.platform.fabric.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded transport frame; the original registered payload codec remains authoritative. */
record FragmentPayload(long sequence, long nonce, ResourceLocation originalType,
        int index, int count, int totalBytes, byte[] bytes) implements CustomPacketPayload {
    static final Type<FragmentPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            "stardewcraft", "fabric_payload_fragment_v1"));
    static final StreamCodec<RegistryFriendlyByteBuf, FragmentPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.sequence);
                buffer.writeLong(payload.nonce);
                buffer.writeUtf(payload.originalType.toString(), 256);
                buffer.writeVarInt(payload.index);
                buffer.writeVarInt(payload.count);
                buffer.writeVarInt(payload.totalBytes);
                buffer.writeByteArray(payload.bytes);
            },
            buffer -> new FragmentPayload(buffer.readLong(), buffer.readLong(),
                    ResourceLocation.parse(buffer.readUtf(256)), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readByteArray(FabricFragmentation.CLIENTBOUND_CHUNK_BYTES)));

    FragmentPayload {
        if (sequence <= 0 || nonce == 0 || originalType == null || originalType.equals(TYPE.id())
                || originalType.toString().length() > 256 || totalBytes < 1
                || totalBytes > FabricFragmentation.MAX_PAYLOAD_BYTES || count < 1
                || count > FabricFragmentation.MAX_FRAGMENT_COUNT || index < 0 || index >= count
                || bytes == null || bytes.length < 1
                || bytes.length > FabricFragmentation.CLIENTBOUND_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid StardewCraft payload fragment metadata");
        }
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
