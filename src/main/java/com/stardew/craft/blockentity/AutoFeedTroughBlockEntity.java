package com.stardew.craft.blockentity;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.block.utility.AutoFeedTroughBlock;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public class AutoFeedTroughBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements UtilityAutomationAccess {
    private static final String TAG_HAY = "hay";

    private final IItemHandler automationItemHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            int total = totalHayInNetwork();
            return total <= 0 ? ItemStack.EMPTY : new ItemStack(ModItems.HAY.get(), total);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0) {
                return stack;
            }
            return insertAutomation(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            return extractAutomation(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? totalCapacityInNetwork() : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(ModItems.HAY.get());
        }
    };

    private ItemStack hayStack = ItemStack.EMPTY;

    public AutoFeedTroughBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOFEED_TROUGH.get(), pos, state);
    }

    public static int refillConnectedNetwork(ServerLevel level, BlockPos origin, UUID ownerPlayerId, int maxHay) {
        if (maxHay <= 0) {
            return 0;
        }

        List<BlockPos> network = AutoFeedTroughBlock.collectConnectedTroughs(level, origin);
        if (network.isEmpty()) {
            return 0;
        }

        AnimalWorldData data = AnimalWorldData.get(level);
        int inserted = refillPass(level, data, network, ownerPlayerId, maxHay, true);
        if (inserted >= maxHay) {
            return inserted;
        }
        return inserted + refillPass(level, data, network, ownerPlayerId, maxHay - inserted, false);
    }

    private static int refillPass(Level level,
                                  AnimalWorldData data,
                                  List<BlockPos> network,
                                  UUID ownerPlayerId,
                                  int remaining,
                                  boolean preferEmptyOnly) {
        int inserted = 0;
        for (BlockPos networkPos : network) {
            if (remaining <= 0) {
                break;
            }

            AutoFeedTroughBlockEntity trough = getTrough(level, networkPos);
            if (trough == null) {
                continue;
            }
            if (preferEmptyOnly) {
                if (trough.hayCount() != 0) {
                    continue;
                }
            } else if (trough.isFull()) {
                continue;
            }
            if (!trough.insertOneSelf(true)) {
                continue;
            }
            if (data.takeHay(ownerPlayerId, 1) <= 0) {
                break;
            }
            trough.insertOneSelf(false);
            inserted++;
            remaining--;
        }
        return inserted;
    }

    public ItemStack getHayStack() {
        if (hayStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ModItems.HAY.get(), 1);
    }

    private static AutoFeedTroughBlockEntity getTrough(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AutoFeedTroughBlockEntity trough) {
            return trough;
        }
        return null;
    }

    private int totalHayInNetwork() {
        Level currentLevel = level;
        if (currentLevel == null) {
            return hayCount();
        }
        int total = 0;
        for (BlockPos pos : AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition)) {
            AutoFeedTroughBlockEntity trough = getTrough(currentLevel, pos);
            if (trough != null) {
                total += trough.hayCount();
            }
        }
        return total;
    }

    private int totalCapacityInNetwork() {
        Level currentLevel = level;
        if (currentLevel == null) {
            return getAutomationSlotLimit(0);
        }
        int total = 0;
        for (BlockPos pos : AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition)) {
            AutoFeedTroughBlockEntity trough = getTrough(currentLevel, pos);
            if (trough != null) {
                total += trough.getAutomationSlotLimit(0);
            }
        }
        return total;
    }

    private int hayCount() {
        return hayStack.isEmpty() ? 0 : 1;
    }

    private boolean isFull() {
        return hayCount() >= getAutomationSlotLimit(0);
    }

    private boolean insertOneSelf(boolean simulate) {
        if (isFull()) {
            return false;
        }
        if (!hayStack.isEmpty() && !hayStack.is(ModItems.HAY.get())) {
            return false;
        }
        if (!simulate) {
            if (hayStack.isEmpty()) {
                hayStack = new ItemStack(ModItems.HAY.get(), 1);
            } else {
                hayStack.grow(1);
            }
            setChanged();
            syncVisualState();
            syncToClient();
        }
        return true;
    }

    private int extractSelfUpTo(int amount, boolean simulate) {
        int current = hayCount();
        if (current <= 0 || amount <= 0) {
            return 0;
        }
        int taken = Math.min(current, amount);
        if (!simulate) {
            hayStack = ItemStack.EMPTY;
            setChanged();
            syncVisualState();
            syncToClient();
        }
        return taken;
    }

    public ItemStack takeOneFromSelf(boolean simulate) {
        int taken = extractSelfUpTo(1, simulate);
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(ModItems.HAY.get(), taken);
    }

    @SuppressWarnings("null")
    public boolean insertOneHay(boolean simulate) {
        ItemStack remainder = insertAutomation(new ItemStack(ModItems.HAY.get(), 1), simulate);
        return remainder.isEmpty();
    }

    @Override
    public ItemStack getAutomationInput() {
        return hayStack;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !stack.is(ModItems.HAY.get())) {
            return stack;
        }

        Level currentLevel = level;
        if (currentLevel == null) {
            return stack;
        }

        int inserted = 0;
        for (BlockPos pos : AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition)) {
            AutoFeedTroughBlockEntity trough = getTrough(currentLevel, pos);
            if (trough == null || trough.hayCount() != 0) {
                continue;
            }
            if (trough.insertOneSelf(simulate)) {
                inserted = 1;
                break;
            }
        }

        if (inserted == 0) {
            for (BlockPos pos : AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition)) {
                AutoFeedTroughBlockEntity trough = getTrough(currentLevel, pos);
                if (trough == null || trough.isFull()) {
                    continue;
                }
                if (trough.insertOneSelf(simulate)) {
                    inserted = 1;
                    break;
                }
            }
        }

        return AutomationStackHelper.remainderAfterInsert(stack, inserted);
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        Level currentLevel = level;
        if (currentLevel == null) {
            return ItemStack.EMPTY;
        }

        int remaining = amount;
        ItemStack out = ItemStack.EMPTY;
        for (BlockPos pos : AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition)) {
            AutoFeedTroughBlockEntity trough = getTrough(currentLevel, pos);
            if (trough == null || trough.hayCount() <= 0) {
                continue;
            }
            int taken = trough.extractSelfUpTo(remaining, simulate);
            if (taken <= 0) {
                continue;
            }
            if (out.isEmpty()) {
                out = new ItemStack(ModItems.HAY.get(), taken);
            } else {
                out.grow(taken);
            }
            remaining -= taken;
            if (remaining <= 0) {
                break;
            }
        }

        return out;
    }

    @Override
    public net.neoforged.neoforge.items.IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Override
    public int getAutomationSlotLimit(int slot) {
        return 1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncVisualState();
    }

    private void syncVisualState() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AutoFeedTroughBlock)) {
            return;
        }
        boolean hasHay = !hayStack.isEmpty();
        if (state.getValue(AutoFeedTroughBlock.HAS_HAY) != hasHay) {
            currentLevel.setBlock(worldPosition, state.setValue(AutoFeedTroughBlock.HAS_HAY, hasHay), 3);
        }
    }

    @SuppressWarnings("null")
    private void syncToClient() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        List<BlockPos> network = AutoFeedTroughBlock.collectConnectedTroughs(currentLevel, worldPosition);
        if (network.isEmpty()) {
            network = List.of(worldPosition);
        }

        for (BlockPos pos : network) {
            BlockState state = currentLevel.getBlockState(pos);
            currentLevel.sendBlockUpdated(pos, state, state, 11);
            if (currentLevel instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(pos);
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!hayStack.isEmpty()) {
            tag.put(TAG_HAY, hayStack.save(registries));
        }
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hayStack = tag.contains(TAG_HAY)
            ? ItemStack.parse(registries, tag.getCompound(TAG_HAY)).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
        if (!hayStack.isEmpty() && hayStack.getCount() > getAutomationSlotLimit(0)) {
            hayStack.setCount(getAutomationSlotLimit(0));
        }
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @SuppressWarnings("null")
    @Override
    public void handleUpdateTag(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
