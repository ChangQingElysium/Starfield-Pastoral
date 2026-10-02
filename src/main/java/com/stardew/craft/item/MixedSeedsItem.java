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
			// 冬季随机取春夏秋作物，只有无视季节的地点（温室等）才种得成
			BlockState cropState = (season == 3
					&& !com.stardew.craft.farming.SeasonLocationRules.seedsIgnoreSeasonsHere(level, abovePos))
					? null
					: pickCropStateForSeason(season, level.getRandom());
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
		// Crop.getRandomLowGradeCropForThisSeason + ResolveSeedId:
		// Spring: Next(472,476) 且 473→472 => 防风草 1/2、花椰菜 1/4、土豆 1/4
		// Summer: 玉米/小麦/辣椒/萝卜 各 1/4
		// Fall: Next(487,491) => 玉米/茄子/洋蓟/南瓜 各 1/4（无山药）
		// Winter: 随机取春/夏/秋之一（仅在无视季节的温室/花盆里能种成）
		if (season == 3) {
			season = random.nextInt(3);
		}
		return switch (season) {
			case 0 -> {
				int r = random.nextInt(4);
				yield switch (r) {
					case 0, 1 -> ModBlocks.PARSNIP_CROP.get().defaultBlockState();
					case 2 -> ModBlocks.CAULIFLOWER_CROP.get().defaultBlockState();
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
				int r = random.nextInt(4);
				yield switch (r) {
					case 0 -> ModBlocks.CORN_CROP.get().defaultBlockState();
					case 1 -> ModBlocks.EGGPLANT_CROP.get().defaultBlockState();
					case 2 -> ModBlocks.ARTICHOKE_CROP.get().defaultBlockState();
					default -> ModBlocks.PUMPKIN_CROP.get().defaultBlockState();
				};
			}
			default -> null;
		};
	}

	@SuppressWarnings("null")
	private static boolean isFarmland(BlockState state) {
        return com.stardew.craft.block.terrain.TerrainSoils.cropSupport(state);
    }
}
