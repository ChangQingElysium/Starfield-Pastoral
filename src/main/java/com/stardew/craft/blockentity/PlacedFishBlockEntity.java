package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One actual catch; taking it clears ownership before the world block can be removed. */
public final class PlacedFishBlockEntity extends BlockEntity {
    private ItemStack fish = ItemStack.EMPTY;

    public PlacedFishBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_FISH.get(), pos, state);
    }
    public ItemStack fish() { return fish.copy(); }
    public void storeFish(ItemStack stack) { fish = stack.copyWithCount(1); changed(); }
    public ItemStack takeFish() { ItemStack result = fish; fish = ItemStack.EMPTY; changed(); return result; }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        if (!fish.isEmpty()) tag.put("Fish", PortItemStacks.save(fish, provider));
    }
    @Override public void load(CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        fish = PortItemStacks.parseOptional(provider, tag.getCompound("Fish"));
        if (!fish.isEmpty()) fish.setCount(1);
    }
    @Override public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        var tag = new CompoundTag(); saveAdditional(tag); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        // This is a full snapshot: an empty tag must clear the last displayed fish.
        // MinecraftForge's default handler skips empty tags.
        loadWithComponents(packet.getTag(), provider);
    }
}
