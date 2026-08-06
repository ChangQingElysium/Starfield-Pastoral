package com.stardew.craft.item;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stardew Mixed Seeds:
 * Plant on tilled soil to randomly grow a low-grade crop for the current season.
 */
public class MixedSeedsItem extends Item implements IStardewItem {
	public MixedSeedsItem(Properties properties) {
		super(properties);
	}

	@Override
	public String getItemTypeKey() {
		return "stardewcraft.type.seed";
	}

	@Override
	public int getSellPrice(ItemStack stack) {
		return -1;
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		@SuppressWarnings("null")
		BlockState clickedState = level.getBlockState(pos);

		if (!isFarmland(clickedState)) {
			return InteractionResult.PASS;
		}

		BlockPos abovePos = pos.above();
		@SuppressWarnings("null")
		BlockState aboveState = level.getBlockState(abovePos);
		if (!aboveState.isAir()) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide) {
			int season = StardewTimeManager.get().getCurrentSeason();
			BlockState cropState = pickCropStateForSeason(season, level.getRandom());
			if (cropState == null) {
				if (context.getPlayer() != null) {
					context.getPlayer().displayClientMessage(
							Component.translatable("stardewcraft.message.seed.wrong_season"),
							true);
				}
				return InteractionResult.FAIL;
			}

			level.setBlock(abovePos, cropState, 3);
			level.playSound(null, abovePos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			context.getItemInHand().shrink(1);
		}

		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	public static BlockState pickCropStateForSeason(int season, net.minecraft.util.RandomSource random) {
		// 参考 Stardew: Crop.getRandomLowGradeCropForThisSeason
		// Spring: Parsnip / Cauliflower / Potato
		// Summer: Corn / Hot Pepper / Radish / Wheat
		// Fall: Artichoke / Corn / Eggplant / Pumpkin / Yam
		// Winter: (vanilla Stardew 无法在户外种，除非温室/室内) —— 这里先拒绝。
		return switch (season) {
			case 0 -> {
				int r = random.nextInt(3);
				yield switch (r) {
					case 0 -> ModBlocks.PARSNIP_CROP.get().defaultBlockState();
					case 1 -> ModBlocks.CAULIFLOWER_CROP.get().defaultBlockState();
					default -> ModBlocks.POTATO_CROP.get().defaultBlockState();
				};
			}
			case 1 -> {
				int r = random.nextInt(4);
				yield switch (r) {
					case 0 -> ModBlocks.CORN_CROP.get().defaultBlockState();
					case 1 -> ModBlocks.HOT_PEPPER_CROP.get().defaultBlockState();
					case 2 -> ModBlocks.RADISH_CROP.get().defaultBlockState();
					default -> ModBlocks.WHEAT_CROP.get().defaultBlockState();
				};
			}
			case 2 -> {
				int r = random.nextInt(5);
				yield switch (r) {
					case 0 -> ModBlocks.ARTICHOKE_CROP.get().defaultBlockState();
					case 1 -> ModBlocks.CORN_CROP.get().defaultBlockState();
					case 2 -> ModBlocks.EGGPLANT_CROP.get().defaultBlockState();
					case 3 -> ModBlocks.PUMPKIN_CROP.get().defaultBlockState();
					default -> ModBlocks.YAM_CROP.get().defaultBlockState();
				};
			}
			default -> null;
		};
	}

	@SuppressWarnings("null")
	private static boolean isFarmland(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof FarmBlock) {
			return true;
		}
		String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString().toLowerCase();
		return blockId.contains("farmland");
	}
}
