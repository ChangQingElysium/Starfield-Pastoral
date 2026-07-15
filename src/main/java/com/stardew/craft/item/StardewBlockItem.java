package com.stardew.craft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

@SuppressWarnings("null")
public class StardewBlockItem extends BlockItem implements IStardewItem {
	private final String itemTypeKey;
	private final int sellPrice;
	private final String descriptionKey;

	@SuppressWarnings("null")
	public StardewBlockItem(Block block, String itemTypeKey, int sellPrice, Item.Properties properties) {
		this(block, itemTypeKey, sellPrice, null, properties);
	}

	@SuppressWarnings("null")
	public StardewBlockItem(Block block, String itemTypeKey, int sellPrice, String descriptionKey, Item.Properties properties) {
		super(block, properties);
		this.itemTypeKey = itemTypeKey;
		this.sellPrice = sellPrice;
		this.descriptionKey = descriptionKey;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (getBlock() instanceof com.stardew.craft.block.decor.CarpetDecorBlock carpet) {
			return com.stardew.craft.entity.decor.CarpetPlacementService.place(carpet, context);
		}
		return super.useOn(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
		if (descriptionKey != null && !descriptionKey.isBlank()) {
			tooltipComponents.add(Component.translatable(descriptionKey).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	@Override
	public String getItemTypeKey() {
		return itemTypeKey;
	}

	@Override
	public int getSellPrice(ItemStack stack) {
		return sellPrice <= 0 ? -1 : sellPrice;
	}
}
