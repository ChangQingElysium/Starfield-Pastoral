package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record InfinityDaggerMarkPayload(int entityId, int durationTicks) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<InfinityDaggerMarkPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "infinity_dagger_mark")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, InfinityDaggerMarkPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        InfinityDaggerMarkPayload::entityId,
        ByteBufCodecs.VAR_INT,
        InfinityDaggerMarkPayload::durationTicks,
        InfinityDaggerMarkPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InfinityDaggerMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.InfinityDaggerMarkClientState.apply(payload.entityId(), payload.durationTicks()));
    }
}
