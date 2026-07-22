package com.stardew.craft.block.nature;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.network.HayHarvestHudMessagePacket;
import com.stardew.craft.time.StardewTimeManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PastureGrassBlock extends BushBlock {
    public static final MapCodec<PastureGrassBlock> CODEC = simpleCodec(PastureGrassBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    /** SDV Grass.numberOfWeeds: clump density stored in one farm tile. */
    public static final IntegerProperty CLUMPS = IntegerProperty.create("clumps", 1, 4);

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @SuppressWarnings("null")
    public PastureGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0).setValue(CLUMPS, 4));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(VARIANT, CLUMPS);
    }

    @SuppressWarnings("null")
    @Override
    protected boolean mayPlaceOn(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    @SuppressWarnings("null")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Player player) {
            double factor = player instanceof ServerPlayer serverPlayer
                    ? BookPowerEffects.getGrassSpeedFactor(com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer))
                    : level.isClientSide ? BookPowerEffects.getClientGrassSpeedFactor() : BookPowerEffects.getGrassSpeedFactor(false);
            entity.makeStuckInBlock(state, new Vec3(factor, 1.0D, factor));
        }
        super.entityInside(state, level, pos, entity);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") net.minecraft.world.item.context.BlockPlaceContext context) {
        int variant = context.getLevel().getRandom().nextInt(3);
        return defaultBlockState().setValue(VARIANT, variant).setValue(CLUMPS, 4);
    }

    @SuppressWarnings("null")
    @Override
    protected void randomTick(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") ServerLevel level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") RandomSource random) {
        // 仅在 Stardew Valley 维度内生效（冬季消失 + 扩散），其他维度完全不处理。
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        // 冬季在 SDV 维度内自动消失（与 SDV 一致）
        if (StardewTimeManager.get().getCurrentSeason() == 3) {
            level.removeBlock(pos, false);
            return;
        }

        // SDV grows grass once during the new-day settlement. Doing it again
        // through Minecraft random ticks doubled growth and made its rate depend
        // on loaded chunks, so non-winter random ticks deliberately do nothing.
    }

    @SuppressWarnings("null")
    public static boolean cutWithScythe(ServerLevel level, BlockPos pos, ServerPlayer player, ScytheItem scythe) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PastureGrassBlock)) {
            return false;
        }
        boolean blueGrass = state.is(ModBlocks.BLUE_PASTURE_GRASS.get());

        level.removeBlock(pos, false);

        int hayCount = rollHayCount(level, scythe, level.getRandom());
        if (blueGrass && hayCount > 0) {
            hayCount = 2;
        }
        if (hayCount <= 0) {
            return true;
        }

        AnimalWorldData data = AnimalWorldData.get(level);
        java.util.UUID hayOwner = com.stardew.craft.core.FarmAreaResolver.getOwnerAt(pos);
        int stored = data.storeHay(hayOwner == null ? player.getUUID() : hayOwner, hayCount);
        if (stored > 0) {
            HayHarvestHudMessagePacket.sendTo(player, stored, false);
        }
        // No silo or silo full: hay is simply lost (SDV parity — no drop, no message)
        return true;
    }

    private static int rollHayCount(ServerLevel level, ScytheItem scythe, RandomSource random) {
        double chance = switch (scythe.getTier()) {
            case NORMAL -> 0.50;
            case GOLD -> 0.75;
            case IRIDIUM -> 1.00;
        };

        if (level.dimension() == ModDimensions.STARDEW_VALLEY && StardewTimeManager.get().getCurrentSeason() == 3) {
            chance *= 0.33;
        }

        int count = random.nextDouble() < chance ? 1 : 0;
        if (count > 0 && random.nextDouble() < 0.10) {
            count++;
        }
        return count;
    }

    @SuppressWarnings("null")
    public static boolean consumeForAnimal(ServerLevel level, BlockPos pos) {
        return consumeForAnimal(level, pos, 1);
    }

    @SuppressWarnings("null")
    public static boolean consumeForAnimal(ServerLevel level, BlockPos pos, int clumpsNeeded) {
        if (clumpsNeeded <= 0) {
            return true;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PastureGrassBlock)) {
            return false;
        }

        List<BlockPos> tiles = collectNearbySameTypeGrass(level, pos, state, clumpsNeeded);
        int available = tiles.stream().mapToInt(tile -> level.getBlockState(tile).getValue(CLUMPS)).sum();
        if (available < clumpsNeeded) {
            return false;
        }

        int remaining = clumpsNeeded;
        for (BlockPos tile : tiles) {
            if (remaining <= 0) break;
            BlockState grass = level.getBlockState(tile);
            int count = grass.getValue(CLUMPS);
            int eaten = Math.min(count, remaining);
            if (eaten == count) {
                level.removeBlock(tile, false);
            } else {
                level.setBlock(tile, grass.setValue(CLUMPS, count - eaten), net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            remaining -= eaten;
        }
        return true;
    }

    private static List<BlockPos> collectNearbySameTypeGrass(ServerLevel level, BlockPos origin, BlockState targetState, int clumpsNeeded) {
        List<BlockPos> result = new ArrayList<>();
        result.add(origin.immutable());
        int foundClumps = targetState.getValue(CLUMPS);
        if (foundClumps >= clumpsNeeded) {
            return result;
        }

        int radius = 5;
        for (int dist = 1; dist <= radius && foundClumps < clumpsNeeded; dist++) {
            for (int x = origin.getX() - dist; x <= origin.getX() + dist && foundClumps < clumpsNeeded; x++) {
                for (int y = origin.getY() - 1; y <= origin.getY() + 1 && foundClumps < clumpsNeeded; y++) {
                    for (int z = origin.getZ() - dist; z <= origin.getZ() + dist && foundClumps < clumpsNeeded; z++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (candidate.equals(origin)) {
                            continue;
                        }
                        BlockState candidateState = level.getBlockState(candidate);
                        if (!(candidateState.getBlock() instanceof PastureGrassBlock)) {
                            continue;
                        }
                        if (candidateState.getBlock() != targetState.getBlock()) {
                            continue;
                        }
                        result.add(candidate.immutable());
                        foundClumps += candidateState.getValue(CLUMPS);
                    }
                }
            }
        }

        return result;
    }

    @SuppressWarnings("null")
    @Override
    protected boolean canSurvive(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LevelReader level, @SuppressWarnings("null") BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }
}
