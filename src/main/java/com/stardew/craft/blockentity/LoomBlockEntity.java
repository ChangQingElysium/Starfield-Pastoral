package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.LoomBlock;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Loom block entity.
 */
public class LoomBlockEntity extends TimedProductionBlockEntity {
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;
    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {}

    public LoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOM.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LoomBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }
        be.updateReadyState(level, pos, state);
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
        updateReadyState(currentLevel, worldPosition, getBlockState());
        return true;
    }

    public boolean hasInput() {
        return !input.isEmpty();
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
    public boolean tryInsert(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return false;
        }

        var recipeOpt = ArtisanRecipeDataManager.getRecipe("loom", stack);
        if (recipeOpt.isEmpty()) {
            return false;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        int outputCount = recipe.outputCount();
        if (outputCount <= 1) {
            outputCount = rollOutputCount(QualityHelper.getQuality(stack));
        }
        ItemStack output = createOutputFromRecipe(recipe, stack, outputCount);
        if (output.isEmpty()) {
            return false;
        }
        var plan = prepareProduction(
                stack, output, recipe.minutes(),
                player, false);
        if (plan.isEmpty()) {
            return false;
        }
        startWork(stack, plan.get(), player);
        return true;
    }

    @SuppressWarnings("null")
    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack input, int outputCount) {
        ItemStack output = new ItemStack(BuiltInRegistries.ITEM.get(recipe.outputId()), outputCount);
        if (recipe.keepInputQuality()) {
            QualityHelper.setQuality(output, QualityHelper.getQuality(input));
        } else if (recipe.outputQuality() >= 0) {
            QualityHelper.setQuality(output, recipe.outputQuality());
        }
        return output;
    }

    private int rollOutputCount(int quality) {
        float chance = switch (quality) {
            case QualityHelper.SILVER -> 0.15f;
            case QualityHelper.GOLD -> 0.50f;
            case QualityHelper.IRIDIUM -> 1.0f;
            default -> 0.0f;
        };
        if (chance <= 0.0f) {
            return 1;
        }
        Level currentLevel = level;
        RandomSource random = currentLevel != null ? currentLevel.random : RandomSource.create();
        return random.nextFloat() < chance ? 2 : 1;
    }

    private void startWork(
            ItemStack inputStack,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            Player player
    ) {
        commitProduction(inputStack, plan, 1, player);
        if (level != null) {
            updateReadyState(level, worldPosition, getBlockState());
        }
    }

    public ItemStack harvestOne() {
        ItemStack out = collectProduction();
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (level != null) {
            updateReadyState(level, worldPosition, getBlockState());
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
    @SuppressWarnings("null")
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("loom", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        int outputCount = recipe.outputCount();
        if (outputCount <= 1) {
            outputCount = rollOutputCount(QualityHelper.getQuality(stack));
        }
        ItemStack output = createOutputFromRecipe(recipe, stack, outputCount);
        if (output.isEmpty()) {
            return stack;
        }
        var plan = prepareProduction(
                stack, output, recipe.minutes(),
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        ItemStack inputCopy = stack.copy();
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
        if (level != null) {
            updateReadyState(level, worldPosition, getBlockState());
        }
        return out;
    }

    /**
     * Debug/utility: advance the current production timer by N days.
     */
    @SuppressWarnings("null")
    public void advanceDays(int days) {
        super.advanceDays(days);
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            updateReadyState(currentLevel, worldPosition, getBlockState());
        }
    }

    @SuppressWarnings("null")
    private void updateReadyState(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(LoomBlock.READY)) {
            return;
        }
        if (state.getValue(LoomBlock.READY) != ready) {
            level.setBlock(pos, state.setValue(LoomBlock.READY, ready), 3);
        }
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
