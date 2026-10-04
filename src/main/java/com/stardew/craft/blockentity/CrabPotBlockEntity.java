package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.book.BookPowerEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 蟹笼方块实体
 * 每天检查一次是否捕获物品
 * 需要鱼饵才能工作
 * 
 * 逻辑参考星露谷物语源代码 CrabPot.cs
 */
public class CrabPotBlockEntity extends BlockEntity implements UtilityAutomationAccess, AdvanceableUtility {
	private static final String TAG_BAIT = "bait";
	private static final String TAG_PRODUCT = "product";
	private static final String TAG_READY = "ready";
	private static final String TAG_LAST_CHECK_DAY = "lastCheckDay";
	private static final String TAG_OWNER = "ownerPlayerUuid";

	private ItemStack bait = ItemStack.EMPTY;
	private ItemStack product = ItemStack.EMPTY;
	private boolean ready = false;
	private int lastCheckDay = -1;
	private UUID ownerPlayerId;
	private static final IItemHandler NO_AUTOMATION = new IItemHandler() {
		@Override
		public int getSlots() {
			return 0;
		}

		@Override
		@Nonnull
		public ItemStack getStackInSlot(int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		@Nonnull
		public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
			return stack;
		}

		@Override
		@Nonnull
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 0;
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
			return false;
		}
	};

	@SuppressWarnings("null")
	public CrabPotBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRAB_POT.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrabPotBlockEntity be) {
		if (level.isClientSide) {
			return;
		}

		// 检查是否到达新的一天
		int currentDay = getCurrentDay();
		if (currentDay != be.lastCheckDay) {
			be.lastCheckDay = currentDay;
			
			// 执行每日更新逻辑
			be.dayUpdate(level, pos);
		}

		// 检查是否准备就绪
		boolean newReady = !be.product.isEmpty();
		if (newReady != be.ready) {
			be.ready = newReady;
			be.setChanged();
			be.syncToClient();
		}
	}

	/**
	 * 每日更新逻辑（参考 CrabPot.DayUpdate）
	 */
	private void dayUpdate(Level level, BlockPos pos) {
		boolean hasMariner = hasOwnerProfession(ProfessionType.MARINER);
		boolean hasLuremaster = hasOwnerProfession(ProfessionType.LUREMASTER);

		// 需要鱼饵且没有产物才能捕获
		if (!canWorkToday(hasLuremaster)) {
			return;
		}

		RandomSource random = level.random;

		// 获取当前群系
		@SuppressWarnings("null")
		Holder<Biome> biome = level.getBiome(pos);
		boolean isOceanBiome = isOceanBiome(biome);
		boolean beachFarmPond = false;
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			var farms = com.stardew.craft.farm.FarmInstanceRegistry.get(serverLevel.getServer());
			var owner = farms.getOwnerAt(pos);
			var farm = owner == null ? null : farms.getFarm(owner);
			if (farm != null && farm.contains(pos) && farm.getFarmLayoutId().equals(
					com.stardew.craft.api.v1.internal.farm.StardewFarmLayoutRegistry
							.builtinId(com.stardew.craft.farm.FarmType.BEACH))) {
				BlockPos local = pos.subtract(farm.getOrigin());
				beachFarmPond = local.getX() >= 115 && local.getX() <= 181
						&& local.getZ() >= 34 && local.getZ() <= 79;
				isOceanBiome = !beachFarmPond;
			}
		}

		// Beach Farm's freshwater pond has the source's exceptional 45% junk rate.
		double junkChance = hasMariner ? 0.0 : beachFarmPond ? 0.45 : 0.2;

		// 读取鱼饵ID判断效果
		@SuppressWarnings("null")
		ResourceLocation baitId = BuiltInRegistries.ITEM.getKey(bait.getItem());
		boolean isDeluxe = false;
		boolean isWild = false;
		boolean isMagic = false;
		if (baitId != null) {
			String baitKey = baitId.toString();
			isDeluxe = baitKey.equals("stardewcraft:deluxe_bait");
			isWild = baitKey.equals("stardewcraft:wild_bait");
			isMagic = baitKey.equals("stardewcraft:magic_bait");
		}

		// 目标鱼饵（SpecificBait）：垃圾概率减半，并提高目标条目的几率。
		String targetFishId = null;
		if (bait.getItem() instanceof com.stardew.craft.item.SpecificBaitItem) {
			String raw = com.stardew.craft.item.SpecificBaitItem.getTargetFishId(bait);
			if (raw != null && !raw.isBlank()) {
				targetFishId = raw;
				junkChance /= 2.0;
			}
		}

		// Deluxe / Wild bait: 垃圾概率减半
		if (isDeluxe || isWild) {
			junkChance /= 2.0;
		}

		ItemStack result;
		if (random.nextDouble() < junkChance) {
			// 捕获垃圾：使用 FishingDataManager 的垃圾池（权威来源）
			result = FishingDataManager.get().getRandomJunk(random);
		} else {
			// 捕获蟹笼物品
			result = getCrabPotCatch(level, pos, isOceanBiome, random, isMagic, hasMariner, targetFishId);
		}

		// Deluxe 增加品质一档；Wild 有 25% 概率产出双倍
		if (!result.isEmpty()) {
			if (isDeluxe) {
				int current = com.stardew.craft.item.quality.QualityHelper.getQuality(result);
				int bumped = Math.min(com.stardew.craft.item.quality.QualityHelper.IRIDIUM, current + 1);
				com.stardew.craft.item.quality.QualityHelper.setQuality(result, bumped);
			}
			if (isWild && random.nextDouble() < 0.25) {
				result.setCount(Math.min(result.getMaxStackSize(), result.getCount() * 2));
			}
			if (ownerPlayerId != null) {
				BookPowerEffects.applyCrabbingDouble(PlayerDataManager.getPlayerData(ownerPlayerId), result, random);
			}
		}

		// 设置产物
		product = result;
		// Luremaster：蟹笼无需消耗鱼饵。
		if (!hasLuremaster) {
			bait = ItemStack.EMPTY;
		}
		
		setChanged();
		syncToClient();
	}

	/**
	 * 蟹笼是否“今天可以工作”。
	 *
	 * 目前规则：
	 * - 必须有鱼饵
	 * - 必须没有产物（不然说明还没收）
	 *
	 * 预留接口：
	 * - Mariner：允许无鱼饵也工作
	 * - Luremaster：不消耗鱼饵
	 */
	private boolean canWorkToday(boolean hasLuremaster) {
		if (!product.isEmpty()) {
			return false;
		}
		return hasLuremaster || !bait.isEmpty();
	}

	private boolean hasOwnerProfession(ProfessionType profession) {
		if (ownerPlayerId == null) {
			return false;
		}
		return PlayerDataManager.getPlayerData(ownerPlayerId).hasProfession(profession);
	}



	/** Data/Fish "trap" entries in source order (CrabPot.DayUpdate iterates them and rolls each one's own chance). */
	private record TrapEntry(String itemId, double chance) {
	}

	private static final List<TrapEntry> TRAP_ENTRIES = List.of(
			new TrapEntry("stardewcraft:lobster", 0.05),
			new TrapEntry("stardewcraft:crab", 0.10),
			new TrapEntry("stardewcraft:oyster", 0.15),
			new TrapEntry("stardewcraft:clam", 0.15),
			new TrapEntry("stardewcraft:shrimp", 0.20),
			new TrapEntry("stardewcraft:cockle", 0.30),
			new TrapEntry("stardewcraft:mussel", 0.35),
			new TrapEntry("stardewcraft:snail", 0.25),
			new TrapEntry("stardewcraft:crayfish", 0.35),
			new TrapEntry("stardewcraft:periwinkle", 0.55));

	/**
	 * 获取蟹笼捕获物
	 * 参考 CrabPot.DayUpdate：按 Data/Fish 中 trap 条目顺序逐个用各自几率掷骰，首个命中即得；
	 * Mariner 则收集所有适配条目后随机取一；目标鱼饵对目标条目几率 ×4/×3/×2。
	 */
	@SuppressWarnings("null")
	private ItemStack getCrabPotCatch(Level level, BlockPos pos, boolean isOcean, RandomSource random,
									  boolean ignoreSeasonTime, boolean mariner, String targetFishId) {
		String season = getCurrentSeasonKey();
		boolean isRaining = com.stardew.craft.weather.WeatherManager.isRaining(level);
		int stardewTime = FishingDataManager.currentStardewTime();

		List<Item> marinerList = new ArrayList<>();
		for (TrapEntry entry : TRAP_ENTRIES) {
			String itemId = entry.itemId();
			if (!matchesCrabPotWaterType(itemId, isOcean)) {
				continue;
			}
			Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
			if (item == net.minecraft.world.item.Items.AIR) {
				continue;
			}
			try {
				var ruleOpt = FishingDataManager.get().getRuleByItemId(itemId);
				if (ruleOpt.isPresent()) {
					var rule = ruleOpt.get();
					if (!rule.matchesBiome(level.getBiome(pos))) continue;
					if (!ignoreSeasonTime
							&& (!rule.matchesSeason(season) || !rule.matchesWeather(isRaining)
							|| !rule.matchesStardewTime(stardewTime))) {
						continue;
					}
				}
			} catch (Exception ignored) {
				// Rule lookup failures keep the entry eligible.
			}
			if (mariner) {
				marinerList.add(item);
				continue;
			}
			double chance = entry.chance();
			if (targetFishId != null && targetFishId.equals(itemId)) {
				chance *= chance < 0.1 ? 4 : (chance < 0.2 ? 3 : 2);
			}
			if (random.nextDouble() < chance) {
				return new ItemStack(item);
			}
		}
		if (mariner && !marinerList.isEmpty()) {
			return new ItemStack(marinerList.get(random.nextInt(marinerList.size())));
		}
		return FishingDataManager.get().getRandomJunk(random);
	}

	private static boolean matchesCrabPotWaterType(String itemId, boolean isOcean) {
		boolean oceanCatch = switch (itemId) {
			case "stardewcraft:lobster", "stardewcraft:crab", "stardewcraft:shrimp", "stardewcraft:clam",
					"stardewcraft:mussel", "stardewcraft:oyster", "stardewcraft:cockle" -> true;
			default -> false;
		};
		boolean freshwaterCatch = switch (itemId) {
			case "stardewcraft:crayfish", "stardewcraft:snail", "stardewcraft:periwinkle" -> true;
			default -> false;
		};
		if (!oceanCatch && !freshwaterCatch) {
			return true;
		}
		return isOcean ? oceanCatch : freshwaterCatch;
	}

	/**
	 * 获取与 FishingDataManager 一致的季节 key（spring/summer/fall/winter）。
	 */
	private static String getCurrentSeasonKey() {
		StardewTimeManager tm = StardewTimeManager.get();
		return switch (tm.getCurrentSeason()) {
			case 0 -> "spring";
			case 1 -> "summer";
			case 2 -> "fall";
			case 3 -> "winter";
			default -> "spring";
		};
	}

	/**
	 * 判断是否是海洋群系
	 */
	private boolean isOceanBiome(Holder<Biome> biome) {
		ResourceLocation biomeId = biome.unwrapKey().orElseThrow().location();
		String path = biomeId.getPath();
		return path.contains("ocean") || path.contains("beach") || path.contains("shore");
	}

	// === 公共 API ===

	public boolean hasBait() {
		return !bait.isEmpty();
	}

	public ItemStack getBait() {
		return bait;
	}

	public IItemHandler getAutomationItemHandler() {
		return NO_AUTOMATION;
	}

	public void setBait(ItemStack baitStack) {
		this.bait = baitStack.copy();
		setChanged();
		syncToClient();
	}

	public void setOwnerIfAbsent(UUID owner) {
		if (ownerPlayerId != null || owner == null) {
			return;
		}
		ownerPlayerId = owner;
		lastCheckDay = getCurrentDay();
		setChanged();
		syncToClient();
	}

	public UUID getOwnerPlayerId() {
		return ownerPlayerId;
	}

	public boolean canAccess(UUID playerId) {
		return ownerPlayerId == null || ownerPlayerId.equals(playerId);
	}

	public ItemStack getProduct() {
		return product;
	}

	public boolean isReady() {
		return ready;
	}

	/**
	 * Debug/utility: force daily update(s) to accelerate progress.
	 */
	@SuppressWarnings("null")
	@Override
	public void advanceDays(int days) {
		if (days <= 0) {
			return;
		}
		if (level == null || level.isClientSide) {
			return;
		}
		for (int i = 0; i < days; i++) {
			dayUpdate(level, worldPosition);
		}
		lastCheckDay = getCurrentDay();
		boolean newReady = !product.isEmpty();
		if (newReady != ready) {
			ready = newReady;
		}
		setChanged();
		syncToClient();
	}

	public void clearProduct() {
		product = ItemStack.EMPTY;
		ready = false;
		setChanged();
		syncToClient();
	}

	@Override
	public ItemStack getAutomationInput() {
		return bait;
	}

	@Override
	public ItemStack getAutomationOutput() {
		return product;
	}

	@Override
	public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
		if (stack.isEmpty() || !bait.isEmpty() || !product.isEmpty()) {
			return stack;
		}
		if (!isBaitItem(stack)) {
			return stack;
		}
		if (simulate) {
			return AutomationStackHelper.remainderAfterInsert(stack, 1);
		}
		ItemStack baitCopy = stack.copy();
		baitCopy.setCount(1);
		setBait(baitCopy);
		return AutomationStackHelper.remainderAfterInsert(stack, 1);
	}

	@Override
	public ItemStack extractAutomation(int amount, boolean simulate) {
		if (product.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = AutomationStackHelper.extractUpTo(product, amount);
		if (simulate) {
			return out;
		}
		if (out.getCount() >= product.getCount()) {
			ItemStack full = product.copy();
			clearProduct();
			return full;
		}
		product.shrink(out.getCount());
		setChanged();
		syncToClient();
		return out;
	}

	@SuppressWarnings("null")
	private static boolean isBaitItem(ItemStack stack) {
		String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return key.equals("stardewcraft:bait")
				|| key.equals("stardewcraft:deluxe_bait")
				|| key.equals("stardewcraft:wild_bait")
				|| key.equals("stardewcraft:magic_bait");
	}

	// === 辅助方法 ===

	private static int getCurrentDay() {
		StardewTimeManager tm = StardewTimeManager.get();
		int year = tm.getCurrentYear();
		int season = tm.getCurrentSeason();
		int day = tm.getCurrentDay();
		// 返回绝对天数
		return (year - 1) * 112 + season * 28 + day;
	}

	@SuppressWarnings("null")
	private void syncToClient() {
		if (level == null || level.isClientSide) {
			return;
		}
		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	// === NBT 序列化 ===

	@SuppressWarnings("null")
	@Override
	protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.saveAdditional(tag);
		if (!bait.isEmpty()) {
			tag.put(TAG_BAIT, PortItemStacks.save(bait, registries));
		}
		if (!product.isEmpty()) {
			tag.put(TAG_PRODUCT, PortItemStacks.save(product, registries));
		}
		tag.putBoolean(TAG_READY, ready);
		tag.putInt(TAG_LAST_CHECK_DAY, lastCheckDay);
		if (ownerPlayerId != null) {
			tag.putUUID(TAG_OWNER, ownerPlayerId);
		}
	}

	@SuppressWarnings("null")
	@Override
	public void load(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.load(tag);
		bait = tag.contains(TAG_BAIT) 
			? PortItemStacks.parse(registries, tag.getCompound(TAG_BAIT)).orElse(ItemStack.EMPTY) 
			: ItemStack.EMPTY;
		product = tag.contains(TAG_PRODUCT) 
			? PortItemStacks.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) 
			: ItemStack.EMPTY;
		ready = tag.getBoolean(TAG_READY);
		lastCheckDay = tag.getInt(TAG_LAST_CHECK_DAY);
		ownerPlayerId = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
	}

	@Override
	public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag);
		return tag;
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
