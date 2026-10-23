package com.stardew.craft.blockentity;

// Upstream-SHA256: d387286f6db4f59c81f0ee7e8d9163e543db1977696304a0a4456d6ea6eb0b9e

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.SmokedOutputResolver;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
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

public class FishSmokerBlockEntity extends TimedProductionBlockEntity
        implements com.stardew.craft.platform.fabric.item.transfer.TransactionalMachineAccess {
    protected final com.stardew.craft.platform.fabric.item.transfer.DeferredFuelBuffer transactionalFuel =
            new com.stardew.craft.platform.fabric.item.transfer.DeferredFuelBuffer(
                    new com.stardew.craft.platform.fabric.item.transfer.DeferredFuelBuffer.Access() {
                        @Override public int amount() { return coalBuffer; }
                        @Override public int capacity() { return MAX_COAL_BUFFER; }
                        @Override public boolean accepts(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant item) {
                            return isCoalStack(item.toStack());
                        }
                        @Override public Object captureState() { return captureTransactionalAutomationState(); }
                        @Override public boolean matchesState(Object state) { return matchesTransactionalAutomationState(state); }
                        @Override public void commit(int amount) { coalBuffer = amount; setChanged(); syncToClient(); }
                    });
    private final com.stardew.craft.platform.fabric.item.transfer.PlannedMachineItemStorage transactionalProduction =
            createTransactionalAutomation(this::planTransactionalInsertion)
                    .withCommitDependency(transactionalFuel::commitBeforeProduction);
    private final net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
            transactionalAutomation = new com.stardew.craft.platform.fabric.item.transfer.FuelledMachineItemStorage(
                    transactionalFuel, transactionalProduction, () -> level != null && !level.isClientSide && !isRemoved());
    @Override public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
    stardewcraft$automationStorage() { return transactionalAutomation; }

    private java.util.Optional<com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion>
    planTransactionalInsertion(ItemStack stack, boolean collectingOutput,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (level == null || level.isClientSide || stack.isEmpty()) return java.util.Optional.empty();
        if (collectingOutput) {
            if (!ready || product.isEmpty()) return java.util.Optional.empty();
        } else if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return java.util.Optional.empty();
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("fish_smoker", stack);
        if (recipeOpt.isEmpty()) return java.util.Optional.empty();
        var recipe = recipeOpt.get();
        int consumed = 1;
        if (stack.getCount() < consumed || transactionalFuel.amount() < 1) return java.util.Optional.empty();
        ItemStack output = createOutputFromRecipe(recipe, stack, () -> {
            int count = recipe.outputCount();
            return count == recipe.maxOutputCount() ? count : count
                    + com.stardew.craft.platform.fabric.item.transfer.TransactionalRandom.nextInt(
                            level.random, recipe.maxOutputCount() - count + 1, transaction);
        });
        var plan = prepareProduction(stack, output, recipeMinutes(recipe), null, true);
        if (plan.isEmpty()) return java.util.Optional.empty();
        if (!transactionalFuel.consume(1, transaction)) throw new IllegalStateException("Reserved machine fuel disappeared");
        var prepared = plan.get();
        ItemStack offered = stack.copy();
        return java.util.Optional.of(new com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion(
                stack.copyWithCount(consumed), consumed,
                () -> ArtisanRecipeDataManager.getRecipe("fish_smoker", offered).orElse(null) == recipe,
                () -> startWork(offered.copy(), prepared, null)));
    }

    private static final int MAX_COAL_BUFFER = 64;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String TAG_COAL_BUFFER = "coalBuffer";

    private int coalBuffer = 0;

    public record RemainingTime(int days, int hours, int minutes) {}

    public FishSmokerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FISH_SMOKER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FishSmokerBlockEntity be) {
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
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("fish_smoker", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack);
        if (output.isEmpty()) {
            return InsertResult.fail();
        }
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
                player, false);
        if (plan.isEmpty()) {
            return InsertResult.fail();
        }

        if (player == null) {
            return InsertResult.fail();
        }
        if (!player.isCreative() && !hasCoal(player)) {
            return InsertResult.missing(new MissingItemRequirement(ModItems.COAL.get(), 1));
        }
        if (!player.isCreative() && !consumeCoal(player)) {
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
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (isCoalStack(stack)) {
            return insertCoal(stack, simulate);
        }
        if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("fish_smoker", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        ItemStack output = createOutputFromRecipe(recipe, stack, simulate);
        if (output.isEmpty()) {
            return stack;
        }
        var plan = prepareProduction(
                stack, output, recipeMinutes(recipe),
                null, true);
        if (plan.isEmpty()) {
            return stack;
        }
        if (coalBuffer <= 0) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        coalBuffer = Math.max(0, coalBuffer - 1);
        ItemStack inputCopy = stack.copy();
        startWork(inputCopy, plan.get(), null);
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }

    @Override
    @SuppressWarnings("null")
    public ItemStack getAutomationExtraDrop() {
        if (coalBuffer <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack coal = new ItemStack(ModItems.COAL.get());
        coal.setCount(Math.min(coalBuffer, coal.getMaxStackSize()));
        return coal;
    }

    @Override
    @SuppressWarnings("null")
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
        BooleanProperty workingProp = com.stardew.craft.block.utility.FishSmokerBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.FishSmokerBlock.getExtensionPos(pos, state);
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(state.getBlock()) && extensionState.hasProperty(workingProp)) {
                level.setBlock(extensionPos, extensionState.setValue(workingProp, workingNow), 3);
            }
        }
    }


    @SuppressWarnings("null")
    private static ItemStack createSmokedOutput(ItemStack inputStack) {
        return SmokedOutputResolver.resolve(inputStack);
    }

    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack input) {
        return createOutputFromRecipe(recipe, input, false);
    }

    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack input, boolean simulate) {
        return createOutputFromRecipe(recipe, input,
                () -> simulate ? recipe.outputCount() : recipe.rollOutputCount(level.random));
    }

    private ItemStack createOutputFromRecipe(ArtisanRecipeDataManager.Recipe recipe, ItemStack input,
            java.util.function.IntSupplier outputCount) {
        ItemStack output = ItemStack.EMPTY;
        if (recipe.outputMode() == ArtisanRecipeDataManager.OutputMode.SMOKED) {
            output = createSmokedOutput(input);
        } else if (recipe.outputMode() == ArtisanRecipeDataManager.OutputMode.FIXED && recipe.outputId() != null) {
            output = new ItemStack(BuiltInRegistries.ITEM.get(recipe.outputId()), outputCount.getAsInt());
        }
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (recipe.keepInputQuality()) {
            QualityHelper.setQuality(output, QualityHelper.getQuality(input));
        } else if (recipe.outputQuality() >= 0) {
            QualityHelper.setQuality(output, recipe.outputQuality());
        }
        return output;
    }

    private static boolean consumeCoal(Player player) {
        if (player == null) {
            return false;
        }
        if (tryConsumeCoal(player.getMainHandItem())) {
            return true;
        }
        if (tryConsumeCoal(player.getOffhandItem())) {
            return true;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (tryConsumeCoal(stack)) {
                return true;
            }
        }
        return false;
    }

	private static boolean hasCoal(Player player) {
		if (player == null) {
			return false;
		}
		if (isCoalStack(player.getMainHandItem())) {
			return true;
		}
		if (isCoalStack(player.getOffhandItem())) {
			return true;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isCoalStack(inv.getItem(i))) {
				return true;
			}
		}
		return false;
	}

    private static boolean tryConsumeCoal(ItemStack stack) {
        if (isCoalStack(stack)) {
            stack.shrink(1);
            return true;
        }
        return false;
    }

    @SuppressWarnings("null")
    private static boolean isCoalStack(ItemStack stack) {
        Item coal = ModItems.COAL.get();
        return coal != null && stack.is(coal);
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
        tag.putInt(TAG_COAL_BUFFER, coalBuffer);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
        coalBuffer = tag.getInt(TAG_COAL_BUFFER);
    }

    @SuppressWarnings("null")
    private ItemStack insertCoal(ItemStack stack, boolean simulate) {
        int space = MAX_COAL_BUFFER - coalBuffer;
        if (space <= 0) {
            return stack;
        }
        int toMove = Math.min(space, stack.getCount());
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, toMove);
        }
        coalBuffer += toMove;
        setChanged();
        syncToClient();
        return AutomationStackHelper.remainderAfterInsert(stack, toMove);
    }
}
