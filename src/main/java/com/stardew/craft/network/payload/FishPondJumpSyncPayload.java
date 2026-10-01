package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.fishpond.ClientFishPondJumpEffects;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record FishPondJumpSyncPayload(
    String dimensionId,
    String fishItemId,
    double startX,
    double startY,
    double startZ,
    double endX,
    double endY,
    double endZ,
    float jumpHeight,
    float angularVelocity,
    int delayTicks,
    boolean flipped
) implements CustomPacketPayload {

    public static final Type<FishPondJumpSyncPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "fish_pond_jump_sync")
    );

    public static final StreamCodec<ByteBuf, FishPondJumpSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.fishItemId());
            ByteBufCodecs.DOUBLE.encode(buf, payload.startX());
            ByteBufCodecs.DOUBLE.encode(buf, payload.startY());
            ByteBufCodecs.DOUBLE.encode(buf, payload.startZ());
            ByteBufCodecs.DOUBLE.encode(buf, payload.endX());
            ByteBufCodecs.DOUBLE.encode(buf, payload.endY());
            ByteBufCodecs.DOUBLE.encode(buf, payload.endZ());
            ByteBufCodecs.FLOAT.encode(buf, payload.jumpHeight());
            ByteBufCodecs.FLOAT.encode(buf, payload.angularVelocity());
            ByteBufCodecs.VAR_INT.encode(buf, payload.delayTicks());
            ByteBufCodecs.BOOL.encode(buf, payload.flipped());
        },
        buf -> new FishPondJumpSyncPayload(
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.STRING_UTF8.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.BOOL.decode(buf)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FishPondJumpSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFishPondJumpEffects.spawn(payload));
    }
}