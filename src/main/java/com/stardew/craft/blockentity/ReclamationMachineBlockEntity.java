package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.ReclamationMachineBlock;
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

import javax.annotation.Nullable;

/**
 * Shared timed production, save/sync, automation and fairy dust for the two source machines.
 */
public class ReclamationMachineBlockEntity extends TimedProductionBlockEntity {
    private boolean modelFootprintChecked;
    private long startedAtGameTick;

    public boolean isWoodChipper() { return getBlockState().is(com.stardew.craft.block.ModBlocks.WOOD_CHIPPER.get()); }
    public long getStartedAtGameTick() { return startedAtGameTick; }
    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";


    public record RemainingTime(int days, int hours, int minutes) {}

    public ReclamationMachineBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(com.stardew.craft.block.ModBlocks.WOOD_CHIPPER.get())
            ? ModBlockEntities.WOOD_CHIPPER.get() : ModBlockEntities.DECONSTRUCTOR.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ReclamationMachineBlockEntity be) {
        long elapsed = level.getGameTime() - be.startedAtGameTick;
        if (!be.isWoodChipper() || !be.isWorking() || elapsed < 0 || elapsed >= 20 || elapsed % 2 != 0) return;
        var facing = state.getValue(ReclamationMachineBlock.FACING);
        double x = pos.getX() + .5 + facing.getStepX() * .3;
        double z = pos.getZ() + .5 + facing.getStepZ() * .3;
        level.addParticle(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                new ItemStack(com.stardew.craft.item.ModItems.WOOD_NORMAL.get())), x, pos.getY() + 1.4, z,
                facing.getStepX() * .06 + (level.random.nextDouble() - .5) * .05, .04,
                facing.getStepZ() * .06 + (level.random.nextDouble() - .5) * .05);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReclamationMachineBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (!be.modelFootprintChecked) {
            be.modelFootprintChecked = com.stardew.craft.block.utility.MachineModelFootprint.repair(level, pos, state);
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

        ItemStack output = resolveOutput(stack, player);
        if (output.isEmpty()) return false;
        var plan = prepareProduction(stack, output, isWoodChipper() ? 180 : 60, player, false);
        if (plan.isEmpty()) {
            return false;
        }
        startWork(stack, plan.get(), player);
        return true;
    }

    private boolean canProcess(ItemStack stack) {
        return isWoodChipper()
            ? stack.is(com.stardew.craft.item.ModItems.WOOD_HARD.get())
                || stack.is(com.stardew.craft.item.ModItems.DRIFTWOOD.get())
            : !com.stardew.craft.item.artisan.DeconstructorRecipes.output(stack).isEmpty();
    }

    private ItemStack resolveOutput(ItemStack stack, Player player) {
        if (!canProcess(stack) || level == null) return ItemStack.EMPTY;
        if (!isWoodChipper()) return com.stardew.craft.item.artisan.DeconstructorRecipes.output(stack, player instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null);
        boolean hardwood = stack.is(com.stardew.craft.item.ModItems.WOOD_HARD.get());
        if (hardwood && level.random.nextFloat() < .02f) {
            String[] products = {"maple_syrup", "oak_resin", "pine_tar"};
            return new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("stardewcraft", products[level.random.nextInt(3)])));
        }
        int min = hardwood && level.random.nextFloat() < .1f ? 15 : 5;
        return new ItemStack(com.stardew.craft.item.ModItems.WOOD_NORMAL.get(), min + level.random.nextInt(6));
    }

    private void startWork(
            ItemStack inputStack,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            Player player
    ) {
        startedAtGameTick = level == null ? 0 : level.getGameTime();
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
        if (!canProcess(stack)) return stack;
        if (simulate) return AutomationStackHelper.remainderAfterInsert(stack, 1);
        ItemStack output = resolveOutput(stack, null);
        if (output.isEmpty()) return stack;
        var plan = prepareProduction(stack, output, isWoodChipper() ? 180 : 60, null, true);
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
        if (!state.hasProperty(ReclamationMachineBlock.READY)) {
            return;
        }
        if (state.getValue(ReclamationMachineBlock.READY) != ready) {
            level.setBlock(pos, state.setValue(ReclamationMachineBlock.READY, ready), 3);
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
        tag.putLong("startedAtGameTick", startedAtGameTick);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
        startedAtGameTick = tag.getLong("startedAtGameTick");
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
