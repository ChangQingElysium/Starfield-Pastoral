package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.item.fish.FishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One original item per display region; all item components survive storage and client updates. */
public final class FishMarketCrateBlockEntity extends BlockEntity {
    private final ItemStack[] fish = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    public FishMarketCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FISH_MARKET_CRATE.get(), pos, state);
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof FishItem
                || stack.getItem() instanceof com.stardew.craft.item.fish.crabpot.CrabPotItem);
    }
    public ItemStack fish(int slot) { return fish[slot].copy(); }

    public int insert(int slot, ItemStack stack) {
        if (!accepts(stack) || !fish[slot].isEmpty()) return -1;
        fish[slot] = stack.copyWithCount(1);
        changed();
        return slot;
    }

    public ItemStack remove(int slot) {
        ItemStack result = fish[slot];
        fish[slot] = ItemStack.EMPTY;
        if (!result.isEmpty()) changed();
        return result;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < fish.length; slot++) {
            ItemStack stack = fish[slot];
            fish[slot] = ItemStack.EMPTY;
            Block.popResource(level, worldPosition, stack);
        }
        setChanged();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        for (int slot = 0; slot < fish.length; slot++) if (!fish[slot].isEmpty()) tag.put("Fish" + slot, PortItemStacks.save(fish[slot], provider));
    }

    @Override public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        for (int slot = 0; slot < fish.length; slot++) {
            ItemStack stack = PortItemStacks.parseOptional(provider, tag.getCompound("Fish" + slot));
            fish[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
    }

    @Override public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        var tag = new CompoundTag(); saveAdditional(tag); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        // This is a full snapshot: an empty tag must clear the last displayed fish.
        // MinecraftForge's default handler skips empty tags.
        load(packet.getTag());
    }
}
