package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** A mark belongs to one caster and one cast. Zero duration removes only that mark. */
public record TemplarMarkPayload(int casterId, long castTick, int entityId, int durationTicks) implements CustomPacketPayload {
    public static final Type<TemplarMarkPayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID, "templar_mark"));
    public static final StreamCodec<ByteBuf, TemplarMarkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TemplarMarkPayload::casterId,
            ByteBufCodecs.VAR_LONG, TemplarMarkPayload::castTick,
            ByteBufCodecs.VAR_INT, TemplarMarkPayload::entityId,
            ByteBufCodecs.VAR_INT, TemplarMarkPayload::durationTicks, TemplarMarkPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TemplarMarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.TemplarMarkClientState.apply(payload));
    }
}
