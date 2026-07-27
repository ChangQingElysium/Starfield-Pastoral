package com.stardew.craft.item;

import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import com.stardew.craft.api.v1.tree.StardewTreeRuntime;
import com.stardew.craft.api.v1.tree.StardewTreeRuntimeAdapter;
import com.stardew.craft.api.v1.tree.StardewTreeState;
import com.stardew.craft.block.tree.WildTreeSaplingBlock;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 树肥 - 对齐 SDV Tree Fertilizer：右键未成熟野树苗后提高每日生长概率，不会立即催熟。 */
public class TreeFertilizerItem extends SimpleStardewItem {

	public TreeFertilizerItem(int sellPrice, Properties properties) {
		super("stardewcraft.type.fertilizer", sellPrice, properties);
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(pos);

		StardewTreeState addonTree = StardewTreeRuntimeRegistry.inspectAddon(level, pos);
		if (addonTree != null) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			StardewTreeRuntimeAdapter.FertilizerResult result =
					StardewTreeRuntime.fertilize((ServerLevel) level, pos);
			if (result != StardewTreeRuntimeAdapter.FertilizerResult.PASS) {
				if (result == StardewTreeRuntimeAdapter.FertilizerResult.APPLIED) {
					consumeAndShowSuccess(context, (ServerLevel) level, pos);
				} else if (context.getPlayer() != null) {
					context.getPlayer().displayClientMessage(Component.translatable(
							switch (result) {
								case ALREADY_APPLIED -> "stardewcraft.tree_fertilizer.already";
								case MATURE -> "stardewcraft.tree_fertilizer.mature";
								default -> "stardewcraft.tree_fertilizer.cannot";
							}
					), true);
				}
				return InteractionResult.CONSUME;
			}
		}

		if (!(state.getBlock() instanceof WildTreeSaplingBlock)) {
			if (!level.isClientSide && WildTrees.findByAnyPart(state) != null && context.getPlayer() != null) {
				context.getPlayer().displayClientMessage(Component.translatable("stardewcraft.tree_fertilizer.mature"), true);
				return InteractionResult.CONSUME;
			}
			return InteractionResult.PASS;
		}

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		TreeGrowthManager manager = TreeGrowthManager.get(serverLevel);
		boolean alreadyFertilized = manager.isFertilized(serverLevel, pos);
		boolean fertilized = manager.fertilize(serverLevel, pos);

		if (fertilized) {
			consumeAndShowSuccess(context, serverLevel, pos);
			return InteractionResult.CONSUME;
		}

		if (context.getPlayer() != null) {
			context.getPlayer().displayClientMessage(Component.translatable(
					alreadyFertilized
							? "stardewcraft.tree_fertilizer.already"
							: "stardewcraft.tree_fertilizer.cannot"
			), true);
		}
		return InteractionResult.CONSUME;
	}

	private static void consumeAndShowSuccess(
			UseOnContext context,
			ServerLevel level,
			BlockPos pos
	) {
		if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
			context.getItemInHand().shrink(1);
		}
		level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				10, 0.3, 0.3, 0.3, 0.0);
	}
}
