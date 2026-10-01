package com.stardew.craft.event;

import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WildTreeChopEvents {
	private WildTreeChopEvents() {
	}

	private static final float BASE_CHOP_SPEED = 6.0f;
	private static final float OTHER_AXE_DAMAGE_MULTIPLIER = 0.55f;
	private static final int MODERN_LOG_HEALTH = 10;
	private static final int MODERN_ROOT_HEALTH = 13;
	private static final int MODERN_BRANCH_HEALTH = 12;
	private static final double HEALTH_DENOM_BASE = 2.2;
	private static final double HEALTH_DENOM_PER = 0.035;
	private static final Map<UUID, Long> LAST_EXHAUST_WARN_TICK = new ConcurrentHashMap<>();

	public static void removePlayer(UUID playerId) {
		LAST_EXHAUST_WARN_TICK.remove(playerId);
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		if (!((Object) event.getEntity() instanceof Player player)) {
			return;
		}
		if (player.isCreative()) {
			return;
		}
		ItemStack tool = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!isAxeLike(tool)) {
			return;
		}

		BlockPos pos = event.getPosition().orElse(null);
		if (pos == null) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(pos);
		WildTrees.Def def = findChoppableTreeDef(state);
		if (def == null) {
			return;
		}

		// Energy gate: only applies in Stardew Valley dimension.
		if (!level.isClientSide
			&& player instanceof ServerPlayer serverPlayer
			&& player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
			if (!PlayerStardewDataAPI.canConsumeEnergy(serverPlayer, Float.MIN_NORMAL)) {
				// Stop mining progress and show message (rate-limited).
				event.setNewSpeed(0.0f);
				long now = level.getGameTime();
				long last = LAST_EXHAUST_WARN_TICK.getOrDefault(serverPlayer.getUUID(), 0L);
				if (now - last >= 20) {
					com.stardew.craft.network.payload.HudHintPayload.send(
							serverPlayer, "stardewcraft.message.player.exhausted");
					LAST_EXHAUST_WARN_TICK.put(serverPlayer.getUUID(), now);
				}
				return;
			}
		}
		int health = computeModernBlockHealth(state, def);
		float damageMul = (float) getStardewAxeDamageMultiplier(tool);
		double denom = HEALTH_DENOM_BASE + (double) health * HEALTH_DENOM_PER;
		float normalized = (float) (BASE_CHOP_SPEED * damageMul / Math.max(0.001, denom));
		event.setNewSpeed((float) Math.max(0.01, applyMiningSpeedModifiers(player, tool, normalized)));
	}

	private static double getStardewAxeDamageMultiplier(ItemStack tool) {
		boolean powerful = StardewEnchantments.has(tool, StardewEnchantments.POWERFUL);
		if (tool.getItem() instanceof StardewAxeItem stardewAxe) {
			int tier = stardewAxe.getStardewTier().ordinal();
			if (powerful) {
				tier = Math.min(4, tier + 2);
			}
			return switch (tier) {
				case 0 -> OTHER_AXE_DAMAGE_MULTIPLIER;
				case 1 -> 0.75;
				case 2 -> 1.0;
				case 3 -> 1.5;
				default -> 5.0;
			};
		}
		// Any other axe acts like tier-0 when chopping our wild trees.
		return OTHER_AXE_DAMAGE_MULTIPLIER;
	}

	private static WildTrees.Def findChoppableTreeDef(BlockState state) {
		for (WildTrees.Def candidate : WildTrees.ALL) {
			if (isModernWood(candidate, state)) {
				return candidate;
			}
		}
		return null;
	}

	@SuppressWarnings("null")
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
		if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
			com.stardew.craft.tree.prefab.PrefabTreeChopHandler.onBlockBreak(
					player, player.serverLevel(), event.getPos(), event);
		}
	}

	@SuppressWarnings("null")
	private static boolean isAxeLike(ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		// Prefer vanilla tag if available.
		if (tool.is(ItemTags.AXES)) {
			return true;
		}
		// Ensure our axes are always recognized even if tag data isn't applied in some dev setups.
		if (tool.getItem() instanceof StardewAxeItem) {
			return true;
		}
		// Recognize any other modded axe-like tool via MinecraftForge tool ability.
		return tool.canPerformAction(ToolActions.AXE_DIG);
	}

	private static boolean isModernWood(WildTrees.Def def, BlockState state) {
		return def.isModernRoot(state) || def.isModernLog(state) || def.isModernBranch(state);
	}

	private static int computeModernBlockHealth(BlockState state, WildTrees.Def def) {
		int health;
		if (def.isModernRoot(state)) {
			health = MODERN_ROOT_HEALTH;
		} else if (def.isModernBranch(state)) {
			health = MODERN_BRANCH_HEALTH;
		} else {
			health = MODERN_LOG_HEALTH;
		}
		return isHardwoodTree(def) ? Math.round(health * 1.2F) : health;
	}

	private static float applyMiningSpeedModifiers(Player player, ItemStack tool, float baseSpeed) {
		float speed = baseSpeed;
		int efficiency = getItemEnchantmentLevel(player, tool, Enchantments.BLOCK_EFFICIENCY);
		if (efficiency > 0) {
			speed += (float) (efficiency * efficiency + 1);
		}

		@SuppressWarnings("null")
		MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
		if (haste != null) {
			speed *= 1.0F + 0.2F * (haste.getAmplifier() + 1);
		}

		@SuppressWarnings("null")
		MobEffectInstance fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
		if (fatigue != null) {
			float mult = switch (fatigue.getAmplifier()) {
				case 0 -> 0.3F;
				case 1 -> 0.09F;
				case 2 -> 0.0027F;
				case 3 -> 8.1E-4F;
				default -> 2.43E-4F;
			};
			speed *= mult;
		}

		if (player.isInWater()) {
			speed /= 5.0F;
		}
		if (!player.onGround()) {
			speed /= 5.0F;
		}
		return speed;
	}

	@SuppressWarnings({ "null", "deprecation" })
	// PORT(1.20.1): vanilla enchantments are code-registered singletons (no data-driven ResourceKey lookup);
	// EnchantmentHelper.getItemEnchantmentLevel goes through Forge's ItemStack#getEnchantmentLevel like NeoForge's.
	private static int getItemEnchantmentLevel(Player player, ItemStack stack, Enchantment enchantment) {
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
	}

	private static boolean isHardwoodTree(WildTrees.Def def) {
		String id = def.id();
		return "mahogany".equals(id) || "mystic_tree".equals(id);
	}
}
