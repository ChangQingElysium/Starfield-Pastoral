package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.FurnitureStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

@SuppressWarnings("null")
public class FurnitureStorageBlock extends Block implements EntityBlock, PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private final int width;
    private final int menuRows;

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

    public FurnitureStorageBlock(Properties properties, int width, int menuRows) {
        super(properties);
        this.width = Math.max(1, width);
        this.menuRows = Math.max(1, menuRows);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PART, Part.MAIN));
    }

    public int menuRows() {
        return menuRows;
    }

    public int slotCount() {
        return menuRows * 9;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return null;
        }
        return new FurnitureStorageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!canPlaceAt(context.getLevel(), context.getClickedPos(), facing, context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(PART, Part.MAIN);
    }

    private boolean canPlaceAt(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (width <= 1) {
            return true;
        }
        BlockPos extensionPos = pos.relative(extensionDirection(facing));
        return level.getWorldBorder().isWithinBounds(extensionPos)
            && level.getBlockState(extensionPos).canBeReplaced(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && width > 1 && state.getValue(PART) == Part.MAIN) {
            level.setBlock(extensionPos(pos, state), state.setValue(PART, Part.EXTENSION), 3);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos mainPos = mainPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this) || mainState.getValue(PART) != Part.MAIN) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp
                && level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                && !sp.isCreative()
                && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(sp, mainPos)) {
            sp.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(mainPos);
        FurnitureStorageBlockEntity storage;
        if (be instanceof FurnitureStorageBlockEntity existingStorage) {
            storage = existingStorage;
        } else {
            storage = new FurnitureStorageBlockEntity(mainPos, mainState);
            level.setBlockEntity(storage);
        }
        player.openMenu(storage);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!isMoving && state.getValue(PART) == Part.MAIN) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FurnitureStorageBlockEntity storage) {
                    storage.dropAllContents(level, pos);
                }
            }
            if (width > 1) {
                BlockPos otherPos = state.getValue(PART) == Part.MAIN ? extensionPos(pos, state) : mainPos(pos, state);
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(this) && otherState.getValue(PART) != state.getValue(PART)) {
                    level.removeBlock(otherPos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && width > 1 && state.getValue(PART) == Part.EXTENSION && !player.isCreative()) {
            popResource(level, pos, new ItemStack(this));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos pos,
                                     BlockPos neighborPos) {
        if (width > 1) {
            BlockPos otherPos = state.getValue(PART) == Part.MAIN ? extensionPos(pos, state) : mainPos(pos, state);
            if (neighborPos.equals(otherPos) && !neighborState.is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static Direction extensionDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static BlockPos extensionPos(BlockPos mainPos, BlockState state) {
        return mainPos.relative(extensionDirection(state.getValue(FACING)));
    }

    private static BlockPos mainPos(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.MAIN) {
            return pos;
        }
        return pos.relative(extensionDirection(state.getValue(FACING)).getOpposite());
    }
}
