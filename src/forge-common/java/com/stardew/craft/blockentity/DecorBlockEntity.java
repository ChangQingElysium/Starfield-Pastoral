package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.FlooringBlock;
import com.stardew.craft.block.utility.LegacyWallpaperBlock;
import com.stardew.craft.deco.DecorationStyleRegistry;
import com.stardew.craft.deco.DecorationType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

@SuppressWarnings("null")
public class DecorBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private static final String TAG_STYLE_ID = "StyleId";
    private static final String TAG_SEGMENT_OVERRIDE = "SegmentOverride";

    private String styleId;
    /** When >= 0, forces this segment value instead of auto-calculating from column position. */
    private int segmentOverride = -1;

    public DecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOR_BLOCK.get(), pos, state);
        this.styleId = DecorationStyleRegistry.getDefaultStyleId(resolveType(state));
    }

    public String getStyleId() {
        return styleId;
    }

    @SuppressWarnings("null")
    public void setStyleId(String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return;
        }
        this.styleId = styleId;
        setChanged();
        if (level != null) {
            syncVisualState();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public int getSegmentOverride() {
        return segmentOverride;
    }

    /**
     * Set segment override. Pass -1 for auto-mode (compute from column),
     * or 0/1/2 for forced bottom/middle/top.
     */
    @SuppressWarnings("null")
    public void setSegmentOverride(int segment) {
        this.segmentOverride = segment;
        setChanged();
        if (level != null) {
            syncVisualState();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private DecorationType resolveType(BlockState state) {
        if (state.getBlock() == com.stardew.craft.block.ModBlocks.WALLPAPER_BLOCK.get()) {
            return DecorationType.WALLPAPER;
        }
        return DecorationType.FLOORING;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_STYLE_ID, styleId);
        if (segmentOverride >= 0) {
            tag.putInt(TAG_SEGMENT_OVERRIDE, segmentOverride);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        styleId = tag.contains(TAG_STYLE_ID)
            ? tag.getString(TAG_STYLE_ID)
            : resolveStyleIdFromBlockState(getBlockState());
        segmentOverride = tag.contains(TAG_SEGMENT_OVERRIDE) ? tag.getInt(TAG_SEGMENT_OVERRIDE) : -1;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getBlockState().is(com.stardew.craft.block.ModBlocks.WALLPAPER_BLOCK.get())) {
            return;
        }
        syncVisualState();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncVisualState() {
        if (level == null) {
            return;
        }
        BlockState current = getBlockState();
        DecorationType type = resolveType(current);
        int visual = DecorationStyleRegistry.getVisualIndex(type, styleId);
        BlockState updated = current;
        if (type == DecorationType.WALLPAPER && current.hasProperty(LegacyWallpaperBlock.STYLE)) {
            if (current.getValue(LegacyWallpaperBlock.STYLE) != visual) {
                updated = current.setValue(LegacyWallpaperBlock.STYLE, visual);
            }
            if (updated.hasProperty(LegacyWallpaperBlock.SEGMENT)) {
                int segment = segmentOverride >= 0
                    ? segmentOverride
                    : current.getValue(LegacyWallpaperBlock.SEGMENT);
                if (updated.getValue(LegacyWallpaperBlock.SEGMENT) != segment) {
                    updated = updated.setValue(LegacyWallpaperBlock.SEGMENT, segment);
                }
            }
        } else if (type == DecorationType.FLOORING && current.hasProperty(FlooringBlock.STYLE)) {
            if (current.getValue(FlooringBlock.STYLE) != visual) {
                updated = current.setValue(FlooringBlock.STYLE, visual);
            }
            if (updated.hasProperty(FlooringBlock.PART)) {
                int px = Math.floorMod(getBlockPos().getX(), 2);
                int pz = Math.floorMod(getBlockPos().getZ(), 2);
                int part = pz * 2 + px;
                if (updated.getValue(FlooringBlock.PART) != part) {
                    updated = updated.setValue(FlooringBlock.PART, part);
                }
            }
        }
        if (updated != current) {
            level.setBlock(getBlockPos(), updated, 3);
        }
    }

    private String resolveStyleIdFromBlockState(BlockState state) {
        DecorationType type = resolveType(state);
        int visual = 0;
        if (type == DecorationType.WALLPAPER && state.hasProperty(LegacyWallpaperBlock.STYLE)) {
            visual = state.getValue(LegacyWallpaperBlock.STYLE);
        } else if (type == DecorationType.FLOORING && state.hasProperty(FlooringBlock.STYLE)) {
            visual = state.getValue(FlooringBlock.STYLE);
        }

        List<com.stardew.craft.deco.DecorationStyle> styles = DecorationStyleRegistry.getStyles(type);
        if (visual >= 0 && visual < styles.size()) {
            return styles.get(visual).id();
        }

        return DecorationStyleRegistry.getDefaultStyleId(type);
    }

}
