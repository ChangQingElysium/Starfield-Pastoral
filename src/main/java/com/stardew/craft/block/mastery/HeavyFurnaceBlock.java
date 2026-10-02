package com.stardew.craft.block.mastery;

import com.stardew.craft.block.utility.FurnaceBlock;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.HeavyFurnaceBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Heavy Furnace — extends FurnaceBlock，BE 换成 HeavyFurnaceBlockEntity（5× 批处理）。
 */
@SuppressWarnings("null")
public class HeavyFurnaceBlock extends FurnaceBlock {
    private static final VoxelShape[] MAIN_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/mastery/heavy_furnace", Direction.SOUTH);
    private static final VoxelShape[] EXT_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/mastery/heavy_furnace_extension", Direction.SOUTH);

    public HeavyFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) return List.of();
        return List.of(new ItemStack(ModBlocks.HEAVY_FURNACE.get()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getPartShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getPartShape(state);
    }

    private static VoxelShape getPartShape(BlockState state) {
        int index = ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING));
        return state.getValue(PART) == Part.EXTENSION ? EXT_SHAPES[index] : MAIN_SHAPES[index];
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) return com.stardew.craft.port.PortMachineExtensions.createExtension(pos, state);
        return new HeavyFurnaceBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
        if (type != ModBlockEntities.HEAVY_FURNACE.get()) return null;
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> com.stardew.craft.blockentity.FurnaceBlockEntity.clientTick(lvl, pos, st, (HeavyFurnaceBlockEntity) be);
        }
        return (lvl, pos, st, be) -> com.stardew.craft.blockentity.FurnaceBlockEntity.serverTick(lvl, pos, st, (HeavyFurnaceBlockEntity) be);
    }
}
