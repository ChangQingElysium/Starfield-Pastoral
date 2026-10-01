package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.stardew.craft.block.utility.OakTableBlock;
import com.stardew.craft.network.payload.TableClothColorSyncPayload;

import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.Objects;

public class TableDisplayBlockEntity extends BlockEntity {
    private static final String TAG_HAS_DISPLAY_ITEM = "HasDisplayItem";
    private static final String TAG_DISPLAY_ITEM = "DisplayItem";
    private static final String TAG_DISPLAY_YAW = "DisplayYaw";
    private static final String TAG_CLOTH_COLOR = "ClothColor";

    private ItemStack displayItem = ItemStack.EMPTY;
    private float displayYawDegrees = 0.0f;
    private int clothColor = OakTableBlock.DEFAULT_CLOTH_COLOR;

    public TableDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TABLE_DISPLAY.get(), pos, state);
    }

    public ItemStack getDisplayItem() {
        return displayItem;
    }

    public boolean hasDisplayItem() {
        return !displayItem.isEmpty();
    }

    public void setDisplayItem(ItemStack stack) {
        setDisplayItem(stack, 0.0f);
    }

    @SuppressWarnings("null")
    public void setDisplayItem(ItemStack stack, float yawDegrees) {
        if (stack == null || stack.isEmpty()) {
            displayItem = ItemStack.EMPTY;
            displayYawDegrees = 0.0f;
        } else {
            ItemStack one = stack.copy();
            one.setCount(1);
            displayItem = one;
            displayYawDegrees = normalizeYaw(yawDegrees);
        }

        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, Objects.requireNonNull(getBlockState()), Objects.requireNonNull(getBlockState()), 3);
        }
    }

    @SuppressWarnings("null")
    public ItemStack removeDisplayItem() {
        ItemStack out = displayItem;
        displayItem = ItemStack.EMPTY;
        displayYawDegrees = 0.0f;
        setChanged();

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, Objects.requireNonNull(getBlockState()), Objects.requireNonNull(getBlockState()), 3);
        }

        return out;
    }

    public float getDisplayYawDegrees() {
        return displayYawDegrees;
    }

    public int getClothColor() {
        return clothColor;
    }

    public void setClothColor(int color) {
        clothColor = OakTableBlock.normalizeClothColor(color);
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, Objects.requireNonNull(getBlockState()), Objects.requireNonNull(getBlockState()), 11);
            serverLevel.getChunkSource().blockChanged(worldPosition);
            TableClothColorSyncPayload payload = new TableClothColorSyncPayload(worldPosition, clothColor);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 65536.0D) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.saveAdditional(tag);
        tag.putInt(TAG_CLOTH_COLOR, clothColor);
        tag.putBoolean(TAG_HAS_DISPLAY_ITEM, !displayItem.isEmpty());
        if (!displayItem.isEmpty()) {
            net.minecraft.nbt.Tag saved = PortItemStacks.save(displayItem, provider);
            if (saved != null) {
                tag.put(TAG_DISPLAY_ITEM, saved);
                tag.putFloat(TAG_DISPLAY_YAW, displayYawDegrees);
            }
        }
    }

    @Override
    public void load(@Nonnull CompoundTag tag) { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        super.load(tag);
        clothColor = tag.contains(TAG_CLOTH_COLOR, Tag.TAG_INT)
            ? OakTableBlock.normalizeClothColor(tag.getInt(TAG_CLOTH_COLOR))
            : OakTableBlock.DEFAULT_CLOTH_COLOR;
        if (tag.contains(TAG_HAS_DISPLAY_ITEM, Tag.TAG_BYTE)) {
            if (!tag.getBoolean(TAG_HAS_DISPLAY_ITEM)) {
                displayItem = ItemStack.EMPTY;
                displayYawDegrees = 0.0f;
                return;
            }
            if (tag.contains(TAG_DISPLAY_ITEM, CompoundTag.TAG_COMPOUND)) {
                CompoundTag itemTag = tag.getCompound(TAG_DISPLAY_ITEM);
                if (itemTag.contains("id", Tag.TAG_STRING)) {
                    displayItem = PortItemStacks.parse(provider, itemTag).orElse(ItemStack.EMPTY);
                    displayYawDegrees = normalizeYaw(tag.getFloat(TAG_DISPLAY_YAW));
                } else {
                    displayItem = ItemStack.EMPTY;
                    displayYawDegrees = 0.0f;
                }
            } else {
                displayItem = ItemStack.EMPTY;
                displayYawDegrees = 0.0f;
            }
            return;
        }

        // Backward compatibility for worlds written before HasDisplayItem existed.
        if (tag.contains(TAG_DISPLAY_ITEM, CompoundTag.TAG_COMPOUND)) {
            CompoundTag itemTag = tag.getCompound(TAG_DISPLAY_ITEM);
            if (itemTag.contains("id", Tag.TAG_STRING)) {
                displayItem = PortItemStacks.parse(provider, itemTag).orElse(ItemStack.EMPTY);
                displayYawDegrees = normalizeYaw(tag.getFloat(TAG_DISPLAY_YAW));
            } else {
                displayItem = ItemStack.EMPTY;
                displayYawDegrees = 0.0f;
            }
        } else {
            displayItem = ItemStack.EMPTY;
            displayYawDegrees = 0.0f;
        }
    }

    private static float normalizeYaw(float yawDegrees) {
        float wrapped = yawDegrees % 360.0f;
        if (wrapped < 0.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    @Override
    public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider provider = com.stardew.craft.port.PortRegistries.lookup();
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
