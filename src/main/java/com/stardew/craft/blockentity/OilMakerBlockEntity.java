package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Oil Maker block entity.
 */
public class OilMakerBlockEntity extends TimedProductionBlockEntity {
    private long lastEffectMinuteBucket = Long.MIN_VALUE;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {}

    @SuppressWarnings("null")
    public OilMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_MAKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OilMakerBlockEntity be) {
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
        if (level instanceof ServerLevel serverLevel) {
            emitWorkingEffect(serverLevel, pos, be);
        }
    }

    @SuppressWarnings("null")
    private static void emitWorkingEffect(ServerLevel level, BlockPos pos, OilMakerBlockEntity be) {
        if (!be.isWorking()) {
            be.lastEffectMinuteBucket = Long.MIN_VALUE;
            return;
        }
        long bucket = getCurrentAbsMinute() / 10;
        if (be.lastEffectMinuteBucket == bucket) {
            return;
        }
        be.lastEffectMinuteBucket = bucket;
        // The original checks 0.33 once per ten game minutes, not every few render ticks.
        if (level.random.nextFloat() < 0.33F) {
            level.sendParticles(ModParticles.OIL_BUBBLE.get(),
                    pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, 0, 0, 0, 0, 0);
        }
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

    public boolean tryInsert(ItemStack stack, Player player) {
        return tryInsertWithResult(stack, player).inserted();
    }

    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return InsertResult.fail();
        }
        if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return InsertResult.fail();
        }

        Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("oil_maker", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        Item outputItem = BuiltInRegistries.ITEM.get(recipe.outputId());
        ItemStack output = new ItemStack(outputItem, recipe.rollOutputCount(level.random));
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }
        startWork(stack, plan.get(), player);
        return InsertResult.success();
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
    @SuppressWarnings("null")
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }

        Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("oil_maker", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }

        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        Item outputItem = BuiltInRegistries.ITEM.get(recipe.outputId());
        ItemStack output = new ItemStack(outputItem, recipe.rollOutputCount(level.random));
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
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
        return out;
    }

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.OilMakerBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
        }
    }


    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, PortItemStacks.save(input, registries));
        }
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, PortItemStacks.save(product, registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    public void load(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        input = tag.contains(TAG_INPUT) ? PortItemStacks.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? PortItemStacks.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
    }
}
