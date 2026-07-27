package com.stardew.craft.blockentity;

import com.stardew.craft.block.utility.RecyclingMachineBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

@SuppressWarnings("all")
public class RecyclingMachineBlockEntity extends TimedProductionBlockEntity {
	private static final String TAG_INPUT = "input";
	private static final String TAG_PRODUCT = "product";
	private static final String TAG_READY_AT = "readyAtAbsMinute";
	private static final String TAG_READY = "ready";
	private static final int MINUTES_UNTIL_READY = 60;

	public record RemainingTime(int days, int hours, int minutes) {}

	public RecyclingMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RECYCLING_MACHINE.get(), pos, state);
	}

	@Override
	public net.neoforged.neoforge.items.IItemHandler getAutomationItemHandler() {
		return super.getAutomationItemHandler();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RecyclingMachineBlockEntity be) {
		if (level.isClientSide) {
			return;
		}
		boolean newReady = be.refreshReady();
		if (newReady != be.ready) {
			be.ready = newReady;
			be.setChanged();
			be.syncToClient();
		}
		be.updateWorkingState(level, pos, state);
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, RecyclingMachineBlockEntity be) {
	}

	public boolean isReady() {
		return ready;
	}

	public boolean isWorking() {
		return !input.isEmpty() && !ready && readyAtAbsMinute > 0;
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
		updateWorkingState(currentLevel, worldPosition, getBlockState());
		return true;
	}

	public ItemStack getInput() {
		return input;
	}

	public ItemStack getProduct() {
		return product;
	}

	public RemainingTime getRemainingTime() {
		long remaining = getRemainingAbsMinutes();
		int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
		int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
		int hours = minutesRemainder / StardewTimeManager.MINUTES_PER_HOUR;
		int minutes = minutesRemainder % StardewTimeManager.MINUTES_PER_HOUR;
		return new RemainingTime(days, hours, minutes);
	}

	@SuppressWarnings("null")
	public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
		if (stack.isEmpty()) {
			return InsertResult.fail();
		}
		if (!product.isEmpty() || readyAtAbsMinute >= 0) {
			return InsertResult.fail();
		}
		Level currentLevel = level;
		if (currentLevel == null) {
			return InsertResult.fail();
		}

		ItemStack output = createOutput(currentLevel, stack);
		if (output.isEmpty()) {
			return InsertResult.fail();
		}

		var plan = prepareProduction(
				stack, output, MINUTES_UNTIL_READY,
				player, false);
		if (plan.isEmpty()) {
			return InsertResult.fail();
		}
		startWork(stack, plan.get(), player);
		return InsertResult.success();
	}

	private void startWork(
			ItemStack inputStack,
			com.stardew.craft.api.v1.machine.StardewProductionPlan plan,
			@Nullable Player player
	) {
		commitProduction(inputStack, plan, 1, player);
	}

	public ItemStack harvestOne() {
		return collectProduction();
	}

	@Override
	public ItemStack getAutomationInput() {
		return input;
	}

	@Override
	public ItemStack getAutomationOutput() {
		return ready ? product : ItemStack.EMPTY;
	}

	@Override
	@SuppressWarnings("null")
	public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
		if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0 || !isAcceptedInput(stack.getItem())) {
			return stack;
		}

		if (simulate) {
			return AutomationStackHelper.remainderAfterInsert(stack, 1);
		}

		Level currentLevel = level;
		if (currentLevel == null) {
			return stack;
		}

		ItemStack output = createOutput(currentLevel, stack);
		if (output.isEmpty()) {
			return stack;
		}
		var plan = prepareProduction(
				stack, output, MINUTES_UNTIL_READY,
				null, true);
		if (plan.isEmpty()) {
			return stack;
		}

		ItemStack inputCopy = stack.copy();
		startWork(inputCopy, plan.get(), null);
		return AutomationStackHelper.remainderAfterInsert(stack, 1);
	}

	@Override
	public ItemStack extractAutomation(int amount, boolean simulate) {
		if (!ready || product.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = AutomationStackHelper.extractUpTo(product, amount);
		if (simulate) {
			return out;
		}
		if (out.getCount() >= product.getCount()) {
			return harvestOne();
		}
		product.shrink(out.getCount());
		setChanged();
		syncToClient();
		return out;
	}

	@SuppressWarnings("null")
	private static ItemStack createOutput(Level level, ItemStack inputStack) {
		Item item = inputStack.getItem();
		if (item == ModItems.TRASH.get()) {
			if (level.random.nextFloat() < 0.3f) {
				return new ItemStack(ModItems.COAL.get(), randomThree(level));
			}
			if (level.random.nextFloat() < 0.3f) {
				return new ItemStack(ModItems.IRON_ORE.get(), randomThree(level));
			}
			return new ItemStack(ModItems.STONE.get(), randomThree(level));
		}
		if (item == ModItems.DRIFTWOOD.get()) {
			if (level.random.nextFloat() < 0.25f) {
				return new ItemStack(ModItems.COAL.get(), randomThree(level));
			}
			return new ItemStack(ModItems.WOOD_NORMAL.get(), randomThree(level));
		}
		if (item == ModItems.BROKEN_GLASSES.get() || item == ModItems.BROKEN_CD.get()) {
			return new ItemStack(ModItems.REFINED_QUARTZ.get());
		}
		if (item == ModItems.SOGGY_NEWSPAPER.get()) {
			if (level.random.nextFloat() < 0.1f) {
				return new ItemStack(ModItems.CLOTH.get());
			}
			return new ItemStack(Items.TORCH, 3);
		}
		return ItemStack.EMPTY;
	}

	private static int randomThree(Level level) {
		return level.random.nextInt(3) + 1;
	}

	private static boolean isAcceptedInput(Item item) {
		return item == ModItems.TRASH.get()
			|| item == ModItems.DRIFTWOOD.get()
			|| item == ModItems.BROKEN_GLASSES.get()
			|| item == ModItems.BROKEN_CD.get()
			|| item == ModItems.SOGGY_NEWSPAPER.get();
	}

	@SuppressWarnings("null")
	private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
		BooleanProperty workingProp = RecyclingMachineBlock.WORKING;
		boolean workingNow = isWorking();
		if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
			level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
		}
	}

	@SuppressWarnings("null")
	@Override
	protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (!input.isEmpty()) {
			tag.put(TAG_INPUT, input.save(registries));
		}
		if (!product.isEmpty()) {
			tag.put(TAG_PRODUCT, product.save(registries));
		}
		tag.putLong(TAG_READY_AT, readyAtAbsMinute);
		tag.putBoolean(TAG_READY, ready);
	}

	@SuppressWarnings("null")
	@Override
	protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		product = tag.contains(TAG_PRODUCT) ? ItemStack.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
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

	@SuppressWarnings("null")
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(2.0D, 1.0D, 2.0D);
	}
}
