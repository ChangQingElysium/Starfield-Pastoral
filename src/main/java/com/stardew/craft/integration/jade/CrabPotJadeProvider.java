package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CrabPotBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;


public enum CrabPotJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "crab_pot");

	private static final String NBT_HAS_BAIT = "hasBait";
	private static final String NBT_BAIT_ITEM = "baitItem";
	private static final String NBT_READY = "ready";
	private static final String NBT_PRODUCT_ITEM = "productItem";

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@SuppressWarnings("null")
	@Override
	public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
		if (!(accessor.getBlockEntity() instanceof CrabPotBlockEntity crabPot)) {
			return;
		}

		ItemStack bait = crabPot.getBait();
		ItemStack product = crabPot.getProduct();
		boolean ready = crabPot.isReady();

		tag.putBoolean(NBT_HAS_BAIT, !bait.isEmpty());
		tag.putBoolean(NBT_READY, ready);

		if (!bait.isEmpty()) {
			// getKey(...) 在极少数情况下可能返回 null；Jade 侧只需要一个可渲染的字符串。
			@SuppressWarnings("null")
			ResourceLocation baitId = BuiltInRegistries.ITEM.getKey(bait.getItem());
			if (baitId != null) {
				tag.putString(NBT_BAIT_ITEM, baitId.toString());
			}
		}

		if (!product.isEmpty()) {
			@SuppressWarnings("null")
			ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
			if (productId != null) {
				tag.putString(NBT_PRODUCT_ITEM, productId.toString());
			}
		}
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
		boolean hasBait = data.getBoolean(NBT_HAS_BAIT);
		String baitItemId = data.getString(NBT_BAIT_ITEM);
		String productItemId = data.getString(NBT_PRODUCT_ITEM);

		// Stage 3: ready => show product + green ready message (same style as Tapper)
		if (ready && !productItemId.isEmpty()) {
			ItemStack productStack = stackFromId(productItemId);
			if (!productStack.isEmpty()) {
				tooltip.add(List.of(
					helper.item(productStack, 1.0f),
					helper.spacer(4, 0),
					helper.text(Component.translatable("stardewcraft.tooltip.tapper.product")
						.append(": ")
						.append(productStack.getHoverName())
						.withStyle(ChatFormatting.WHITE))
				));
			}
			tooltip.add(Component.translatable("stardewcraft.tooltip.crab_pot.ready")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			return;
		}

		// Stage 1/2: show bait line
		if (!hasBait || baitItemId.isEmpty()) {
			tooltip.add(Component.translatable("stardewcraft.tooltip.crab_pot.bait")
					.append(": ")
					.append(Component.translatable("stardewcraft.tooltip.crab_pot.bait.none"))
					.withStyle(ChatFormatting.WHITE));
			return;
		}

		ItemStack baitStack = stackFromId(baitItemId);
		if (!baitStack.isEmpty()) {
			tooltip.add(List.of(
				helper.item(baitStack, 1.0f),
				helper.spacer(4, 0),
				helper.text(Component.translatable("stardewcraft.tooltip.crab_pot.bait")
					.append(": ")
					.append(baitStack.getHoverName())
					.withStyle(ChatFormatting.WHITE))
			));
		} else {
			tooltip.add(Component.translatable("stardewcraft.tooltip.crab_pot.bait")
					.append(": ")
					.append(Component.literal(baitItemId))
					.withStyle(ChatFormatting.WHITE));
		}

		tooltip.add(Component.translatable("stardewcraft.tooltip.crab_pot.working")
				.withStyle(ChatFormatting.GRAY));
	}

	private static ItemStack stackFromId(String itemId) {
		@SuppressWarnings("null")
		ResourceLocation id = ResourceLocation.tryParse(itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(BuiltInRegistries.ITEM.get(id));
	}
}
