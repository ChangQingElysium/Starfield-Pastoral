package com.stardew.craft.block.utility;

import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.GardenPotBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/** Stardew Valley's Garden Pot, with Minecraft crop and automation adapters. */
public final class GardenPotBlock extends FarmBlock implements EntityBlock {
    private static final VoxelShape POT_SHAPE = Block.box(0.5D, 0.0D, 0.5D, 15.5D, 11.0D, 15.5D);
    private static boolean seasonRuleRegistered;

    public GardenPotBlock(Properties properties) {
        super(properties);
    }

    public static void registerSeasonRule() {
        if (seasonRuleRegistered) {
            return;
        }
        com.stardew.craft.farming.SeasonLocationRules.registerIgnoreSeasonsRule(
                (level, cropPos) -> level.getBlockState(cropPos.below()).getBlock() instanceof GardenPotBlock);
        seasonRuleRegistered = true;
    }

    public static boolean isPottedPlant(BlockGetter level, BlockPos pos, BlockState state) {
        if (!isSupportedPlant(state)) {
            return false;
        }
        if (level.getBlockState(pos.below()).getBlock() instanceof GardenPotBlock) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                && level.getBlockState(pos.below()).getBlock() == state.getBlock()
                && level.getBlockState(pos.below(2)).getBlock() instanceof GardenPotBlock;
    }

    public static boolean isSupportedPlant(BlockState state) {
        return state.is(BlockTags.CROPS)
                || state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof com.stardew.craft.block.crop.DeadCropBlock
                || state.getBlock() instanceof com.stardew.craft.block.nature.ForageBlock
                || state.getBlock() instanceof com.stardew.craft.block.nature.TeaBushBlock
                || StardewCropRuntime.isRegisteredBlock(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POT_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POT_SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // A Garden Pot is a permanent container and must never decay into dirt.
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(ModBlocks.GARDEN_POT.get()));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos cropPos = pos.above();
            if (isSupportedPlant(level.getBlockState(cropPos))) {
                level.destroyBlock(cropPos, !player.isCreative(), player);
            }
            if (player.isCreative() && level.getBlockEntity(pos) instanceof GardenPotBlockEntity pot) {
                pot.clearStoredOutputs();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GardenPotBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && state.getValue(MOISTURE) < 7
                && level.isRainingAt(pos.above())) {
            level.setBlock(pos, state.setValue(MOISTURE, 7), Block.UPDATE_ALL);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.GARDEN_POT.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> GardenPotBlockEntity.serverTick(
                tickLevel, pos, tickState, (GardenPotBlockEntity) blockEntity);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above()) && state.getValue(MOISTURE) < 7) {
            level.setBlock(pos, state.setValue(MOISTURE, 7), Block.UPDATE_ALL);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof GardenPotBlockEntity pot) {
            if (pot.hasOutput() && !level.isClientSide
                    && com.stardew.craft.blockentity.UtilityDropHelper.tryHarvest(
                            level, pos, player, pot::hasOutput, pot::harvestOne, 0)) {
                return ItemInteractionResult.sidedSuccess(false);
            }
            if (stack.getItem() instanceof BoneMealItem && pot.canBoneMeal()) {
                if (!level.isClientSide && pot.applyBoneMeal()) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    level.levelEvent(1505, pos.above(), 0);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (stack.isEmpty() && player instanceof ServerPlayer serverPlayer && pot.harvestForPlayer(serverPlayer, hand)) {
                return ItemInteractionResult.sidedSuccess(false);
            }
        }
        // Fertilizer and all seed items continue through their own useOn implementation.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GardenPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }
        if (pot.hasOutput()
                && com.stardew.craft.blockentity.UtilityDropHelper.tryHarvest(
                        level, pos, player, pot::hasOutput, pot::harvestOne, 0)) {
            return InteractionResult.CONSUME;
        }
        return player instanceof ServerPlayer serverPlayer
                && pot.harvestForPlayer(serverPlayer, InteractionHand.MAIN_HAND)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            if (!level.isClientSide) {
                BlockPos cropPos = pos.above();
                if (isSupportedPlant(level.getBlockState(cropPos))) {
                    level.destroyBlock(cropPos, true);
                }
                if (level.getBlockEntity(pos) instanceof GardenPotBlockEntity pot) {
                    pot.dropStoredOutputs();
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
