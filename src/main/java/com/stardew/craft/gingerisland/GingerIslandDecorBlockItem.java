package com.stardew.craft.gingerisland;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.item.StardewBlockItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Manual placement uses the authored footprint's bottom, rather than its modeling pivot. */
public class GingerIslandDecorBlockItem extends StardewBlockItem {
    private final String placementMode;

    public GingerIslandDecorBlockItem(Block block, String itemTypeKey, int sellPrice, Properties properties) {
        this(block, itemTypeKey, sellPrice, properties, "floor");
    }

    public GingerIslandDecorBlockItem(Block block, String itemTypeKey, int sellPrice, Properties properties,
                                     String placementMode) {
        super(block, itemTypeKey, sellPrice, properties);
        this.placementMode = placementMode;
    }

    @Override public InteractionResult place(BlockPlaceContext context) {
        var stack = context.getItemInHand();
        var original = PortItemData.get(stack, DataComponents.BLOCK_STATE);
        if (original == null) return super.place(context);
        // Vanilla applies this component after validating the footprint. Only
        // actual appearance choices may survive; part/facing/waterlogged and
        // machine progress must be supplied by the placement code itself.
        var selected = getBlock() instanceof GingerIslandStateDecorBlock decor
                ? decor.itemState(stack) : BlockItemStateProperties.EMPTY;
        PortItemData.set(stack, DataComponents.BLOCK_STATE, selected);
        try { return super.place(context); }
        finally { PortItemData.set(stack, DataComponents.BLOCK_STATE, original); }
    }

    /** Shared with water placement; never step sideways around an occupied modeling anchor. */
    @Nullable
    public static BlockPlaceContext anchorAtBottom(BlockPlaceContext context, Block block) {
        int lift = block instanceof MapDecorStaticBlock decor ? decor.placementAnchorYOffset() : 0;
        if (lift == 0) return context;
        BlockPos anchor = context.getClickedPos().above(lift);
        BlockPlaceContext adjusted = BlockPlaceContext.at(context, anchor, context.getClickedFace());
        return adjusted.getClickedPos().equals(anchor) && adjusted.canPlace() ? adjusted : null;
    }

    @Override
    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        BlockPlaceContext adjusted = anchorAtBottom(context, getBlock());
        if (adjusted == null) return null;
        Direction face = context.getClickedFace();
        boolean wall = "wall".equals(placementMode) || "cliff_palm".equals(placementMode)
                && "1".equals(PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,
                        BlockItemStateProperties.EMPTY).properties().get("variant"));
        if (!wall || !face.getAxis().isHorizontal()) return adjusted;
        // The clicked support face is authoritative even when the player aims
        // across its edge. Pass it through validation, not only the final state.
        return new BlockPlaceContext(adjusted) {
            @Override public Direction getHorizontalDirection() { return face.getOpposite(); }
        };
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        BlockPos main = context.getClickedPos();
        BlockState before = level.getBlockState(main);
        var previousEntity = level.getBlockEntity(main);
        var previousData = previousEntity == null ? null : previousEntity.saveWithFullMetadata();
        if (!super.placeBlock(context, state)) return false;
        if (!(getBlock() instanceof MapDecorStaticBlock decor) || decor.placeExtensions(level, main, state)) return true;
        // BlockItem has not consumed the stack yet. The extension installer
        // already restored its cells; restore MAIN and any replaceable BE too.
        MapDecorStaticBlock.runWithDropsSuppressed(() -> level.setBlock(main, before, 2 | 16));
        var restored = level.getBlockEntity(main);
        if (restored != null && previousData != null) {
            restored.load(previousData);
            restored.setChanged();
        }
        return false;
    }
}
