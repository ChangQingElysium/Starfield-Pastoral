package com.stardew.craft.blockentity;

// Upstream-SHA256: dd6e9caee3f5f6fd017997256ee57a9c6ff7ad2c20cda4414079a035c49adfc1

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

public class BaitMakerBlockEntity extends TimedProductionBlockEntity
        implements com.stardew.craft.platform.fabric.item.transfer.TransactionalMachineAccess {
    private final net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
            transactionalAutomation = createTransactionalAutomation(this::planTransactionalInsertion);
    @Override public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
    stardewcraft$automationStorage() { return transactionalAutomation; }

    private java.util.Optional<com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion>
    planTransactionalInsertion(ItemStack stack, boolean collectingOutput,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (level == null || level.isClientSide || stack.isEmpty() || !isFishInput(stack)) return java.util.Optional.empty();
        if (collectingOutput) {
            if (!ready || product.isEmpty()) return java.util.Optional.empty();
        } else if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return java.util.Optional.empty();
        }
        int count = 5 + com.stardew.craft.platform.fabric.item.transfer.TransactionalRandom.nextInt(level.random, 6, transaction);
        ItemStack output = SpecificBaitItem.createForFish(stack, count);
        var plan = prepareProduction(stack, output, MINUTES_UNTIL_READY, null, true);
        if (plan.isEmpty()) return java.util.Optional.empty();
        var prepared = plan.get();
        ItemStack offered = stack.copy();
        return java.util.Optional.of(new com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion(
                stack.copyWithCount(1), 1, () -> isFishInput(offered),
                () -> startWork(offered.copy(), prepared, null)));
    }

    private static final int MINUTES_UNTIL_READY = 10;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";

    public record RemainingTime(int days, int hours, int minutes) {}

    public BaitMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BAIT_MAKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BaitMakerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }
        be.updateWorkingState(level, pos, state);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !input.isEmpty() && !ready && readyAtAbsMinute > 0;
    }

    public boolean canApplyFairyDust() {
        return isWorking();
    }

    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return false;
        }
        readyAtAbsMinute = getCurrentAbsMinute();
        ready = true;
        setChanged();
        syncToClient();
        updateWorkingState(currentLevel, worldPosition, getBlockState());
        return true;
    }

    public ItemStack getInput() {
        return input;
    }

    public ItemStack getProduct() {
        return product;
    }

    public RemainingTime getRemainingTime() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
        return new RemainingTime(days, hours, minutes);
    }

    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return InsertResult.fail();
        }
        if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return InsertResult.fail();
        }
        if (!isFishInput(stack)) {
            return InsertResult.fail();
        }

        ItemStack output = createOutputFromInput(stack);
        if (output.isEmpty()) {
            return InsertResult.fail();
        }

        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }
        startWork(stack, plan.get(), player);
        return InsertResult.success();
    }

    private static boolean isFishInput(ItemStack stack) {
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        return "stardewcraft.type.fish".equals(typeKey)
            || "stardewcraft.type.crabpot".equals(typeKey)
            || "stardewcraft.type.legendary_fish".equals(typeKey);
    }

    private ItemStack createOutputFromInput(ItemStack inputStack) {
        Level currentLevel = level;
        int count = 5;
        if (currentLevel != null) {
            count = 5 + currentLevel.random.nextInt(6);
        }
        return SpecificBaitItem.createForFish(inputStack, count);
    }

    private void startWork(
            ItemStack inputStack,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            Player player
    ) {
        commitProduction(inputStack, plan, 1, player);
    }

    public ItemStack harvestOne() {
        return collectProduction();
    }

    @Override
    public ItemStack getAutomationInput() {
        return input;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0 || !isFishInput(stack)) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        ItemStack inputCopy = stack.copy();
        ItemStack output = createOutputFromInput(inputCopy);
        if (output.isEmpty()) {
            return stack;
        }
        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        startWork(inputCopy, plan.get(), null);
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (!ready || product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = AutomationStackHelper.extractUpTo(product, amount);
        if (simulate) {
            return out;
        }
        if (out.getCount() >= product.getCount()) {
            return harvestOne();
        }
        product.shrink(out.getCount());
        setChanged();
        syncToClient();
        return out;
    }

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.BaitMakerBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.BaitMakerBlock.getExtensionPos(pos, state);
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(state.getBlock()) && extensionState.hasProperty(workingProp)) {
                level.setBlock(extensionPos, extensionState.setValue(workingProp, workingNow), 3);
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, input.save(registries));
        }
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
    }
}
