package com.stardew.craft.item.tool;

import com.stardew.craft.fishing.server.FishingSessionManager;
import com.stardew.craft.fishing.FishingCastPower;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.function.Consumer;

@SuppressWarnings("null")
public class FishingRodItem extends net.minecraft.world.item.FishingRodItem implements IStardewItem {
	private static final int CAST_COOLDOWN_TICKS = 10;

	// SDV 鱼竿没有耐久 —— 通过 Unbreakable 组件在构造时设置
	private static final String TAG_ROOT = "StardewFishingRod";
	private static final String TAG_BAIT = "Bait";
	private static final String TAG_TACKLE_1 = "Tackle1";
	private static final String TAG_TACKLE_2 = "Tackle2";
	private static final String TAG_CAST_ACTIVE = "CastActive";
	private static final String TAG_PRESERVING = "Preserving";
	private static final String TAG_DAMAGE = "damage";
	private static final String TAG_CUSTOM_DATA = "custom_data";
	private static final String TAG_FAIR_TEMPORARY = "FairTemporaryRod";
	private static final String TAG_FAIR_ATTACHMENT_SLOTS = "FairAttachmentSlots";

	public enum RodTier {
		BAMBOO_POLE(false, 0),
		TRAINING_ROD(false, 0),
		FIBERGLASS_ROD(true, 0),
		IRIDIUM_ROD(true, 1),
		ADVANCED_IRIDIUM_ROD(true, 2);

		private final boolean canUseBait;
		private final int tackleSlots;

		RodTier(boolean canUseBait, int tackleSlots) {
			this.canUseBait = canUseBait;
			this.tackleSlots = tackleSlots;
		}

		public boolean canUseBait() {
			return canUseBait;
		}

		public int tackleSlots() {
			return tackleSlots;
		}
	}

	public record Attachments(ItemStack bait, ItemStack tackle1, ItemStack tackle2) {
		public static Attachments empty() {
			return new Attachments(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		}
	}

	private final RodTier tier;

	public FishingRodItem(RodTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public RodTier getTier() {
		return tier;
	}

	public boolean canUseBait() {
		return tier.canUseBait();
	}

	private boolean canUseBait(ItemStack rodStack) {
		return canUseBait() || isFairTemporaryRod(rodStack);
	}

	public int getTackleSlots() {
		return tier.tackleSlots();
	}

	private int getTackleSlots(ItemStack rodStack) {
		if (isFairTemporaryRod(rodStack)) {
			return Math.max(getTackleSlots(), Math.max(0, fairAttachmentSlots(rodStack) - 1));
		}
		return getTackleSlots();
	}

	@Override
	public boolean canPerformAction(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") ItemAbility ability) {
		// 关键：让原版FishingHook识别我们的钓竿，不会自动discard
		return ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS.contains(ability);
	}

	@Override
	public String getItemTypeKey() {
		return "stardewcraft.type.fishing";
	}

	@Override
	public boolean isEnchantable(@SuppressWarnings("null") ItemStack stack) {
		return stack.getMaxStackSize() == 1;
	}

	@Override
	public int getEnchantmentValue() {
		return switch (tier) {
			case BAMBOO_POLE, TRAINING_ROD -> 5;
			case FIBERGLASS_ROD -> 10;
			case IRIDIUM_ROD -> 15;
			case ADVANCED_IRIDIUM_ROD -> 20;
		};
	}

	public Attachments getAttachmentsForTooltip(ItemStack rodStack) {
		return new Attachments(getBait(rodStack), getTackle1(rodStack), getTackle2(rodStack));
	}

	public static void configureFairTemporaryRod(ItemStack rodStack, ItemStack attachment0, ItemStack attachment1) {
		if (rodStack == null || !(rodStack.getItem() instanceof FishingRodItem)) {
			return;
		}
		CompoundTag tag = getCustomDataCopy(rodStack);
		CompoundTag root;
		if (tag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
			root = tag.getCompound(TAG_ROOT);
		} else {
			root = new CompoundTag();
			tag.put(TAG_ROOT, root);
		}
		root.putBoolean(TAG_FAIR_TEMPORARY, true);
		root.putInt(TAG_FAIR_ATTACHMENT_SLOTS, 2);
		setCustomData(rodStack, tag);
		writeStack(rodStack, TAG_BAIT, attachment0);
		writeStack(rodStack, TAG_TACKLE_1, attachment1);
	}

	public static boolean isFairTemporaryRod(ItemStack rodStack) {
		CompoundTag root = getRootOrNull(rodStack);
		return root != null && root.getBoolean(TAG_FAIR_TEMPORARY);
	}

	private static int fairAttachmentSlots(ItemStack rodStack) {
		CompoundTag root = getRootOrNull(rodStack);
		if (root == null || !root.contains(TAG_FAIR_ATTACHMENT_SLOTS, CompoundTag.TAG_INT)) {
			return 0;
		}
		return Math.max(0, root.getInt(TAG_FAIR_ATTACHMENT_SLOTS));
	}

	/**
	 * Find the player's currently held fishing rod, checking main hand first then offhand.
	 * Returns {@link ItemStack#EMPTY} if no rod is held.
	 */
	public static ItemStack findRod(Player player) {
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof FishingRodItem) return main;
		ItemStack off = player.getOffhandItem();
		if (off.getItem() instanceof FishingRodItem) return off;
		return ItemStack.EMPTY;
	}
	
	/**
	 * 获取鱼饵效力（星露谷物语逻辑：baitPotency = price / 10）
	 */
	public static double getBaitPotency(ItemStack rodStack) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return 0.0;
		}
		ItemStack bait = rod.getBait(rodStack);
		if (bait.isEmpty()) {
			return 0.0;
		}
		return Math.max(0, StardewItemDataApi.getSellPrice(bait)) / 10.0;
	}
	
	/**
	 * 检查是否装备了指定的鱼饵
	 */
	public static boolean hasBait(ItemStack rodStack, String baitId) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return false;
		}
		ItemStack bait = rod.getBait(rodStack);
		if (bait.isEmpty()) {
			return false;
		}
		@SuppressWarnings("null")
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(bait.getItem());
		return id.toString().equals(baitId);
	}
	
	/**
	 * 检查是否装备了指定的渔具
	 */
	public static boolean hasTackle(ItemStack rodStack, String tackleId) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return false;
		}
		ItemStack t1 = rod.getTackle1(rodStack);
		if (!t1.isEmpty()) {
			@SuppressWarnings("null")
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(t1.getItem());
			if (id.toString().equals(tackleId)) return true;
		}
		ItemStack t2 = rod.getTackle2(rodStack);
		if (!t2.isEmpty()) {
			@SuppressWarnings("null")
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(t2.getItem());
			if (id.toString().equals(tackleId)) return true;
		}
		return false;
	}
	
	/**
	 * 统计装备的指定渔具数量（用于叠加效果）
	 */
	public static int countTackle(ItemStack rodStack, String tackleId) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return 0;
		}
		int count = 0;
		ItemStack t1 = rod.getTackle1(rodStack);
		if (!t1.isEmpty()) {
			@SuppressWarnings("null")
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(t1.getItem());
			if (id.toString().equals(tackleId)) count++;
		}
		ItemStack t2 = rod.getTackle2(rodStack);
		if (!t2.isEmpty()) {
			@SuppressWarnings("null")
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(t2.getItem());
			if (id.toString().equals(tackleId)) count++;
		}
		return count;
	}
	
	/**
	 * 该钓竿是否拥有 Preserving 效果（SV：50% 概率不消耗鱼饵）。
	 *
	 * 兼容旧 CustomData 标记；正式效果读取 MC 附魔 stardewcraft:preserving。
	 */
	public static boolean hasPreserving(ItemStack rodStack) {
		CompoundTag root = getRootOrNull(rodStack);
		return StardewEnchantments.has(rodStack, StardewEnchantments.PRESERVING)
				|| (root != null && root.getBoolean(TAG_PRESERVING));
	}

	/**
	 * 消耗一个鱼饵（钓鱼结算时调用）。
	 *
	 * SV：默认每次结算消耗 1 个鱼饵；若有 Preserving，则 50% 概率不消耗。
	 */
	public static void consumeBait(ServerPlayer player, ItemStack rodStack) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return;
		}
		if (player != null && hasPreserving(rodStack) && player.getRandom().nextFloat() < 0.5f) {
			return;
		}
		ItemStack bait = rod.getBait(rodStack);
		if (bait.isEmpty()) {
			return;
		}
		bait.shrink(1);
		if (bait.isEmpty()) {
			FishingRodItem.writeStack(rodStack, TAG_BAIT, ItemStack.EMPTY);
		} else {
			FishingRodItem.writeStack(rodStack, TAG_BAIT, bait);
		}
	}

	/**
	 * 兼容旧调用点：无玩家上下文时，按“必定消耗”处理。
	 */
	public static void consumeBait(ItemStack rodStack) {
		consumeBait(null, rodStack);
	}
	
	/**
	 * 消耗渔具耐久度（钓鱼成功后调用）
	 */
	public static void consumeTackleDurability(ItemStack rodStack) {
		consumeTackleDurability(null, rodStack);
	}

	public static void consumeTackleDurability(ServerPlayer player, ItemStack rodStack) {
		if (!(rodStack.getItem() instanceof FishingRodItem rod)) {
			return;
		}
		if (player != null && hasPreserving(rodStack) && player.getRandom().nextFloat() < 0.5f) {
			return;
		}
		// 渔具耐久度：使用 Minecraft 原版耐久条（ItemStack damage）。
		// 每次结算成功对每个已装备渔具 damage + 1，到达 maxDamage 时断裂并从槽位移除。
		if (!consumeOneTackleDurability(rod, rodStack, TAG_TACKLE_1)) {
			return;
		}
		consumeOneTackleDurability(rod, rodStack, TAG_TACKLE_2);
	}

	private static boolean consumeOneTackleDurability(FishingRodItem rod, ItemStack rodStack, String tackleKey) {
		ItemStack tackle = readStack(rodStack, tackleKey);
		if (tackle.isEmpty()) {
			return true;
		}
		if (tackle.is(ModItems.LUCKY_PURPLE_SHORTS.get())) {
			return false;
		}
		if (!tackle.isDamageableItem()) {
			return true;
		}
		int nextDamage = tackle.getDamageValue() + 1;
		if (nextDamage >= tackle.getMaxDamage()) {
			FishingRodItem.writeStack(rodStack, tackleKey, ItemStack.EMPTY);
			return true;
		}
		tackle.setDamageValue(nextDamage);
		FishingRodItem.writeStack(rodStack, tackleKey, tackle);
		return true;
	}

	@Override
	public boolean overrideStackedOnOther(@SuppressWarnings("null") ItemStack rodStack, @SuppressWarnings("null") Slot slot, @SuppressWarnings("null") ClickAction action, @SuppressWarnings("null") Player player) {
		// Inventory interactions are handled server-side.
		// In Creative mode, some slot interactions are client-authoritative; handle ONLY the
		// "right-click empty slot to pop an attachment" case client-side to keep Creative usable.
		if (player.level().isClientSide) {
			if (action != ClickAction.SECONDARY) {
				return false;
			}
			if (!player.getAbilities().instabuild) {
				return false;
			}
			if (rodStack.isEmpty()) {
				return false;
			}
			if (!slot.hasItem()) {
				ItemStack popped = popOneAttachment(rodStack);
				if (!popped.isEmpty()) {
					slot.set(popped);
					slot.setChanged();
					return true;
				}
				return false;
			}

			ItemStack target = slot.getItem();
			if (target.isEmpty()) {
				return false;
			}
			if (isBaitItem(target)) {
				return tryInsertBait(rodStack, slot);
			}
			if (isTackleItem(target)) {
				return tryInsertTackle(rodStack, slot);
			}
			return false;
		}
		if (rodStack.isEmpty()) {
			return false;
		}

		// Only right-click should perform the Stardew-like attach/detach behavior.
		// Let vanilla handle left-click moves/swaps.
		if (action != ClickAction.SECONDARY) {
			return false;
		}

		// Right-click an empty slot to pop an attachment out (like the Bundle).
		if (action == ClickAction.SECONDARY && !slot.hasItem()) {
			ItemStack popped = popOneAttachment(rodStack);
			if (!popped.isEmpty()) {
				slot.set(popped);
				slot.setChanged();
				return true;
			}
			return false;
		}

		if (!slot.hasItem()) {
			return false;
		}
		ItemStack target = slot.getItem();
		if (target.isEmpty()) {
			return false;
		}

		if (isBaitItem(target)) {
			return tryInsertBait(rodStack, slot);
		}
		if (isTackleItem(target)) {
			return tryInsertTackle(rodStack, slot);
		}
		return false;
	}

	@Override
	public boolean overrideOtherStackedOnMe(@SuppressWarnings("null") ItemStack rodStack, @SuppressWarnings("null") ItemStack incoming, @SuppressWarnings("null") Slot slot, @SuppressWarnings("null") ClickAction action, @SuppressWarnings("null") Player player, @SuppressWarnings("null") SlotAccess access) {
		if (action != ClickAction.SECONDARY || rodStack.isEmpty() || incoming.isEmpty()) {
			return false;
		}
		boolean inserted = tryInsertAttachment(rodStack, incoming, access::set);
		if (inserted) {
			slot.setChanged();
		}
		return inserted;
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResultHolder<ItemStack> use(@SuppressWarnings("null") Level level, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand) {
		@SuppressWarnings("null")
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(this)) {
			return InteractionResultHolder.fail(stack);
		}

		// 对齐原版：体力 <= 1 时不允许开始抛竿（beginUsing 检查）。
		if (!level.isClientSide
				&& player instanceof ServerPlayer serverPlayer
				&& !player.isCreative()
				&& !player.isSpectator()
				&& player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
			if (PlayerStardewDataAPI.getEnergy(serverPlayer) <= 1.0f) {
				player.displayClientMessage(Component.translatable("stardewcraft.message.player.exhausted"), true);
				return InteractionResultHolder.fail(stack);
			}
		}

		// Client-side: if the bobber is already out, never enter charge mode.
		// This avoids the brief "charge HUD" flash when the server is actually reeling in or starting the minigame.
		if (level.isClientSide && isBobberOut(player)) {
			// Also clear local cast flag immediately on reel-in / interaction.
			setCastActive(player.getMainHandItem(), false);
			setCastActive(player.getOffhandItem(), false);
			return InteractionResultHolder.consume(stack);
		}

		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			FishingSessionManager mgr = FishingSessionManager.get(serverPlayer.server);
			var state = mgr.getState(serverPlayer);
			if (state != null) {
				// 已经在钓鱼：
				// - 等待咬钩(WAITING_BITE)：鱼塘里有鱼时，二次右键直接拉出一条鱼
				// - 咬钩(BITE_READY)：再次右键进入小游戏
				// - 其他状态：再次右键收杆取消（不允许再呼出蓄力条）
				if (state == com.stardew.craft.fishing.server.FishingSession.State.WAITING_BITE
						&& mgr.tryPullFishPondCatch(serverPlayer)) {
					player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
					return InteractionResultHolder.consume(stack);
				}
				if (state == com.stardew.craft.fishing.server.FishingSession.State.BITE_READY) {
					boolean accepted = mgr.tryStartMinigame(serverPlayer);
					if (accepted) {
						player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
						return InteractionResultHolder.consume(stack);
					}
					return InteractionResultHolder.consume(stack);
				}
				if (state == com.stardew.craft.fishing.server.FishingSession.State.HOOKED_ANIM
						|| state == com.stardew.craft.fishing.server.FishingSession.State.MINIGAME) {
					// Don't cancel while the hooked animation/minigame is active.
					player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
					return InteractionResultHolder.consume(stack);
				}

				mgr.cancel(serverPlayer);
				setCastActive(player.getMainHandItem(), false);
				setCastActive(player.getOffhandItem(), false);
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, new com.stardew.craft.fishing.network.FishingRodCastStatePayload(false));
				player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
				return InteractionResultHolder.consume(stack);
			}
		}

		// 进入“蓄力”状态（松手时抛竿）。
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@SuppressWarnings("null")
	public static boolean isBobberOut(Player player) {
		if (player == null) {
			return false;
		}
		if (player.fishing != null && player.fishing.isAlive()) {
			return true;
		}
		Level level = player.level();
		if (level == null) {
			return false;
		}
		// Fallback: sometimes player.fishing isn't populated on the client when the hook is spawned server-side.
		double radius = 96.0;
		for (FishingHook hook : level.getEntitiesOfClass(FishingHook.class, player.getBoundingBox().inflate(radius))) {
			if (hook != null && hook.isAlive() && hook.getOwner() == player) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getUseDuration(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") LivingEntity entity) {
		return 72000;
	}

	@SuppressWarnings("null")
	@Override
	public void releaseUsing(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity livingEntity, int timeLeft) {
		if (!(livingEntity instanceof Player player)) {
			return;
		}
		if (player.getCooldowns().isOnCooldown(this)) {
			return;
		}
		if (level.isClientSide) {
			// Client should flip immediately for rendering; server will authoritative-correct via packet.
			// Write to BOTH hands in case the rod is in offhand
			setCastActive(player.getMainHandItem(), true);
			setCastActive(player.getOffhandItem(), true);
			return;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		int usedTicks = getUseDuration(stack, livingEntity) - timeLeft;
		float castPower01 = FishingCastPower.getCastPower01FromUsedTicks(usedTicks);

		// Vanilla-like: always throw the hook. The server session will only begin bite logic once the hook lands in water.
		boolean started = FishingSessionManager.get(serverPlayer.server).start(serverPlayer, castPower01);
		if (!started) {
			serverPlayer.displayClientMessage(Component.translatable("stardewcraft.fishing.already_fishing"), true);
			return;
		}

		// 对齐原版 SV FishingRod.DoFunction：抛竿开始时扣体力
		// who.Stamina -= 8f - who.FishingLevel * 0.1f;
		if (!player.isCreative() && player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
			int fishingLevel = PlayerStardewDataAPI.getSkillLevel(serverPlayer, SkillType.FISHING);
			float staminaCost = 8.0f - (fishingLevel * 0.1f);
			if (staminaCost > 0.0f) {
				PlayerStardewDataAPI.consumeEnergy(serverPlayer, staminaCost);
			}
		}
		// Server writes to BOTH hands
		setCastActive(player.getMainHandItem(), true);
		setCastActive(player.getOffhandItem(), true);
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, new com.stardew.craft.fishing.network.FishingRodCastStatePayload(true));
		level.playSound(null, player.blockPosition(), ModSounds.CAST.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

		player.getCooldowns().addCooldown(this, CAST_COOLDOWN_TICKS);
	}

	public static boolean isCastActive(ItemStack rodStack) {
		CompoundTag root = getRootOrNull(rodStack);
		if (root == null) {
			return false;
		}
		return root.getBoolean(TAG_CAST_ACTIVE);
	}

	public static void setCastActive(ItemStack rodStack, boolean active) {
		if (rodStack == null || rodStack.isEmpty()) {
			return;
		}
		// Get data once and modify it
		CompoundTag tag = getCustomDataCopy(rodStack);
		CompoundTag root;
		if (tag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
			root = tag.getCompound(TAG_ROOT);
		} else {
			root = new CompoundTag();
		}
		
		if (!active) {
			root.remove(TAG_CAST_ACTIVE);
		} else {
			root.putBoolean(TAG_CAST_ACTIVE, true);
		}
		
		tag.put(TAG_ROOT, root);
		setCustomData(rodStack, tag);
	}

	private static CompoundTag getCustomDataCopy(ItemStack stack) {
		@SuppressWarnings("null")
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag() : new CompoundTag();
	}

	@SuppressWarnings("null")
	private static void setCustomData(ItemStack stack, CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
			return;
		}
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static CompoundTag getRootOrNull(ItemStack rodStack) {
		CompoundTag tag = getCustomDataCopy(rodStack);
		if (!tag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
			return null;
		}
		return tag.getCompound(TAG_ROOT);
	}

	@SuppressWarnings("null")
	private static ItemStack readStack(ItemStack rodStack, String key) {
		CompoundTag root = getRootOrNull(rodStack);
		if (root == null || !root.contains(key, CompoundTag.TAG_COMPOUND)) {
			return ItemStack.EMPTY;
		}
		@SuppressWarnings("null")
		CompoundTag stored = root.getCompound(key);
		if (!stored.contains("id", CompoundTag.TAG_STRING)) {
			return ItemStack.EMPTY;
		}
		@SuppressWarnings("null")
		ResourceLocation id = ResourceLocation.tryParse(stored.getString("id"));
		if (id == null) {
			return ItemStack.EMPTY;
		}
		var item = BuiltInRegistries.ITEM.get(id);
		int count = stored.contains("count", CompoundTag.TAG_INT) ? stored.getInt("count") : 1;
		ItemStack stack = new ItemStack(item, Math.max(1, count));
		if (stored.contains(TAG_CUSTOM_DATA, CompoundTag.TAG_COMPOUND)) {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(stored.getCompound(TAG_CUSTOM_DATA)));
		}
		if (stack.isDamageableItem() && stored.contains(TAG_DAMAGE, CompoundTag.TAG_INT)) {
			int dmg = stored.getInt(TAG_DAMAGE);
			if (dmg > 0) {
				stack.setDamageValue(Math.min(dmg, stack.getMaxDamage() - 1));
			}
		}
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	@SuppressWarnings("null")
	private static void writeStack(ItemStack rodStack, String key, ItemStack stack) {
		CompoundTag tag = getCustomDataCopy(rodStack);
		CompoundTag root;
		if (tag.contains(TAG_ROOT, CompoundTag.TAG_COMPOUND)) {
			root = tag.getCompound(TAG_ROOT);
		} else {
			root = new CompoundTag();
			tag.put(TAG_ROOT, root);
		}
		if (stack == null || stack.isEmpty()) {
			root.remove(key);
			setCustomData(rodStack, tag);
			return;
		}
		@SuppressWarnings("null")
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		CompoundTag saved = new CompoundTag();
		saved.putString("id", id.toString());
		saved.putInt("count", stack.getCount());
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData != null) {
			CompoundTag customDataTag = customData.copyTag();
			if (!customDataTag.isEmpty()) {
				saved.put(TAG_CUSTOM_DATA, customDataTag);
			}
		}
		if (stack.isDamageableItem()) {
			int dmg = stack.getDamageValue();
			if (dmg > 0) {
				saved.putInt(TAG_DAMAGE, dmg);
			}
		}
		root.put(key, saved);
		setCustomData(rodStack, tag);
	}

	private ItemStack getBait(ItemStack rodStack) {
		if (!canUseBait(rodStack)) {
			return ItemStack.EMPTY;
		}
		return readStack(rodStack, TAG_BAIT);
	}

	private ItemStack getTackle1(ItemStack rodStack) {
		if (getTackleSlots(rodStack) < 1) {
			return ItemStack.EMPTY;
		}
		return readStack(rodStack, TAG_TACKLE_1);
	}

	private ItemStack getTackle2(ItemStack rodStack) {
		if (getTackleSlots(rodStack) < 2) {
			return ItemStack.EMPTY;
		}
		return readStack(rodStack, TAG_TACKLE_2);
	}

	@SuppressWarnings("null")
	private boolean tryInsertBait(ItemStack rodStack, Slot slot) {
		boolean inserted = tryInsertBait(rodStack, slot.getItem(), slot::set);
		if (inserted) {
			slot.setChanged();
		}
		return inserted;
	}

	@SuppressWarnings("null")
	public boolean tryInsertAttachment(ItemStack rodStack, ItemStack incoming, Consumer<ItemStack> replaceIncoming) {
		if (incoming == null || incoming.isEmpty()) {
			return false;
		}
		if (isBaitItem(incoming)) {
			return tryInsertBait(rodStack, incoming, replaceIncoming);
		}
		if (isTackleItem(incoming)) {
			return tryInsertTackle(rodStack, incoming, replaceIncoming);
		}
		return false;
	}

	public boolean hasAttachmentSlots() {
		return canUseBait() || getTackleSlots() > 0;
	}

	public boolean canAcceptAttachment(ItemStack incoming) {
		if (incoming == null || incoming.isEmpty()) {
			return false;
		}
		return (isBaitItem(incoming) && canUseBait()) || (isTackleItem(incoming) && getTackleSlots() > 0);
	}

	private static boolean isBaitItem(ItemStack stack) {
		return stack.is(ModItems.BAIT.get())
				|| stack.is(ModItems.MAGNET.get())
				|| stack.is(ModItems.WILD_BAIT.get())
				|| stack.is(ModItems.MAGIC_BAIT.get())
				|| stack.is(ModItems.DELUXE_BAIT.get())
				|| stack.is(ModItems.CHALLENGE_BAIT.get())
				|| stack.is(ModItems.TARGETED_BAIT.get());
	}

	private static boolean isTackleItem(ItemStack stack) {
		return stack.is(ModItems.SPINNER.get())
				|| stack.is(ModItems.DRESSED_SPINNER.get())
				|| stack.is(ModItems.TRAP_BOBBER.get())
				|| stack.is(ModItems.CORK_BOBBER.get())
				|| stack.is(ModItems.LEAD_BOBBER.get())
				|| stack.is(ModItems.TREASURE_HUNTER.get())
				|| stack.is(ModItems.BARBED_HOOK.get())
				|| stack.is(ModItems.CURIOSITY_LURE.get())
				|| stack.is(ModItems.QUALITY_BOBBER.get())
				|| stack.is(ModItems.SONAR_BOBBER.get())
				|| stack.is(ModItems.LUCKY_PURPLE_SHORTS.get());
	}

	@SuppressWarnings("null")
	private boolean tryInsertBait(ItemStack rodStack, ItemStack incoming, Consumer<ItemStack> replaceIncoming) {
		if (!canUseBait(rodStack)) {
			return false;
		}
		if (incoming.isEmpty()) {
			return false;
		}
		ItemStack existing = getBait(rodStack);
		if (existing.isEmpty()) {
			// Move the entire stack into the rod (matches Stardew: bait is stored as a stack).
			writeStack(rodStack, TAG_BAIT, incoming.copy());
			replaceIncoming.accept(ItemStack.EMPTY);
			return true;
		}
		// Same bait: merge counts into the rod.
		if (ItemStack.isSameItemSameComponents(existing, incoming)) {
			int total = existing.getCount() + incoming.getCount();
			existing.setCount(total);
			writeStack(rodStack, TAG_BAIT, existing);
			replaceIncoming.accept(ItemStack.EMPTY);
			return true;
		}
		// Different bait: swap (bag-like behavior).
		writeStack(rodStack, TAG_BAIT, incoming.copy());
		replaceIncoming.accept(existing);
		return true;
	}

	@SuppressWarnings("null")
	private boolean tryInsertTackle(ItemStack rodStack, Slot slot) {
		boolean inserted = tryInsertTackle(rodStack, slot.getItem(), slot::set);
		if (inserted) {
			slot.setChanged();
		}
		return inserted;
	}

	@SuppressWarnings("null")
	private boolean tryInsertTackle(ItemStack rodStack, ItemStack incoming, Consumer<ItemStack> replaceIncoming) {
		int tackleSlots = getTackleSlots(rodStack);
		if (tackleSlots <= 0) {
			return false;
		}
		if (incoming.isEmpty()) {
			return false;
		}
		// Tackle is treated as a single item.
		ItemStack one = incoming.copy();
		one.setCount(1);

		if (tackleSlots >= 1 && getTackle1(rodStack).isEmpty()) {
			writeStack(rodStack, TAG_TACKLE_1, one);
			incoming.shrink(1);
			if (incoming.isEmpty()) {
				replaceIncoming.accept(ItemStack.EMPTY);
			}
			return true;
		}
		if (tackleSlots >= 2 && getTackle2(rodStack).isEmpty()) {
			writeStack(rodStack, TAG_TACKLE_2, one);
			incoming.shrink(1);
			if (incoming.isEmpty()) {
				replaceIncoming.accept(ItemStack.EMPTY);
			}
			return true;
		}

		// If no empty tackle slot, swap with slot 1.
		ItemStack existing = getTackle1(rodStack);
		writeStack(rodStack, TAG_TACKLE_1, one);
		incoming.shrink(1);
		if (incoming.isEmpty()) {
			replaceIncoming.accept(existing);
		} else {
			// Put swapped tackle back onto the slot by adding it.
			if (!existing.isEmpty()) {
				incoming.grow(existing.getCount());
			}
			// keep the slot stack as-is (it was shrunk)
		}
		return true;
	}

	public ItemStack popOneAttachment(ItemStack rodStack) {
		// Pop order: tackle2 -> tackle1 -> bait
		int tackleSlots = getTackleSlots(rodStack);
		if (tackleSlots >= 2) {
			ItemStack t2 = getTackle2(rodStack);
			if (!t2.isEmpty()) {
				writeStack(rodStack, TAG_TACKLE_2, ItemStack.EMPTY);
				return t2;
			}
		}
		if (tackleSlots >= 1) {
			ItemStack t1 = getTackle1(rodStack);
			if (!t1.isEmpty()) {
				writeStack(rodStack, TAG_TACKLE_1, ItemStack.EMPTY);
				return t1;
			}
		}
		if (canUseBait(rodStack)) {
			ItemStack bait = getBait(rodStack);
			if (!bait.isEmpty()) {
				writeStack(rodStack, TAG_BAIT, ItemStack.EMPTY);
				return bait;
			}
		}
		return ItemStack.EMPTY;
	}
}
