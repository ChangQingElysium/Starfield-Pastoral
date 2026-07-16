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

/**
 * 服务器发送给客户端，启动钓鱼小游戏界面
 */
public record StartMinigamePayload(
		UUID sessionId,
		int difficulty,
		int motionTypeId,
		boolean legendaryFish,
		int durationTicks,
		boolean hasTreasure,
		boolean goldenTreasure,
		boolean hasSonarBobber,
		String sonarFishItemId,
		int barSizeBonus,           // Cork Bobber: +24 pixels per tackle; Deluxe Bait: +12 pixels
		float escapeLossPerTick,    // Trap Bobber: distanceFromCatching decrease when fish is NOT in bar
		int barbedHookCount,        // Barbed Hook: affects bar gravity + auto-tracking
		int leadBobberCount,        // Lead Bobber: damps bounce at bottom
		int treasureHunterCount,    // Treasure Hunter: protects the fish meter while catching treasure
		int minFishSize,
		int maxFishSize,
		int currentFishSize,
		float initialCatchProgress,
		boolean loseProgressOutsideBar
) implements CustomPacketPayload {

	@SuppressWarnings("null")
	public static final Type<StartMinigamePayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "start_minigame")
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

	public static final StreamCodec<ByteBuf, StartMinigamePayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public StartMinigamePayload decode(@SuppressWarnings("null") ByteBuf buf) {
			@SuppressWarnings("null")
			UUID sessionId = UUID_STREAM_CODEC.decode(buf);
			@SuppressWarnings("null")
			int difficulty = ByteBufCodecs.INT.decode(buf);
			@SuppressWarnings("null")
			int motionTypeId = ByteBufCodecs.INT.decode(buf);
			@SuppressWarnings("null")
			boolean legendaryFish = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			int durationTicks = ByteBufCodecs.INT.decode(buf);
			@SuppressWarnings("null")
			boolean hasTreasure = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			boolean goldenTreasure = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			boolean hasSonarBobber = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			String sonarFishItemId = ByteBufCodecs.STRING_UTF8.decode(buf);
			@SuppressWarnings("null")
			int barSizeBonus = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			float escapeLossPerTick = ByteBufCodecs.FLOAT.decode(buf);
			@SuppressWarnings("null")
			int barbedHookCount = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			int leadBobberCount = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			int treasureHunterCount = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			int minFishSize = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			int maxFishSize = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			int currentFishSize = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			float initialCatchProgress = ByteBufCodecs.FLOAT.decode(buf);
			@SuppressWarnings("null")
			boolean loseProgressOutsideBar = ByteBufCodecs.BOOL.decode(buf);
			return new StartMinigamePayload(sessionId, difficulty, motionTypeId, legendaryFish, durationTicks,
					hasTreasure, goldenTreasure, hasSonarBobber, sonarFishItemId,
					barSizeBonus, escapeLossPerTick, barbedHookCount, leadBobberCount, treasureHunterCount,
					minFishSize, maxFishSize, currentFishSize, initialCatchProgress, loseProgressOutsideBar);
		}

		@SuppressWarnings("null")
		@Override
		public void encode(@SuppressWarnings("null") ByteBuf buf, @SuppressWarnings("null") StartMinigamePayload value) {
			UUID_STREAM_CODEC.encode(buf, value.sessionId());
			ByteBufCodecs.INT.encode(buf, value.difficulty());
			ByteBufCodecs.INT.encode(buf, value.motionTypeId());
			ByteBufCodecs.BOOL.encode(buf, value.legendaryFish());
			ByteBufCodecs.INT.encode(buf, value.durationTicks());
			ByteBufCodecs.BOOL.encode(buf, value.hasTreasure());
			ByteBufCodecs.BOOL.encode(buf, value.goldenTreasure());
			ByteBufCodecs.BOOL.encode(buf, value.hasSonarBobber());
			ByteBufCodecs.STRING_UTF8.encode(buf, value.sonarFishItemId());
			ByteBufCodecs.VAR_INT.encode(buf, value.barSizeBonus());
			ByteBufCodecs.FLOAT.encode(buf, value.escapeLossPerTick());
			ByteBufCodecs.VAR_INT.encode(buf, value.barbedHookCount());
			ByteBufCodecs.VAR_INT.encode(buf, value.leadBobberCount());
			ByteBufCodecs.VAR_INT.encode(buf, value.treasureHunterCount());
			ByteBufCodecs.VAR_INT.encode(buf, value.minFishSize());
			ByteBufCodecs.VAR_INT.encode(buf, value.maxFishSize());
			ByteBufCodecs.VAR_INT.encode(buf, value.currentFishSize());
			ByteBufCodecs.FLOAT.encode(buf, value.initialCatchProgress());
			ByteBufCodecs.BOOL.encode(buf, value.loseProgressOutsideBar());
		}
	};

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(StartMinigamePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> handleClient(payload));
	}

	@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
	private static void handleClient(StartMinigamePayload payload) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc != null && mc.player != null) {
			mc.setScreen(new com.stardew.craft.client.fishing.FishingMinigameScreen(
					payload.sessionId(),
					payload.difficulty(),
					payload.motionTypeId(),
					payload.legendaryFish(),
					payload.durationTicks(),
					payload.hasTreasure(),
					payload.goldenTreasure(),
					payload.hasSonarBobber(),
					payload.sonarFishItemId(),
					payload.barSizeBonus(),
					payload.escapeLossPerTick(),
					payload.barbedHookCount(),
					payload.leadBobberCount(),
					payload.treasureHunterCount(),
					payload.minFishSize(),
					payload.maxFishSize(),
					payload.currentFishSize(),
					payload.initialCatchProgress(),
					payload.loseProgressOutsideBar()
			));
		}
	}
}
