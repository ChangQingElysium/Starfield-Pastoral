package com.stardew.craft.menu;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.service.CoopManagerValidationService;
import com.stardew.craft.animal.service.AnimalBuildingViewService;
import com.stardew.craft.block.utility.CoopManagerBlock;
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
public class CoopManagerMenu extends AbstractContainerMenu implements IBuildingManagerMenu {
    public static final int ACTION_BUILD_OR_UPGRADE = 0;
    public static final int ACTION_DEMOLISH = 1;
    public static final int ACTION_RELOCATE = 2;

    private final Player player;
    private final BlockPos managerPos;

    private int currentTier;
    private int targetTier;
    private int canBuildOrUpgrade;

    private int reqFeedTrough;
    private int curFeedTrough;
    private int reqAutoFeedTrough;
    private int curAutoFeedTrough;
    private int reqHayHopper;
    private int curHayHopper;
    private int reqIncubator;
    private int curIncubator;

    private int reqInteriorBlocks;
    private int curInteriorBlocks;
    private int curWidth;
    private int curLength;
    private int curHeight;

    private int reqEnclosed;
    private int curEnclosed;
    private int reqDoor;
    private int reqDoorCount;
    private int curDoorCount;
    private int hasInteriorSpace;
    private int boundAnimalCount;

    public CoopManagerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, BlockPos.ZERO);
    }

    public CoopManagerMenu(int containerId, Inventory playerInventory, BlockPos managerPos) {
        super(ModMenuTypes.COOP_MANAGER.get(), containerId);
        this.player = playerInventory.player;
        this.managerPos = managerPos.immutable();

        this.addDataSlot(sync(() -> currentTier, value -> currentTier = value));
        this.addDataSlot(sync(() -> targetTier, value -> targetTier = value));
        this.addDataSlot(sync(() -> canBuildOrUpgrade, value -> canBuildOrUpgrade = value));

        this.addDataSlot(sync(() -> reqFeedTrough, value -> reqFeedTrough = value));
        this.addDataSlot(sync(() -> curFeedTrough, value -> curFeedTrough = value));
        this.addDataSlot(sync(() -> reqAutoFeedTrough, value -> reqAutoFeedTrough = value));
        this.addDataSlot(sync(() -> curAutoFeedTrough, value -> curAutoFeedTrough = value));
        this.addDataSlot(sync(() -> reqHayHopper, value -> reqHayHopper = value));
        this.addDataSlot(sync(() -> curHayHopper, value -> curHayHopper = value));
        this.addDataSlot(sync(() -> reqIncubator, value -> reqIncubator = value));
        this.addDataSlot(sync(() -> curIncubator, value -> curIncubator = value));

        this.addDataSlot(sync(() -> reqInteriorBlocks, value -> reqInteriorBlocks = value));
        this.addDataSlot(sync(() -> curInteriorBlocks, value -> curInteriorBlocks = value));
        this.addDataSlot(sync(() -> curWidth, value -> curWidth = value));
        this.addDataSlot(sync(() -> curLength, value -> curLength = value));
        this.addDataSlot(sync(() -> curHeight, value -> curHeight = value));

        this.addDataSlot(sync(() -> reqEnclosed, value -> reqEnclosed = value));
        this.addDataSlot(sync(() -> curEnclosed, value -> curEnclosed = value));
        this.addDataSlot(sync(() -> reqDoor, value -> reqDoor = value));
        this.addDataSlot(sync(() -> reqDoorCount, value -> reqDoorCount = value));
        this.addDataSlot(sync(() -> curDoorCount, value -> curDoorCount = value));
        this.addDataSlot(sync(() -> hasInteriorSpace, value -> hasInteriorSpace = value));
        this.addDataSlot(sync(() -> boundAnimalCount, value -> boundAnimalCount = value));

        refreshState();
    }

    private static DataSlot sync(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        };
    }

    private void refreshState() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return;
        }

        AnimalWorldData data = AnimalWorldData.get(level);
        data.reconcileFarmOwnership(level);
        java.util.UUID farmOwner = com.stardew.craft.farm.FarmResourceOwnership
                .resolveManageableOwner(level, managerPos, serverPlayer);
        if (farmOwner == null) {
            currentTier = 0;
            targetTier = 0;
            canBuildOrUpgrade = 0;
            resetRequirementSnapshot();
            return;
        }
        Optional<AnimalBuildingRecord> existing = data.findBuildingByManager(
            level.dimension().location().toString(),
            farmOwner,
            "coop",
            managerPos
        );
        boundAnimalCount = existing
                .map(record -> AnimalBuildingViewService.managedAnimalCount(data, record))
                .orElse(0);

        currentTier = existing.map(record -> record.buildingType().tier()).orElse(0);
        AnimalBuildingMenuFlow.Decision flow = AnimalBuildingMenuFlow.resolve(
                currentTier,
                existing.map(AnimalBuildingRecord::validationState)
                        .orElse(AnimalBuildingRecord.ValidationState.VALID));
        targetTier = flow.targetTier();

        if (!flow.shouldScan()) {
            canBuildOrUpgrade = 0;
            resetRequirementSnapshot();
            return;
        }

        CoopManagerValidationService.ValidationResult validation = CoopManagerValidationService.validateForTier(level, managerPos, targetTier);
        CoopManagerValidationService.TierRequirement requirement = validation.requirement();
        CoopManagerValidationService.ScanResult scan = validation.scan();

        reqFeedTrough = requirement.feedTroughCount();
        curFeedTrough = scan.feedTroughCount();
        reqAutoFeedTrough = requirement.autoFeedTroughCount();
        curAutoFeedTrough = scan.autoFeedTroughCount();
        reqHayHopper = requirement.hayHopperCount();
        curHayHopper = scan.hayHopperCount();
        reqIncubator = requirement.incubatorCount();
        curIncubator = scan.incubatorCount();

        reqInteriorBlocks = requirement.minInteriorBlocks();
        curInteriorBlocks = scan.interiorAirCount();
        curWidth = scan.width();
        curLength = scan.length();
        curHeight = scan.height();

        reqEnclosed = requirement.requireEnclosed() ? 1 : 0;
        curEnclosed = scan.enclosed() ? 1 : 0;
        reqDoor = requirement.requireDoor() ? 1 : 0;
        reqDoorCount = requirement.minDoorCount();
        curDoorCount = scan.doorCount();
        hasInteriorSpace = scan.hasInteriorSpace() ? 1 : 0;

        canBuildOrUpgrade = validation.success() ? 1 : 0;
    }

    private void resetRequirementSnapshot() {
        reqFeedTrough = 0;
        curFeedTrough = 0;
        reqAutoFeedTrough = 0;
        curAutoFeedTrough = 0;
        reqHayHopper = 0;
        curHayHopper = 0;
        reqIncubator = 0;
        curIncubator = 0;
        reqInteriorBlocks = 0;
        curInteriorBlocks = 0;
        curWidth = 0;
        curLength = 0;
        curHeight = 0;
        reqEnclosed = 0;
        curEnclosed = 0;
        reqDoor = 0;
        reqDoorCount = 0;
        curDoorCount = 0;
        hasInteriorSpace = 0;
        boundAnimalCount = 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean success;
        if (id == ACTION_BUILD_OR_UPGRADE) {
            success = CoopManagerBlock.tryBuildOrUpgrade(level, managerPos, serverPlayer);
        } else if (id == ACTION_DEMOLISH) {
            success = CoopManagerBlock.tryDemolishBuilding(level, managerPos, serverPlayer);
        } else if (id == ACTION_RELOCATE) {
            success = CoopManagerBlock.tryRelocateManager(level, managerPos, serverPlayer);
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
        if (!(player.level().getBlockState(managerPos).getBlock() instanceof CoopManagerBlock)) {
            return false;
        }
        double centerX = managerPos.getX() + 0.5D;
        double centerY = managerPos.getY() + 0.5D;
        double centerZ = managerPos.getZ() + 0.5D;
        return player.distanceToSqr(centerX, centerY, centerZ) <= 64.0D;
    }

    public int getCurrentTier() {
        return currentTier;
    }

    public int getTargetTier() {
        return targetTier;
    }

    public boolean canBuildOrUpgrade() {
        return canBuildOrUpgrade == 1;
    }

    public int getReqFeedTrough() {
        return reqFeedTrough;
    }

    public int getCurFeedTrough() {
        return curFeedTrough;
    }

    public int getReqAutoFeedTrough() {
        return reqAutoFeedTrough;
    }

    public int getCurAutoFeedTrough() {
        return curAutoFeedTrough;
    }

    public int getReqHayHopper() {
        return reqHayHopper;
    }

    public int getCurHayHopper() {
        return curHayHopper;
    }

    public int getReqIncubator() {
        return reqIncubator;
    }

    public int getCurIncubator() {
        return curIncubator;
    }

    public int getReqInteriorBlocks() {
        return reqInteriorBlocks;
    }

    public int getCurInteriorBlocks() {
        return curInteriorBlocks;
    }

    public int getCurWidth() {
        return curWidth;
    }

    public int getCurLength() {
        return curLength;
    }

    public int getCurHeight() {
        return curHeight;
    }

    public boolean isEnclosedRequired() {
        return reqEnclosed == 1;
    }

    public boolean isEnclosed() {
        return curEnclosed == 1;
    }

    public boolean isDoorRequired() {
        return reqDoor == 1;
    }

    public int getReqDoorCount() {
        return reqDoorCount;
    }

    public int getCurDoorCount() {
        return curDoorCount;
    }

    public boolean hasInteriorSpace() {
        return hasInteriorSpace == 1;
    }

    public boolean isAtMaxTier() {
        return currentTier >= 3 && targetTier == 0;
    }

    public boolean hasExistingBuilding() {
        return currentTier > 0;
    }

    public int getBoundAnimalCount() {
        return boundAnimalCount;
    }

    @Override
    public String buildingFamily() {
        return "coop";
    }
}
