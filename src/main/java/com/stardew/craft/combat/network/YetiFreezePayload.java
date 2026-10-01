package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record YetiFreezePayload(int entityId, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<YetiFreezePayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "yeti_freeze")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, YetiFreezePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        YetiFreezePayload::entityId,
        ByteBufCodecs.VAR_INT,
        YetiFreezePayload::durationTicks,
        YetiFreezePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(YetiFreezePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.YetiFreezeClientState.apply(payload.entityId(), payload.durationTicks()));
    }
}
