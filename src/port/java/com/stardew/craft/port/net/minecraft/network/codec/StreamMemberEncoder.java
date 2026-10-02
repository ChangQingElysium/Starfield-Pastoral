package com.stardew.craft.port.net.minecraft.network.codec;

/** 1.21.1 {@code net.minecraft.network.codec.StreamMemberEncoder} for Forge 1.20.1. */
@FunctionalInterface
public interface StreamMemberEncoder<O, T> {
    void encode(T value, O buffer);
}
