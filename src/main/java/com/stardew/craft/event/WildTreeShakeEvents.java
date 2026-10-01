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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.FORGE)
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

		BlockPos rootPos = findTreeRoot(level, clickedPos, def);
		if (rootPos == null) {
			return;
		}

		// 农场保护：在别人农场上无权操作
		if (level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
				&& !player.isCreative()
				&& !FarmAreaProtectionEvents.canModifyAt(player, rootPos)) {
			player.displayClientMessage(
					net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
			return;
		}

		// 记录/确保跟踪
		WildTreeSeedManager mgr = WildTreeSeedManager.get(level);
		mgr.trackTree(level, rootPos, def);

		mgr.shake(level, rootPos, def, player);

		// No UI: just a small physical feedback so the player can tell it worked.
		player.swing(event.getHand(), true);
		level.playSound(null, rootPos, SoundEvents.AZALEA_LEAVES_HIT, SoundSource.BLOCKS, 0.6F, 1.0F);
		BlockState leafState = def.modernLeaves().get().defaultBlockState();
		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, leafState),
				rootPos.getX() + 0.5,
				rootPos.getY() + 1.6,
				rootPos.getZ() + 0.5,
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
		BlockPos root = findTreeRoot(level, clickedPos, def);
		return root != null
				&& (level.dimension()
						!= com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
					|| player.isCreative()
					|| FarmAreaProtectionEvents.canModifyAt(player, root));
	}

	@SuppressWarnings("null")
	private static BlockPos findTreeRoot(ServerLevel level, BlockPos clickedPos, WildTrees.Def def) {
		var prefab = com.stardew.craft.tree.prefab.PrefabTreeRegistry.get(level).getByMember(clickedPos);
		return prefab != null && !prefab.felled() && def.id().equals(prefab.species()) ? prefab.root() : null;
	}
}
