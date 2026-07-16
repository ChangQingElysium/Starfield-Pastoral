package com.stardew.craft.blockentity;

import com.stardew.craft.core.ModTags;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.SeedMakerOutputResolver;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

public class SeedMakerBlockEntity extends TimedProductionBlockEntity implements BubbleItemCountProvider {
    private static final int EFFECTIVE_MINUTES_PER_DAY = 1260;
    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {}

    public SeedMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEED_MAKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeedMakerBlockEntity be) {
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

    public boolean hasInput() {
        return !input.isEmpty();
    }

    public ItemStack getInput() {
        return input;
    }

    public ItemStack getProduct() {
        return product;
    }

    @Override
    public int getBubbleItemCount() {
        return product.getCount();
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
        if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return InsertResult.fail();
        }
        if (stack.is(ModTags.Items.SEEDMAKER_BANNED)) {
            return InsertResult.fail();
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("seed_maker", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack);
        if (output.isEmpty()) {
            return InsertResult.fail();
        }

        startWork(stack, output, recipe.minutes(), player);
        return InsertResult.success();
    }

    @SuppressWarnings("null")
    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack stack) {
        if (recipe.outputMode() != ArtisanRecipeDataManager.OutputMode.SEEDMAKER) {
            return ItemStack.EMPTY;
        }
        ArtisanRecipeDataManager.SeedMakerRule rule = recipe.seedMakerRule();
        if (rule == null) {
            return ItemStack.EMPTY;
        }
        Item seedItem = SeedMakerOutputResolver.resolve(stack.getItem());
        if (seedItem == null) {
            return ItemStack.EMPTY;
        }
        RandomSource random = createSeedMakerRandom(worldPosition);
        if (random.nextDouble() < rule.ancientChance()) {
            return new ItemStack(ModItems.ANCIENT_FRUIT_SEEDS.get(), 1);
        }
        if (random.nextDouble() < rule.mixedChance()) {
            int range = Math.max(0, rule.mixedMax() - rule.mixedMin() + 1);
            int count = range == 0 ? rule.mixedMin() : rule.mixedMin() + random.nextInt(range);
            return new ItemStack(ModItems.MIXED_SEEDS.get(), count);
        }

        int range = Math.max(0, rule.seedMax() - rule.seedMin() + 1);
        int count = range == 0 ? rule.seedMin() : rule.seedMin() + random.nextInt(range);
        return new ItemStack(seedItem, count);
    }

    private void startWork(ItemStack inputStack, ItemStack output, int minutesUntilReady, Player player) {
        input = inputStack.copy();
        input.setCount(1);
        product = output;
        readyAtAbsMinute = getCurrentAbsMinute() + minutesUntilReady;
        ready = false;
        if (player == null || !player.isCreative()) {
            inputStack.shrink(1);
        }
        setChanged();
        syncToClient();
    }

    public ItemStack harvestOne() {
        if (!isReady()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        input = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        setChanged();
        syncToClient();
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
        if (stack.is(ModTags.Items.SEEDMAKER_BANNED)) {
            return stack;
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("seed_maker", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack);
        if (output.isEmpty()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        ItemStack inputCopy = stack.copy();
        startWork(inputCopy, output, recipe.minutes(), null);
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
        BooleanProperty workingProp = com.stardew.craft.block.utility.SeedMakerBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
        }
    }

    private static RandomSource createSeedMakerRandom(BlockPos pos) {
        StardewTimeManager tm = StardewTimeManager.get();
        int year = tm.getCurrentYear();
        int season = tm.getCurrentSeason();
        int day = tm.getCurrentDay();
        int time = tm.getCurrentTime();
        int dateKey = ((year * 4) + season) * 28 + (day - 1);
        long seed = ((long) dateKey * 0x9E3779B97F4A7C15L) ^ pos.asLong() ^ ((long) time * 0xBF58476D1CE4E5B9L);
        return RandomSource.create(seed);
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
