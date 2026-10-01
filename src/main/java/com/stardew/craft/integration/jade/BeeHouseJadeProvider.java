package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.BeeHouseBlock;
import com.stardew.craft.blockentity.BeeHouseBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

public enum BeeHouseJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "bee_house");

	private static final String NBT_READY = "ready";
	private static final String NBT_WORKING = "working";
	private static final String NBT_PRODUCT_ITEM = "productItem";
	private static final String NBT_FLOWER_ITEM = "flowerItem";
	private static final String NBT_DAYS = "days";
	private static final String NBT_HOURS = "hours";
	private static final String NBT_MINUTES = "minutes";

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@SuppressWarnings("null")
	@Override
	public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
		BeeHouseBlockEntity beeHouse = getBeeHouse(accessor);
		if (beeHouse == null) {
			return;
		}

		tag.putBoolean(NBT_READY, beeHouse.isReady());
		tag.putBoolean(NBT_WORKING, beeHouse.isWorking());

		ItemStack product = beeHouse.getCurrentProduct();
		if (!product.isEmpty()) {
			ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
			if (productId != null) {
				tag.putString(NBT_PRODUCT_ITEM, productId.toString());
			}
		}

		ItemStack flower = beeHouse.getNearbyFlowerItem();
		if (!flower.isEmpty()) {
			ResourceLocation flowerId = BuiltInRegistries.ITEM.getKey(flower.getItem());
			if (flowerId != null) {
				tag.putString(NBT_FLOWER_ITEM, flowerId.toString());
			}
		}

		BeeHouseBlockEntity.RemainingTime rt = beeHouse.getRemainingTime();
		tag.putInt(NBT_DAYS, rt.days());
		tag.putInt(NBT_HOURS, rt.hours());
		tag.putInt(NBT_MINUTES, rt.minutes());
	}

	@SuppressWarnings("null")
	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		CompoundTag data = accessor.getServerData();
		if (data == null) {
			return;
		}

		var helper = IElementHelper.get();

		boolean ready = data.getBoolean(NBT_READY);
		boolean working = data.getBoolean(NBT_WORKING);
		String productItemId = data.getString(NBT_PRODUCT_ITEM);
		String flowerItemId = data.getString(NBT_FLOWER_ITEM);

		if (ready) {
			Component productName = null;
			ItemStack flowerStack = stackFromId(flowerItemId);
			ItemStack productStack = ItemStack.EMPTY;
			if (!flowerStack.isEmpty()) {
				productName = Component.translatable("stardewcraft.tooltip.bee_house.product.flavored", flowerStack.getHoverName());
			} else {
				productStack = stackFromId(productItemId);
				if (!productStack.isEmpty()) {
					productName = productStack.getHoverName();
				}
			}
			if (productName != null) {
				ItemStack iconStack = !productStack.isEmpty() ? productStack : flowerStack;
				if (!iconStack.isEmpty()) {
					tooltip.add(List.of(
						helper.item(iconStack, 1.0f),
						helper.spacer(4, 0),
						helper.text(Component.translatable("stardewcraft.tooltip.bee_house.product")
							.append(": ")
							.append(productName)
							.withStyle(ChatFormatting.WHITE))
					));
				} else {
					tooltip.add(Component.translatable("stardewcraft.tooltip.bee_house.product")
						.append(": ")
						.append(productName)
						.withStyle(ChatFormatting.WHITE));
				}
			}
			tooltip.add(Component.translatable("stardewcraft.tooltip.bee_house.ready")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			return;
		}

		if (!working) {
			tooltip.add(Component.translatable("stardewcraft.tooltip.bee_house.product")
					.append(": ")
					.append(Component.translatable("stardewcraft.tooltip.bee_house.product.none"))
					.withStyle(ChatFormatting.WHITE));
			return;
		}

		// Working: show what honey type is being produced + remaining time
		ItemStack flowerStack = stackFromId(flowerItemId);
		if (!flowerStack.isEmpty()) {
			Component productName = Component.translatable("stardewcraft.tooltip.bee_house.product.flavored", flowerStack.getHoverName());
			tooltip.add(List.of(
				helper.item(flowerStack, 1.0f),
				helper.spacer(4, 0),
				helper.text(Component.translatable("stardewcraft.tooltip.bee_house.product")
					.append(": ")
					.append(productName)
					.withStyle(ChatFormatting.WHITE))
			));
		} else {
			ItemStack productStack = stackFromId(productItemId);
			if (!productStack.isEmpty()) {
				tooltip.add(List.of(
					helper.item(productStack, 1.0f),
					helper.spacer(4, 0),
					helper.text(Component.translatable("stardewcraft.tooltip.bee_house.product")
						.append(": ")
						.append(productStack.getHoverName())
						.withStyle(ChatFormatting.WHITE))
				));
			}
		}

		int days = data.getInt(NBT_DAYS);
		int hours = data.getInt(NBT_HOURS);
		int minutes = data.getInt(NBT_MINUTES);
		var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.bee_house.remaining", days, hours, minutes)
			.withStyle(ChatFormatting.GRAY);
		tooltip.add(remaining);
	}

	@SuppressWarnings("null")
	private static ItemStack stackFromId(String itemId) {
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(BuiltInRegistries.ITEM.get(id));
	}

	@SuppressWarnings("null")
	private static BeeHouseBlockEntity getBeeHouse(BlockAccessor accessor) {
		BlockEntity beDirect = accessor.getBlockEntity();
		if (beDirect instanceof BeeHouseBlockEntity) {
			return (BeeHouseBlockEntity) beDirect;
		}

		BlockState state = accessor.getBlockState();
		if (!(state.getBlock() instanceof BeeHouseBlock)) {
			return null;
		}

		BlockPos mainPos = BeeHouseBlock.getMainPos(accessor.getPosition(), state);
		BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
		if (be instanceof BeeHouseBlockEntity) {
			return (BeeHouseBlockEntity) be;
		}
		return null;
	}
}
