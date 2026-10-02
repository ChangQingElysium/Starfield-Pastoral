package com.stardew.craft.port.net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.stardew.craft.port.PortCodecs;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;

/**
 * 1.21.1 {@code ComponentSerialization}. 1.20.1 serializes components as JSON on the wire
 * ({@code FriendlyByteBuf#writeComponent}); both codecs below use that format.
 */
public final class ComponentSerialization {
    public static final Codec<Component> CODEC = ExtraCodecs.COMPONENT;
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = PortCodecs.COMPONENT;
    /** Vanilla's "trusted" variant only skips size accounting; 1.20.1 JSON reading has one path. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = PortCodecs.COMPONENT;

    private ComponentSerialization() {
    }
}
