package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S → C：早上唤起精通山洞提示。
 * 对应 SDV {@code Game1.cs:9135-9141}: morningQueue.Enqueue showGlobalMessage(MasteryHint)。
 */
public record MasteryHintPayload() implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<MasteryHintPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mastery_hint"));

    public static final StreamCodec<ByteBuf, MasteryHintPayload> STREAM_CODEC =
        StreamCodec.unit(new MasteryHintPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MasteryHintPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            com.stardew.craft.client.hud.StardewHudMessageManager.showGlobalMessage(
                net.minecraft.network.chat.Component.translatable("stardewcraft.mastery.hint")));
    }
}
