package com.stardew.craft.menu;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MineFloorGenerator;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.network.MiningFloorSyncPacket;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 矿井出口菜单（服务端）
 * 
 * 提供3个传送选项：
 * 1. 返回上一层（floor - 1）
 * 2. 返回第0层（矿井入口）
 * 3. 退出矿井维度（回主世界）
 */
public class MineExitMenu extends AbstractContainerMenu {
    
    private final Player player;
    private int currentFloor;
    
    // 统一构造函数（客户端和服务端都使用）
    public MineExitMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.MINE_EXIT.get(), containerId);
        this.player = playerInventory.player;
        
        // 获取当前楼层（服务端从数据获取，客户端默认0，之后通过数据同步）
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
            this.currentFloor = playerData != null ? playerData.getCurrentFloor() : 0;
        } else {
            this.currentFloor = 0;
        }

        // 同步当前楼层到客户端
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return MineExitMenu.this.currentFloor;
            }

            @Override
            public void set(int value) {
                MineExitMenu.this.currentFloor = value;
            }
        });
    }
    
    public int getCurrentFloor() {
        return currentFloor;
    }
    
    @Override
    public ItemStack quickMoveStack(@SuppressWarnings("null") Player player, int index) {
        // 没有物品槽，不需要快速移动
        return ItemStack.EMPTY;
    }
    
    @Override
    public boolean stillValid(@SuppressWarnings("null") Player player) {
        // 检查玩家是否在矿井维度
        Level level = player.level();
        return !level.isClientSide() 
            && level.dimension() == ModMiningDimensions.STARDEW_MINING;
    }
    
    /**
     * 传送到指定楼层
     */
    public void teleportToFloor(int targetFloor) {
        if (player instanceof ServerPlayer serverPlayer) {
            @SuppressWarnings("null")
            ServerLevel mineLevel = serverPlayer.server.getLevel(ModMiningDimensions.STARDEW_MINING);
            
            if (mineLevel == null) {
                StardewCraft.LOGGER.error("Mining dimension is not loaded");
                return;
            }

            if (targetFloor > 0) {
                MineFloorGenerator.generateFloor(mineLevel, targetFloor);
            }
            
            // 传送玩家
            MiningCoordinates.teleportPlayerToFloor(serverPlayer, mineLevel, targetFloor);
            
            // 更新玩家数据
            MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
            playerData.setCurrentFloor(targetFloor);
            MiningDataManager.savePlayerData(serverPlayer, playerData);

            // 同步层数到客户端 HUD
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                serverPlayer,
                new MiningFloorSyncPacket(targetFloor)
            );
            com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(serverPlayer, targetFloor);
            
            StardewCraft.LOGGER.info("Player {} used the exit menu to teleport to floor {}",
                serverPlayer.getName().getString(), targetFloor);
            
            // 关闭菜单
            serverPlayer.closeContainer();
        }
    }
    
    /**
     * 退出矿井维度（回到主世界）
     */
    public void exitMine() {
        if (player instanceof ServerPlayer serverPlayer) {
            @SuppressWarnings("null")
            ServerLevel overworld = serverPlayer.server.getLevel(Level.OVERWORLD);
            
            if (overworld == null) {
                StardewCraft.LOGGER.error("Overworld is not loaded");
                return;
            }
            
            // 传送到主世界出生点（或玩家的床位置）
            BlockPos spawnPos = serverPlayer.getRespawnPosition();
            if (spawnPos == null) {
                spawnPos = overworld.getSharedSpawnPos();
            }
            
            ModTeleport.to(serverPlayer, overworld, spawnPos,
                serverPlayer.getYRot(), serverPlayer.getXRot());
            
            StardewCraft.LOGGER.info("Player {} left the mining dimension", serverPlayer.getName().getString());
            
            // 关闭菜单
            serverPlayer.closeContainer();
        }
    }
}
