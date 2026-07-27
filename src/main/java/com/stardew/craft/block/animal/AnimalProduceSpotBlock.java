package com.stardew.craft.block.animal;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.service.AnimalProducePlacementService;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class AnimalProduceSpotBlock extends BaseEntityBlock {
    public static final MapCodec<AnimalProduceSpotBlock> CODEC = simpleCodec(AnimalProduceSpotBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D);

    public AnimalProduceSpotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @Nonnull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @Nonnull RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected @Nonnull VoxelShape getShape(@Nonnull BlockState state,
                                           @Nonnull BlockGetter level,
                                           @Nonnull BlockPos pos,
                                           @Nonnull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @Nonnull VoxelShape getCollisionShape(@Nonnull BlockState state,
                                                    @Nonnull BlockGetter level,
                                                    @Nonnull BlockPos pos,
                                                    @Nonnull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected @Nonnull ItemInteractionResult useItemOn(@Nonnull ItemStack stack,
                                                       @Nonnull BlockState state,
                                                       @Nonnull Level level,
                                                       @Nonnull BlockPos pos,
                                                       @Nonnull Player player,
                                                       @Nonnull InteractionHand hand,
                                                       @Nonnull BlockHitResult hit) {
        return useWithoutItem(state, level, pos, player, hit) == InteractionResult.CONSUME
            ? ItemInteractionResult.sidedSuccess(level.isClientSide)
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @Nonnull InteractionResult useWithoutItem(@Nonnull BlockState state,
                                                        @Nonnull Level level,
                                                        @Nonnull BlockPos pos,
                                                        @Nonnull Player player,
                                                        @Nonnull BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AnimalProduceSpotBlockEntity produceBe)) {
            return InteractionResult.PASS;
        }

        long ledgerEntryId =
                produceBe.getProduceLedgerEntryId();
        AnimalWorldData worldData = null;
        boolean ledgerBacked =
                level instanceof ServerLevel
                        && ledgerEntryId > 0L;
        ItemStack stack;
        if (ledgerBacked) {
            worldData = AnimalWorldData.get(
                    (ServerLevel) level);
            var entry = worldData.getAnimalProduce(
                    ledgerEntryId).orElse(null);
            if (entry == null) {
                level.removeBlock(pos, false);
                return InteractionResult.CONSUME;
            }
            stack = AnimalProducePlacementService
                    .stackForLedgerEntry(entry);
        } else {
            stack = produceBe.harvestOne();
        }
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!player.addItem(stack)) {
            if (!ledgerBacked) {
                produceBe.setProduceStack(stack);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.animal.produce.inventory_full"), true);
            return InteractionResult.CONSUME;
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PlayerStardewDataAPI.recordAnimalProductsCollected(serverPlayer, stack.getCount());
        }
        if (worldData != null) {
            worldData.completeAnimalProduce(
                    ledgerEntryId);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.0f);
        level.removeBlock(pos, false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(
            @Nonnull BlockState state,
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AnimalProduceSpotBlockEntity produceSpot
                    && produceSpot.getProduceLedgerEntryId() > 0L) {
                AnimalWorldData.get(serverLevel).releaseAnimalProduceProjection(
                        produceSpot.getProduceLedgerEntryId(),
                        serverLevel.dimension().location().toString(),
                        pos
                );
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new AnimalProduceSpotBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level,
                                                                   @Nonnull BlockState state,
                                                                   @Nonnull BlockEntityType<T> type) {
        return type == ModBlockEntities.ANIMAL_PRODUCE_SPOT.get() ? (lvl, p, s, be) -> {
            if (!(be instanceof AnimalProduceSpotBlockEntity produceBe)) {
                return;
            }
            if (produceBe.getProduceStack().isEmpty()) {
                lvl.removeBlock(p, false);
                return;
            }

            if (!(lvl instanceof ServerLevel serverLevel)) {
                return;
            }

            String buildingId = produceBe.getBuildingId();
            if (buildingId == null || buildingId.isBlank()) {
                lvl.removeBlock(p, false);
                return;
            }

            AnimalBuildingRecord building = AnimalWorldData.get(serverLevel).getBuildingIncludingInactive(buildingId).orElse(null);
            if (building == null) {
                lvl.removeBlock(p, false);
                return;
            }

            int managerRangeSq = (building.range() + 2) * (building.range() + 2);
            boolean validIndoor = building.isInBounds(p);
            boolean validOutdoorNearManager = p.distSqr(building.managerPos()) <= managerRangeSq;
            if (!validIndoor && !validOutdoorNearManager) {
                lvl.removeBlock(p, false);
                return;
            }

            BlockState supportState = lvl.getBlockState(p.below());
            if (supportState.isAir() || supportState.getCollisionShape(lvl, p.below()).isEmpty()) {
                lvl.removeBlock(p, false);
            }
        } : null;
    }
}
