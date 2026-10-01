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
 * Server -> client: the player reacted to the bite. Play the "Hooked!" animation before opening the minigame.
 */
public record FishingHookedAnimPayload(java.util.UUID sessionId, int durationTicks) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingHookedAnimPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fishing_hooked_anim")
	);

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingHookedAnimPayload> STREAM_CODEC = StreamCodec.composite(
			com.stardew.craft.port.PortCodecs.UUID, FishingHookedAnimPayload::sessionId,
			ByteBufCodecs.VAR_INT, FishingHookedAnimPayload::durationTicks,
			FishingHookedAnimPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingHookedAnimPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingHookedAnimPayload payload) {
		if (!com.stardew.craft.client.fishing.FishingInteractionState.accepts(payload.sessionId())) return;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		com.stardew.craft.client.fishing.FishingBiteVisuals.startHookedAnim(payload.durationTicks());
	}
}
