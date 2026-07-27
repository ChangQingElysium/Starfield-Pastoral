package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.tool.ScytheItem;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ScytheHarvestEvents {
	// 收割几何：前方扇形（按玩家视线），避免“必须指着作物”以及“扫到身后”。
	// 这里用 XZ 平面计算（高度用 3 个平面兜底，适配台阶/坡地）。
	private static final double SWING_RADIUS_BLOCKS = 2.9;
	private static final double COS_HALF_ANGLE = Math.cos(Math.toRadians(85.0));
	private static final float STARDEW_VANILLA_BRUSH_FIBER_CHANCE = 0.20f;
	private static final float STARDEW_VANILLA_BRUSH_MIXED_SEEDS_CHANCE = 0.03f;

	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		var player = event.getEntity();
		ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		boolean scythe = mainHand.getItem() instanceof ScytheItem;
		boolean weaponGrassCut = isWeaponGrassCuttingStack(mainHand)
				&& isWeaponGrassCuttingState(player.level().getBlockState(event.getPos()));
		if (!player.level().isClientSide) {
			if (weaponGrassCut) {
				event.setCanceled(true);
			}
			return;
		}
		if (!scythe && !weaponGrassCut) {
			return;
		}

		// 关键：在客户端直接拦截挖掘开始（尤其是创造模式的瞬间破坏）。
		event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!(mainHand.getItem() instanceof ScytheItem)
				&& !(isWeaponGrassCuttingStack(mainHand) && isWeaponGrassCuttingState(event.getState()))) {
			return;
		}
		// 进一步确保不会进入挖掘进度。
		event.setNewSpeed(0.0F);
	}

	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!(event.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (mainHand.getItem() instanceof ScytheItem
				|| (isWeaponGrassCuttingStack(mainHand) && isWeaponGrassCuttingState(event.getState()))) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onAttackEntity(AttackEntityEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		// 镰刀不是战斗武器：禁用左键攻击实体。
		if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof ScytheItem) {
			event.setCanceled(true);
		}
	}

	public static boolean harvestSwing(ServerLevel level, ServerPlayer player, ScytheItem scythe) {
		return harvestSwingInternal(level, player, scythe, ItemStack.EMPTY, false);
	}

	public static boolean harvestHaymakerSwing(ServerLevel level, ServerPlayer player, ItemStack weapon) {
		return harvestWeaponGrassSwing(level, player, weapon);
	}

	public static boolean harvestWeaponGrassSwing(ServerLevel level, ServerPlayer player, ItemStack weapon) {
		return harvestSwingInternal(level, player, null, weapon,
				StardewEnchantments.has(weapon, StardewEnchantments.HAYMAKER));
	}

	public static boolean isWeaponGrassCuttingStack(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof IStardewWeapon;
	}

	public static boolean isWeaponGrassCuttingState(BlockState state) {
		if (state == null || state.isAir()) {
			return false;
		}
		Block block = state.getBlock();
		return block instanceof WildWeedsBlock
				|| block instanceof PastureGrassBlock
				|| block == Blocks.SHORT_GRASS
				|| block == Blocks.FERN
				|| block == Blocks.TALL_GRASS
				|| block == Blocks.LARGE_FERN
				|| block instanceof TallGrassBlock
				|| block instanceof DeadBushBlock;
	}

	private static boolean harvestSwingInternal(ServerLevel level, ServerPlayer player, ScytheItem scythe, ItemStack tool, boolean haymaker) {
		boolean didSomething = false;
		boolean forceScytheHarvest = scythe != null && scythe.getTier() == ScytheItem.Tier.IRIDIUM;
		BlockPos origin = player.blockPosition();
		Vec3 look = player.getLookAngle();
		double lookX = look.x;
		double lookZ = look.z;
		double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
		if (lookLen < 1.0e-6) {
			Direction dir = player.getDirection();
			lookX = dir.getStepX();
			lookZ = dir.getStepZ();
			lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
		}
		lookX /= lookLen;
		lookZ /= lookLen;

		int r = (int) Math.ceil(SWING_RADIUS_BLOCKS);
		for (int dy = -1; dy <= 1; dy++) {
			int y = origin.getY() + dy;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);

					double vx = (pos.getX() + 0.5) - player.getX();
					double vz = (pos.getZ() + 0.5) - player.getZ();
					double dist = Math.sqrt(vx * vx + vz * vz);
					if (dist > SWING_RADIUS_BLOCKS) {
						continue;
					}

					// 脚下/近身格不强制朝向，避免“站在作物上收不到”。
					if (dist >= 0.75 && dist >= 1.0e-6) {
						double dot = (vx / dist) * lookX + (vz / dist) * lookZ;
						if (dot <= 0.0) {
							continue;
						}
						if (dot < COS_HALF_ANGLE) {
							continue;
						}
					}

					if (tryHarvestAt(level, player, scythe, tool, pos, forceScytheHarvest, haymaker)) {
						didSomething = true;
					}
				}
			}
		}

		return didSomething;
	}

	@SuppressWarnings("null")
	private static boolean tryHarvestAt(ServerLevel level, ServerPlayer player, ScytheItem scythe, ItemStack tool, BlockPos pos, boolean forceScytheHarvest, boolean haymaker) {
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		// 农场保护
		boolean inStardew = level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY;
		boolean isGreenhouseInterior = inStardew
				&& com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos);
		boolean canModify = player.isCreative() || !inStardew
				|| (isGreenhouseInterior
				? FarmAreaProtectionEvents.canModifyGreenhouseAt(player, level, pos)
				: FarmAreaProtectionEvents.canModifyAt(player, pos));
		// 公共区域（小镇等）：允许砍杂草类方块，但不允许其他操作
		boolean isPublicArea = inStardew && !player.isCreative()
				&& !isGreenhouseInterior
				&& com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, pos);

		if (!canModify && !isPublicArea) {
			return false;
		}

		if (state.getBlock() instanceof StardewCropBlock crop) {
			if (scythe == null) return false;
			if (!canModify) return false; // 公共区域不允许收割作物
			return crop.tryHarvestByTool(level, pos, state, player, forceScytheHarvest);
		}
		if (StardewCropRuntime.inspect(level, pos) != null) {
			if (scythe == null || !canModify) return false;
			return StardewCropRuntime.harvestForPlayer(
					player,
					pos,
					InteractionHand.MAIN_HAND,
					true,
					forceScytheHarvest
			).harvested();
		}

		if (state.getBlock() instanceof WildWeedsBlock) {
			if (isPublicArea) {
				com.stardew.craft.farm.PublicAreaBlockTracker.get().recordRemoval(pos, state);
			}
			boolean cut = WildWeedsBlock.cutWithScythe(level, pos, player);
			if (cut && haymaker) {
				if (level.random.nextFloat() < 0.50f) {
					Block.popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
				}
				if (level.random.nextFloat() < 0.33f) {
					Block.popResource(level, pos, new ItemStack(ModItems.HAY.get()));
				}
			}
			return cut;
		}

		// 过季枯萎的作物：任何镰刀都可清除（原版行为）。无掉落。
		if (state.getBlock() instanceof com.stardew.craft.block.crop.DeadCropBlock) {
			if (scythe == null) return false;
			if (isPublicArea) {
				com.stardew.craft.farm.PublicAreaBlockTracker.get().recordRemoval(pos, state);
			}
			level.removeBlock(pos, false);
			return true;
		}

		if (state.getBlock() instanceof PastureGrassBlock) {
			if (!canModify) return false; // 公共区域不允许砍牧草
			if (scythe == null) {
				return cutPastureGrassWithWeapon(level, pos, player, haymaker);
			}
			return PastureGrassBlock.cutWithScythe(level, pos, player, scythe);
		}
		// 原版杂草/草丛：short_grass, fern, tall_grass, large_fern, dead_bush
		Block block = state.getBlock();
		if (block == Blocks.SHORT_GRASS || block == Blocks.FERN
				|| block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN
				|| block instanceof TallGrassBlock || block instanceof DeadBushBlock) {
			if (isPublicArea) {
				com.stardew.craft.farm.PublicAreaBlockTracker.get().recordRemoval(pos, state);
				recordPairedVanillaBrushRemoval(level, pos, state);
			}
			if (inStardew) {
				removeVanillaBrushBlock(level, pos, state);
				if (!player.isCreative()) {
					spawnStardewVanillaBrushDrops(level, pos);
					if (haymaker && level.random.nextFloat() < 0.33f) {
						Block.popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
					}
				}
				return true;
			}
			level.destroyBlock(pos, true, player);
			if (haymaker && level.random.nextFloat() < 0.33f) {
				Block.popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
			}
			return true;
		}
		if (scythe == null) {
			return false;
		}
		// 原版差异：铱镰刀（66）能更广泛地“用镰刀收割作物”。
		if (scythe.getTier() != ScytheItem.Tier.IRIDIUM) {
			return false;
		}

		// 兼容原版/其他模组作物：优先用 vanilla 的 crops tag，其次兼容继承 CropBlock 的实现。
		if (!(state.is(BlockTags.CROPS) || state.getBlock() instanceof CropBlock)) {
			return false;
		}

		// 只破坏作物类方块；不会破坏普通方块。
		return level.destroyBlock(pos, true, player);
	}

	private static void spawnStardewVanillaBrushDrops(ServerLevel level, BlockPos pos) {
		if (level.random.nextFloat() < STARDEW_VANILLA_BRUSH_FIBER_CHANCE) {
			Block.popResource(level, pos, new ItemStack(ModItems.FIBER.get()));
		}
		if (level.random.nextFloat() < STARDEW_VANILLA_BRUSH_MIXED_SEEDS_CHANCE) {
			Block.popResource(level, pos, new ItemStack(ModItems.MIXED_SEEDS.get()));
		}
	}

	private static void removeVanillaBrushBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasProperty(DoublePlantBlock.HALF)) {
			DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
			BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			if (level.getBlockState(otherPos).is(state.getBlock())) {
				level.removeBlock(otherPos, false);
			}
		}
		level.removeBlock(pos, false);
	}

	private static void recordPairedVanillaBrushRemoval(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(DoublePlantBlock.HALF)) {
			return;
		}
		DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
		BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
		BlockState otherState = level.getBlockState(otherPos);
		if (otherState.is(state.getBlock())) {
			com.stardew.craft.farm.PublicAreaBlockTracker.get().recordRemoval(otherPos, otherState);
		}
	}

	private static boolean cutPastureGrassWithWeapon(ServerLevel level, BlockPos pos, ServerPlayer player, boolean haymaker) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof PastureGrassBlock)) {
			return false;
		}
		level.removeBlock(pos, false);
		float hayChance = haymaker ? 0.75f : 0.50f;
		if (level.random.nextFloat() >= hayChance) {
			return true;
		}
		java.util.UUID hayOwner = com.stardew.craft.core.FarmAreaResolver.getOwnerAt(pos);
		int stored = com.stardew.craft.animal.data.AnimalWorldData.get(level)
				.storeHay(hayOwner == null ? player.getUUID() : hayOwner, 1);
		if (stored > 0) {
			com.stardew.craft.network.HayHarvestHudMessagePacket.sendTo(player, stored, false);
		}
		return true;
	}
}
