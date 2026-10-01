package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class ShippingBinBlock extends com.stardew.craft.block.decor.MapDecorStaticBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape CLOSED_SHAPE = Block.box(0, 0, 0, 32, 16, 16);
    private static final VoxelShape OPEN_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 32, 2, 16),
            Block.box(0, 2, 0, 2, 14, 16), Block.box(30, 2, 0, 32, 14, 16),
            Block.box(2, 2, 0, 30, 14, 2), Block.box(2, 2, 14, 30, 14, 16));

    public ShippingBinBlock(Properties properties) {
        super(properties, "stardewcraft:block/utility/shipping_bin", 0, 0, 0, 32, 16, 16);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(PART) == Part.EXTENSION ? List.of() : List.of(new ItemStack(ModBlocks.SHIPPING_BIN.get()));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return Shapes.empty();
        BlockState owner = level.getBlockState(main);
        if (!owner.is(this)) return Shapes.empty();
        VoxelShape canonical = owner.getValue(OPEN) ? OPEN_SHAPE : CLOSED_SHAPE;
        if (level.getBlockEntity(main) instanceof ShippingBinBlockEntity bin && !bin.hasFullFootprint() && owner.getValue(OPEN)) {
            canonical = Shapes.or(canonical, Block.box(14, 2, 0, 16, 14, 16));
        }
        VoxelShape shape = rotateShapeForFacing(canonical, owner.getValue(FACING));
        BlockPos offset = pos.subtract(main);
        // Keep collision local to each reserved cell.
        return Shapes.join(shape.move(-offset.getX(), 0, -offset.getZ()), Shapes.block(), net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new ShippingBinBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION || type != ModBlockEntities.SHIPPING_BIN.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, pos, st, be) -> ((ShippingBinBlockEntity) be).tickLid()
                : (lvl, pos, st, be) -> ShippingBinBlockEntity.serverTick(lvl, pos, st, (ShippingBinBlockEntity) be);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useWithoutItem(state, level, pos, player, hit);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 出货箱只能在农场区域使用
        if (!com.stardew.craft.core.FarmAreaResolver.isInFarmArea(level, pos)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.farm.shipping_bin_farm_only"), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShippingBinBlockEntity shippingBin)) {
            return InteractionResult.PASS;
        }

        player.openMenu(shippingBin);
        level.playSound(null, pos, ModSounds.SHWIP.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        return InteractionResult.CONSUME;
    }

    public static net.minecraft.world.phys.AABB proximityArea(BlockPos pos, BlockState state) {
        return rotateShapeForFacing(Block.box(-16, 0, -16, 48, 40, 32), state.getValue(FACING)).bounds().move(pos);
    }

    public static net.minecraft.world.phys.AABB intakeArea(BlockPos pos, BlockState state, boolean full) {
        return rotateShapeForFacing(Block.box(2, 2, 2, full ? 30 : 14, 15, 14), state.getValue(FACING)).bounds().move(pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShippingBinBlockEntity shippingBin) {
                shippingBin.dropAllContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
