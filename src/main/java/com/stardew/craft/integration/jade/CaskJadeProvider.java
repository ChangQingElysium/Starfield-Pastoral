package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CaskBlockEntity;
import com.stardew.craft.item.quality.QualityHelper;
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

public enum CaskJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "cask");

    private static final String NBT_READY = "ready";
    private static final String NBT_PRODUCT_ITEM = "productItem";
    private static final String NBT_PRODUCT_QUALITY = "productQuality";
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
        if (!(accessor.getBlockEntity() instanceof CaskBlockEntity cask)) {
            return;
        }

        tag.putBoolean(NBT_READY, cask.isReady());

        ItemStack product = cask.getProduct();
        if (!product.isEmpty()) {
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
            if (productId != null) {
                tag.putString(NBT_PRODUCT_ITEM, productId.toString());
            }
            tag.putInt(NBT_PRODUCT_QUALITY, QualityHelper.getQuality(product));
        }

        CaskBlockEntity.RemainingTime rt = cask.getRemainingTime();
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

        String productItemId = data.getString(NBT_PRODUCT_ITEM);

        if (productItemId.isEmpty()) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.cask.input")
                .append(": ")
                .append(Component.translatable("stardewcraft.tooltip.cask.input.none"))
                .withStyle(ChatFormatting.WHITE));
            return;
        }

        ItemStack productStack = stackFromId(productItemId);
        int quality = data.getInt(NBT_PRODUCT_QUALITY);
        if (!productStack.isEmpty()) {
            QualityHelper.setQuality(productStack, quality);
            QualityHelper.ensureQualityModelData(productStack);
            tooltip.add(List.of(
                helper.item(productStack, 1.0f),
                helper.spacer(4, 0),
                helper.text(Component.translatable("stardewcraft.tooltip.tapper.product")
                    .append(": ")
                    .append(productStack.getHoverName())
                    .withStyle(ChatFormatting.WHITE))
            ));
        }

        if (quality >= QualityHelper.IRIDIUM) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.cask.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        int days = data.getInt(NBT_DAYS);
        int hours = data.getInt(NBT_HOURS);
        int minutes = data.getInt(NBT_MINUTES);
        var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.cask.remaining", days, hours, minutes)
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
