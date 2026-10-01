package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Presentation of the already-resolved debuff, including its adjusted duration. */
public record BoneFractureTracePayload(int target, long endTick, int remaining) implements CustomPacketPayload {
    public static final Type<BoneFractureTracePayload> TYPE = new Type<>(new ResourceLocation(StardewCraft.MODID,"bone_fracture_trace"));
    public static final StreamCodec<ByteBuf,BoneFractureTracePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,BoneFractureTracePayload::target, ByteBufCodecs.VAR_LONG,BoneFractureTracePayload::endTick,
            ByteBufCodecs.VAR_INT,BoneFractureTracePayload::remaining,BoneFractureTracePayload::new);
    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(BoneFractureTracePayload p, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.BoneClaymoreVisuals.trace(p));
    }
}
