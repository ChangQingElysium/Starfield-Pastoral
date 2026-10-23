package com.stardew.craft.blockentity;

// Upstream-SHA256: 7d8a043bb6596e7db2c9da335d4bf9a765980649d2bb2bc5727c84c1364d823d

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

@SuppressWarnings("all")
public class FurnaceBlockEntity extends TimedProductionBlockEntity
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
                    transactionalFuel, transactionalProduction, () -> level != null && !level.isClientSide && !isRemoved()
                            && !transactionalProduction.hasPendingInsertion()
                            && ((product.isEmpty() && readyAtAbsMinute < 0) || transactionalProduction.hasCollectedAllOutput()));
    @Override public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
    stardewcraft$automationStorage() { return transactionalAutomation; }

    protected java.util.Optional<com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion>
    planTransactionalInsertion(ItemStack stack, boolean collectingOutput,
            net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
        if (level == null || level.isClientSide || stack.isEmpty()) return java.util.Optional.empty();
        if (collectingOutput) {
            if (!ready || product.isEmpty()) return java.util.Optional.empty();
        } else if (!product.isEmpty() || readyAtAbsMinute >= 0) {
            return java.util.Optional.empty();
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("furnace", stack);
        if (recipeOpt.isEmpty()) return java.util.Optional.empty();
        var recipe = recipeOpt.get();
        int consumed = recipe.consumeCount();
        if (stack.getCount() < consumed || transactionalFuel.amount() < 1) return java.util.Optional.empty();
        ItemStack output = new ItemStack(BuiltInRegistries.ITEM.get(recipe.outputId()), recipe.outputCount() == recipe.maxOutputCount()
                ? recipe.outputCount() : recipe.outputCount()
                + com.stardew.craft.platform.fabric.item.transfer.TransactionalRandom.nextInt(
                        level.random, recipe.maxOutputCount() - recipe.outputCount() + 1, transaction));
        var plan = prepareProduction(stack, output, recipeMinutes(recipe), null, true);
        if (plan.isEmpty()) return java.util.Optional.empty();
        if (!transactionalFuel.consume(1, transaction)) throw new IllegalStateException("Reserved machine fuel disappeared");
        var prepared = plan.get();
        ItemStack offered = stack.copy();
        return java.util.Optional.of(new com.stardew.craft.platform.fabric.item.transfer.PlannedItemMachine.Insertion(
                stack.copyWithCount(consumed), consumed,
                () -> ArtisanRecipeDataManager.getRecipe("furnace", offered).orElse(null) == recipe,
                () -> startWork(offered.copy(), prepared, consumed, null)));
    }


    private static final int MAX_COAL_BUFFER = 64;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String TAG_COAL_BUFFER = "coalBuffer";

    protected int coalBuffer = 0;

    public record RemainingTime(int days, int hours, int minutes) {}

    public FurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNACE.get(), pos, state);
    }

    /** 子类（如 HeavyFurnaceBlockEntity）使用，避免硬编码到 FURNACE 类型。 */
    protected FurnaceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler getAutomationItemHandler() {
        return super.getAutomationItemHandler();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FurnaceBlockEntity be) {
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

    @SuppressWarnings("null")
    public static void clientTick(Level level, BlockPos pos, BlockState state, FurnaceBlockEntity be) {
        if (!be.isWorking()) {
            return;
        }
        if (level.random.nextInt(6) != 0) {
            return;
        }

        double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 2.0;
        level.addParticle(ParticleTypes.SMOKE, x, y + 0.05, z, 0.0, 0.01, 0.0);
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

        Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("furnace", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }

        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        int consumeCount = recipe.consumeCount();
        if (stack.getCount() < consumeCount) {
            return InsertResult.missing(new MissingItemRequirement(stack.getItem(), consumeCount));
        }
        Item outputItem = BuiltInRegistries.ITEM.get(
                recipe.outputId());
        ItemStack output = new ItemStack(
                outputItem, recipe.rollOutputCount(level.random));
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

        startWork(
                stack, plan.get(), consumeCount, player);
        return InsertResult.success();
    }

    protected void startWork(
            ItemStack inputStack,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            int inputCount,
            Player player
    ) {
        commitProduction(
                inputStack, plan, inputCount, player);
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
        if (isCoalStack(stack)) {
            return insertCoal(stack, simulate);
        }
        Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("furnace", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        int consumeCount = recipe.consumeCount();
        if (stack.getCount() < consumeCount) {
            return stack;
        }
        Item outputItem = BuiltInRegistries.ITEM.get(
                recipe.outputId());
        ItemStack output = new ItemStack(
                outputItem, (simulate ? recipe.outputCount() : recipe.rollOutputCount(level.random)));
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
            return AutomationStackHelper.remainderAfterInsert(stack, consumeCount);
        }
        coalBuffer = Math.max(0, coalBuffer - 1);
        ItemStack inputCopy = stack.copy();
        startWork(
                inputCopy, plan.get(), consumeCount, null);
        return AutomationStackHelper.remainderAfterInsert(stack, consumeCount);
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
        BooleanProperty workingProp = com.stardew.craft.block.utility.FurnaceBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.FurnaceBlock.getExtensionPos(pos, state);
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(state.getBlock()) && extensionState.hasProperty(workingProp)) {
                level.setBlock(extensionPos, extensionState.setValue(workingProp, workingNow), 3);
            }
        }
    }

    protected static boolean consumeCoal(Player player) {
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

    protected static boolean hasCoal(Player player) {
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
    protected static boolean isCoalStack(ItemStack stack) {
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
    protected ItemStack insertCoal(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
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
