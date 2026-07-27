package com.stardew.craft.blockentity;

import com.stardew.craft.core.ModTags;
import com.stardew.craft.api.v1.machine.StardewMachineCycleKind;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

@SuppressWarnings("all")
public class CrystalariumBlockEntity extends TimedProductionBlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;
    private static final int TIME_DEFAULT = 5000;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {}

    public CrystalariumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTALARIUM.get(), pos, state);
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler getAutomationItemHandler() {
        return super.getAutomationItemHandler();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrystalariumBlockEntity be) {
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
        return refreshReady();
    }

    @Override
    protected StardewMachineCycleKind defaultCycleKind() {
        return StardewMachineCycleKind.REPEATING;
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

    @SuppressWarnings("null")
    public boolean tryInsert(ItemStack stack, Player player) {
        return tryInsertWithResult(stack, player).inserted();
    }

    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return InsertResult.fail();
        }
        if (!input.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return InsertResult.fail();
        }

        if (stack.is(ModTags.Items.CRYSTALARIUM_BANNED)) {
            return InsertResult.fail();
        }

        var recipeOpt = ArtisanRecipeDataManager.getRecipe("crystalarium", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack);
        if (output.isEmpty()) {
            return InsertResult.fail();
        }

        var plan = prepareMachineCycle(
                StardewMachineCycleKind.REPEATING,
                stack, output, recipe.minutes(),
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }
        startWork(stack, plan.get(), player, false);
        return InsertResult.success();
    }

    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack input) {
        if (recipe.outputMode() == ArtisanRecipeDataManager.OutputMode.COPY_INPUT) {
            ItemStack output = input.copy();
            output.setCount(1);
            return output;
        }
        if (recipe.outputMode() == ArtisanRecipeDataManager.OutputMode.FIXED && recipe.outputId() != null) {
            return new ItemStack(BuiltInRegistries.ITEM.get(recipe.outputId()), recipe.outputCount());
        }
        return ItemStack.EMPTY;
    }

    private void startWork(
            ItemStack inputStack,
            StardewProductionPlan plan,
            Player player,
            boolean automation
    ) {
        commitMachineCycle(
                inputStack, plan, 1, player,
                StardewMachineCycleKind.REPEATING,
                automation);
    }

    public ItemStack harvestOne() {
        if (!isReady()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        emitProductionEvent(
                com.stardew.craft.api.v1.machine
                        .StardewProductionPhase.COLLECTED);
        if (input.isEmpty()) {
            product = ItemStack.EMPTY;
            readyAtAbsMinute = -1;
            ready = false;
            setChanged();
            syncToClient();
            return out;
        }

        ArtisanRecipeDataManager.Recipe recipe = ArtisanRecipeDataManager.getRecipe("crystalarium", input).orElse(null);
        ItemStack nextOutput = recipe != null ? createOutputFromRecipe(recipe, input) : ItemStack.EMPTY;
        if (nextOutput.isEmpty()) {
            nextOutput = input.copy();
            nextOutput.setCount(1);
        }
        int minutes = recipe != null ? recipe.minutes() : TIME_DEFAULT;
        var nextPlan = prepareMachineCycle(
                StardewMachineCycleKind.REPEATING,
                input, nextOutput, minutes,
                null, true);
        if (nextPlan.isPresent()) {
            restartMachineCycle(
                    nextPlan.get(),
                    StardewMachineCycleKind.REPEATING,
                    true);
        } else {
            product = ItemStack.EMPTY;
            readyAtAbsMinute = -1;
            ready = false;
            setChanged();
            syncToClient();
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
        if (stack.isEmpty() || !input.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        if (stack.is(ModTags.Items.CRYSTALARIUM_BANNED)) {
            return stack;
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("crystalarium", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack);
        if (output.isEmpty()) {
            return stack;
        }
        var plan = prepareMachineCycle(
                StardewMachineCycleKind.REPEATING,
                stack, output, recipe.minutes(),
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        ItemStack inputCopy = stack.copy();
        startWork(inputCopy, plan.get(), null, true);
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
        BooleanProperty workingProp = com.stardew.craft.block.utility.CrystalariumBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.CrystalariumBlock.getExtensionPos(pos, state);
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

    // ==================== GeckoLib ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No animations — static geo model
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
