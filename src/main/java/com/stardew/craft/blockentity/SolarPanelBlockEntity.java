package com.stardew.craft.blockentity;

import com.stardew.craft.production.MachineProductionData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

public class SolarPanelBlockEntity extends BlockEntity implements UtilityAutomationAccess, FairyDustAcceleratable, AdvanceableUtility {
    private static final int EFFECTIVE_MINUTES_PER_DAY = TimedProductionBlockEntity.EFFECTIVE_MINUTES_PER_DAY;

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_REMAINING = "remainingAbsMinutes";
    private static final String TAG_READY = "ready";
    private static final String TAG_LAST_DAY = "lastDayIndex";
    private static final String TAG_PAUSED = "paused";

    private ItemStack product = ItemStack.EMPTY;
    private long remainingAbsMinutes = -1;
    private boolean ready = false;
    private long lastDayIndex = -1;
    private boolean paused = false;
    private boolean minuteMode;
    private long lastAbsMinute = -1;
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    public record RemainingTime(int days, int hours, int minutes) {}

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        if (product.isEmpty()) {
            startCycle(level);
        }

        // 每 20 tick 检查一次天气/天空，无需每 tick
        if (level.getGameTime() % 20 == 0) {
            boolean newPaused = computePaused(level, pos);
            if (newPaused != paused) {
                paused = newPaused;
                setChanged();
                syncToClient();
            }
        }

        long currentDayIndex = getCurrentDayIndex();
        if (lastDayIndex < 0) {
            lastDayIndex = currentDayIndex;
        }

        if (minuteMode) {
            long now = TimedProductionBlockEntity.getCurrentAbsMinute();
            if (lastAbsMinute < 0) lastAbsMinute = now;
            long elapsed = Math.max(0, now - lastAbsMinute);
            if (elapsed > 0) {
                lastAbsMinute = now;
                if (!paused && !product.isEmpty() && !ready) {
                    remainingAbsMinutes = Math.max(0, remainingAbsMinutes - elapsed);
                    ready = remainingAbsMinutes == 0;
                    syncToClient();
                }
                setChanged();
            }
        } else if (currentDayIndex != lastDayIndex) {
            long deltaDays = Math.max(0, currentDayIndex - lastDayIndex);
            lastDayIndex = currentDayIndex;

            if (!paused && !product.isEmpty() && !ready && remainingAbsMinutes > 0) {
                remainingAbsMinutes = Math.max(0, remainingAbsMinutes - deltaDays * (long) EFFECTIVE_MINUTES_PER_DAY);
                boolean newReady = remainingAbsMinutes == 0;
                if (newReady != ready) {
                    ready = newReady;
                    setChanged();
                    syncToClient();
                }
            }
        }

        updateWorkingState(level, pos, state);
    }

    @SuppressWarnings("null")
    private void startCycle(Level level) {
        var cycle = MachineProductionData.cycle("solar_panel", "default");
        var profile = MachineProductionData.profile("solar_panel");
        product = cycle.createOutput(level.random);
        remainingAbsMinutes = MachineProductionData.minutes("solar_panel",
            cycle.mornings() ? cycle.duration() * EFFECTIVE_MINUTES_PER_DAY : cycle.duration());
        minuteMode = !cycle.mornings() || profile.minutes() != null || profile.multiplier() != 1;
        lastAbsMinute = TimedProductionBlockEntity.getCurrentAbsMinute();
        ready = false;
        lastDayIndex = getCurrentDayIndex();
        paused = computePaused(level, worldPosition);
        setChanged();
        syncToClient();
    }

    @SuppressWarnings("null")
    private boolean computePaused(Level level, BlockPos pos) {
        BlockState state = getBlockState();
        if (!state.hasProperty(com.stardew.craft.block.utility.SolarPanelBlock.FACING)) {
            BlockPos above = pos.above();
            boolean openSky = level.canSeeSky(above);
            boolean raining = com.stardew.craft.weather.WeatherManager.isRaining(level);
            return !openSky || raining;
        }

        BlockPos mainPos = com.stardew.craft.block.utility.SolarPanelBlock.getMainPos(pos, state);
        BlockPos extPos = com.stardew.craft.block.utility.SolarPanelBlock.getExtensionPos(mainPos, state);
        BlockPos mainAbove = mainPos.above();
        BlockPos extAbove = extPos.above();

        boolean openSky = level.canSeeSky(mainAbove) && level.canSeeSky(extAbove);
        boolean raining = com.stardew.craft.weather.WeatherManager.isRaining(level);
        return !openSky || raining;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !product.isEmpty() && !ready && remainingAbsMinutes > 0 && !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public ItemStack getProduct() {
        return product;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    public RemainingTime getRemainingTime() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
        return new RemainingTime(days, hours, minutes);
    }

    public long getRemainingAbsMinutes() {
        if (product.isEmpty() || ready || remainingAbsMinutes < 0) {
            return 0;
        }
        return Math.max(0, remainingAbsMinutes);
    }

    public ItemStack harvestOne() {
        if (!ready) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        remainingAbsMinutes = -1;
        ready = false;
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return stack;
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

    public boolean canApplyFairyDust() {
        return !product.isEmpty() && !ready;
    }

    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        remainingAbsMinutes = 0;
        ready = true;
        setChanged();
        syncToClient();
        return true;
    }

    @Override
    public void advanceDays(int days) {
        if (days <= 0) {
            return;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        if (product.isEmpty() || remainingAbsMinutes < 0) {
            return;
        }
        long delta = (long) days * (long) EFFECTIVE_MINUTES_PER_DAY;
        remainingAbsMinutes = Math.max(0, remainingAbsMinutes - delta);
        ready = remainingAbsMinutes == 0;
        setChanged();
        syncToClient();
    }

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.SolarPanelBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
        }
    }

    @SuppressWarnings("null")
    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static long getCurrentDayIndex() {
        StardewTimeManager tm = StardewTimeManager.get();
        int year = tm.getCurrentYear();
        int season = tm.getCurrentSeason();
        int day = tm.getCurrentDay();
        return (long) (year - 1) * 112L + (long) season * 28L + (long) day;
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
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putLong(TAG_REMAINING, remainingAbsMinutes);
        tag.putBoolean(TAG_READY, ready);
        tag.putLong(TAG_LAST_DAY, lastDayIndex);
        tag.putBoolean(TAG_PAUSED, paused);
        tag.putBoolean("minuteMode", minuteMode);
        tag.putLong("lastAbsMinute", lastAbsMinute);
        tag.putInt(TimedProductionBlockEntity.TAG_MACHINE_CLOCK, EFFECTIVE_MINUTES_PER_DAY);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_PRODUCT)) {
            product = ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY);
        } else {
            product = ItemStack.EMPTY;
        }
        remainingAbsMinutes = tag.getLong(TAG_REMAINING);
        ready = tag.getBoolean(TAG_READY);
        if (tag.contains(TAG_LAST_DAY)) {
            lastDayIndex = tag.getLong(TAG_LAST_DAY);
        } else {
            lastDayIndex = -1;
        }
        paused = tag.getBoolean(TAG_PAUSED);
        minuteMode = tag.getBoolean("minuteMode");
        lastAbsMinute = tag.contains("lastAbsMinute") ? tag.getLong("lastAbsMinute") : -1;
        if (!tag.contains(TimedProductionBlockEntity.TAG_MACHINE_CLOCK)) {
            // Pre-F-1 charge was counted on the 1260-minute day; keep the same share of days left.
            if (remainingAbsMinutes > 0) {
                remainingAbsMinutes = Math.ceilDiv(remainingAbsMinutes * EFFECTIVE_MINUTES_PER_DAY,
                        (long) TimedProductionBlockEntity.LEGACY_MINUTES_PER_DAY);
            }
            lastAbsMinute = TimedProductionBlockEntity.migrateLegacyAbsMinute(lastAbsMinute);
        }
    }
}
