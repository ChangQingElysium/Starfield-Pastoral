package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OssifiedMarkPayload(int entityId, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<OssifiedMarkPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "ossified_mark")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, OssifiedMarkPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        OssifiedMarkPayload::entityId,
        ByteBufCodecs.VAR_INT,
        OssifiedMarkPayload::durationTicks,
        OssifiedMarkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OssifiedMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.OssifiedMarkClientState.apply(payload.entityId(), payload.durationTicks()));
    }
}
