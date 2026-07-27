package com.stardew.craft.block.utility;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.AutoGrabberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class AutoGrabberBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    private static final VoxelShape[] MAIN_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/auto_grabber", Direction.NORTH);
    private static final VoxelShape[] EXT_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/auto_grabber_extension", Direction.NORTH);

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

    public AutoGrabberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FULL, false)
            .setValue(PART, Part.MAIN));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULL, PART);
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(ModBlocks.AUTO_GRABBER.get()));
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
                               @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos,
                               @Nonnull CollisionContext context) {
        return getPartShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state,
                                        @Nonnull BlockGetter level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull CollisionContext context) {
        return getPartShape(state);
    }

    private static VoxelShape getPartShape(BlockState state) {
        int index = ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING));
        return state.getValue(PART) == Part.EXTENSION ? EXT_SHAPES[index] : MAIN_SHAPES[index];
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return null;
        }
        return new AutoGrabberBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos extensionPos = pos.above();
        if (!level.getWorldBorder().isWithinBounds(extensionPos)) {
            return null;
        }
        if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(FULL, false)
            .setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level,
                            @Nonnull BlockPos pos,
                            @Nonnull BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }
        BlockPos extensionPos = pos.above();
        level.setBlock(extensionPos, state.setValue(PART, Part.EXTENSION), 3);
        if (level instanceof ServerLevel serverLevel) {
            AnimalWorldData data = AnimalWorldData.get(serverLevel);
            for (AnimalBuildingRecord building : data.getBuildings()) {
                if (building.dimensionId().equals(
                                serverLevel.dimension().location().toString())
                        && building.isInBounds(pos)) {
                    AnimalProducePlacementService.projectPendingForBuilding(
                            serverLevel,
                            data,
                            building
                    );
                    break;
                }
            }
        }
    }

    @Override
    public BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
                                              @Nonnull BlockState state,
                                              @Nonnull Level level,
                                              @Nonnull BlockPos pos,
                                              @Nonnull Player player,
                                              @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            return useItemOn(stack, level.getBlockState(mainPos), level, mainPos, player, hand, hit);
        }
        return useWithoutItem(state, level, pos, player, hit) == InteractionResult.CONSUME
            ? ItemInteractionResult.sidedSuccess(level.isClientSide)
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state,
                                               @Nonnull Level level,
                                               @Nonnull BlockPos pos,
                                               @Nonnull Player player,
                                               @Nonnull BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = getMainPos(pos, state);
            return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AutoGrabberBlockEntity autoGrabber)) {
            return InteractionResult.PASS;
        }
        player.openMenu(autoGrabber);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(@Nonnull BlockState state,
                         @Nonnull Level level,
                         @Nonnull BlockPos pos,
                         @Nonnull BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!isMoving && state.getValue(PART) == Part.MAIN) {
                                BlockEntity be = level.getBlockEntity(pos);
                                if (be instanceof AutoGrabberBlockEntity autoGrabber) {
                                    autoGrabber.dropAllContents(level, pos);
                                }
            }
            Part part = state.getValue(PART);
            BlockPos otherPos = part == Part.MAIN ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);
            if (other.getBlock() == this && other.getValue(PART) != part) {
                level.removeBlock(otherPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(@Nonnull Level level,
                                        @Nonnull BlockPos pos,
                                        @Nonnull BlockState state,
                                        @Nonnull Player player) {
        if (!level.isClientSide && state.getValue(PART) == Part.EXTENSION && !player.isCreative()) {
            popResource(level, pos, new ItemStack(ModBlocks.AUTO_GRABBER.get()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        Part part = state.getValue(PART);
        BlockPos otherPos = part == Part.MAIN ? pos.above() : pos.below();
        if (neighborPos.equals(otherPos) && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    public static BlockPos getMainPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.EXTENSION ? pos.below() : pos;
    }

    public static BlockPos getExtensionPos(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.EXTENSION ? pos : pos.above();
    }
}
