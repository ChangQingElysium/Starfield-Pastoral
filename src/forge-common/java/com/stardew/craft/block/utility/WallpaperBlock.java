package com.stardew.craft.block.utility;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class WallpaperBlock extends Block {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 2);
    private final String styleId;

    public WallpaperBlock(BlockBehaviour.Properties properties, String styleId) {
        super(properties);
        this.styleId = styleId;
        registerDefaultState(stateDefinition.any().setValue(SEGMENT, 0));
    }

    public String getStyleId() {
        return styleId;
    }

    @Override
    public String getDescriptionId() {
        return "block.stardewcraft.wallpaper_block";
    }

    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        int segment = 0;
        if (below.getBlock() instanceof WallpaperBlock && below.hasProperty(SEGMENT)) {
            segment = Math.floorMod(below.getValue(SEGMENT) + 1, 3);
        } else if (below.getBlock() instanceof LegacyWallpaperBlock && below.hasProperty(LegacyWallpaperBlock.SEGMENT)) {
            segment = Math.floorMod(below.getValue(LegacyWallpaperBlock.SEGMENT) + 1, 3);
        }
        return defaultBlockState().setValue(SEGMENT, segment);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT);
    }
}
