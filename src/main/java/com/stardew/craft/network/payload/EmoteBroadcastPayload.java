package com.stardew.craft.network.payload;

import org.jetbrains.annotations.NotNull;

import com.stardew.craft.StardewCraft;

import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record EmoteBroadcastPayload(int entityId, int baseIndex) implements CustomPacketPayload {

	@SuppressWarnings("null")
	public static final Type<EmoteBroadcastPayload> TYPE = new Type<>(
		new ResourceLocation(StardewCraft.MODID, "emote_broadcast")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, EmoteBroadcastPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT,
		EmoteBroadcastPayload::entityId,
		ByteBufCodecs.VAR_INT,
		EmoteBroadcastPayload::baseIndex,
		EmoteBroadcastPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(EmoteBroadcastPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> com.stardew.craft.client.emote.EmoteBubbleClientState.trigger(payload.entityId(), payload.baseIndex()));
	}
}
