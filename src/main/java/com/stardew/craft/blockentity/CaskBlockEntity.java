package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.internal.machine.StardewArtisanResolverRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.FlavoredArtisanDrinkItem;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Cask block entity.
 * Ages artisan goods to higher quality over time.
 */
public class CaskBlockEntity extends BlockEntity implements UtilityAutomationAccess, FairyDustAcceleratable, AdvanceableUtility {
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;
    private static final float DAYS_NORMAL = 56f;
    private static final float DAYS_SILVER = 42f;
    private static final float DAYS_GOLD = 28f;
    private static final float DAYS_IRIDIUM = 0f;

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY = "ready";
    private static final String TAG_DAYS_TO_MATURE = "daysToMature";
    private static final String TAG_AGING_RATE = "agingRate";
    private static final String TAG_LAST_CHECK_DAY = "lastCheckDay";

    private ItemStack product = ItemStack.EMPTY;
    private boolean ready = false;
    private float daysToMature = -1f;
    private float agingRate = 1f;
    private int lastCheckDay = -1;
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    private static final Map<Item, Float> AGING_RATES = Map.ofEntries(
        Map.entry(ModItems.CHEESE.get(), 4f),
        Map.entry(ModItems.GOAT_CHEESE.get(), 4f),
        Map.entry(ModItems.MEAD.get(), 2f),
        Map.entry(ModItems.BEER.get(), 2f),
        Map.entry(ModItems.PALE_ALE.get(), 1.66f),
        Map.entry(ModItems.WINE.get(), 1f)
    );

    public record RemainingTime(int days, int hours, int minutes) {}

    public CaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASK.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CaskBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickServer(level, pos, state);
    }

    private void tickServer(Level level, BlockPos pos, BlockState state) {
        int currentDay = getCurrentDayIndex();
        if (lastCheckDay < 0) {
            lastCheckDay = currentDay;
        }
        if (currentDay > lastCheckDay) {
            int delta = currentDay - lastCheckDay;
            advanceDays(delta);
            lastCheckDay = currentDay;
        }
        updateWorkingState(level, pos, state);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !product.isEmpty() && daysToMature > 0f && QualityHelper.getQuality(product) < QualityHelper.IRIDIUM;
    }

    public boolean canApplyFairyDust() {
        return !product.isEmpty() && daysToMature >= 0f && QualityHelper.getQuality(product) < QualityHelper.IRIDIUM;
    }

    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return false;
        }
        int quality = QualityHelper.getQuality(product);
        int nextQuality = getNextQuality(quality);
        if (nextQuality == quality) {
            return false;
        }
        daysToMature = getDaysForQuality(nextQuality);
        checkForMaturity();
        updateWorkingState(currentLevel, worldPosition, getBlockState());
        return true;
    }

    public boolean hasProduct() {
        return !product.isEmpty();
    }

    public ItemStack getProduct() {
        return product;
    }

    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    public RemainingTime getRemainingTime() {
        float remainingDays = getRemainingDaysToNextQuality();
        int days = (int) Math.floor(remainingDays);
        // Stardew day = 21 effective hours (6:00-27:00 = 1260 minutes)
        float effectiveHoursPerDay = (float) EFFECTIVE_MINUTES_PER_DAY / StardewTimeManager.MINUTES_PER_HOUR;
        float fractionalHours = (remainingDays - days) * effectiveHoursPerDay;
        int hours = (int) Math.floor(fractionalHours);
        int minutes = (int) Math.floor((fractionalHours - hours) * StardewTimeManager.MINUTES_PER_HOUR);
        return new RemainingTime(days, hours, minutes);
    }

    public float getRemainingDaysToNextQuality() {
        if (product.isEmpty() || daysToMature < 0f) {
            return 0f;
        }
        int quality = QualityHelper.getQuality(product);
        int nextQuality = getNextQuality(quality);
        if (nextQuality == quality) {
            return 0f;
        }
        float nextThreshold = getDaysForQuality(nextQuality);
        return Math.max(0f, daysToMature - nextThreshold);
    }

    @SuppressWarnings("null")
    public boolean tryInsert(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!product.isEmpty()) {
            return false;
        }

        Float rate = resolveAgingRate(
                stack, AGING_RATES.get(stack.getItem()));
        if (rate == null) {
            return false;
        }

        int quality = QualityHelper.getQuality(stack);
        if (quality >= QualityHelper.IRIDIUM) {
            return false;
        }

        ItemStack output = stack.copy();
        output.setCount(1);
        QualityHelper.setQuality(output, quality);
        QualityHelper.ensureQualityModelData(output);

        product = output;
        ready = quality >= QualityHelper.SILVER;
        agingRate = rate;
        daysToMature = getDaysForQuality(quality);
        lastCheckDay = getCurrentDayIndex();

        if (player == null || !player.isCreative()) {
            stack.shrink(1);
        }
        setChanged();
        syncToClient();
        return true;
    }

    public ItemStack harvestOne() {
        if (!isReady()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        ready = false;
        daysToMature = -1f;
        agingRate = 1f;
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public ItemStack getAutomationInput() {
        return !product.isEmpty() && !ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty()) {
            return stack;
        }
        Float rate = resolveAgingRate(
                stack, AGING_RATES.get(stack.getItem()));
        if (rate == null) {
            return stack;
        }
        int quality = QualityHelper.getQuality(stack);
        if (quality >= QualityHelper.IRIDIUM) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        ItemStack output = stack.copy();
        output.setCount(1);
        QualityHelper.setQuality(output, quality);
        QualityHelper.ensureQualityModelData(output);
        product = output;
        ready = quality >= QualityHelper.SILVER;
        agingRate = rate;
        daysToMature = getDaysForQuality(quality);
        lastCheckDay = getCurrentDayIndex();
        setChanged();
        syncToClient();
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }

    @Nullable
    static Float resolveAgingRate(ItemStack stack) {
        return resolveAgingRate(
                stack, AGING_RATES.get(stack.getItem()));
    }

    @Nullable
    private static Float resolveAgingRate(
            ItemStack stack,
            @Nullable Float legacyConfiguredRate
    ) {
        if (stack.getItem() instanceof FlavoredArtisanDrinkItem drink
                && drink.getFlavorType() == PreserveType.WINE) {
            return 1f;
        }
        return legacyConfiguredRate != null
                ? legacyConfiguredRate
                : StardewArtisanResolverRegistry.resolveCaskAgingRate(stack);
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

    /**
     * Debug/utility: advance the current aging timer by N days.
     */
    @SuppressWarnings("null")
    @Override
    public void advanceDays(int days) {
        if (days <= 0) {
            return;
        }
        if (level == null || level.isClientSide) {
            return;
        }
        if (product.isEmpty() || daysToMature < 0f) {
            return;
        }
        daysToMature -= agingRate * days;
        checkForMaturity();
        setChanged();
        syncToClient();
    }

    private void checkForMaturity() {
        if (product.isEmpty()) {
            ready = false;
            return;
        }
        int quality = QualityHelper.getQuality(product);
        boolean updated = false;

        while (quality < QualityHelper.IRIDIUM) {
            int nextQuality = getNextQuality(quality);
            float threshold = getDaysForQuality(nextQuality);
            if (daysToMature > threshold) {
                break;
            }
            quality = nextQuality;
            QualityHelper.setQuality(product, quality);
            QualityHelper.ensureQualityModelData(product);
            updated = true;
            if (quality >= QualityHelper.IRIDIUM) {
                daysToMature = 0f;
                break;
            }
        }

        boolean wasReady = ready;
        ready = quality >= QualityHelper.SILVER;

        if (updated || wasReady != ready) {
            setChanged();
            syncToClient();
        }
    }

    private static float getDaysForQuality(int quality) {
        return switch (quality) {
            case QualityHelper.SILVER -> DAYS_SILVER;
            case QualityHelper.GOLD -> DAYS_GOLD;
            case QualityHelper.IRIDIUM -> DAYS_IRIDIUM;
            default -> DAYS_NORMAL;
        };
    }

    private static int getNextQuality(int quality) {
        return switch (quality) {
            case QualityHelper.SILVER -> QualityHelper.GOLD;
            case QualityHelper.GOLD -> QualityHelper.IRIDIUM;
            case QualityHelper.IRIDIUM -> QualityHelper.IRIDIUM;
            default -> QualityHelper.SILVER;
        };
    }

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.CaskBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
        }
    }

    @SuppressWarnings("null")
    private void syncToClient() {
        if (level == null || level.isClientSide) {
            return;
        }
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static int getCurrentDayIndex() {
        StardewTimeManager tm = StardewTimeManager.get();
        int year = tm.getCurrentYear();
        int season = tm.getCurrentSeason();
        int day = tm.getCurrentDay();
        return (year - 1) * 112 + season * 28 + day;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putBoolean(TAG_READY, ready);
        tag.putFloat(TAG_DAYS_TO_MATURE, daysToMature);
        tag.putFloat(TAG_AGING_RATE, agingRate);
        tag.putInt(TAG_LAST_CHECK_DAY, lastCheckDay);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ready = false;
        daysToMature = tag.contains(TAG_DAYS_TO_MATURE) ? tag.getFloat(TAG_DAYS_TO_MATURE) : -1f;
        agingRate = tag.contains(TAG_AGING_RATE) ? tag.getFloat(TAG_AGING_RATE) : 1f;
        lastCheckDay = tag.contains(TAG_LAST_CHECK_DAY) ? tag.getInt(TAG_LAST_CHECK_DAY) : -1;
        if (!product.isEmpty()) {
            int quality = QualityHelper.getQuality(product);
            ready = quality >= QualityHelper.SILVER;
            if (quality >= QualityHelper.IRIDIUM) {
                daysToMature = 0f;
            }
        } else {
            ready = false;
        }
    }

    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
