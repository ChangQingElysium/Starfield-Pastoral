package com.stardew.craft.block.utility;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.WizardBuildingItem;
import com.stardew.craft.warp.ObeliskWarpService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** A model-sized multiblock anchor for the Wizard's placeable buildings. */
@SuppressWarnings("null")
public final class WizardBuildingBlock extends MapDecorStaticBlock implements EntityBlock {
    private final WizardBuildingKind kind;

    public WizardBuildingBlock(Properties properties, WizardBuildingKind kind) {
        // #aabb asks the existing geometry parser for a rotated model-space envelope.
        // MapDecorStaticBlock then slices it into occupied cells for placement/collision.
        super(properties, kind.shapeModelId(), true);
        this.kind = kind;
    }

    public WizardBuildingBlock(Properties properties, WizardBuildingKind kind,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        super(properties, StardewCraft.MODID + ":wizard_building/" + kind.id(),
                minX, minY, minZ, maxX, maxY, maxZ);
        this.kind = kind;
    }

    public WizardBuildingKind kind() {
        return kind;
    }

    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return state;
        }
        ItemStack stack = context.getItemInHand();
        if (WizardBuildingItem.getOwner(stack) == null) {
            WizardBuildingItem.bindTo(stack, player);
        }
        UUID itemOwner = WizardBuildingItem.getOwner(stack);
        if (itemOwner == null || !itemOwner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.stardewcraft.wizard_building.owner_only",
                    WizardBuildingItem.getOwnerName(stack)), true);
            return null;
        }
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(itemOwner);
        if (level.dimension() != ModDimensions.STARDEW_VALLEY || farm == null) {
            player.displayClientMessage(Component.translatable(
                    "message.stardewcraft.wizard_building.farm_only"), true);
            return null;
        }
        for (CellOffset offset : occupiedOffsets(state.getValue(FACING))) {
            BlockPos occupied = context.getClickedPos().offset(offset.dx(), offset.dy(), offset.dz());
            if (!farm.contains(occupied)) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.farm_only"), true);
                return null;
            }
        }
        if (kind.isGoldClock()) {
            if (farm.hasGoldClock()) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.gold_clock.already_built"), true);
                return null;
            }
        }
        return state;
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                            @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && state.getValue(PART) == Part.MAIN
                && level.getBlockEntity(pos) instanceof WizardBuildingBlockEntity building) {
            UUID owner = WizardBuildingItem.getOwner(stack);
            if (owner == null && placer instanceof ServerPlayer player) {
                WizardBuildingItem.bindTo(stack, player);
                owner = player.getUUID();
            }
            building.setOwner(owner);
            if (kind.isGoldClock()) {
                building.syncGoldClockFarmState();
            }
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null
                    && level.getBlockEntity(mainPos) instanceof WizardBuildingBlockEntity building
                    && building.owner() != null
                    && !building.owner().equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable(
                        "message.stardewcraft.wizard_building.owner_only",
                        building.owner().toString()), true);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        ItemStack drop = new ItemStack(this);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof WizardBuildingBlockEntity building && building.owner() != null) {
            String ownerName = "";
            if (building.getLevel() instanceof ServerLevel serverLevel) {
                ownerName = com.stardew.craft.player.PlayerDisplayName.get(
                        serverLevel.getServer(), building.owner());
            }
            WizardBuildingItem.bindTo(drop, building.owner(), ownerName);
        }
        return List.of(drop);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new WizardBuildingBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || !kind.isJunimoHut() || type != ModBlockEntities.WIZARD_BUILDING.get()) {
            return null;
        }
        return (tickerLevel, pos, tickerState, blockEntity) -> WizardBuildingBlockEntity.serverTick(
                tickerLevel, pos, tickerState, (WizardBuildingBlockEntity) blockEntity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        return interact(state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = interact(state, level, pos, player);
        return result == InteractionResult.PASS
                ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (!serverPlayer.isCreative() && !FarmAreaProtectionEvents.canModifyAt(serverPlayer, mainPos)) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "stardewcraft.farm.build_farm_only"), true);
            return InteractionResult.CONSUME;
        }

        if (kind.isJunimoHut()) {
            if (level.getBlockEntity(mainPos) instanceof WizardBuildingBlockEntity hut) {
                serverPlayer.openMenu(hut);
            }
            return InteractionResult.CONSUME;
        }

        if (kind.isGoldClock()) {
            if (level.getBlockEntity(mainPos) instanceof WizardBuildingBlockEntity clock) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new com.stardew.craft.network.payload.OpenGoldClockQuestionPayload(
                                mainPos, clock.isGoldClockEnabled()));
            }
            return InteractionResult.CONSUME;
        }

        ObeliskWarpService.begin(serverPlayer, kind);
        return InteractionResult.CONSUME;
    }

    @Override
    public void initializeClient(@Nonnull Consumer<net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions() {
            @Override
            @SuppressWarnings("null")
            public boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target,
                                         net.minecraft.client.particle.ParticleEngine manager) {
                if (target instanceof BlockHitResult hit) {
                    spawnBuildingFragments(level,
                            hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                            hit.getDirection(), 4);
                }
                return true;
            }

            @Override
            @SuppressWarnings("null")
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                             net.minecraft.client.particle.ParticleEngine manager) {
                BlockPos mainPos = WizardBuildingBlock.this.findMainPos(level, pos, state);
                BlockPos origin = mainPos != null ? mainPos : pos;
                Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
                Random random = new Random();
                for (CellOffset offset : WizardBuildingBlock.this.occupiedOffsets(facing)) {
                    BlockPos cell = origin.offset(offset.dx(), offset.dy(), offset.dz());
                    spawnBuildingFragments(level,
                            cell.getX() + 0.12D + random.nextDouble() * 0.76D,
                            cell.getY() + 0.12D + random.nextDouble() * 0.76D,
                            cell.getZ() + 0.12D + random.nextDouble() * 0.76D,
                            null, 3);
                }
                return true;
            }

            private void spawnBuildingFragments(Level level, double x, double y, double z,
                                                @Nullable Direction face, int count) {
                net.minecraft.core.particles.ItemParticleOption particle =
                        new net.minecraft.core.particles.ItemParticleOption(
                                net.minecraft.core.particles.ParticleTypes.ITEM,
                                new ItemStack(WizardBuildingBlock.this));
                Random random = new Random();
                for (int i = 0; i < count; i++) {
                    double px = x;
                    double py = y;
                    double pz = z;
                    if (face != null) {
                        px += face.getStepX() * 0.05D;
                        py += face.getStepY() * 0.05D;
                        pz += face.getStepZ() * 0.05D;
                    }
                    level.addParticle(particle, px, py, pz,
                            (random.nextDouble() - 0.5D) * 0.28D,
                            0.06D + random.nextDouble() * 0.16D,
                            (random.nextDouble() - 0.5D) * 0.28D);
                }
            }
        });
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && state.getValue(PART) == Part.MAIN
                && level.getBlockEntity(pos) instanceof WizardBuildingBlockEntity building) {
            building.dropAllContents(level, pos);
            building.dismissHarvesters();
            if (kind.isGoldClock() && building.owner() != null) {
                com.stardew.craft.farm.FarmInstance farm = FarmInstanceRegistry.get()
                        .getFarmForPlayer(building.owner());
                if (farm != null) {
                    farm.setGoldClockState(false, true);
                    FarmInstanceRegistry.get().setDirty();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
