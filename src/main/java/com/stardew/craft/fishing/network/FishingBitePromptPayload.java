package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Server -> client: a fish has bitten. Show an obvious bite prompt (exclamation) and a short bobber dip.
 */
public record FishingBitePromptPayload(java.util.UUID sessionId, int hookEntityId, int durationTicks) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingBitePromptPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fishing_bite_prompt")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingBitePromptPayload> STREAM_CODEC = StreamCodec.composite(
			com.stardew.craft.port.PortCodecs.UUID, FishingBitePromptPayload::sessionId,
			ByteBufCodecs.VAR_INT, FishingBitePromptPayload::hookEntityId,
			ByteBufCodecs.VAR_INT, FishingBitePromptPayload::durationTicks,
			FishingBitePromptPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingBitePromptPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingBitePromptPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		com.stardew.craft.client.fishing.FishingBiteVisuals.startBitePrompt(payload.hookEntityId(), payload.durationTicks());
	}
}
