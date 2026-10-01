package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.network.FriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 矿井出口操作数据包 — 客户端 → 服务端。
 * 
 * 在服务端处理离矿对话的传送操作。
 * 映射自 SDV "ExitMine" 对话回调。
 */
public record MineExitActionPayload(Action action) implements CustomPacketPayload {
	
	@SuppressWarnings("null")
	public static final Type<MineExitActionPayload> TYPE = 
			new Type<>(new ResourceLocation(StardewCraft.MODID, "mine_exit_action"));
	
	@SuppressWarnings("null")
	public static final StreamCodec<FriendlyByteBuf, MineExitActionPayload> STREAM_CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeEnum(payload.action),
			buf -> new MineExitActionPayload(buf.readEnum(Action.class))
	);
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	
	public enum Action {
		GO_UP_FLOOR,
		GO_TO_FLOOR_0,
		EXIT_MINE
	}
	
	public static void handle(MineExitActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
			if (serverPlayer.level().dimension() != ModMiningDimensions.STARDEW_MINING) return;

			MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
			if (playerData == null) return;

			switch (payload.action) {
				case EXIT_MINE -> {
					int currentFloor = playerData.getCurrentFloor();
					if (currentFloor > 120) {
						// 骷髅矿：返回沙漠（而非矿井 Floor 0）
						com.stardew.craft.mining.SkullCavernSessionManager.onPlayerLeave(
								serverPlayer, (ServerLevel) serverPlayer.level());
						teleportToFloor(serverPlayer, playerData, com.stardew.craft.mining.SkullCavernRuntime.LOBBY);
						StardewCraft.LOGGER.info("Player {} exited skull cavern to desert", serverPlayer.getName().getString());
					} else {
						// SDV ExitMine_Leave: Game1.warpFarmer("Mine", 23, 8) → 传送到矿井入口大厅（0层）
						teleportToFloor(serverPlayer, playerData, 0);
						StardewCraft.LOGGER.info("Player {} exited to mine entrance (floor 0)", serverPlayer.getName().getString());
					}
				}
				case GO_UP_FLOOR -> {
					int currentFloor = playerData.getCurrentFloor();
					if (currentFloor > 0 && currentFloor <= 120) {
						teleportToFloor(serverPlayer, playerData, currentFloor - 1);
					}
				}
				case GO_TO_FLOOR_0 -> {
                    if(playerData.getCurrentFloor()>120) {
                        com.stardew.craft.mining.SkullCavernSessionManager.onPlayerLeave(serverPlayer,serverPlayer.serverLevel());
                        teleportToFloor(serverPlayer,playerData,com.stardew.craft.mining.SkullCavernRuntime.LOBBY);
                    } else teleportToFloor(serverPlayer,playerData,0);
                }
			}
		});
	}

	private static void teleportToFloor(ServerPlayer serverPlayer, MiningPlayerData playerData, int targetFloor) {
		@SuppressWarnings("null")
		ServerLevel mineLevel = serverPlayer.server.getLevel(ModMiningDimensions.STARDEW_MINING);
		if (mineLevel == null) return;

		if (targetFloor > 0) {
			com.stardew.craft.mining.OrdinaryMineRuntime.ensure(mineLevel, targetFloor);
		}

		MiningCoordinates.teleportPlayerToFloor(serverPlayer, mineLevel, targetFloor);
		playerData.setCurrentFloor(targetFloor);
		MiningDataManager.savePlayerData(serverPlayer, playerData);

		net.minecraftforge.network.PacketDistributor.sendToPlayer(
			serverPlayer,
			new com.stardew.craft.network.MiningFloorSyncPacket(targetFloor)
		);
	}

	/** 骷髅矿出口 → 传送到沙漠矿洞入口附近 */
	private static void teleportToDesert(ServerPlayer serverPlayer, MiningPlayerData playerData) {
		@SuppressWarnings("null")
		ServerLevel stardew = serverPlayer.server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
		if (stardew == null) return;

		// 使用骷髅矿专用出口坐标，朝南
		net.minecraft.core.BlockPos arrival = com.stardew.craft.desert.DesertConstants.worldPos(
				com.stardew.craft.desert.DesertConstants.SKULL_CAVERN_EXIT_OFFSET);

		ModTeleport.to(serverPlayer, stardew,
				arrival.getX() + 0.5D,
				arrival.getY(),
				arrival.getZ() + 0.5D,
				180.0F, 0.0F);

		playerData.setCurrentFloor(0);
		MiningDataManager.savePlayerData(serverPlayer, playerData);

		net.minecraftforge.network.PacketDistributor.sendToPlayer(
			serverPlayer,
			new com.stardew.craft.network.MiningFloorSyncPacket(0)
		);
	}
}
