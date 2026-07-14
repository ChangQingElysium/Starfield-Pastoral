package com.stardew.craft.menu;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.MiningCoordinates;
import com.stardew.craft.mining.MineFloorGenerator;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 矿井电梯菜单（服务端）
 * 允许传送到已解锁的整5层
 */
public class ElevatorMenu extends AbstractContainerMenu {

    private final Player player;
    private int currentFloor;
    private int maxFloorReached;

    public ElevatorMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.ELEVATOR.get(), containerId);
        this.player = playerInventory.player;

        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
            this.currentFloor = playerData != null ? playerData.getCurrentFloor() : 0;
            this.maxFloorReached = playerData != null ? playerData.getMaxFloorReached() : 0;
        } else {
            this.currentFloor = 0;
            this.maxFloorReached = 0;
        }

        // 同步当前楼层和最深到达层
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ElevatorMenu.this.currentFloor;
            }

            @Override
            public void set(int value) {
                ElevatorMenu.this.currentFloor = value;
            }
        });

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ElevatorMenu.this.maxFloorReached;
            }

            @Override
            public void set(int value) {
                ElevatorMenu.this.maxFloorReached = value;
            }
        });
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public int getMaxFloorReached() {
        return maxFloorReached;
    }

    @Override
    public ItemStack quickMoveStack(@SuppressWarnings("null") Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@SuppressWarnings("null") Player player) {
        Level level = player.level();
        return !level.isClientSide() && level.dimension() == ModMiningDimensions.STARDEW_MINING;
    }

    public void teleportToFloor(int targetFloor) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (targetFloor < 0 || targetFloor % 5 != 0) {
            return;
        }

        MiningPlayerData playerData = MiningDataManager.getPlayerData(serverPlayer);
        int maxReached = playerData != null ? playerData.getMaxFloorReached() : 0;
        if (targetFloor > maxReached) {
            return;
        }

        @SuppressWarnings("null")
        ServerLevel mineLevel = serverPlayer.server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (mineLevel == null) {
            StardewCraft.LOGGER.error("Mining dimension is not loaded");
            return;
        }

        if (targetFloor > 0) {
            MineFloorGenerator.generateFloor(mineLevel, targetFloor);
        }

        MiningCoordinates.teleportPlayerToFloor(serverPlayer, mineLevel, targetFloor);

        if (playerData != null) {
            playerData.setCurrentFloor(targetFloor);
            MiningDataManager.savePlayerData(serverPlayer, playerData);

            // 触发矿井层数到达事件（任务系统）
            com.stardew.craft.quest.StardewQuestEvents.fireMineFloorReached(serverPlayer, targetFloor);
        }

        serverPlayer.closeContainer();
    }
}
