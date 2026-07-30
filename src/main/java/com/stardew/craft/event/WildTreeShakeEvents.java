package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.manager.WildTreeSeedManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WildTreeShakeEvents {
	private WildTreeShakeEvents() {
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		// Avoid double-firing from offhand.
		if (event.getHand() != InteractionHand.MAIN_HAND) {
			return;
		}
		ServerLevel level = player.serverLevel();
		BlockPos clickedPos = event.getPos();
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(clickedPos);
		WildTrees.Def def = WildTrees.findByAnyPart(state);
		if (def == null) {
			return;
		}

		// 避免影响 Tapper / 其它方块物品的右键放置：仅空手可摇树。
		@SuppressWarnings("null")
		ItemStack hand = player.getItemInHand(event.getHand());
		if (!hand.isEmpty()) {
			return;
		}

		BlockPos trunk0Pos = findBaseTrunk0(level, clickedPos, def);
		if (trunk0Pos == null) {
			return;
		}

		// 农场保护：在别人农场上无权操作
		if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
				&& !player.isCreative()
				&& !FarmAreaProtectionEvents.canModifyAt(player, trunk0Pos)) {
			player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
			return;
		}

		// 记录/确保跟踪
		WildTreeSeedManager mgr = WildTreeSeedManager.get(level);
		mgr.trackTree(level, trunk0Pos, def);

		mgr.shake(level, trunk0Pos, def, player);

		// No UI: just a small physical feedback so the player can tell it worked.
		player.swing(event.getHand(), true);
		level.playSound(null, trunk0Pos, SoundEvents.AZALEA_LEAVES_HIT, SoundSource.BLOCKS, 0.6F, 1.0F);
		BlockState leafState = def.isModernPart(state) ? def.modernLeaves().get().defaultBlockState() : def.leaves().get().defaultBlockState();
		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, leafState),
				trunk0Pos.getX() + 0.5,
				trunk0Pos.getY() + 1.6,
				trunk0Pos.getZ() + 0.5,
				10,
				0.25,
				0.35,
				0.25,
				0.03
		);
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}

	public static boolean canShake(ServerPlayer player, BlockPos clickedPos) {
		if (player == null || clickedPos == null
				|| !player.getMainHandItem().isEmpty()) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		WildTrees.Def def = WildTrees.findByAnyPart(
				level.getBlockState(clickedPos));
		if (def == null) {
			return false;
		}
		BlockPos root = findBaseTrunk0(level, clickedPos, def);
		return root != null
				&& (level.dimension()
						!= com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
					|| player.isCreative()
					|| FarmAreaProtectionEvents.canModifyAt(player, root));
	}

	@SuppressWarnings("null")
	private static BlockPos findBaseTrunk0(ServerLevel level, BlockPos clickedPos, WildTrees.Def def) {
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(clickedPos);
		if (def.isModernRoot(state)) {
			return clickedPos;
		}
		if (def.isModernLog(state)) {
			return WildTrees.findModernRootFromLog(level, clickedPos, def);
		}
		if (state.getBlock() == def.trunk0().get()) {
			return clickedPos;
		}
		if (state.getBlock() == def.trunk1().get()) {
			BlockPos below = clickedPos.below();
			if (level.getBlockState(below).getBlock() == def.trunk0().get()) {
				return below;
			}
		}

		// 宽松兜底：在附近找最近的 trunk0（用于点到树枝/叶子）
		BlockPos best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos p = clickedPos.offset(dx, dy, dz);
					if (!level.isLoaded(p)) {
						continue;
					}
					BlockState nearbyState = level.getBlockState(p);
					if (nearbyState.getBlock() != def.trunk0().get() && !def.isModernRoot(nearbyState)) {
						continue;
					}
					int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
					if (dist < bestDist) {
						bestDist = dist;
						best = p;
					}
				}
			}
		}
		return best;
	}
}
