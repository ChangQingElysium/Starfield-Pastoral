package com.stardew.craft.block.nature;

import com.stardew.craft.block.utility.GardenPotBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.interior.SunroomService;
import com.stardew.craft.manager.TeaBushManager;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** SDV green-tea bush: a permanent, unwatered two-block plant with four visual states. */
public final class TeaBushBlock extends Block {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TeaBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(STAGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, HALF);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof Level actualLevel && SunroomService.isCentralTeaBush(actualLevel, pos)) {
            return true;
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }

        BlockState above = level.getBlockState(pos.above());
        return isValidGround(level.getBlockState(pos.below()))
                && (above.canBeReplaced()
                || above.is(this) && above.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    public static boolean isValidGround(BlockState ground) {
        return ground.getBlock() instanceof GardenPotBlock
                || com.stardew.craft.tree.fruit.FruitTreeRules.isValidGround(ground);
    }

    public static boolean canPlantAt(LevelReader level, BlockPos lowerPos) {
        return isValidGround(level.getBlockState(lowerPos.below()))
                && level.getBlockState(lowerPos).canBeReplaced()
                && level.getBlockState(lowerPos.above()).canBeReplaced();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos)
                ? super.updateShape(state, direction, neighborState, level, pos, neighborPos)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && !state.is(oldState.getBlock())) {
            BlockPos above = pos.above();
            level.setBlock(above, state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL);
            if (level instanceof ServerLevel serverLevel) {
                TeaBushManager.get(serverLevel).add(serverLevel, pos);
            }
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockPos above = pos.above();
                if (level.getBlockState(above).is(this)) {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
                if (level instanceof ServerLevel serverLevel) {
                    TeaBushManager.get(serverLevel).remove(serverLevel, pos);
                }
            } else {
                BlockPos below = pos.below();
                if (level.getBlockState(below).is(this)) {
                    level.setBlock(below, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (GardenPotBlock.isPottedPlant(level, pos, state)) {
            return Shapes.empty();
        }
        return halfShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (GardenPotBlock.isPottedPlant(level, pos, state)) {
            return Shapes.empty();
        }
        return halfShape(state);
    }

    /** Uses the same texture-derived per-stage geometry as the project's other two-block crops. */
    private VoxelShape halfShape(BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        int stage = state.getValue(STAGE);
        String half = state.getValue(HALF) == DoubleBlockHalf.UPPER ? "upper" : "lower";
        String modelId = ModelVoxelShapeCache.variantModel(
                blockId, "half=" + half + ",stage=" + stage);
        if (modelId != null && !modelId.isBlank()) {
            // An empty shape is intentional when that half of the 16x32 source sprite is transparent.
            return ModelVoxelShapeCache.shape(modelId);
        }
        return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        interact(serverPlayer, serverLevel, lowerPos(state, pos));
        return InteractionResult.CONSUME;
    }

    public boolean interact(ServerPlayer player, ServerLevel level, BlockPos lowerPos) {
        if (!canHarvest(player, level, lowerPos)) {
            return false;
        }

        TeaBushManager manager = TeaBushManager.get(level);
        boolean harvested = manager.harvest(level, lowerPos);
        if (harvested) {
            popResource(level, lowerPos, new ItemStack(ModItems.TEA_LEAVES.get()));
        }

        boolean sheltered = manager.isSheltered(level, lowerPos);
        if (StardewTimeManager.get().getCurrentSeason() != 3 || sheltered) {
            level.playSound(null, lowerPos, ModSounds.LEAFRUSTLE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return harvested;
    }

    public static boolean canModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return FarmAreaProtectionEvents.canModifyGreenhouseAt(player, level, pos);
        }
        return FarmAreaProtectionEvents.canModifyAt(player, pos);
    }

    /** Harvesting is allowed in public locations (notably Caroline's Sunroom), but not on protected farms. */
    public static boolean canHarvest(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return FarmAreaProtectionEvents.canModifyGreenhouseAt(player, level, pos);
        }
        return !FarmAreaProtectionEvents.isOnProtectedFarm(player, pos);
    }

    public static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.TEA_SAPLING.get());
    }
}
