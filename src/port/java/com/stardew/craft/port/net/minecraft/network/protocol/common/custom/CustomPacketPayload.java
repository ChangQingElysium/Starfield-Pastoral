package com.stardew.craft.port.net.minecraft.network.protocol.common.custom;

import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.codec.StreamDecoder;
import com.stardew.craft.port.net.minecraft.network.codec.StreamMemberEncoder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 {@code CustomPacketPayload}. On Forge 1.20.1 payloads travel inside the Stardew
 * SimpleChannel envelope (see {@code com.stardew.craft.port.internal.network.PortNetwork}).
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(
            StreamMemberEncoder<B, T> encoder, StreamDecoder<B, T> decoder) {
        return StreamCodec.ofMember(encoder, decoder);
    }

    static <T extends CustomPacketPayload> Type<T> createType(String id) {
        return new Type<>(new ResourceLocation(id));
    }

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }

    record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(Type<T> type, StreamCodec<B, T> codec) {
    }
}
