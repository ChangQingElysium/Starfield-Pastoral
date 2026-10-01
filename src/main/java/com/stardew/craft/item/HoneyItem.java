package com.stardew.craft.item;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;

public class HoneyItem extends SimpleStardewItem {
	private static final String TAG_HONEY_VALUE = "HoneyValue";

	public HoneyItem(String typeKey, int sellPrice, Properties properties) {
		super(typeKey, sellPrice, properties);
	}

	@Override
	@SuppressWarnings("null")
	public int getSellPrice(ItemStack stack) {
		CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
		if (data == null) {
			return super.getSellPrice(stack);
		}
		CompoundTag tag = data.copyTag();
		if (tag.contains(TAG_HONEY_VALUE)) {
			return tag.getInt(TAG_HONEY_VALUE);
		}
		return super.getSellPrice(stack);
	}
}
