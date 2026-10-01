package com.stardew.craft.blockentity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesIngredientDataManager;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.particles.ParticleTypes;

import javax.annotation.Nullable;

/**
 * Preserves jar block entity.
 */
public class PreservesJarBlockEntity extends TimedProductionBlockEntity {
    private boolean modelFootprintChecked;

	private static final String TAG_INPUT = "input";
	private static final String TAG_PRODUCT = "product";
	private static final String TAG_READY_AT = "readyAtAbsMinute";
	private static final String TAG_READY = "ready";

	public record RemainingTime(int days, int hours, int minutes) {}

	public PreservesJarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PRESERVES_JAR.get(), pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PreservesJarBlockEntity be) {
		if (level.isClientSide) {
			return;
		}
        if (!be.modelFootprintChecked) {
            be.modelFootprintChecked = com.stardew.craft.block.utility.MachineModelFootprint.repair(level, pos, state);
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
	public static void clientTick(Level level, BlockPos pos, BlockState state, PreservesJarBlockEntity be) {
		if (!be.isWorking()) {
			return;
		}
		if (level.random.nextInt(8) != 0) {
			return;
		}
		double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
		double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.3;
		double y1 = pos.getY() + 1.15;
		double y2 = pos.getY() + 1.05;
		level.addParticle(ParticleTypes.ENCHANT, x, y1, z, 0.0, 0.01, 0.0);
		level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y2, z, 0.0, 0.01, 0.0);
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
		if (stack.isEmpty()) {
			return false;
		}
		if (!product.isEmpty() || readyAtAbsMinute >= 0) {
			return false;
		}

		Item item = stack.getItem();

		if (item instanceof PreservesItem preserveItem && preserveItem.getPreserveType() == PreserveType.ROE) {
			ResourceLocation sourceId = PreservesItem.getSourceItemId(stack);
			ItemStack ingredientForFlavor = stack;
			if (sourceId != null && BuiltInRegistries.ITEM.containsKey(sourceId)) {
				ingredientForFlavor = new ItemStack(BuiltInRegistries.ITEM.get(sourceId));
			}
			ResourceLocation outputId = isSturgeonRoe(sourceId)
					? BuiltInRegistries.ITEM.getKey(ModItems.CAVIAR.get())
					: BuiltInRegistries.ITEM.getKey(ModItems.AGED_ROE.get());
			var recipeOpt = ArtisanRecipeDataManager.getRecipeByOutput("preserves_jar", outputId);
			if (recipeOpt.isEmpty()) {
				return false;
			}
			ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
			ItemStack output = createFlavoredOutput(recipe, ingredientForFlavor);
			if (output.isEmpty()) {
				return false;
			}
			var plan = prepareProduction(
					stack, output, recipe.minutes(),
					player, false);
			if (plan.isEmpty()) {
				return false;
			}
			startWork(stack, plan.get(), player);
			return true;
		}

		if (!"stardewcraft.type.crop".equals(StardewItemDataApi.getTypeKey(stack))) {
			return false;
		}

		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		if (!PreservesIngredientDataManager.hasData(id)) {
			return false;
		}
		var recipeOpt = ArtisanRecipeDataManager.getRecipe("preserves_jar", stack);
		if (recipeOpt.isEmpty()) {
			return false;
		}
		ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
		ItemStack output = createFlavoredOutput(recipe, stack);
		if (output.isEmpty()) {
			return false;
		}
		var plan = prepareProduction(
				stack, output, recipe.minutes(),
				player, false);
		if (plan.isEmpty()) {
			return false;
		}
		startWork(stack, plan.get(), player);
		return true;
	}

	@SuppressWarnings("null")
	private ItemStack createFlavoredOutput(ArtisanRecipeDataManager.Recipe recipe, ItemStack ingredientForFlavor) {
	    return createFlavoredOutput(recipe, ingredientForFlavor, false);
	}

	private ItemStack createFlavoredOutput(ArtisanRecipeDataManager.Recipe recipe, ItemStack ingredientForFlavor, boolean simulate) {
		PreserveType preserveType = recipe.preserveType();
		if (preserveType == null) {
			return ItemStack.EMPTY;
		}
		ItemStack output = new ItemStack(BuiltInRegistries.ITEM.get(recipe.outputId()), (simulate ? recipe.outputCount() : recipe.rollOutputCount(level.random)));
		PreservesItem.createFlavored(preserveType, ingredientForFlavor, output);
		return output;
	}

	private void startWork(
			ItemStack inputStack,
			com.stardew.craft.api.v1.machine.StardewProductionPlan plan,
			Player player
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
		if (stack.isEmpty() || !product.isEmpty() || readyAtAbsMinute >= 0) {
			return stack;
		}

		Item item = stack.getItem();
		ItemStack output = ItemStack.EMPTY;
		int minutes = 0;
		int consumeCount = 1;

		if (item instanceof PreservesItem preserveItem && preserveItem.getPreserveType() == PreserveType.ROE) {
			ResourceLocation sourceId = PreservesItem.getSourceItemId(stack);
			ItemStack ingredientForFlavor = stack;
			if (sourceId != null && BuiltInRegistries.ITEM.containsKey(sourceId)) {
				ingredientForFlavor = new ItemStack(BuiltInRegistries.ITEM.get(sourceId));
			}
			ResourceLocation outputId = isSturgeonRoe(sourceId)
					? BuiltInRegistries.ITEM.getKey(ModItems.CAVIAR.get())
					: BuiltInRegistries.ITEM.getKey(ModItems.AGED_ROE.get());
			var recipeOpt = ArtisanRecipeDataManager.getRecipeByOutput("preserves_jar", outputId);
			if (recipeOpt.isPresent()) {
				ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
				output = createFlavoredOutput(recipe, ingredientForFlavor, simulate);
				minutes = recipe.minutes();
			}
		} else if ("stardewcraft.type.crop".equals(StardewItemDataApi.getTypeKey(stack))) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (PreservesIngredientDataManager.hasData(id)) {
				var recipeOpt = ArtisanRecipeDataManager.getRecipe("preserves_jar", stack);
				if (recipeOpt.isPresent()) {
					ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
					output = createFlavoredOutput(recipe, stack, simulate);
					minutes = recipe.minutes();
				}
			}
		}

		if (output.isEmpty()) {
			return stack;
		}
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
		startWork(inputCopy, plan.get(), null);
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
		BooleanProperty workingProp = com.stardew.craft.block.utility.PreservesJarBlock.WORKING;
		boolean workingNow = isWorking();
		if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
			level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
		}
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@SuppressWarnings("null")
	@Override
	public CompoundTag getUpdateTag() { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@SuppressWarnings("null")
	@Override
	protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.saveAdditional(tag);
		if (!input.isEmpty()) {
			tag.put(TAG_INPUT, PortItemStacks.save(input, registries));
		}
		if (!product.isEmpty()) {
			tag.put(TAG_PRODUCT, PortItemStacks.save(product, registries));
		}
		tag.putLong(TAG_READY_AT, readyAtAbsMinute);
		tag.putBoolean(TAG_READY, ready);
	}

	@SuppressWarnings("null")
	@Override
	public void load(@SuppressWarnings("null") CompoundTag tag) { net.minecraft.core.HolderLookup.Provider registries = com.stardew.craft.port.PortRegistries.lookup();
		super.load(tag);
		input = tag.contains(TAG_INPUT) ? PortItemStacks.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		product = tag.contains(TAG_PRODUCT) ? PortItemStacks.parse(registries, tag.getCompound(TAG_PRODUCT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
		readyAtAbsMinute = tag.getLong(TAG_READY_AT);
		ready = tag.getBoolean(TAG_READY);
	}

	private static boolean isSturgeonRoe(ResourceLocation sourceId) {
		return sourceId != null && "sturgeon".equals(sourceId.getPath());
	}
}
