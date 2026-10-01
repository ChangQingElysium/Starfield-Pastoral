package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.SolarPanelBlockEntity;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.List;
import com.stardew.craft.port.PortBlockInteraction;

public class SolarPanelBlock extends Block implements EntityBlock, PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape[] MAIN_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/solar_panel_main", Direction.SOUTH);
    private static final VoxelShape[] EXT_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/solar_panel_extension", Direction.SOUTH);

    public enum Part implements StringRepresentable {
        MAIN("main"),
        EXTENSION("extension");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    @SuppressWarnings("null")
    public SolarPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.SOUTH)
            .setValue(WORKING, false)
            .setValue(PART, Part.MAIN));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING, PART);
    }

    @SuppressWarnings("null")
    @Override
    public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("null")
    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(ModBlocks.SOLAR_PANEL.get()));
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getPartShape(state);
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
        return getPartShape(state);
    }

    @SuppressWarnings("null")
    private static VoxelShape getPartShape(BlockState state) {
        int index = ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING));
        return state.getValue(PART) == Part.EXTENSION ? EXT_SHAPES[index] : MAIN_SHAPES[index];
    }

    @SuppressWarnings("null")
    @Override
    @Nullable
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return null;
        }
        return new SolarPanelBlockEntity(pos, state);
    }

    @SuppressWarnings("null")
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return null;
        }
        if (type != ModBlockEntities.SOLAR_PANEL.get()) {
            return null;
        }
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> SolarPanelBlockEntity.serverTick(lvl, pos, st, (SolarPanelBlockEntity) be);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();
        BlockPos extensionPos = pos.relative(facing);
        if (!level.getWorldBorder().isWithinBounds(extensionPos)) {
            return null;
        }
        if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(PART, Part.MAIN)
            .setValue(WORKING, false);
    }

    @SuppressWarnings("null")
    @Override
    public void setPlacedBy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @Nullable LivingEntity placer, @SuppressWarnings("null") ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        if (state.getValue(PART) != Part.MAIN) {
            return;
        }
        BlockPos extensionPos = pos.relative(state.getValue(FACING));
        BlockState extensionState = state.setValue(PART, Part.EXTENSION);
        level.setBlock(extensionPos, extensionState, 3);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @SuppressWarnings("null")
    @Override
    public ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            return useItemOn(stack, mainState, level, mainPos, player, hand, hit);
        }

        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SolarPanelBlockEntity panel)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return UtilityDropHelper.tryHarvest(level, pos, player, panel::isReady, panel::harvestOne,
            UtilityDropHelper.LOW_MACHINE_VANILLA_XP)
            ? ItemInteractionResult.sidedSuccess(false)
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            return useWithoutItem(mainState, level, mainPos, player, hit);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SolarPanelBlockEntity panel)) {
            return InteractionResult.PASS;
        }

        return UtilityDropHelper.tryHarvest(level, pos, player, panel::isReady, panel::harvestOne,
            UtilityDropHelper.LOW_MACHINE_VANILLA_XP)
            ? InteractionResult.CONSUME
            : InteractionResult.PASS;
    }

    @SuppressWarnings("null")
    @Override
    public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!isMoving && state.getValue(PART) == Part.MAIN) {
                UtilityDropHelper.dropAutomationContents(level, pos);
            }
            Part part = state.getValue(PART);
            Direction facing = state.getValue(FACING);
            BlockPos otherPos = part == Part.MAIN ? pos.relative(facing) : pos.relative(facing.getOpposite());
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.getBlock() == this && otherState.getValue(PART) != part) {
                level.removeBlock(otherPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @SuppressWarnings("null")
    @Override
    public void playerWillDestroy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player) {
        if (!level.isClientSide && state.getValue(PART) == Part.EXTENSION && !player.isCreative()) {
            popResource(level, pos, new ItemStack(ModBlocks.SOLAR_PANEL.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Direction direction, @SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") LevelAccessor level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        BlockPos otherPos = part == Part.MAIN ? pos.relative(facing) : pos.relative(facing.getOpposite());
        if (neighborPos.equals(otherPos) && !neighborState.is(this)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @SuppressWarnings("null")
    public static BlockPos getMainPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.EXTENSION ? pos.relative(facing.getOpposite()) : pos;
    }

    @SuppressWarnings("null")
    public static BlockPos getExtensionPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == Part.EXTENSION ? pos : pos.relative(facing);
    }
}
