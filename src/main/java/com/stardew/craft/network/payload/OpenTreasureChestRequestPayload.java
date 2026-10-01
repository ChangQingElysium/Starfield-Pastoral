package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.server.FishingSessionManager;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Client -> server request to open the pending fishing treasure chest after local animation ends.
 */
public record OpenTreasureChestRequestPayload(long chestId) implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<OpenTreasureChestRequestPayload> TYPE =
			new Type<>(new ResourceLocation(StardewCraft.MODID, "open_treasure_chest_request"));

	@SuppressWarnings("null")
	public static final StreamCodec<ByteBuf, OpenTreasureChestRequestPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG,
			OpenTreasureChestRequestPayload::chestId,
			OpenTreasureChestRequestPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(OpenTreasureChestRequestPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer sp)) {
				return;
			}
			FishingSessionManager.get(sp.server).openPendingTreasureChest(sp, payload.chestId());
		});
	}
}
