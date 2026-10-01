package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.TapperBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

public enum TapperJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "tapper");
	private static final String NBT_READY = "ready";
	private static final String NBT_ITEM = "item";
	private static final String NBT_DAYS = "days";
	private static final String NBT_HOURS = "hours";
	private static final String NBT_MINUTES = "minutes";
	private static final String NBT_INVALID_TREE = "invalid_tree";

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@SuppressWarnings("null")
	@Override
	public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof TapperBlockEntity tapper)) {
			return;
		}

			boolean invalidTree = !tapper.isProductionSiteValid();
			tag.putBoolean(NBT_INVALID_TREE, invalidTree);

			ItemStack product = tapper.getProduct();
			if (product.isEmpty()) {
				return;
			}

		@SuppressWarnings("null")
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(product.getItem());
		tag.putString(NBT_ITEM, id.toString());
		tag.putBoolean(NBT_READY, tapper.isReady());

		TapperBlockEntity.RemainingTime rt = tapper.getRemainingTime();
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

			if (data.getBoolean(NBT_INVALID_TREE)) {
				tooltip.add(Component.translatable("stardewcraft.tooltip.tapper.invalid_tree")
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
				if (!data.contains(NBT_ITEM)) {
					return;
				}
			}

			if (!data.contains(NBT_ITEM)) {
				return;
			}

		@SuppressWarnings("null")
		ResourceLocation itemId = ResourceLocation.tryParse(data.getString(NBT_ITEM));
		if (itemId == null) {
			return;
		}

		Item item = BuiltInRegistries.ITEM.get(itemId);
		ItemStack stack = new ItemStack(item);

		var helper = IElementHelper.get();

		// Line 1: product icon + name
		tooltip.add(List.of(
			helper.item(stack, 1.0f),
			helper.spacer(4, 0),
			helper.text(Component.translatable("stardewcraft.tooltip.tapper.product")
				.append(": ")
				.append(stack.getHoverName()).withStyle(ChatFormatting.WHITE))
		));

		boolean ready = data.getBoolean(NBT_READY);
		if (ready) {
			tooltip.add(Component.translatable("stardewcraft.tooltip.tapper.ready")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			return;
		}

		int days = data.getInt(NBT_DAYS);
		int hours = data.getInt(NBT_HOURS);
		int minutes = data.getInt(NBT_MINUTES);
		var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.tapper.remaining", days, hours, minutes)
			.withStyle(ChatFormatting.GRAY);
		tooltip.add(remaining);
	}
}
