package com.stardew.craft.blockentity;

import com.stardew.craft.production.MachineProductionData;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.block.utility.BeeHouseBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bee House block entity.
 * No input needed; produces honey on a cycle, and determines flavored honey at harvest time.
 */
public class BeeHouseBlockEntity extends TimedProductionBlockEntity {
	private static final int WILD_HONEY_VALUE = 100;

	private static final String TAG_READY_AT = "readyAtAbsMinute";
	private static final String TAG_READY = "ready";


	private record FlowerRule(Block block, Item item, int value) {}

	private static final List<FlowerRule> FLOWERS = List.of(
		new FlowerRule(ModBlocks.FAIRY_ROSE_CROP.get(), ModItems.FAIRY_ROSE.get(), 680),
		new FlowerRule(ModBlocks.POPPY_CROP.get(), ModItems.POPPY.get(), 380),
		new FlowerRule(ModBlocks.SUMMER_SPANGLE_CROP.get(), ModItems.SUMMER_SPANGLE.get(), 280),
		new FlowerRule(ModBlocks.SUNFLOWER_CROP.get(), ModItems.SUNFLOWER.get(), 260),
		new FlowerRule(ModBlocks.BLUE_JAZZ_CROP.get(), ModItems.BLUE_JAZZ.get(), 200),
		new FlowerRule(ModBlocks.TULIP_CROP.get(), ModItems.TULIP.get(), 160)
	);

	public record RemainingTime(int days, int hours, int minutes) {}

	public BeeHouseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BEE_HOUSE.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BeeHouseBlockEntity be) {
		if (level.isClientSide) {
			return;
		}
		be.tickServer(level, pos, state);
	}

	private void tickServer(Level level, BlockPos pos, BlockState state) {
		if (!canProduce(level, pos)) {
			if (ready || readyAtAbsMinute >= 0) {
				clearState(level, pos, state);
			}
			return;
		}

		if (readyAtAbsMinute < 0) {
			startCycle(level, pos, state);
			return;
		}

		boolean newReady = refreshReady();
		if (newReady != ready) {
			ready = newReady;
			setChanged();
			syncToClient();
			updateReadyState(level, pos, state);
		}
	}

	private void startCycle(Level level, BlockPos pos, BlockState state) {
		readyAtAbsMinute = MachineProductionData.cycle("bee_house", "default").deadline(getCurrentAbsMinute(), "bee_house");
		ready = false;
		setChanged();
		syncToClient();
		updateReadyState(level, pos, state);
	}

	private void clearState(Level level, BlockPos pos, BlockState state) {
		readyAtAbsMinute = -1;
		ready = false;
		setChanged();
		syncToClient();
		updateReadyState(level, pos, state);
	}

	@SuppressWarnings("null")
	private void updateReadyState(Level level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BeeHouseBlock.READY)) {
			return;
		}
		if (state.getValue(BeeHouseBlock.READY) != ready) {
			level.setBlock(pos, state.setValue(BeeHouseBlock.READY, ready), 3);
			BlockPos extensionPos = BeeHouseBlock.getExtensionPos(pos, state);
			BlockState extensionState = level.getBlockState(extensionPos);
			if (extensionState.is(state.getBlock()) && extensionState.hasProperty(BeeHouseBlock.READY)) {
				level.setBlock(extensionPos, extensionState.setValue(BeeHouseBlock.READY, ready), 3);
			}
		}
	}

	@Override
	protected boolean computeReady() {
		if (readyAtAbsMinute < 0) {
			return false;
		}
		return getCurrentAbsMinute() >= readyAtAbsMinute;
	}

	@Override
	protected boolean readyCheckRequiresProduct() {
		return false;
	}

	public boolean isReady() {
		return ready;
	}

	public boolean isWorking() {
		return !ready && readyAtAbsMinute >= 0;
	}

	public boolean canApplyFairyDust() {
		return isWorking();
	}

	public boolean applyFairyDust() {
		if (!canApplyFairyDust()) {
			return false;
		}
		Level currentLevel = level;
		if (currentLevel == null || currentLevel.isClientSide) {
			return false;
		}
		readyAtAbsMinute = getCurrentAbsMinute();
		ready = true;
		setChanged();
		syncToClient();
		updateReadyState(currentLevel, worldPosition, getBlockState());
		return true;
	}

	public RemainingTime getRemainingTime() {
		long remaining = getRemainingAbsMinutes();
		int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
		int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
		int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
		int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
		return new RemainingTime(days, hours, minutes);
	}

	@Override
	public long getRemainingAbsMinutes() {
		if (ready || readyAtAbsMinute < 0) {
			return 0;
		}
		return Math.max(0, readyAtAbsMinute - getCurrentAbsMinute());
	}

	public ItemStack getCurrentProduct() {
		if (!ready || level == null) {
			return ItemStack.EMPTY;
		}
		return createHoneyStack(level, worldPosition);
	}

	public ItemStack harvestOne(Player player) {
		if (!ready || level == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = createHoneyStack(level, worldPosition);
		clearState(level, worldPosition, getBlockState());
		if (canProduce(level, worldPosition)) {
			startCycle(level, worldPosition, getBlockState());
		}
		return out;
	}

	@Override
	public ItemStack getAutomationInput() {
		return ItemStack.EMPTY;
	}

	@Override
	public ItemStack getAutomationOutput() {
		return getCurrentProduct();
	}

	@Override
	public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
		return stack;
	}

	@Override
	public ItemStack extractAutomation(int amount, boolean simulate) {
		ItemStack current = getCurrentProduct();
		if (current.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = AutomationStackHelper.extractUpTo(current, amount);
		if (simulate) {
			return out;
		}
		if (out.getCount() >= current.getCount()) {
			return harvestOne(null);
		}
		return out;
	}

	@SuppressWarnings("null")
	public ItemStack getNearbyFlowerItem() {
		if (level == null) {
			return ItemStack.EMPTY;
		}
		FlowerRule rule = findNearbyFlower(level, worldPosition);
		if (rule == null) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(rule.item());
	}

	@SuppressWarnings("null")
	private static ItemStack createHoneyStack(Level level, BlockPos pos) {
		ItemStack stack = new ItemStack(ModItems.HONEY.get());
		QualityHelper.setQuality(stack, QualityHelper.NORMAL);

		FlowerRule flower = findNearbyFlower(level, pos);
		int honeyValue = WILD_HONEY_VALUE;
		if (flower != null) {
			ItemStack flowerStack = new ItemStack(flower.item());
			stack.set(DataComponents.CUSTOM_NAME, Component.translatable(
				"stardewcraft.honey.flavored",
				flowerStack.getHoverName()
			).withStyle(style -> style.withItalic(false)));
			honeyValue = flower.value();
		}

		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = data.copyTag();
		tag.putInt("HoneyValue", honeyValue);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		return stack;
	}

	@SuppressWarnings("null")
	private static FlowerRule findNearbyFlower(Level level, BlockPos pos) {
		FlowerRule best = null;
		int bestDist = Integer.MAX_VALUE;

		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int dist = Math.abs(dx) + Math.abs(dz);
				if (dist > 5) {
					continue;
				}
				BlockPos checkPos = pos.offset(dx, 0, dz);
				BlockState state = level.getBlockState(checkPos);
				FlowerRule rule = matchFlower(state.getBlock());
				if (rule == null) {
					continue;
				}
				if (!isMatureFlower(state)) {
					continue;
				}
				if (dist < bestDist) {
					best = rule;
					bestDist = dist;
					continue;
				}
				if (dist == bestDist && best != null && rule.value() > best.value()) {
					best = rule;
				}
			}
		}

		return best;
	}

	private static FlowerRule matchFlower(Block block) {
		for (FlowerRule rule : FLOWERS) {
			if (rule.block() == block) {
				return rule;
			}
		}
		return null;
	}

	@SuppressWarnings("null")
	private static boolean isMatureFlower(BlockState state) {
		if (state.hasProperty(StardewCropBlock.AGE)) {
			return state.getValue(StardewCropBlock.AGE) >= StardewCropBlock.MAX_AGE;
		}
		return true;
	}

	@SuppressWarnings("null")
	private static boolean canProduce(Level level, BlockPos pos) {
		if (!level.canSeeSky(pos.above())) {
			return false;
		}
		StardewTimeManager tm = StardewTimeManager.get();
		return tm.getCurrentSeason() != 3;
	}


	/**
	 * Debug/utility: advance the current production timer by N days.
	 */
	@SuppressWarnings("null")
	public void advanceDays(int days) {
		if (days <= 0) {
			return;
		}
		if (level == null || level.isClientSide) {
			return;
		}
		if (readyAtAbsMinute < 0) {
			return;
		}
		long delta = (long) days * (long) EFFECTIVE_MINUTES_PER_DAY;
		readyAtAbsMinute = Math.max(0, readyAtAbsMinute - delta);
		boolean newReady = refreshReady();
		if (newReady != ready) {
			ready = newReady;
			updateReadyState(level, worldPosition, getBlockState());
		}
		setChanged();
		syncToClient();
	}

	@SuppressWarnings("null")
	@Override
	protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putLong(TAG_READY_AT, readyAtAbsMinute);
		tag.putBoolean(TAG_READY, ready);
	}

	@SuppressWarnings("null")
	@Override
	protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		readyAtAbsMinute = tag.getLong(TAG_READY_AT);
		ready = tag.getBoolean(TAG_READY);
	}

	@Override
	public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

}
