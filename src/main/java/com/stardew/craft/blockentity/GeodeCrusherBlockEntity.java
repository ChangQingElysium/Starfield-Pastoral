package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.AbstractTwoBlockUtilityBlock;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.shop.GeodeLootService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class GeodeCrusherBlockEntity extends TimedProductionBlockEntity implements UtilityMachineInfo {
    private static final int MINUTES_UNTIL_READY = 60;
    private static final int STEAM_DELAY_TICKS = 4;

    private static final String TAG_INPUT = "input";
    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String TAG_STEAM_DELAY = "steamDelayTicks";
    private int steamDelayTicks = -1;

    public GeodeCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEODE_CRUSHER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeodeCrusherBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        be.tickLoadEffects(level, pos);
        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }
        be.updateWorkingState(level, pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GeodeCrusherBlockEntity be) {
        if (!be.isWorking()) {
            return;
        }
        if (level.random.nextInt(5) != 0) {
            return;
        }

        double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.65;
        double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.65;
        double y = pos.getY() + 1.15 + level.random.nextDouble() * 0.25;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.015, 0.0);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !input.isEmpty() && !ready && readyAtAbsMinute > 0;
    }

    public ItemStack getProduct() {
        return product;
    }

    public ItemStack getInput() {
        return input;
    }

    public boolean tryInsert(ItemStack stack, Player player) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return false;
        }
        if (!GeodeLootService.isGeodeCrusherInput(stack)) {
            return false;
        }

        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        ItemStack output = GeodeLootService.getTreasureForGeodeCrusher(stack, serverPlayer);
        if (output.isEmpty()) {
            return false;
        }

        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
                player, false);
        if (plan.isEmpty()) {
            return false;
        }
        startWork(stack, plan.get(), player);
        return true;
    }

    private void startWork(
            ItemStack inputStack,
            com.stardew.craft.api.v1.machine
                    .StardewProductionPlan plan,
            @Nullable Player player
    ) {
        commitProduction(inputStack, plan, 1, player);
        playLoadEffects();
    }

    public ItemStack harvestOne() {
        ItemStack out = collectProduction();
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            updateWorkingState(currentLevel, worldPosition, getBlockState());
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
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        if (!GeodeLootService.isGeodeCrusherInput(stack)) {
            return stack;
        }
        ItemStack output = GeodeLootService.getTreasureForGeodeCrusher(stack, null);
        if (output.isEmpty()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        var plan = prepareProduction(
                stack, output, MINUTES_UNTIL_READY,
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

    public boolean canApplyFairyDust() {
        return isWorking();
    }

    public boolean applyFairyDust() {
        if (!canApplyFairyDust()) {
            return false;
        }
        readyAtAbsMinute = getCurrentAbsMinute();
        ready = true;
        setChanged();
        syncToClient();
        Level currentLevel = level;
        if (currentLevel != null && !currentLevel.isClientSide) {
            updateWorkingState(currentLevel, worldPosition, getBlockState());
        }
        return true;
    }

    @Override
    public String getUtilityTooltipKey() {
        return "geode_crusher";
    }

    @Override
    public boolean isReadyForDisplay() {
        return isReady();
    }

    @Override
    public boolean isWorkingForDisplay() {
        return isWorking();
    }

    @Override
    public ItemStack getDisplayInput() {
        return input;
    }

    @Override
    public ItemStack getDisplayOutput() {
        return product;
    }

    @Override
    public boolean hasRemainingTimeForDisplay() {
        return isWorking();
    }

    @Override
    public UtilityMachineInfo.RemainingTime getRemainingTimeForDisplay() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
        return new UtilityMachineInfo.RemainingTime(days, hours, minutes);
    }

    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        AbstractTwoBlockUtilityBlock.updateWorkingState(level, pos, state, isWorking());
    }

    private void playLoadEffects() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        currentLevel.playSound(null, worldPosition, ModSounds.DRUMKIT4.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        currentLevel.playSound(null, worldPosition, ModSounds.STONE_CRACK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        steamDelayTicks = STEAM_DELAY_TICKS;
    }

    private void tickLoadEffects(Level level, BlockPos pos) {
        if (steamDelayTicks < 0) {
            return;
        }
        if (steamDelayTicks > 0) {
            steamDelayTicks--;
        }
        if (steamDelayTicks == 0) {
            level.playSound(null, pos, ModSounds.STEAM.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            steamDelayTicks = -1;
            setChanged();
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, input.save(registries));
        }
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
        tag.putInt(TAG_STEAM_DELAY, steamDelayTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
        steamDelayTicks = tag.contains(TAG_STEAM_DELAY) ? tag.getInt(TAG_STEAM_DELAY) : -1;
    }
}
