package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record LavaKatanaMarkPayload(int entityId, int remainingTicks, int heat) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<LavaKatanaMarkPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "lava_katana_mark")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, LavaKatanaMarkPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        LavaKatanaMarkPayload::entityId,
        ByteBufCodecs.VAR_INT,
        LavaKatanaMarkPayload::remainingTicks,
        ByteBufCodecs.VAR_INT,
        LavaKatanaMarkPayload::heat,
        LavaKatanaMarkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LavaKatanaMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.LavaKatanaMarkClientState.apply(
            payload.entityId(), payload.remainingTicks(), payload.heat()
        ));
    }
}
