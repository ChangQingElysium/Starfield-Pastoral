package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.AdvanceableUtility;
import com.stardew.craft.fishpond.service.FishPondHusbandry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

public record AdvanceUtilitiesPayload() implements CustomPacketPayload {
	@SuppressWarnings("null")
	public static final Type<AdvanceUtilitiesPayload> TYPE = new Type<>(
			new ResourceLocation(StardewCraft.MODID, "advance_utilities")
	);
	public static final StreamCodec<ByteBuf, AdvanceUtilitiesPayload> STREAM_CODEC = StreamCodec.unit(new AdvanceUtilitiesPayload());

	@Override
	public Type<AdvanceUtilitiesPayload> type() {
		return TYPE;
	}

	@SuppressWarnings("null")
	public static void handle(AdvanceUtilitiesPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			Player player = context.player();
			if (!player.isCreative() || !player.hasPermissions(2)) {
				return;
			}
			Level level = player.level();
			BlockPos playerPos = player.blockPosition();

			if (!(level instanceof ServerLevel serverLevel)) {
				return;
			}

			for (int x = -5; x <= 5; x++) {
				for (int y = -2; y <= 2; y++) {
					for (int z = -5; z <= 5; z++) {
						BlockPos pos = playerPos.offset(x, y, z);
						if (!serverLevel.isLoaded(pos)) {
							continue;
						}
						@SuppressWarnings("null")
						var be = serverLevel.getBlockEntity(pos);
						if (be instanceof AdvanceableUtility utility) {
							utility.advanceDays(1);
						}
					}
				}
			}

			FishPondHusbandry.advanceNearby(serverLevel, playerPos, 1);
		});
	}
}
