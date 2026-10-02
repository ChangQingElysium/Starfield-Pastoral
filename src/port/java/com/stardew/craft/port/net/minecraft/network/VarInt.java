package com.stardew.craft.port.net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

/** 1.21.1 {@code net.minecraft.network.VarInt}; identical wire format to FriendlyByteBuf#writeVarInt. */
public final class VarInt {
    private static final int MAX_VARINT_SIZE = 5;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    private VarInt() {
    }

    public static int getByteSize(int value) {
        for (int i = 1; i < MAX_VARINT_SIZE; i++) {
            if ((value & -1 << i * DATA_BITS_PER_BYTE) == 0) {
                return i;
            }
        }
        return MAX_VARINT_SIZE;
    }

    public static boolean hasContinuationBit(byte data) {
        return (data & CONTINUATION_BIT_MASK) == CONTINUATION_BIT_MASK;
    }

    public static int read(ByteBuf buffer) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = buffer.readByte();
            value |= (current & DATA_BITS_MASK) << position++ * DATA_BITS_PER_BYTE;
            if (position > MAX_VARINT_SIZE) {
                throw new DecoderException("VarInt too big");
            }
        } while (hasContinuationBit(current));
        return value;
    }

    public static ByteBuf write(ByteBuf buffer, int value) {
        while ((value & -CONTINUATION_BIT_MASK) != 0) {
            buffer.writeByte(value & DATA_BITS_MASK | CONTINUATION_BIT_MASK);
            value >>>= DATA_BITS_PER_BYTE;
        }
        buffer.writeByte(value);
        return buffer;
    }
}
