package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.block.utility.WardrobeBlock;
import com.stardew.craft.wardrobe.WardrobeCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class WardrobeBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private static final String TAG_ITEMS = "items";

    private final List<ItemStack> items = new ArrayList<>();

    public WardrobeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WARDROBE.get(), pos, state);
    }

    public List<ItemStack> itemsView() {
        return items.stream().map(ItemStack::copy).toList();
    }

    public int itemCount() {
        return items.size();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && getBlockState().getBlock() instanceof WardrobeBlock) {
            WardrobeBlock.ensurePlacedParts(level, worldPosition, getBlockState());
        }
        updateFillState();
    }

    public boolean addFromInventory(ItemStack stack) {
        if (stack.isEmpty() || !WardrobeCategory.isAccepted(stack)) {
            return false;
        }
        items.add(stack.copy());
        setChanged();
        updateFillState();
        return true;
    }

    public ItemStack peek(int index) {
        if (index < 0 || index >= items.size()) {
            return ItemStack.EMPTY;
        }
        return items.get(index).copy();
    }

    public ItemStack removeAt(int index) {
        if (index < 0 || index >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = items.remove(index);
        setChanged();
        updateFillState();
        return removed;
    }

    public void dropAllContents(Level level, BlockPos pos) {
        if (level.isClientSide || items.isEmpty()) {
            return;
        }
        SimpleContainer container = new SimpleContainer(items.toArray(new ItemStack[0]));
        Containers.dropContents(level, pos, container);
        items.clear();
        setChanged();
        updateFillState();
    }

    public void updateFillState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WardrobeBlock)) {
            return;
        }
        WardrobeBlock.Fill fill = WardrobeBlock.Fill.fromCount(itemCount());
        for (BlockPos partPos : WardrobeBlock.partPositions(worldPosition, state)) {
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(state.getBlock()) && partState.getValue(WardrobeBlock.FILL) != fill) {
                setFillState(partPos, partState, fill);
            }
        }
    }

    private void setFillState(BlockPos pos, BlockState state, WardrobeBlock.Fill fill) {
        BlockState updated = state.setValue(WardrobeBlock.FILL, fill);
        level.setBlock(pos, updated, 3);
        level.sendBlockUpdated(pos, state, updated, 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", PortItemStacks.save(stack, registries));
            list.add(entry);
        }
        tag.put(TAG_ITEMS, list);
    }

    @Override
    public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        items.clear();
        if (tag.contains(TAG_ITEMS, 9)) {
            ListTag list = tag.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ItemStack parsed = PortItemStacks.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
                if (!parsed.isEmpty()) {
                    items.add(parsed);
                }
            }
        }
    }
}
