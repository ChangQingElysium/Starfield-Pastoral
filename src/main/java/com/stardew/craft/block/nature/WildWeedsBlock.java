package com.stardew.craft.block.nature;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.blockentity.WildWeedsBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.secretnote.SecretNoteService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 星露谷“杂草/野草”地表物（对应原版 debris weeds）。
 *
 * 原版要点（简化实现）：
 * - 有季节外观（春/夏/秋/冬）。
 * - 破坏掉落：有概率掉 Fiber，少量概率掉 Mixed Seeds。
 */
public class WildWeedsBlock extends Block implements EntityBlock {
	public static final IntegerProperty SEASON = IntegerProperty.create("season", 0, 3);
	public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

	@SuppressWarnings("null")
	public WildWeedsBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(SEASON, 0).setValue(VARIANT, 0));
	}

	@Override
	protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SEASON, VARIANT);
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
		return new WildWeedsBlockEntity(pos, state);
	}

	@SuppressWarnings("null")
	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return true;
	}

	@SuppressWarnings("null")
	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@SuppressWarnings("null")
	@Override
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}

	@SuppressWarnings("null")
	@Override
	public @Nullable BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
		int season = StardewTimeManager.get().getCurrentSeason();
		if (context.getLevel().dimension() != ModDimensions.STARDEW_VALLEY) {
			season = 0;
		}
		season = clampSeason(season);
		int variant = pickVariantForSeason(season, context.getLevel().getRandom());
		return defaultBlockState().setValue(SEASON, season).setValue(VARIANT, variant);
	}

	@SuppressWarnings("null")
	@Override
	protected void randomTick(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") ServerLevel level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") RandomSource random) {
		if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
			return;
		}
		int expectedSeason = clampSeason(StardewTimeManager.get().getCurrentSeason());
		if (state.getValue(SEASON) != expectedSeason) {
			level.setBlock(pos, state.setValue(SEASON, expectedSeason), 3);
		}
	}

	@Override
	@SuppressWarnings("null")
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (entity instanceof Player player) {
			double factor = player instanceof ServerPlayer serverPlayer
					? BookPowerEffects.getGrassSpeedFactor(PlayerDataManager.getPlayerData(serverPlayer))
					: level.isClientSide ? BookPowerEffects.getClientGrassSpeedFactor() : BookPowerEffects.getGrassSpeedFactor(false);
			entity.makeStuckInBlock(state, new Vec3(factor, 1.0D, factor));
		}
		super.entityInside(state, level, pos, entity);
	}

	@Override
	public BlockState playerWillDestroy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player) {
		if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
			if (!player.isCreative()) {
				spawnWeedDrops(serverLevel, pos, serverLevel.getRandom(), player);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * 用镰刀等工具“割除”野草：按原逻辑掉落，然后移除方块。
	 */
	@SuppressWarnings("null")
	public static boolean cutWithScythe(ServerLevel level, BlockPos pos, Player player) {
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof WildWeedsBlock)) {
			return false;
		}
		if (player == null || player.isCreative()) {
			level.removeBlock(pos, false);
			return true;
		}
		spawnWeedDrops(level, pos, level.getRandom(), player);
		level.removeBlock(pos, false);
		return true;
	}

	private static int clampSeason(int season) {
		return Math.max(0, Math.min(3, season));
	}

	private static int pickVariantForSeason(int season, RandomSource random) {
		// 原版 GameLocation.getWeedForSeason:
		// - 春/夏/秋：各 3 种 weeds
		// - 冬：外观仍映射到同一个模型，但保留 variant 以便下个季节继续分化。
		return random.nextInt(3);
	}

	/**
	 * 换季当天主动刷新已加载区域内的杂草外观：
	 * - 方块保持同一个 wild_weeds
	 * - season 状态切到当前季节
	 * - variant 在该季节内随机
	 */
	@SuppressWarnings("null")
	public static void refreshLoadedWeedsForSeason(ServerLevel level, int season) {
		if (level == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
			return;
		}

		int normalizedSeason = clampSeason(season);
		Set<Long> visitedChunks = new HashSet<>();
		for (var player : level.players()) {
			int centerChunkX = player.blockPosition().getX() >> 4;
			int centerChunkZ = player.blockPosition().getZ() >> 4;
			int radius = 10;

			for (int cx = centerChunkX - radius; cx <= centerChunkX + radius; cx++) {
				for (int cz = centerChunkZ - radius; cz <= centerChunkZ + radius; cz++) {
					long chunkKey = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
					if (!visitedChunks.add(chunkKey) || !level.hasChunk(cx, cz)) {
						continue;
					}

					for (int lx = 0; lx < 16; lx++) {
						for (int lz = 0; lz < 16; lz++) {
							int worldX = (cx << 4) + lx;
							int worldZ = (cz << 4) + lz;
							int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);

							int minY = Math.max(level.getMinBuildHeight(), surfaceY - 2);
							int maxY = Math.min(level.getMaxBuildHeight() - 1, surfaceY + 2);
							for (int y = minY; y <= maxY; y++) {
								BlockPos pos = new BlockPos(worldX, y, worldZ);
								BlockState state = level.getBlockState(pos);
								if (!(state.getBlock() instanceof WildWeedsBlock)) {
									continue;
								}

								int currentSeason = state.getValue(SEASON);
								if (currentSeason == normalizedSeason) {
									continue;
								}

								level.setBlock(pos, state.setValue(SEASON, normalizedSeason), 3);
							}
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("null")
	private static void spawnWeedDrops(ServerLevel level, BlockPos pos, RandomSource random, Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack secretNote = SecretNoteService.tryCreateFromSource(serverPlayer, random, 0.009F);
			if (!secretNote.isEmpty()) {
				popResource(level, pos, secretNote);
			}
		}
		// 原版 cutWeed：
		// - 50% 掉 Fiber（这里按 1 个 Fiber 实现）
		// - 否则，小概率掉 Mixed Seeds
		if (random.nextBoolean()) {
			popResource(level, pos, new ItemStack(ModItems.FIBER.get(), 1));
			return;
		}

		double mixedSeedsChance = player instanceof ServerPlayer serverPlayer
				? BookPowerEffects.getWildSeedsChance(PlayerDataManager.getPlayerData(serverPlayer))
				: 0.05D;
		if (random.nextDouble() < mixedSeedsChance) {
			popResource(level, pos, new ItemStack(ModItems.MIXED_SEEDS.get(), 1));
		}
	}

}
