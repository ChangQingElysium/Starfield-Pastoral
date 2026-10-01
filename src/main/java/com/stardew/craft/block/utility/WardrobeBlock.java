package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.WardrobeBlockEntity;
import com.stardew.craft.network.payload.OpenWardrobePayload;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("null")
public class WardrobeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<FurnitureStorageBlock.Part> PART =
        EnumProperty.create("part", FurnitureStorageBlock.Part.class);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Fill> FILL = EnumProperty.create("fill", Fill.class);

    public enum Fill implements StringRepresentable {
        EMPTY("empty"),
        LOW("low"),
        MEDIUM("medium"),
        FULL("full");

        private final String name;

        Fill(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static Fill fromCount(int count) {
            if (count <= 0) {
                return EMPTY;
            }
            if (count <= 5) {
                return LOW;
            }
            if (count <= 10) {
                return MEDIUM;
            }
            return FULL;
        }
    }

    public WardrobeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PART, FurnitureStorageBlock.Part.MAIN)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(FILL, Fill.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, HALF, FILL);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == FurnitureStorageBlock.Part.EXTENSION
            || state.getValue(HALF) == DoubleBlockHalf.UPPER) {
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
        if (state.getValue(PART) == FurnitureStorageBlock.Part.EXTENSION
            || state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        return new WardrobeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos mainPos = context.getClickedPos();
        BlockPos extensionPos = context.getClickedPos().relative(extensionDirection(facing));
        if (!context.getLevel().getWorldBorder().isWithinBounds(extensionPos)
            || !context.getLevel().getWorldBorder().isWithinBounds(mainPos.above())
            || !context.getLevel().getWorldBorder().isWithinBounds(extensionPos.above())
            || !context.getLevel().getBlockState(extensionPos).canBeReplaced(context)
            || !context.getLevel().getBlockState(mainPos.above()).canBeReplaced(context)
            || !context.getLevel().getBlockState(extensionPos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(PART, FurnitureStorageBlock.Part.MAIN)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(FILL, Fill.EMPTY);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
            && state.getValue(PART) == FurnitureStorageBlock.Part.MAIN
            && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockPos extensionPos = extensionPos(pos, state);
            level.setBlock(extensionPos, state.setValue(PART, FurnitureStorageBlock.Part.EXTENSION), 3);
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
            level.setBlock(extensionPos.above(), state
                .setValue(PART, FurnitureStorageBlock.Part.EXTENSION)
                .setValue(HALF, DoubleBlockHalf.UPPER), 3);
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos mainPos = mainPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this)
            || mainState.getValue(PART) != FurnitureStorageBlock.Part.MAIN
            || mainState.getValue(HALF) != DoubleBlockHalf.LOWER) {
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

        ensurePlacedParts(level, mainPos, mainState);

        BlockEntity be = level.getBlockEntity(mainPos);
        WardrobeBlockEntity wardrobe;
        if (be instanceof WardrobeBlockEntity existingWardrobe) {
            wardrobe = existingWardrobe;
        } else {
            wardrobe = new WardrobeBlockEntity(mainPos, mainState);
            level.setBlockEntity(wardrobe);
        }
        wardrobe.updateFillState();

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, OpenWardrobePayload.from(mainPos, wardrobe));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos mainPos = mainPos(pos, state);
            if (!isMoving
                && state.getValue(PART) == FurnitureStorageBlock.Part.MAIN
                && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(mainPos);
                if (be instanceof WardrobeBlockEntity wardrobe) {
                    wardrobe.dropAllContents(level, mainPos);
                }
            }
            for (BlockPos otherPos : partPositions(mainPos, level.getBlockState(mainPos).is(this) ? level.getBlockState(mainPos) : state)) {
                if (!otherPos.equals(pos) && level.getBlockState(otherPos).is(this)) {
                    level.removeBlock(otherPos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
            && !player.isCreative()
            && (state.getValue(PART) == FurnitureStorageBlock.Part.EXTENSION
                || state.getValue(HALF) == DoubleBlockHalf.UPPER)) {
            popResource(level, pos, new ItemStack(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state,
                                     Direction direction,
                                     BlockState neighborState,
                                     LevelAccessor level,
                                     BlockPos pos,
                                     BlockPos neighborPos) {
        if (isCompanionPos(pos, state, neighborPos) && !isValidCompanion(neighborState, state)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    public static BlockPos extensionPos(BlockPos mainPos, BlockState state) {
        return mainPos.relative(extensionDirection(state.getValue(FACING)));
    }

    public static List<BlockPos> partPositions(BlockPos mainPos, BlockState state) {
        BlockPos extensionPos = extensionPos(mainPos, state);
        return List.of(mainPos, extensionPos, mainPos.above(), extensionPos.above());
    }

    public static void ensurePlacedParts(Level level, BlockPos mainPos, BlockState mainState) {
        if (level.isClientSide || !(mainState.getBlock() instanceof WardrobeBlock)) {
            return;
        }
        BlockState lowerMain = mainState
            .setValue(PART, FurnitureStorageBlock.Part.MAIN)
            .setValue(HALF, DoubleBlockHalf.LOWER);
        BlockState lowerExtension = lowerMain.setValue(PART, FurnitureStorageBlock.Part.EXTENSION);
        BlockState upperMain = lowerMain.setValue(HALF, DoubleBlockHalf.UPPER);
        BlockState upperExtension = lowerExtension.setValue(HALF, DoubleBlockHalf.UPPER);
        placeMissingPart(level, extensionPos(mainPos, lowerMain), lowerExtension);
        placeMissingPart(level, mainPos.above(), upperMain);
        placeMissingPart(level, extensionPos(mainPos, lowerMain).above(), upperExtension);
    }

    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (state.getValue(PART) == FurnitureStorageBlock.Part.MAIN) {
            return lowerPos;
        }
        return lowerPos.relative(extensionDirection(state.getValue(FACING)).getOpposite());
    }

    private static Direction extensionDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static void placeMissingPart(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, state, 3);
        }
    }

    private static boolean isCompanionPos(BlockPos pos, BlockState state, BlockPos neighborPos) {
        BlockPos mainPos = mainPos(pos, state);
        return partPositions(mainPos, state).contains(neighborPos);
    }

    private static boolean isValidCompanion(BlockState candidate, BlockState state) {
        return candidate.is(state.getBlock())
            && candidate.getValue(FACING) == state.getValue(FACING);
    }
}
