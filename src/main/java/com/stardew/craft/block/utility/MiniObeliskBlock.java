package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.warp.WarpEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MiniObeliskBlock extends MapUtilityStaticBlock {
    public MiniObeliskBlock(Properties properties) {
        super(properties, "stardewcraft:block/utility/mini_obelisk", true);
        // Missing facing in old saves resolves to the former fixed model orientation.
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(PART) == Part.MAIN ? List.of(new ItemStack(ModBlocks.MINI_OBELISK.get())) : List.of();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        if (placed == null) return null;
        if (context.getLevel().isClientSide) {
            return placed;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return placed;
        }
        if (!FarmAreaResolver.isInPlayerFarm(player.getUUID(), context.getClickedPos())) {
            if (player instanceof ServerPlayer serverPlayer) {
                ObjectDialogueService.show(serverPlayer, "stardewcraft.mini_obelisk.own_farm_only");
            }
            return null;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm != null && findObelisks(serverLevel, farm).size() >= 2) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ObjectDialogueService.show(serverPlayer, "stardewcraft.mini_obelisk.only_two");
                }
                return null;
            }
        }
        return placed;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            tryWarp(serverLevel, pos, serverPlayer);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useWithoutItem(state, level, pos, player, hit);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            tryWarp(serverLevel, pos, serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private void tryWarp(ServerLevel level, BlockPos pos, ServerPlayer player) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null || !farm.contains(pos)) {
            ObjectDialogueService.show(player, "stardewcraft.mini_obelisk.own_farm_only");
            return;
        }

        List<BlockPos> obelisks = findObelisks(level, farm);
        if (obelisks.size() < 2) {
            ObjectDialogueService.show(player, "stardewcraft.mini_obelisk.needs_pair");
            return;
        }

        BlockPos source = pos.immutable();
        BlockPos target = obelisks.stream()
                .filter(other -> !other.equals(source))
                .max(Comparator.comparingDouble(other -> other.distSqr(player.blockPosition())))
                .orElse(null);
        if (target == null) {
            ObjectDialogueService.show(player, "stardewcraft.mini_obelisk.needs_pair");
            return;
        }

        BlockPos destination = firstOpenWarpTile(level, target);
        if (destination == null) {
            ObjectDialogueService.show(player, "stardewcraft.mini_obelisk.needs_space");
            return;
        }

        WarpEffects.spawnWarpParticles(level, player.getX(), player.getY(), player.getZ());
        level.playSound(null, player.blockPosition(), ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.teleportTo(level, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        WarpEffects.spawnWarpParticles(level, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
    }

    private static List<BlockPos> findObelisks(ServerLevel level, FarmInstance farm) {
        List<BlockPos> result = new ArrayList<>(2);
        BlockPos min = farm.getFarmBoundsMin();
        BlockPos max = farm.getFarmBoundsMax();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.hasChunkAt(cursor) && level.getBlockState(cursor).is(ModBlocks.MINI_OBELISK.get()) && level.getBlockState(cursor).getValue(PART) == Part.MAIN) {
                        result.add(cursor.immutable());
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    private static BlockPos firstOpenWarpTile(ServerLevel level, BlockPos target) {
        BlockPos[] candidates = {
                target.south(),
                target.west(),
                target.east(),
                target.north()
        };
        for (BlockPos candidate : candidates) {
            if (canStandAt(level, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
