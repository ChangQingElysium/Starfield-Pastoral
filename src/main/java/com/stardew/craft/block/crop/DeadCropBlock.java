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
    public static final MapCodec<DeadCropBlock> CODEC = simpleCodec(DeadCropBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);
    private volatile VoxelShape[] variantShapes;
    private volatile boolean variantShapesResolved;

    @SuppressWarnings("null")
    public DeadCropBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
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
        VoxelShape[] shapes = getVariantShapes(state);
        int variant = state.getValue(VARIANT);
        variant = Math.max(0, Math.min(3, variant));
        return shapes[variant];
    }

    @SuppressWarnings("null")
    private VoxelShape[] getVariantShapes(BlockState state) {
        if (variantShapesResolved) {
            return variantShapes != null ? variantShapes : new VoxelShape[]{ SHAPE, SHAPE, SHAPE, SHAPE };
        }

        synchronized (this) {
            if (variantShapesResolved) {
                return variantShapes != null ? variantShapes : new VoxelShape[]{ SHAPE, SHAPE, SHAPE, SHAPE };
            }

            @SuppressWarnings("null")
            Block block = state.getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            VoxelShape[] resolved = new VoxelShape[4];
            for (int i = 0; i < 4; i++) {
                String modelId = ModelVoxelShapeCache.variantModel(blockId, "variant=" + i);
                if (modelId == null || modelId.isBlank()) {
                    resolved = null;
                    break;
                }
                resolved[i] = ModelVoxelShapeCache.shape(modelId);
            }

            variantShapes = resolved;
            variantShapesResolved = true;
            return variantShapes != null ? variantShapes : new VoxelShape[]{ SHAPE, SHAPE, SHAPE, SHAPE };
        }
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
    public ItemStack getCloneItemStack(@SuppressWarnings("null") LevelReader level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        return ItemStack.EMPTY; // No item for dead crop? Or maybe returns the ModItems.FIBER equivalent? Stardew logic says scythe destroys it.
    }
    
    // Handle Scythe interaction (if implemented via event or tool tagging)
    // For now, allow instant break by hand or tool.
}
