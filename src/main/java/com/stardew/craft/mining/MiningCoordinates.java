package com.stardew.craft.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 矿井坐标管理器 - 按照 Implementation Plan 管理层级坐标
 * 
 * 坐标规则：
 * - 0层：大厅中心 (0, 64, 0)，出生点 (0, 66, -8) 面朝北
 * - 其他层：中心 (0, 66, floor×100+14)
 * 
 * 示例：
 * - 大厅（0层）：中心 (0, 64, 0)
 * - 1层：中心 (0, 66, 214)
 * - 2层：中心 (0, 66, 414)
 * - 40层：中心 (0, 66, 8014)
 */
public class MiningCoordinates {
    
    // 固定坐标
    public static final int FIXED_X = 0;
    public static final int FIXED_Y = 66;
    
    // 层间距
    public static final int FLOOR_SPACING = 200;
    
    /**
     * 获取指定层数的中心坐标
     */
    public static BlockPos getFloorCenter(int floor) {
        if (floor == 0) {
            return new BlockPos(FIXED_X, 64, 0);
        }
        int z = floor * FLOOR_SPACING + 14;
        return new BlockPos(FIXED_X, FIXED_Y, z);
    }
    
    /**
      * 传送玩家到指定层数
      * 传送到安全区中心，并给予短暂无敌帧防止传送伤害
     */
    @SuppressWarnings("null")
    public static void teleportPlayerToFloor(ServerPlayer player, ServerLevel level, int floor) {
        double x = 0.5;
        double y = (floor == 0) ? 66.0 : 66.0;
        int z = (floor == 0) ? -8 : floor * FLOOR_SPACING + 14;
        double z_pos = z + 0.5;

        float yaw = 180.0f;
        if (OrdinaryMineRuntime.handles(floor)) {
            OrdinaryMineRuntime.ensure(level, floor);
            var layout = OrdinaryMineLayout.load(level, floor);
            var spawn = layout.spawn(floor);
            x=spawn.x; y=spawn.y; z_pos=spawn.z; yaw=floor<=0?180:0;
        }

        // 传送前给予短暂无敌帧（防止 fall damage / 撞击伤害）。不要使用 setInvulnerable(true)，
        // 该状态会写入玩家实体，若延迟恢复任务被跳过会导致存档永久无敌。
        player.invulnerableTime = Math.max(player.invulnerableTime, 20);

        // Publish the intended floor before ChangedDimension handles the teleport.
        var miningData=MiningDataManager.getPlayerData(player);
        miningData.setCurrentFloor(floor);MiningDataManager.savePlayerData(player,miningData);
        ModTeleport.to(player, level, x, y, z_pos, yaw, 0.0f);
        com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.stardew.craft.network.MiningFloorSyncPacket(floor));

        // 清除速度，防止残留 momentum 造成摔落伤害
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        MineRewardClaimManager.get(level).sync(player);
        player.hurtMarked = true; // 同步到客户端

        player.invulnerableTime = Math.max(player.invulnerableTime, 20);

        StardewCraft.LOGGER.info("[MINE] Teleported player {} to floor {} at ({}, {}, {})", 
            player.getName().getString(), floor, x, y, z);
    }
    
    /**
     * 获取结构生成的起始位置（左下角）
     * 假设结构是 20x20 到 50x50 的正方形，以中心点为基准
     * 这个需要根据你实际的 NBT 结构大小调整
     */
    public static BlockPos getStructureOrigin(int floor, int structureSize) {
        BlockPos center = getFloorCenter(floor);
        int offset = structureSize / 2;
        return center.offset(-offset, 0, -offset);
    }
}
