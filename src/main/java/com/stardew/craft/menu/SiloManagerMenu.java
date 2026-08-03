package com.stardew.craft.menu;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.block.utility.SiloManagerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

@SuppressWarnings("null")
public class SiloManagerMenu extends AbstractContainerMenu {
    public static final int ACTION_BUILD = 0;
    public static final int ACTION_DEMOLISH = 1;
    public static final int ACTION_RELOCATE = 2;

    private final Player player;
    private final BlockPos managerPos;

    private int isFormed;
    private int hayAmount;
    private int hayCapacity;
    private int canBuild;

    public SiloManagerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, BlockPos.ZERO);
    }

    public SiloManagerMenu(int containerId, Inventory playerInventory, BlockPos managerPos) {
        super(ModMenuTypes.SILO_MANAGER.get(), containerId);
        this.player = playerInventory.player;
        this.managerPos = managerPos.immutable();

        this.addDataSlot(sync(() -> isFormed, v -> isFormed = v));
        this.addDataSlot(sync(() -> hayAmount, v -> hayAmount = v));
        this.addDataSlot(sync(() -> hayCapacity, v -> hayCapacity = v));
        this.addDataSlot(sync(() -> canBuild, v -> canBuild = v));

        refreshState();
    }

    private static DataSlot sync(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return new DataSlot() {
            @Override public int get() { return getter.getAsInt(); }
            @Override public void set(int value) { setter.accept(value); }
        };
    }

    private void refreshState() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return;
        }

        AnimalWorldData data = AnimalWorldData.get(level);
        data.reconcileFarmOwnership(level);
        Optional<AnimalBuildingRecord> existing = data.findBuildingByManagerAnyOwner(
            level.dimension().location().toString(),
            "silo",
            managerPos
        );

        if (existing.isPresent()) {
            java.util.UUID hayOwner = ownerUuidOrFallback(existing.get(), serverPlayer.getUUID());
            isFormed = 1;
            hayAmount = data.getHayAmount(hayOwner);
            hayCapacity = data.getHayCapacity(hayOwner);
            canBuild = 0;
        } else {
            isFormed = 0;
            hayAmount = 0;
            hayCapacity = 0;
            // 检查是否可以建造
            var validation = com.stardew.craft.animal.service.SiloManagerValidationService.validate(level, managerPos);
            canBuild = validation.success()
                    && com.stardew.craft.farm.FarmResourceOwnership
                            .resolveManageableOwner(level, managerPos, serverPlayer) != null
                    ? 1 : 0;
        }
    }

    private static java.util.UUID ownerUuidOrFallback(AnimalBuildingRecord record, java.util.UUID fallback) {
        try {
            return java.util.UUID.fromString(record.ownerPlayerUuid());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean success;
        if (id == ACTION_BUILD) {
            success = SiloManagerBlock.tryBuild(level, managerPos, serverPlayer);
        } else if (id == ACTION_DEMOLISH) {
            success = SiloManagerBlock.tryDemolishBuilding(level, managerPos, serverPlayer);
        } else if (id == ACTION_RELOCATE) {
            success = SiloManagerBlock.tryRelocateManager(level, managerPos, serverPlayer);
        } else {
            return false;
        }

        refreshState();
        return success;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (managerPos.equals(BlockPos.ZERO)) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel)) {
            return true;
        }
        if (!(player.level().getBlockState(managerPos).getBlock() instanceof SiloManagerBlock)) {
            return false;
        }
        double centerX = managerPos.getX() + 0.5D;
        double centerY = managerPos.getY() + 0.5D;
        double centerZ = managerPos.getZ() + 0.5D;
        return player.distanceToSqr(centerX, centerY, centerZ) <= 64.0D;
    }

    public boolean isFormed() { return isFormed == 1; }
    public int getHayAmount() { return hayAmount; }
    public int getHayCapacity() { return hayCapacity; }
    public boolean canBuild() { return canBuild == 1; }
}
