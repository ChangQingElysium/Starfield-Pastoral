package com.stardew.craft.block.crop;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DeadCropBlock extends BushBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);

    @SuppressWarnings("null")
    public DeadCropBlock(Properties properties) {
        super(properties.dynamicShape());
        this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        if (com.stardew.craft.block.utility.GardenPotBlock.isPottedPlant(level, pos, state)) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
        String model = ModelVoxelShapeCache.variantModel(
                BuiltInRegistries.BLOCK.getKey(this).toString(), "variant=" + state.getValue(VARIANT));
        VoxelShape shape = ModelVoxelShapeCache.requiredShape(model);
        BlockPos soil = pos.below();
        BlockState support = level.getBlockState(soil);
        double offset = 0;
        if (support.getBlock() instanceof com.stardew.craft.block.decor.GardenPlanterBlock) offset = -.25;
        else if (support.getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
            VoxelShape floor = support.getCollisionShape(level, soil);
            if (!floor.isEmpty()) offset = floor.max(net.minecraft.core.Direction.Axis.Y) - 1;
        }
        return shape.move(0, offset, 0);
    }

    @SuppressWarnings("null")
    @Nullable
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        return this.defaultBlockState().setValue(VARIANT, context.getLevel().getRandom().nextInt(4));
    }

    @Override
    protected boolean mayPlaceOn(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos) {
        // Can be placed on farmland
        return state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock;
    }
    
    // Dead crops don't grow
    @Override
    public boolean isRandomlyTicking(@SuppressWarnings("null") BlockState state) {
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(@SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        return ItemStack.EMPTY; // No item for dead crop? Or maybe returns the ModItems.FIBER equivalent? Stardew logic says scythe destroys it.
    }
    
    // Handle Scythe interaction (if implemented via event or tool tagging)
    // For now, allow instant break by hand or tool.
}
