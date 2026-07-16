package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.tree.NewTreePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class NewTreeFootprintEvents {
	private NewTreeFootprintEvents() {
	}

	@SubscribeEvent
	public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
		if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
			return;
		}
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = event.getPos();
		if (!violatesTreeFootprint(level, pos)) {
			return;
		}
		event.setCanceled(true);
		if (event.getEntity() instanceof ServerPlayer player) {
			player.displayClientMessage(Component.translatable("stardewcraft.message.tree.clear_footprint"), true);
		}
	}

	@SubscribeEvent
	public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
		if (!(event.getLevel() instanceof ServerLevel level)) {
			return;
		}
		for (var snapshot : event.getReplacedBlockSnapshots()) {
			if (!violatesTreeFootprint(level, snapshot.getPos())) {
				continue;
			}
			event.setCanceled(true);
			if (event.getEntity() instanceof ServerPlayer player) {
				player.displayClientMessage(Component.translatable("stardewcraft.message.tree.clear_footprint"), true);
			}
			return;
		}
	}

	private static boolean violatesTreeFootprint(ServerLevel level, BlockPos placedPos) {
		BlockState placedState = level.getBlockState(placedPos);
		if (placedState.getBlock() instanceof NewTreePartBlock placedPart
				&& placedPart.requiresHorizontalClearance()
				&& !NewTreePartBlock.hasHorizontalClearance(level, placedPos)) {
			return true;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockState neighborState = level.getBlockState(placedPos.offset(dx, 0, dz));
				if (neighborState.getBlock() instanceof NewTreePartBlock neighborPart
						&& neighborPart.requiresHorizontalClearance()) {
					return true;
				}
			}
		}
		return false;
	}
}
