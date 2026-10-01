package com.stardew.craft.blockentity;

import com.stardew.craft.block.farm.MushroomBoxBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 蘑菇培养盆方块实体。无输入格，只持有当日产出的蘑菇。
 * <p>每日由 {@code FarmCaveDailyService} 按 SDV 概率写入 product。</p>
 */
public class MushroomBoxBlockEntity extends BlockEntity implements UtilityAutomationAccess, BubbleItemCountProvider {

    private static final String TAG_PRODUCT = "product";
    private static final String TAG_READY = "ready";

    private ItemStack product = ItemStack.EMPTY;
    private boolean ready = false;
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    public MushroomBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSHROOM_BOX.get(), pos, state);
    }

    public boolean isReady() {
        return ready;
    }

    public ItemStack getProduct() {
        return product;
    }

    /** 每日生成蘑菇（服务端调用）。如果已满则跳过。 */
    public void setProductIfEmpty(ItemStack stack) {
        if (ready || !product.isEmpty()) return;
        if (stack == null || stack.isEmpty()) return;
        this.product = stack.copy();
        this.ready = true;
        updateBlockStateReady(true);
        setChanged();
        syncToClient();
    }

    /** 通过 item id 填充（便于序列化 / 每日生成）。 */
    public void setProductIfEmpty(ResourceLocation itemId) {
        if (itemId == null) return;
        var item = BuiltInRegistries.ITEM.get(itemId);
        setProductIfEmpty(new ItemStack(item));
    }

    /** 采集：返回产出并清空。 */
    public ItemStack harvest() {
        if (!ready || product.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = product.copy();
        product = ItemStack.EMPTY;
        ready = false;
        updateBlockStateReady(false);
        setChanged();
        syncToClient();
        return out;
    }

    @SuppressWarnings("null")
    private void updateBlockStateReady(boolean newReady) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.hasProperty(MushroomBoxBlock.READY) && state.getValue(MushroomBoxBlock.READY) != newReady) {
            level.setBlock(worldPosition, state.setValue(MushroomBoxBlock.READY, newReady), 3);
        }
    }

    @SuppressWarnings("null")
    private void syncToClient() {
        if (level == null || level.isClientSide) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ── UtilityAutomationAccess：只允许导出，不接受插入 ──

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ready ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (!ready || product.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack out = AutomationStackHelper.extractUpTo(product, amount);
        if (simulate) return out;
        if (out.getCount() >= product.getCount()) {
            return harvest();
        }
        product.shrink(out.getCount());
        setChanged();
        syncToClient();
        return out;
    }

    @Override
    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    // ── BubbleItemCountProvider ──

    @Override
    public int getBubbleItemCount() {
        // SDV 蘑菇盆每日只产 1 个（heldObject 单个）
        return 1;
    }

    // ── NBT ──

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!product.isEmpty()) {
            tag.put(TAG_PRODUCT, product.save(registries));
        }
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_PRODUCT)) {
            product = ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY);
        } else {
            product = ItemStack.EMPTY;
        }
        ready = tag.getBoolean(TAG_READY);
    }
}
