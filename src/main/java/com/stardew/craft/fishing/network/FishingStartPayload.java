package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record FishingStartPayload(UUID sessionId, int difficulty, int motionTypeId, int durationTicks) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingStartPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fishing_start")
	);

	private static final StreamCodec<ByteBuf, UUID> UUID_STREAM_CODEC = new StreamCodec<>() {
		@Override
		public UUID decode(@SuppressWarnings("null") ByteBuf buf) {
			long msb = buf.readLong();
			long lsb = buf.readLong();
			return new UUID(msb, lsb);
		}

		@Override
		public void encode(@SuppressWarnings("null") ByteBuf buf, @SuppressWarnings("null") UUID value) {
			buf.writeLong(value.getMostSignificantBits());
			buf.writeLong(value.getLeastSignificantBits());
		}
	};

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, FishingStartPayload> STREAM_CODEC = StreamCodec.composite(
			UUID_STREAM_CODEC,
			FishingStartPayload::sessionId,
			ByteBufCodecs.INT,
			FishingStartPayload::difficulty,
			ByteBufCodecs.INT,
			FishingStartPayload::motionTypeId,
			ByteBufCodecs.INT,
			FishingStartPayload::durationTicks,
			FishingStartPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingStartPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(FishingStartPayload payload) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		var player = mc.player;
		if (player == null) {
			return;
		}
		// Accept any StardewCraft fishing rod tier.
		if (!(player.getMainHandItem().getItem() instanceof com.stardew.craft.item.tool.FishingRodItem)
				&& !(player.getOffhandItem().getItem() instanceof com.stardew.craft.item.tool.FishingRodItem)) {
			return;
		}
		mc.setScreen(new com.stardew.craft.client.fishing.FishingMinigameScreen(
				payload.sessionId(),
				payload.difficulty(),
				payload.motionTypeId(),
				false,
				payload.durationTicks(),
				false,
				false,
				false,
				"",
				0,     // 默认bar size bonus (pixels)
				0.003f, // 默认不在条内掉进度
				0,     // 默认barbed hook count
				0,     // 默认lead bobber count
				0,     // 默认treasure hunter count
				0,
				0,
				0,
				0.3F,
				true
			));
	}
}
