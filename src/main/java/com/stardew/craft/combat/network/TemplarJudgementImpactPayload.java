package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record TemplarJudgementImpactPayload(int entityId, boolean settlement) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<TemplarJudgementImpactPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "templar_judgement_impact")
    );

    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, TemplarJudgementImpactPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        TemplarJudgementImpactPayload::entityId,
        ByteBufCodecs.BOOL,
        TemplarJudgementImpactPayload::settlement,
        TemplarJudgementImpactPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TemplarJudgementImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.TemplarJudgementImpactClient.playImpact(payload.entityId(), payload.settlement()));
    }
}
