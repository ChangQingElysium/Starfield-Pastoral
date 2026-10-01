package com.stardew.craft.client;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.tool.FishingRodItem;
import com.stardew.craft.item.tool.WizardTowerCompassItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class ModItemProperties {
	private static final ResourceLocation CAST = new ResourceLocation("minecraft", "cast");

	private ModItemProperties() {
	}

	@SuppressWarnings("unused")
	private static int debugTick = 0;

	@SuppressWarnings("null")
	public static void register() {
        ItemProperties.register(ModItems.MINE_CHEST.get(), new ResourceLocation(StardewCraft.MODID, "mine_chest_special"),
                (stack, level, entity, seed) -> Boolean.TRUE.equals(stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                        com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).get(com.stardew.craft.block.mine.MineChestBlock.SPECIAL)) ? 1 : 0);
        ItemProperties.register(ModItems.MINE_COAL_BACKPACK.get(), new ResourceLocation(StardewCraft.MODID, "mine_backpack_variant"),
                (stack, level, entity, seed) -> {
                    var saved = stack.getOrDefault(com.stardew.craft.port.net.minecraft.core.component.DataComponents.BLOCK_STATE,
                            com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties.EMPTY);
                    int theme = Boolean.TRUE.equals(saved.get(com.stardew.craft.block.mine.MineCoalBackpackBlock.DESERT)) ? 2
                            : Boolean.TRUE.equals(saved.get(com.stardew.craft.block.mine.MineCoalBackpackBlock.DARK)) ? 1 : 0;
                    return theme * 2 + (Boolean.TRUE.equals(saved.get(com.stardew.craft.block.mine.MineCoalBackpackBlock.OPEN)) ? 1 : 0);
                });
        ItemProperties.register(ModItems.MINE_STEP_STONE.get(), new ResourceLocation(StardewCraft.MODID, "mine_step_stone_theme"),
                (stack, level, entity, seed) -> com.stardew.craft.item.MineStepStoneItem.theme(stack).ordinal() / 7.0F);
        ItemProperties.register(ModItems.MINE_LADDER.get(), new ResourceLocation(StardewCraft.MODID, "mine_entrance_theme"),
                (stack, level, entity, seed) -> com.stardew.craft.item.MineEntranceItem.modelIndex(stack) / 9.0F);
        ItemProperties.register(ModItems.MINE_EXIT.get(), new ResourceLocation(StardewCraft.MODID, "mine_exit_theme"),
                (stack, level, entity, seed) -> com.stardew.craft.item.MineExitItem.theme(stack).ordinal() / 7.0F);
        ItemProperties.register(ModItems.MINE_LAMP.get(), new ResourceLocation(StardewCraft.MODID, "mine_lamp_theme"),
                (stack, level, entity, seed) -> com.stardew.craft.item.MineLampItem.theme(stack).ordinal() / 7.0F);

		StardewCraft.LOGGER.info("Registering item properties");

		var castProperty = (net.minecraft.client.renderer.item.ClampedItemPropertyFunction) (stack, level, entity, seed) -> {
			if (!(entity instanceof Player player)) {
				return 0.0f;
			}

			// Prefer our own cast flag (drives first-person animation and can flip immediately client-side).
			if (stack.getItem() instanceof FishingRodItem) {
				boolean castActive = FishingRodItem.isCastActive(stack);
				if (castActive) {
					return 1.0f;
				}
			}

			// Fallback: derive from bobber state.
			return FishingRodItem.isBobberOut(player) ? 1.0f : 0.0f;
		};

		ItemProperties.register(ModItems.FISHING_ROD.get(), CAST, castProperty);
		ItemProperties.register(ModItems.TRAINING_ROD.get(), CAST, castProperty);
		ItemProperties.register(ModItems.FIBERGLASS_ROD.get(), CAST, castProperty);
		ItemProperties.register(ModItems.IRIDIUM_ROD.get(), CAST, castProperty);
		ItemProperties.register(ModItems.ADVANCED_IRIDIUM_ROD.get(), CAST, castProperty);

		// ── 法师塔指南针角度 ──
		var compassAngle = new ResourceLocation("minecraft", "angle");
		final float[] lastAngle = {0f};

		var compassProperty = (net.minecraft.client.renderer.item.ClampedItemPropertyFunction)
				(stack, level, entity, seed) -> {
			if (entity == null || level == null) return 0f;

			// 只在主世界且有目标时指向；否则随机旋转
			boolean hasTarget = level.dimension() == net.minecraft.world.level.Level.OVERWORLD
					&& WizardTowerCompassItem.hasClientTarget();
			if (!hasTarget) {
				return hash(seed + (int)(level.getGameTime() / 20)) / 2147483647.0f * 0.5f + 0.5f;
			}

			double dx = WizardTowerCompassItem.getClientTargetX() + 0.5 - entity.getX();
			double dz = WizardTowerCompassItem.getClientTargetZ() + 0.5 - entity.getZ();
			// MC 坐标系：+X=东, +Z=南。atan2(-dx, dz) 使 0=南，顺时针增长，匹配 YRot。
			double targetAngle = Math.atan2(-dx, dz);
			// 玩家朝向（弧度，0=南，顺时针）
			double playerAngle = Math.toRadians(entity.getYRot());
			// 相对角度（目标相对于玩家视角的偏转）
			double relative = targetAngle - playerAngle;
			// 归一化到 [0, 1)：0=指前方（南），0.5=指背后（北）
			float angle = (float) Mth.positiveModulo(relative / (2.0 * Math.PI), 1.0);
			// 平滑
			float current = lastAngle[0];
			float diff = angle - current;
			if (diff > 0.5f) diff -= 1.0f;
			if (diff < -0.5f) diff += 1.0f;
			current += diff * 0.1f;
			current = (float) Mth.positiveModulo(current, 1.0);
			lastAngle[0] = current;
			return current;
		};

		ItemProperties.register(ModItems.WIZARD_TOWER_COMPASS.get(), compassAngle, compassProperty);
	}

	/** 简单的整数哈希（用于非星露谷维度时的随机旋转） */
	private static int hash(int x) {
		x = ((x >> 16) ^ x) * 0x45d9f3b;
		x = ((x >> 16) ^ x) * 0x45d9f3b;
		x = (x >> 16) ^ x;
		return x & 0x7FFFFFFF;
	}
}
