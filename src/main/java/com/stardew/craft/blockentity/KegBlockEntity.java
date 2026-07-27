package com.stardew.craft.blockentity;

import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.FlavoredArtisanOutputResolver;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 小桶方块实体
 * 将作物加工为果酒/果汁/饮品
 */
public class KegBlockEntity extends TimedProductionBlockEntity {
	private static final String TAG_INPUT = "input";
	private static final String TAG_PRODUCT = "product";
	private static final String TAG_READY_AT = "readyAtAbsMinute";
	private static final String TAG_READY = "ready";

	public record RemainingTime(int days, int hours, int minutes) {}

	public KegBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.KEG.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, KegBlockEntity be) {
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

	@SuppressWarnings("null")
	public static void clientTick(Level level, BlockPos pos, BlockState state, KegBlockEntity be) {
		if (!be.isWorking()) {
			return;
		}
		if (level.random.nextInt(8) != 0) {
			return;
		}
		double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
		double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
		double y1 = pos.getY() + 1.5;
		double y2 = pos.getY() + 1.4;
		level.addParticle(ParticleTypes.BUBBLE_POP, x, y1, z, 0.0, 0.01, 0.0);
		level.addParticle(ParticleTypes.SPLASH, x, y2, z, 0.0, 0.02, 0.0);
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

	public boolean hasInput() {
		return !input.isEmpty();
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
	public boolean tryInsert(ItemStack stack, Player player) {
		return tryInsertWithResult(stack, player).inserted();
	}

	@SuppressWarnings("null")
	public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
		if (stack.isEmpty()) {
			return InsertResult.fail();
		}
		if (!product.isEmpty() || readyAtAbsMinute >= 0) {
			return InsertResult.fail();
		}

		Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("keg", stack);
		if (recipeOpt.isEmpty()) {
			return InsertResult.fail();
		}
		ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
		int consumeCount = recipe.consumeCount();
		if (stack.getCount() < consumeCount) {
			return InsertResult.missing(new MissingItemRequirement(stack.getItem(), consumeCount));
		}
		Item outputItem = BuiltInRegistries.ITEM.get(recipe.outputId());
		ItemStack output = new ItemStack(outputItem, recipe.outputCount());
		if (recipe.preserveType() != null) {
			FlavoredArtisanOutputResolver.apply(recipe.preserveType(), stack, output);
		}
		QualityHelper.setQuality(output, QualityHelper.NORMAL);
		var plan = prepareProduction(
				stack, output, recipe.minutes(),
				player, false);
		if (plan.isEmpty()) {
			return InsertResult.fail();
		}
		startWork(stack, plan.get(), consumeCount, player);
		return InsertResult.success();
	}

	private void startWork(
			ItemStack inputStack,
			com.stardew.craft.api.v1.machine.StardewProductionPlan plan,
			int consumeCount,
			Player player
	) {
		commitProduction(
				inputStack, plan, consumeCount, player);
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
		if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
			return stack;
		}

		Optional<ArtisanRecipeDataManager.Recipe> recipeOpt = ArtisanRecipeDataManager.getRecipe("keg", stack);
		if (recipeOpt.isEmpty()) {
			return stack;
		}
		ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
		int consumeCount = recipe.consumeCount();
		if (stack.getCount() < consumeCount) {
			return stack;
		}
		Item outputItem = BuiltInRegistries.ITEM.get(recipe.outputId());
		ItemStack output = new ItemStack(outputItem, recipe.outputCount());
		if (recipe.preserveType() != null) {
			FlavoredArtisanOutputResolver.apply(recipe.preserveType(), stack, output);
		}
		QualityHelper.setQuality(output, QualityHelper.NORMAL);
		int minutes = recipe.minutes();
		var plan = prepareProduction(
				stack, output, minutes,
				null, true);
		if (plan.isEmpty()) {
			return stack;
		}

		if (simulate) {
			return AutomationStackHelper.remainderAfterInsert(stack, consumeCount);
		}

		ItemStack inputCopy = stack.copy();
		startWork(
				inputCopy, plan.get(), consumeCount, null);
		return AutomationStackHelper.remainderAfterInsert(stack, consumeCount);
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
	private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
		BooleanProperty workingProp = com.stardew.craft.block.utility.KegBlock.WORKING;
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
		// Keg visuals can span beyond one block when using model-driven extension geometry.
		return new AABB(worldPosition).inflate(2.0D, 1.0D, 2.0D);
	}
}
