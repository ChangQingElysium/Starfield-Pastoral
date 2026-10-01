package com.stardew.craft.fishing.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.server.FishingSessionManager;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record FishingResultPayload(UUID sessionId, boolean success, float catchProgress, boolean treasureCaught,
								   int numCaught, boolean perfect, int caughtFishSize) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<FishingResultPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "fishing_result")
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

	public static final StreamCodec<ByteBuf, FishingResultPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public FishingResultPayload decode(@SuppressWarnings("null") ByteBuf buf) {
			@SuppressWarnings("null")
			UUID sessionId = UUID_STREAM_CODEC.decode(buf);
			@SuppressWarnings("null")
			boolean success = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			float catchProgress = ByteBufCodecs.FLOAT.decode(buf);
			@SuppressWarnings("null")
			boolean treasureCaught = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			int numCaught = ByteBufCodecs.VAR_INT.decode(buf);
			@SuppressWarnings("null")
			boolean perfect = ByteBufCodecs.BOOL.decode(buf);
			@SuppressWarnings("null")
			int caughtFishSize = ByteBufCodecs.VAR_INT.decode(buf);
			return new FishingResultPayload(sessionId, success, catchProgress, treasureCaught,
					numCaught, perfect, caughtFishSize);
		}

		@Override
		public void encode(@SuppressWarnings("null") ByteBuf buf, @SuppressWarnings("null") FishingResultPayload value) {
			UUID_STREAM_CODEC.encode(buf, value.sessionId());
			ByteBufCodecs.BOOL.encode(buf, value.success());
			ByteBufCodecs.FLOAT.encode(buf, value.catchProgress());
			ByteBufCodecs.BOOL.encode(buf, value.treasureCaught());
			ByteBufCodecs.VAR_INT.encode(buf, value.numCaught());
			ByteBufCodecs.BOOL.encode(buf, value.perfect());
			ByteBufCodecs.VAR_INT.encode(buf, value.caughtFishSize());
		}
	};

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FishingResultPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer sp)) {
				return;
			}
			FishingSessionManager.get(sp.server).handleResult(sp, payload.sessionId(), payload.success(),
					payload.catchProgress(), payload.treasureCaught(), payload.numCaught(),
					payload.perfect(), payload.caughtFishSize());
		});
	}
}
