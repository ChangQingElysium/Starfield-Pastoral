package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.DehydratorBlock;
import com.stardew.craft.blockentity.DehydratorBlockEntity;
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

public enum DehydratorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "dehydrator");

	private static final String NBT_READY = "ready";
	private static final String NBT_INPUT_ITEM = "inputItem";
	private static final String NBT_PRODUCT_ITEM = "productItem";
	private static final String NBT_INPUT_STACK = "inputStack";
	private static final String NBT_PRODUCT_STACK = "productStack";
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
		DehydratorBlockEntity dehydrator = getDehydrator(accessor);
		if (dehydrator == null) {
			return;
		}

		tag.putBoolean(NBT_READY, dehydrator.isReady());

		ItemStack input = dehydrator.getInput();
		ItemStack product = dehydrator.getProduct();

		if (!input.isEmpty()) {
			ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
			if (inputId != null) {
				tag.putString(NBT_INPUT_ITEM, inputId.toString());
			}
			tag.put(NBT_INPUT_STACK, input.save(accessor.getLevel().registryAccess()));
		}

		if (!product.isEmpty()) {
			ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
			if (productId != null) {
				tag.putString(NBT_PRODUCT_ITEM, productId.toString());
			}
			tag.put(NBT_PRODUCT_STACK, product.save(accessor.getLevel().registryAccess()));
		}

		DehydratorBlockEntity.RemainingTime rt = dehydrator.getRemainingTime();
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
		String inputItemId = data.getString(NBT_INPUT_ITEM);
		String productItemId = data.getString(NBT_PRODUCT_ITEM);
		ItemStack inputStack = stackFromTag(data.getCompound(NBT_INPUT_STACK), accessor);
		ItemStack productStack = stackFromTag(data.getCompound(NBT_PRODUCT_STACK), accessor);

		if (ready && !productItemId.isEmpty()) {
			if (productStack.isEmpty()) {
				productStack = stackFromId(productItemId);
			}
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
			tooltip.add(Component.translatable("stardewcraft.tooltip.dehydrator.ready")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			return;
		}

		if (inputItemId.isEmpty()) {
			tooltip.add(Component.translatable("stardewcraft.tooltip.dehydrator.input")
					.append(": ")
					.append(Component.translatable("stardewcraft.tooltip.dehydrator.input.none"))
					.withStyle(ChatFormatting.WHITE));
			return;
		}

		if (inputStack.isEmpty()) {
			inputStack = stackFromId(inputItemId);
		}
		if (!inputStack.isEmpty()) {
			tooltip.add(List.of(
				helper.item(inputStack, 1.0f),
				helper.spacer(4, 0),
				helper.text(Component.translatable("stardewcraft.tooltip.dehydrator.input")
					.append(": ")
					.append(inputStack.getHoverName())
					.withStyle(ChatFormatting.WHITE))
			));
		} else {
			tooltip.add(Component.translatable("stardewcraft.tooltip.dehydrator.input")
					.append(": ")
					.append(Component.literal(inputItemId))
					.withStyle(ChatFormatting.WHITE));
		}

		int days = data.getInt(NBT_DAYS);
		int hours = data.getInt(NBT_HOURS);
		int minutes = data.getInt(NBT_MINUTES);
		var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.dehydrator.remaining", days, hours, minutes)
			.withStyle(ChatFormatting.GRAY);
		tooltip.add(remaining);
	}

	@SuppressWarnings("null")
	private static ItemStack stackFromTag(CompoundTag tag, BlockAccessor accessor) {
		if (tag == null || tag.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return ItemStack.parse(accessor.getLevel().registryAccess(), tag).orElse(ItemStack.EMPTY);
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
	private static DehydratorBlockEntity getDehydrator(BlockAccessor accessor) {
		BlockEntity beDirect = accessor.getBlockEntity();
		if (beDirect instanceof DehydratorBlockEntity) {
			return (DehydratorBlockEntity) beDirect;
		}

		BlockState state = accessor.getBlockState();
		if (!(state.getBlock() instanceof DehydratorBlock)) {
			return null;
		}

		BlockPos mainPos = DehydratorBlock.getMainPos(accessor.getPosition(), state);
		BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
		if (be instanceof DehydratorBlockEntity) {
			return (DehydratorBlockEntity) be;
		}
		return null;
	}
}
