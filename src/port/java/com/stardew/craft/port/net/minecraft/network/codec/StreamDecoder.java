package com.stardew.craft.port.net.minecraft.network.codec;

/** 1.21.1 {@code net.minecraft.network.codec.StreamDecoder} for Forge 1.20.1. */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I buffer);
}
