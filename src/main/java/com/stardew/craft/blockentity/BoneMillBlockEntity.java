package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.AbstractTwoBlockUtilityBlock;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class BoneMillBlockEntity extends TimedProductionBlockEntity implements UtilityMachineInfo, BubbleItemCountProvider {
    private static final int MINUTES_UNTIL_READY = 240;
    private static final int SKELETON_HIT_DELAY_TICKS = 3;
    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String TAG_LOAD_HIT_DELAY = "loadHitDelayTicks";
    private int loadHitDelayTicks = -1;

    public BoneMillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BONE_MILL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoneMillBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickLoadEffects(level, pos);
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

    public boolean tryInsert(ItemStack stack, Player player) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return false;
        }
        int required = requiredInputCount(stack);
        if (required <= 0 || stack.getCount() < required) {
            return false;
        }
        ItemStack output = rollOutput();
        if (output.isEmpty()) {
            return false;
        }
        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
                player, false);
        if (plan.isEmpty()) {
            return false;
        }
        startWork(stack, required, plan.get(), player);
        return true;
    }

    private void startWork(
            ItemStack source,
            int required,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            @Nullable Player player
    ) {
        commitProduction(
                source, plan, required, player);
        playLoadEffects();
    }

    public ItemStack harvestOne() {
        ItemStack out = collectProduction();
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            updateWorkingState(currentLevel, worldPosition, getBlockState());
        }
        return out;
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
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        int required = requiredInputCount(stack);
        if (required <= 0 || stack.getCount() < required) {
            return stack;
        }
        ItemStack output = rollOutput();
        if (output.isEmpty()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, required);
        }
        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        ItemStack inputCopy = stack.copy();
        startWork(
                inputCopy, required, plan.get(), null);
        return AutomationStackHelper.remainderAfterInsert(stack, required);
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

    @Override
    public boolean canApplyFairyDust() {
        return isWorking();
    }

    @Override
    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        readyAtAbsMinute = getCurrentAbsMinute();
        ready = true;
        setChanged();
        syncToClient();
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            updateWorkingState(currentLevel, worldPosition, getBlockState());
        }
        return true;
    }

    @Override
    public String getUtilityTooltipKey() {
        return "bone_mill";
    }

    @Override
    public boolean isReadyForDisplay() {
        return isReady();
    }

    @Override
    public boolean isWorkingForDisplay() {
        return isWorking();
    }

    @Override
    public ItemStack getDisplayInput() {
        return input;
    }

    @Override
    public ItemStack getDisplayOutput() {
        return product;
    }

    @Override
    public int getBubbleItemCount() {
        return product.getCount();
    }

    @Override
    public boolean hasRemainingTimeForDisplay() {
        return isWorking();
    }

    @Override
    public UtilityMachineInfo.RemainingTime getRemainingTimeForDisplay() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
        return new UtilityMachineInfo.RemainingTime(days, hours, minutes);
    }

    private int requiredInputCount(ItemStack stack) {
        if (stack.is(ModItems.BONE_FRAGMENT.get())) {
            return 5;
        }
        return stack.is(ModTags.Items.BONE_ITEMS) ? 1 : 0;
    }

    private ItemStack rollOutput() {
        RandomSource random = level != null ? level.random : RandomSource.create();
        ItemStack out = switch (random.nextInt(4)) {
            case 0 -> new ItemStack(ModItems.DELUXE_SPEED_GRO.get(), 3);
            case 1 -> new ItemStack(ModItems.SPEED_GRO.get(), 5);
            case 2 -> new ItemStack(ModItems.QUALITY_FERTILIZER.get(), 10);
            default -> new ItemStack(ModItems.TREE_FERTILIZER.get(), 5);
        };
        if (random.nextDouble() < 0.1D) {
            out.setCount(out.getCount() * 2);
        }
        return out;
    }

    private void playLoadEffects() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.playSound(null, worldPosition, ModSounds.SKELETON_STEP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        loadHitDelayTicks = SKELETON_HIT_DELAY_TICKS;
    }

    private void tickLoadEffects(Level level, BlockPos pos) {
        if (loadHitDelayTicks < 0) {
            return;
        }
        if (loadHitDelayTicks > 0) {
            loadHitDelayTicks--;
        }
        if (loadHitDelayTicks == 0) {
            level.playSound(null, pos, ModSounds.SKELETON_HIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            loadHitDelayTicks = -1;
            setChanged();
        }
    }

    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        AbstractTwoBlockUtilityBlock.updateWorkingState(level, pos, state, isWorking());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, input.save(registries));
        }
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
        tag.putInt(TAG_LOAD_HIT_DELAY, loadHitDelayTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
        loadHitDelayTicks = tag.contains(TAG_LOAD_HIT_DELAY) ? tag.getInt(TAG_LOAD_HIT_DELAY) : -1;
    }
}
