package com.stardew.craft.port.net.minecraft.network.codec;

/** 1.21.1 {@code net.minecraft.network.codec.StreamEncoder} for Forge 1.20.1. */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buffer, T value);
}
