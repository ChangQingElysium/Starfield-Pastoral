package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.machine.StardewTimedProduction;
import com.stardew.craft.api.v1.machine.StardewMachineCycleContext;
import com.stardew.craft.api.v1.machine.StardewMachineCycleEvent;
import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewProductionContext;
import com.stardew.craft.api.v1.machine.StardewProductionEvent;
import com.stardew.craft.api.v1.machine.StardewProductionPhase;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import com.stardew.craft.api.v1.machine.StardewProductionPlans;
import com.stardew.craft.api.v1.internal.machine.StardewProductionEventRegistry;
import com.stardew.craft.api.v1.internal.machine.StardewMachineCycleRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

public abstract class TimedProductionBlockEntity extends BlockEntity implements UtilityAutomationAccess, FairyDustAcceleratable, AdvanceableUtility, StardewTimedProduction {
    /**
     * Machine minutes per calendar day: 1200 daytime minutes (6:00-26:00) plus the
     * overnight top-up of Utility.CalculateMinutesUntilMorning, so every day totals 1600
     * regardless of bedtime.
     */
    public static final int EFFECTIVE_MINUTES_PER_DAY = 1600;
    /** Day length of the pre-F-1 machine clock; only used to migrate saved deadlines. */
    public static final int LEGACY_MINUTES_PER_DAY = 1260;
    /** Marks machine NBT whose absolute minutes already use the 1600-minute clock. */
    public static final String TAG_MACHINE_CLOCK = "stardewcraftMachineClock";
    private static final int DAYTIME_MINUTES = 1200;

    private boolean legacyClockPending;

    protected ItemStack input = ItemStack.EMPTY;
    protected ItemStack product = ItemStack.EMPTY;
    protected long readyAtAbsMinute = -1;
    protected boolean ready = false;
    private StardewMachineCycleKind cycleKind =
            StardewMachineCycleKind.BATCH;
    private boolean cycleAutomation;

    private long lastReadyCheckAbsMinute = Long.MIN_VALUE;
    private long lastReadyCheckReadyAt = Long.MIN_VALUE;
    private boolean lastReadyCheckHasProduct = false;
    private boolean lastReadyCheckResult = false;
    private long readyEventEmittedAt =
            Long.MIN_VALUE;

    protected final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    protected TimedProductionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected boolean readyCheckRequiresProduct() {
        return true;
    }

    protected StardewMachineCycleKind defaultCycleKind() {
        return StardewMachineCycleKind.BATCH;
    }

    protected boolean refreshReady() {
        ensureMachineClock();
        if (readyAtAbsMinute < 0) {
            return false;
        }
        if (ready) {
            if (readyEventEmittedAt
                    != readyAtAbsMinute) {
                readyEventEmittedAt =
                        readyAtAbsMinute;
                emitProductionEvent(
                        StardewProductionPhase.READY);
            }
            return true;
        }
        if (readyCheckRequiresProduct() && product.isEmpty()) {
            return false;
        }
        boolean result = computeReady();
        if (result
                && readyEventEmittedAt
                != readyAtAbsMinute) {
            readyEventEmittedAt =
                    readyAtAbsMinute;
            emitProductionEvent(
                    StardewProductionPhase.READY);
        }
        return result;
    }

    protected boolean hasReadyPayload() {
        return readyCheckRequiresProduct() ? !product.isEmpty() : !input.isEmpty();
    }

    protected boolean computeReady() {
        boolean hasPayload = hasReadyPayload();
        if (!hasPayload || readyAtAbsMinute < 0) {
            lastReadyCheckAbsMinute = Long.MIN_VALUE;
            lastReadyCheckReadyAt = readyAtAbsMinute;
            lastReadyCheckHasProduct = hasPayload;
            lastReadyCheckResult = false;
            return false;
        }
        long currentAbsMinute = getCurrentAbsMinute();
        if (currentAbsMinute == lastReadyCheckAbsMinute
                && readyAtAbsMinute == lastReadyCheckReadyAt
                && lastReadyCheckHasProduct == hasPayload) {
            return lastReadyCheckResult;
        }
        boolean result = currentAbsMinute >= readyAtAbsMinute;
        lastReadyCheckAbsMinute = currentAbsMinute;
        lastReadyCheckReadyAt = readyAtAbsMinute;
        lastReadyCheckHasProduct = hasPayload;
        lastReadyCheckResult = result;
        return result;
    }

    public long getRemainingAbsMinutes() {
        ensureMachineClock();
        if (!hasReadyPayload() || readyAtAbsMinute < 0) {
            return 0;
        }
        return Math.max(0, readyAtAbsMinute - getCurrentAbsMinute());
    }

    @Override
    public final ItemStack stardewInput() {
        return input.copy();
    }

    @Override
    public final ItemStack stardewOutput() {
        return product.copy();
    }

    @Override
    public final long stardewReadyAtAbsoluteMinute() {
        ensureMachineClock();
        return readyAtAbsMinute;
    }

    @Override
    public final long stardewRemainingMinutes() {
        return getRemainingAbsMinutes();
    }

    @Override
    public final boolean stardewIsReady() {
        return refreshReady();
    }

    @Override
    public final StardewMachineCycleKind stardewCycleKind() {
        StardewMachineCycleKind inherent = defaultCycleKind();
        return cycleKind == StardewMachineCycleKind.BATCH
                && inherent != StardewMachineCycleKind.BATCH
                ? inherent : cycleKind;
    }

    @Override
    public final boolean stardewAutomationStarted() {
        return cycleAutomation;
    }

    @Override
    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Override
    public boolean canApplyFairyDust() {
        return false;
    }

    @Override
    public boolean applyFairyDust() {
        return false;
    }

    /**
     * Debug/utility: advance the current production timer by N days.
     */
    @Override
    @SuppressWarnings("null")
    public void advanceDays(int days) {
        if (days <= 0) {
            return;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        ensureMachineClock();
        if (!hasReadyPayload() || readyAtAbsMinute < 0) {
            return;
        }
        long delta = (long) days * (long) EFFECTIVE_MINUTES_PER_DAY;
        readyAtAbsMinute = Math.max(0, readyAtAbsMinute - delta);
        ready = computeReady();
        setChanged();
        syncToClient();
    }

    @SuppressWarnings("null")
    protected void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Current machine minute: 1600 per day, with 6:00 of day N at (N - 1) * 1600.
     * Sleeping jumps to the next multiple, matching Utility.CalculateMinutesUntilMorning.
     */
    public static long getCurrentAbsMinute() {
        StardewTimeManager tm = StardewTimeManager.get();
        int currentTime = tm.getCurrentTime();
        int effectiveMinuteOfDay;
        if (currentTime >= StardewTimeManager.MORNING_START) {
            effectiveMinuteOfDay = currentTime - StardewTimeManager.MORNING_START;
        } else {
            effectiveMinuteOfDay = 0;
        }
        return (getCurrentDayIndex() - 1) * EFFECTIVE_MINUTES_PER_DAY + (long) effectiveMinuteOfDay;
    }

    protected static long getCurrentDayIndex() {
        StardewTimeManager tm = StardewTimeManager.get();
        int year = tm.getCurrentYear();
        int season = tm.getCurrentSeason();
        int day = tm.getCurrentDay();
        return (long) (year - 1) * 112L + (long) season * 28L + (long) day;
    }

    /** Utility.CalculateMinutesUntilMorning(timeOfDay, days) on the machine clock. */
    public static int minutesUntilMorning(long nowAbsMinute, int days) {
        if (days < 1) {
            return 0;
        }
        long morning = (Math.floorDiv(nowAbsMinute, EFFECTIVE_MINUTES_PER_DAY) + days)
                * (long) EFFECTIVE_MINUTES_PER_DAY;
        return Math.toIntExact(morning - nowAbsMinute);
    }

    /** Recipe duration: DaysUntilReady recipes finish on the Nth morning, others after their minutes. */
    protected static int recipeMinutes(com.stardew.craft.item.artisan.ArtisanRecipeDataManager.Recipe recipe) {
        return recipe.days() > 0
                ? minutesUntilMorning(getCurrentAbsMinute(), recipe.days())
                : recipe.minutes();
    }

    /**
     * Maps an absolute minute of the pre-F-1 1260-minute clock to the same day and
     * clock time on the 1600-minute clock. Daytime minutes keep their offset; the old
     * 60-minute overnight slot is stretched over the 400-minute overnight slot. Ready
     * state and completion moment are therefore unchanged by the migration.
     */
    public static long migrateLegacyAbsMinute(long legacyAbsMinute) {
        if (legacyAbsMinute < 0) {
            return legacyAbsMinute;
        }
        long day = legacyAbsMinute / LEGACY_MINUTES_PER_DAY;
        long offset = legacyAbsMinute % LEGACY_MINUTES_PER_DAY;
        if (offset > DAYTIME_MINUTES) {
            int legacyNight = LEGACY_MINUTES_PER_DAY - DAYTIME_MINUTES;
            int night = EFFECTIVE_MINUTES_PER_DAY - DAYTIME_MINUTES;
            offset = DAYTIME_MINUTES + (offset - DAYTIME_MINUTES) * night / legacyNight;
        }
        return day * EFFECTIVE_MINUTES_PER_DAY + offset;
    }

    /** Subclasses whose saved minutes never used the 1260-minute clock opt out. */
    protected boolean migratesLegacyMachineClock() {
        return true;
    }

    /**
     * Applies the one-time 1260 -> 1600 migration after the subclass has read its NBT.
     * The next save writes the converted deadline with the clock marker; until then the
     * stored NBT is untouched, so a reload converts the same original value again.
     */
    protected final void ensureMachineClock() {
        if (!legacyClockPending) {
            return;
        }
        legacyClockPending = false;
        readyAtAbsMinute = migrateLegacyAbsMinute(readyAtAbsMinute);
        lastReadyCheckAbsMinute = Long.MIN_VALUE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureMachineClock();
    }

    /**
     * Resolves addon transforms before callers consume inputs or auxiliary fuel.
     */
    protected final Optional<StardewProductionPlan>
    prepareProduction(
            ItemStack sourceInput,
            ItemStack proposedOutput,
            int proposedMinutes,
            Player player,
            boolean automation
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || sourceInput.isEmpty()
                || proposedOutput.isEmpty()) {
            return Optional.empty();
        }
        var machineId =
                BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getKey(getType());
        if (machineId == null) {
            return Optional.empty();
        }
        Optional<ServerPlayer> serverPlayer =
                player instanceof ServerPlayer value
                        ? Optional.of(value)
                        : Optional.empty();
        Optional<StardewProductionPlan> legacy =
                StardewProductionPlans.resolve(
                new StardewProductionContext(
                        machineId, serverLevel, worldPosition,
                        sourceInput, serverPlayer, automation),
                new StardewProductionPlan(
                        proposedOutput, proposedMinutes));
        return legacy.flatMap(plan -> prepareMachineCycle(
                StardewMachineCycleKind.BATCH,
                sourceInput,
                plan.output(),
                plan.minutes(),
                player,
                automation));
    }

    /**
     * Resolves the general cycle pipeline. Empty input is valid for passive
     * and environmental machines.
     */
    protected final Optional<StardewProductionPlan>
    prepareMachineCycle(
            StardewMachineCycleKind kind,
            ItemStack sourceInput,
            ItemStack proposedOutput,
            int proposedMinutes,
            Player player,
            boolean automation
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || sourceInput == null
                || proposedOutput.isEmpty()) {
            return Optional.empty();
        }
        var machineId =
                BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getKey(getType());
        if (machineId == null) {
            return Optional.empty();
        }
        Optional<ServerPlayer> serverPlayer =
                player instanceof ServerPlayer value
                        ? Optional.of(value)
                        : Optional.empty();
        return StardewMachineCycleRegistry.resolve(
                new StardewMachineCycleContext(
                        machineId, serverLevel, worldPosition,
                        kind, sourceInput, serverPlayer,
                        automation),
                new StardewProductionPlan(
                        proposedOutput, proposedMinutes));
    }

    /** Commits an already prepared plan and consumes the declared primary input count. */
    protected final void commitProduction(
            ItemStack sourceInput,
            StardewProductionPlan plan,
            int consumeCount,
            Player player
    ) {
        commitMachineCycle(
                sourceInput, plan, consumeCount, player,
                StardewMachineCycleKind.BATCH,
                player == null);
    }

    /** Commits a prepared cycle and consumes its declared primary input. */
    protected final void commitMachineCycle(
            ItemStack sourceInput,
            StardewProductionPlan plan,
            int consumeCount,
            Player player,
            StardewMachineCycleKind kind,
            boolean automation
    ) {
        if (consumeCount <= 0
                || sourceInput.getCount() < consumeCount) {
            throw new IllegalArgumentException(
                    "invalid production input consumption");
        }
        ItemStack trackedInput = sourceInput.copy();
        trackedInput.setCount(Math.min(
                consumeCount, sourceInput.getMaxStackSize()));
        beginMachineCycle(
                trackedInput, plan, kind, automation);
        if (player == null || !player.isCreative()) {
            sourceInput.shrink(consumeCount);
        }
    }

    /** Starts the next cycle while retaining the current catalyst/input. */
    protected final void restartMachineCycle(
            StardewProductionPlan plan,
            StardewMachineCycleKind kind,
            boolean automation
    ) {
        beginMachineCycle(input, plan, kind, automation);
    }

    private void beginMachineCycle(
            ItemStack trackedInput,
            StardewProductionPlan plan,
            StardewMachineCycleKind kind,
            boolean automation
    ) {
        legacyClockPending = false;
        input = trackedInput.copy();
        product = plan.output();
        readyAtAbsMinute =
                getCurrentAbsMinute() + plan.minutes();
        ready = false;
        readyEventEmittedAt = Long.MIN_VALUE;
        cycleKind = kind;
        cycleAutomation = automation;
        setChanged();
        syncToClient();
        emitProductionEvent(StardewProductionPhase.STARTED);
    }

    /** Collects the complete output and emits the common lifecycle event before clearing state. */
    protected final ItemStack collectProduction() {
        if (!refreshReady() || product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = product.copy();
        emitProductionEvent(
                StardewProductionPhase.COLLECTED);
        product = ItemStack.EMPTY;
        input = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        readyEventEmittedAt = Long.MIN_VALUE;
        setChanged();
        syncToClient();
        return result;
    }

    protected final void emitProductionEvent(
            StardewProductionPhase phase
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var machineId =
                BuiltInRegistries.BLOCK_ENTITY_TYPE
                        .getKey(getType());
        if (machineId == null) {
            return;
        }
        if (cycleKind == StardewMachineCycleKind.BATCH) {
            StardewProductionEventRegistry.dispatch(
                    new StardewProductionEvent(
                            phase, machineId, serverLevel,
                            worldPosition, input, product,
                            readyAtAbsMinute));
        }
        StardewMachineCycleRegistry.dispatch(
                new StardewMachineCycleEvent(
                        phase, cycleKind, machineId, serverLevel,
                        worldPosition, input, product,
                        readyAtAbsMinute, cycleAutomation));
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);
        // Subclasses write their minutes after this call, so convert before they do.
        ensureMachineClock();
        tag.putInt(TAG_MACHINE_CLOCK, EFFECTIVE_MINUTES_PER_DAY);
        tag.putString("stardewcraftCycleKind",
                cycleKind.name());
        tag.putBoolean("stardewcraftCycleAutomation",
                cycleAutomation);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);
        // Machines saved before F-1 carry 1260-minute deadlines; subclasses read them
        // after this call, so the conversion runs on first use (onLoad at the latest).
        legacyClockPending = migratesLegacyMachineClock()
                && !tag.contains(TAG_MACHINE_CLOCK);
        cycleKind = defaultCycleKind();
        if (tag.contains("stardewcraftCycleKind")) {
            try {
                cycleKind = StardewMachineCycleKind.valueOf(
                        tag.getString("stardewcraftCycleKind"));
            } catch (IllegalArgumentException ignored) {
                cycleKind = defaultCycleKind();
            }
        }
        cycleAutomation = tag.getBoolean(
                "stardewcraftCycleAutomation");
    }
}
