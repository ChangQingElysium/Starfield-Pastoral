package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.DeluxeWormBinBlockEntity;
import com.stardew.craft.blockentity.WormBinBlockEntity;
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

public enum WormBinJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "worm_bin");

    private static final String NBT_READY = "ready";
    private static final String NBT_WORKING = "working";
    private static final String NBT_PRODUCT_ITEM = "productItem";
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
        var mainPos = com.stardew.craft.capability.UtilityAutomationCapabilities.resolveMainPos(
                accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        var owner = accessor.getLevel().getBlockEntity(mainPos);
        if (owner instanceof WormBinBlockEntity wormBin) {
            appendWormBinData(tag, wormBin);
            return;
        }
        if (owner instanceof DeluxeWormBinBlockEntity wormBin) {
            appendDeluxeWormBinData(tag, wormBin);
        }
    }

    @SuppressWarnings("null")
    private static void appendWormBinData(CompoundTag tag, WormBinBlockEntity wormBin) {
        tag.putBoolean(NBT_READY, wormBin.isReady());
        tag.putBoolean(NBT_WORKING, wormBin.isWorking());

        ItemStack product = wormBin.getProduct();
        if (!product.isEmpty()) {
            @SuppressWarnings("null")
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
            if (productId != null) {
                @SuppressWarnings("null")
                String productIdString = productId.toString();
                tag.putString(NBT_PRODUCT_ITEM, productIdString);
            }
        }

        WormBinBlockEntity.RemainingTime rt = wormBin.getRemainingTime();
        tag.putInt(NBT_DAYS, rt.days());
        tag.putInt(NBT_HOURS, rt.hours());
        tag.putInt(NBT_MINUTES, rt.minutes());
    }

    @SuppressWarnings("null")
    private static void appendDeluxeWormBinData(CompoundTag tag, DeluxeWormBinBlockEntity wormBin) {
        tag.putBoolean(NBT_READY, wormBin.isReady());
        tag.putBoolean(NBT_WORKING, wormBin.isWorking());

        ItemStack product = wormBin.getProduct();
        if (!product.isEmpty()) {
            @SuppressWarnings("null")
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
            if (productId != null) {
                @SuppressWarnings("null")
                String productIdString = productId.toString();
                tag.putString(NBT_PRODUCT_ITEM, productIdString);
            }
        }

        DeluxeWormBinBlockEntity.RemainingTime rt = wormBin.getRemainingTime();
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

        if (!productItemId.isEmpty()) {
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
        } else {
            tooltip.add(Component.translatable("stardewcraft.tooltip.tapper.product")
                .append(": ")
                .append(Component.translatable("stardewcraft.tooltip.worm_bin.product.none"))
                .withStyle(ChatFormatting.WHITE));
        }

        if (ready) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.worm_bin.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        if (!working) {
            return;
        }

        int days = data.getInt(NBT_DAYS);
        int hours = data.getInt(NBT_HOURS);
        int minutes = data.getInt(NBT_MINUTES);
        var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.worm_bin.remaining", days, hours, minutes)
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
}
