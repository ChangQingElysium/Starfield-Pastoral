package com.stardew.craft.platform.fabric.light;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Full chunk snapshot or an ordered delta, including zero entries for removed sources. */
public record LightSyncPayload(ResourceLocation dimension, ChunkPos chunk, boolean full,
        Map<Long, Byte> values) implements CustomPacketPayload {
    public static final Type<LightSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("stardewcraft", "fabric_auxiliary_light"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightSyncPayload> CODEC = CustomPacketPayload.codec(LightSyncPayload::write, LightSyncPayload::read);
    private static final int MAX_ENTRIES = 256 * 4096;

    public LightSyncPayload {
        Objects.requireNonNull(dimension, "dimension"); Objects.requireNonNull(chunk, "chunk");
        values = Map.copyOf(values);
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many chunk light sources");
        values.forEach((pos, value) -> {
            ChunkAuxiliaryLightManager.validatePosition(chunk, pos);
            if (value < 0 || value > 15 || (full && value == 0)) throw new IllegalArgumentException("Invalid auxiliary light level");
        });
    }

    private static LightSyncPayload read(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation(); var chunk = new ChunkPos(buffer.readInt(), buffer.readInt());
        boolean full = buffer.readBoolean(); int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid auxiliary light entry count");
        var values = new HashMap<Long, Byte>();
        for (int index = 0; index < count; index++) {
            long pos = buffer.readLong(); byte value = buffer.readByte();
            if (values.put(pos, value) != null) throw new IllegalArgumentException("Duplicate auxiliary light position");
        }
        return new LightSyncPayload(dimension, chunk, full, values);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension); buffer.writeInt(chunk.x); buffer.writeInt(chunk.z); buffer.writeBoolean(full); buffer.writeVarInt(values.size());
        values.forEach((pos, light) -> { buffer.writeLong(pos); buffer.writeByte(light); });
    }

    @Override public Type<LightSyncPayload> type() { return TYPE; }
}
