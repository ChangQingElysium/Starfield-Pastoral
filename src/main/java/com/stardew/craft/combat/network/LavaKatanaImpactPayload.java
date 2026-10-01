package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** A positive, applied Lava Katana hit; contains no client-authored combat data. */
public record LavaKatanaImpactPayload(
        int casterId, int targetId, long gameTick,
        double x, double y, double z,
        float directionX, float directionY, float directionZ,
        boolean critical, boolean brand, boolean finisher, boolean burn
) implements CustomPacketPayload {
    public static final Type<LavaKatanaImpactPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "lava_katana_impact"));

    public static final StreamCodec<ByteBuf, LavaKatanaImpactPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public LavaKatanaImpactPayload decode(ByteBuf buffer) {
            return new LavaKatanaImpactPayload(
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
        }

        @Override
        public void encode(ByteBuf buffer, LavaKatanaImpactPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.casterId());
            ByteBufCodecs.VAR_INT.encode(buffer, value.targetId());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.gameTick());
            buffer.writeDouble(value.x()).writeDouble(value.y()).writeDouble(value.z());
            buffer.writeFloat(value.directionX()).writeFloat(value.directionY()).writeFloat(value.directionZ());
            buffer.writeBoolean(value.critical()).writeBoolean(value.brand()).writeBoolean(value.finisher()).writeBoolean(value.burn());
        }
    };

    @Override
    public Type<LavaKatanaImpactPayload> type() {
        return TYPE;
    }

    public static void handle(LavaKatanaImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.LavaKatanaVisuals.impact(payload));
    }
}
