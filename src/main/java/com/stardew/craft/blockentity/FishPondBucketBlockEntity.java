package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.fishpond.service.FishPondHusbandry;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FishPondBucketBlockEntity extends BlockEntity implements UtilityAutomationAccess, BubbleItemCountProvider {
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);
    private ItemStack cachedOutput = ItemStack.EMPTY;
    private ItemStack cachedRequest = ItemStack.EMPTY;
    private ItemStack cachedFishSign = ItemStack.EMPTY;
    private boolean ready;
    private int requestCount;
    private int fishPopulation;
    private int pondMinOffsetX;
    private int pondMaxOffsetX;
    private int pondMinOffsetZ;
    private int pondMaxOffsetZ;
    private long[] pondWaterCells = new long[0];
    public long[] getPondWaterCells() { return pondWaterCells.clone(); }
    private double waterSurfaceY = 1.05D;
    private int fishSignOffsetX;
    private int fishSignOffsetY;
    private int fishSignOffsetZ;
    private double requestRenderX = 0.5D;
    private double requestRenderY = 1.5D;
    private double requestRenderZ = 0.5D;

    public FishPondBucketBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FISH_POND_BUCKET.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel && refreshSnapshot(serverLevel)) {
            syncToClient();
        }
    }

    public boolean isReady() {
        return ready && !cachedOutput.isEmpty();
    }

    public ItemStack getProduct() {
        return cachedOutput.copy();
    }

    public boolean hasPendingRequest() {
        return requestCount > 0 && !cachedRequest.isEmpty();
    }

    public ItemStack getRequestPreview() {
        return cachedRequest.copy();
    }

    public int getRequestCount() {
        return requestCount;
    }

    public boolean hasFishSign() {
        return fishPopulation > 0 && !cachedFishSign.isEmpty() && hasResolvedFishSignPos();
    }

    public ItemStack getFishSignPreview() {
        return cachedFishSign.copy();
    }

    public int getFishPopulation() {
        return fishPopulation;
    }

    public boolean hasFishVisuals() {
        return fishPopulation > 0 && !cachedFishSign.isEmpty() && pondMinOffsetX != pondMaxOffsetX && pondMinOffsetZ != pondMaxOffsetZ;
    }

    public double getFishAreaMinX() {
        return pondMinOffsetX + 0.5D;
    }

    public double getFishAreaMaxX() {
        return pondMaxOffsetX + 0.5D;
    }

    public double getFishAreaMinZ() {
        return pondMinOffsetZ + 0.5D;
    }

    public double getFishAreaMaxZ() {
        return pondMaxOffsetZ + 0.5D;
    }

    public double getWaterSurfaceY() {
        return waterSurfaceY;
    }

    public BlockPos getFishSignPos() {
        return this.worldPosition.offset(fishSignOffsetX, fishSignOffsetY, fishSignOffsetZ);
    }

    public double getRequestRenderX() {
        return requestRenderX;
    }

    public double getRequestRenderY() {
        return requestRenderY;
    }

    public double getRequestRenderZ() {
        return requestRenderZ;
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        if (this.level instanceof ServerLevel serverLevel) {
            refreshSnapshot(serverLevel);
        }
        return isReady() ? cachedOutput.copy() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (!(this.level instanceof ServerLevel serverLevel) || amount <= 0) {
            return ItemStack.EMPTY;
        }

        FishPondRecord pond = FishPondWorldData.get(serverLevel)
            .findPondByBucket(serverLevel.dimension().location().toString(), this.worldPosition)
            .orElse(null);
        if (pond == null) {
            return ItemStack.EMPTY;
        }

        ItemStack available = FishPondHusbandry.createOutputStack(pond);
        if (available.isEmpty()) {
            refreshSnapshot(serverLevel);
            return ItemStack.EMPTY;
        }

        int extractedCount = Math.min(amount, available.getCount());
        ItemStack extracted = available.copy();
        extracted.setCount(extractedCount);
        if (simulate) {
            return extracted;
        }

        int remaining = available.getCount() - extractedCount;
        if (remaining <= 0) {
            pond.setOutputItemId("");
            pond.setOutputCount(0);
        } else {
            pond.setOutputCount(remaining);
        }
        FishPondWorldData.get(serverLevel).markChanged();
        if (refreshSnapshot(serverLevel)) {
            syncToClient();
        }
        return extracted;
    }

    @Override
    public int getAutomationSlotLimit(int slot) {
        return 64;
    }

    @Override
    public UtilityItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Override
    public int getBubbleItemCount() {
        return isReady() ? cachedOutput.getCount() : 0;
    }

    public static void syncVisualState(ServerLevel level, BlockPos bucketPos) {
        if (level.getBlockEntity(bucketPos) instanceof FishPondBucketBlockEntity bucketBlockEntity
            && bucketBlockEntity.refreshSnapshot(level)) {
            bucketBlockEntity.syncToClient();
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putBoolean("ready", ready);
        if (!cachedOutput.isEmpty()) {
            tag.put("cachedOutput", PortItemStacks.save(cachedOutput, registries));
        }
        if (!cachedRequest.isEmpty()) {
            tag.put("cachedRequest", PortItemStacks.save(cachedRequest, registries));
        }
        if (!cachedFishSign.isEmpty()) {
            tag.put("cachedFishSign", PortItemStacks.save(cachedFishSign, registries));
        }
        tag.putInt("requestCount", requestCount);
        tag.putInt("fishPopulation", fishPopulation);
        tag.putInt("pondMinOffsetX", pondMinOffsetX);
        tag.putInt("pondMaxOffsetX", pondMaxOffsetX);
        tag.putInt("pondMinOffsetZ", pondMinOffsetZ);
        tag.putInt("pondMaxOffsetZ", pondMaxOffsetZ);
        tag.putDouble("waterSurfaceY", waterSurfaceY);
        tag.putLongArray("pondWaterCells",pondWaterCells);
        tag.putInt("fishSignOffsetX", fishSignOffsetX);
        tag.putInt("fishSignOffsetY", fishSignOffsetY);
        tag.putInt("fishSignOffsetZ", fishSignOffsetZ);
        tag.putDouble("requestRenderX", requestRenderX);
        tag.putDouble("requestRenderY", requestRenderY);
        tag.putDouble("requestRenderZ", requestRenderZ);
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        ready = tag.getBoolean("ready");
        cachedOutput = tag.contains("cachedOutput")
            ? PortItemStacks.parseOptional(registries, tag.getCompound("cachedOutput"))
            : ItemStack.EMPTY;
        cachedRequest = tag.contains("cachedRequest")
            ? PortItemStacks.parseOptional(registries, tag.getCompound("cachedRequest"))
            : ItemStack.EMPTY;
        cachedFishSign = tag.contains("cachedFishSign")
            ? PortItemStacks.parseOptional(registries, tag.getCompound("cachedFishSign"))
            : ItemStack.EMPTY;
        requestCount = Math.max(0, tag.getInt("requestCount"));
        fishPopulation = Math.max(0, tag.getInt("fishPopulation"));
        pondMinOffsetX = tag.getInt("pondMinOffsetX");
        pondMaxOffsetX = tag.getInt("pondMaxOffsetX");
        pondMinOffsetZ = tag.getInt("pondMinOffsetZ");
        pondMaxOffsetZ = tag.getInt("pondMaxOffsetZ");
        pondWaterCells = tag.getLongArray("pondWaterCells");
        waterSurfaceY = tag.contains("waterSurfaceY") ? tag.getDouble("waterSurfaceY") : 1.05D;
        fishSignOffsetX = tag.getInt("fishSignOffsetX");
        fishSignOffsetY = tag.getInt("fishSignOffsetY");
        fishSignOffsetZ = tag.getInt("fishSignOffsetZ");
        requestRenderX = tag.contains("requestRenderX") ? tag.getDouble("requestRenderX") : 0.5D;
        requestRenderY = tag.contains("requestRenderY") ? tag.getDouble("requestRenderY") : 1.5D;
        requestRenderZ = tag.contains("requestRenderZ") ? tag.getDouble("requestRenderZ") : 0.5D;
        if (cachedOutput.isEmpty()) {
            ready = false;
        }
        if (cachedRequest.isEmpty()) {
            requestCount = 0;
        }
        if (cachedFishSign.isEmpty()) {
            fishPopulation = 0;
        }
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private boolean refreshSnapshot(ServerLevel level) {
        FishPondRecord pond = FishPondWorldData.get(level)
            .findPondByBucket(level.dimension().location().toString(), this.worldPosition)
            .orElse(null);
        ItemStack nextOutput = pond == null ? ItemStack.EMPTY : FishPondHusbandry.createOutputStack(pond);
        ItemStack nextRequest = resolveRequestPreview(pond);
        ItemStack nextFishSign = resolveFishSignPreview(pond);
        BlockPos nextFishSignPos = resolveFishSignAnchor(level, pond);
        int nextRequestCount = pond == null ? 0 : Math.max(0, pond.neededItemCount());
        int nextFishPopulation = pond == null ? 0 : Math.max(0, pond.currentPopulation());
        int nextPondMinOffsetX = pond == null ? 0 : pond.minX() - this.worldPosition.getX();
        int nextPondMaxOffsetX = pond == null ? 0 : pond.maxX() - this.worldPosition.getX();
        int nextPondMinOffsetZ = pond == null ? 0 : pond.minZ() - this.worldPosition.getZ();
        int nextPondMaxOffsetZ = pond == null ? 0 : pond.maxZ() - this.worldPosition.getZ();
        double nextWaterSurfaceY = pond == null ? 1.05D : (pond.maxY() - this.worldPosition.getY()) + 8.0D/9;
        long[] nextWaterCells = pond == null ? new long[0] : pond.waterCells().stream().mapToLong(Long::longValue).sorted().toArray();
        int nextFishSignOffsetX = nextFishSignPos == null ? 0 : nextFishSignPos.getX() - this.worldPosition.getX();
        int nextFishSignOffsetY = nextFishSignPos == null ? 0 : nextFishSignPos.getY() - this.worldPosition.getY();
        int nextFishSignOffsetZ = nextFishSignPos == null ? 0 : nextFishSignPos.getZ() - this.worldPosition.getZ();
        double nextRequestRenderX = pond == null ? 0.5D : computeCenterX(pond);
        double nextRequestRenderY = pond == null ? 1.5D : computeCenterY(pond);
        double nextRequestRenderZ = pond == null ? 0.5D : computeCenterZ(pond);
        boolean nextReady = !nextOutput.isEmpty();
        // Both appearances belong to the pond state, not to a placeable item variant.
        BlockState state = getBlockState();
        BlockState nextState = state
                .setValue(com.stardew.craft.block.utility.FishPondBucketBlock.GOLDEN, pond != null && pond.goldenAnimalCracker())
                .setValue(com.stardew.craft.block.utility.FishPondBucketBlock.READY, nextReady);
        if (state != nextState) {
            level.setBlock(worldPosition, nextState, 3);
        }

        if (ready == nextReady
            && ItemStack.matches(cachedOutput, nextOutput)
            && cachedOutput.getCount() == nextOutput.getCount()
            && ItemStack.matches(cachedRequest, nextRequest)
            && cachedRequest.getCount() == nextRequest.getCount()
            && ItemStack.matches(cachedFishSign, nextFishSign)
            && cachedFishSign.getCount() == nextFishSign.getCount()
            && requestCount == nextRequestCount
            && fishPopulation == nextFishPopulation
            && pondMinOffsetX == nextPondMinOffsetX
            && pondMaxOffsetX == nextPondMaxOffsetX
            && pondMinOffsetZ == nextPondMinOffsetZ
            && pondMaxOffsetZ == nextPondMaxOffsetZ
            && java.util.Arrays.equals(pondWaterCells,nextWaterCells)
            && Double.compare(waterSurfaceY, nextWaterSurfaceY) == 0
            && fishSignOffsetX == nextFishSignOffsetX
            && fishSignOffsetY == nextFishSignOffsetY
            && fishSignOffsetZ == nextFishSignOffsetZ
            && Double.compare(requestRenderX, nextRequestRenderX) == 0
            && Double.compare(requestRenderY, nextRequestRenderY) == 0
            && Double.compare(requestRenderZ, nextRequestRenderZ) == 0) {
            return false;
        }

        ready = nextReady;
        cachedOutput = nextOutput.copy();
        cachedRequest = nextRequest.copy();
        cachedFishSign = nextFishSign.copy();
        requestCount = nextRequestCount;
        fishPopulation = nextFishPopulation;
        pondMinOffsetX = nextPondMinOffsetX;
        pondMaxOffsetX = nextPondMaxOffsetX;
        pondMinOffsetZ = nextPondMinOffsetZ;
        pondMaxOffsetZ = nextPondMaxOffsetZ;
        waterSurfaceY = nextWaterSurfaceY;
        pondWaterCells = nextWaterCells;
        fishSignOffsetX = nextFishSignOffsetX;
        fishSignOffsetY = nextFishSignOffsetY;
        fishSignOffsetZ = nextFishSignOffsetZ;
        requestRenderX = nextRequestRenderX;
        requestRenderY = nextRequestRenderY;
        requestRenderZ = nextRequestRenderZ;
        setChanged();
        return true;
    }

    private ItemStack resolveRequestPreview(FishPondRecord pond) {
        if (pond == null || pond.neededItemId().isBlank() || pond.neededItemCount() <= 0 || pond.hasCompletedRequest()) {
            return ItemStack.EMPTY;
        }
        return FishPondQualifiedItemService.resolve(pond.neededItemId())
            .map(FishPondQualifiedItemService.ResolvedItem::item)
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);
    }

    private ItemStack resolveFishSignPreview(FishPondRecord pond) {
        if (pond == null || pond.fishTypeId().isBlank() || pond.currentPopulation() <= 0) {
            return ItemStack.EMPTY;
        }
        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(fishId));
    }

    private BlockPos resolveFishSignAnchor(ServerLevel level, FishPondRecord pond) {
        if (pond == null || pond.waterCells().isEmpty()) {
            return null;
        }

        var building = com.stardew.craft.building.runtime.FishPondPrefabs.at(level,pond.managerPos());
        if (building != null) {
            BlockPos sign = com.stardew.craft.building.runtime.FishPondPrefabs.signPosition(building);
            return level.getBlockState(sign).is(com.stardew.craft.block.ModBlocks.WOOD_SIGN.get()) ? sign : null;
        }
        Set<BlockPos> candidates = new HashSet<>();
        for (Long packedPos : pond.waterCells()) {
            BlockPos waterPos = BlockPos.of(packedPos);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = waterPos.relative(direction);
                if (pond.containsWater(neighborPos)) {
                    continue;
                }
                if (!(level.getBlockEntity(neighborPos) instanceof SignBlockEntity)) {
                    continue;
                }
                candidates.add(neighborPos.immutable());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        double centerX = (pond.minX() + pond.maxX() + 1) * 0.5D;
        double centerY = pond.maxY() + 0.5D;
        double centerZ = (pond.minZ() + pond.maxZ() + 1) * 0.5D;
        return candidates.stream()
            .min(Comparator
                .comparingDouble((BlockPos pos) -> pos.distToCenterSqr(centerX, centerY, centerZ))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ))
            .orElse(null);
    }

    private boolean hasResolvedFishSignPos() {
        return fishSignOffsetX != 0 || fishSignOffsetY != 0 || fishSignOffsetZ != 0;
    }

    private double computeCenterX(FishPondRecord pond) {
        return (pond.minX() + pond.maxX() + 1) * 0.5D - this.worldPosition.getX();
    }

    private double computeCenterY(FishPondRecord pond) {
        return (pond.maxY() - this.worldPosition.getY()) + 1.45D;
    }

    private double computeCenterZ(FishPondRecord pond) {
        return (pond.minZ() + pond.maxZ() + 1) * 0.5D - this.worldPosition.getZ();
    }

    private void syncToClient() {
        var level = this.level;
        if (level == null) {
            return;
        }

        BlockState state = getBlockState();
        level.sendBlockUpdated(this.worldPosition, state, state, 3);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(this.worldPosition);
        }
    }
}