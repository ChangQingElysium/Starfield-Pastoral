package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.LightningRodBlock;
import com.stardew.craft.blockentity.LightningRodBlockEntity;
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

public enum LightningRodJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "lightning_rod");

    private static final String NBT_READY = "ready";
    private static final String NBT_PRODUCT_ITEM = "productItem";
    private static final String NBT_DAYS = "days";
    private static final String NBT_HOURS = "hours";
    private static final String NBT_MINUTES = "minutes";
    private static final String NBT_STORMING = "storming";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        LightningRodBlockEntity rod = getRod(accessor);
        if (rod == null) {
            return;
        }

        tag.putBoolean(NBT_READY, rod.isReady());
        tag.putBoolean(NBT_STORMING, com.stardew.craft.weather.WeatherManager.isThundering(accessor.getLevel()));

        ItemStack product = rod.getProduct();
        if (!product.isEmpty()) {
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
            if (productId != null) {
                tag.putString(NBT_PRODUCT_ITEM, productId.toString());
            }
            LightningRodBlockEntity.RemainingTime rt = rod.getRemainingTime();
            tag.putInt(NBT_DAYS, rt.days());
            tag.putInt(NBT_HOURS, rt.hours());
            tag.putInt(NBT_MINUTES, rt.minutes());
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
        boolean storming = data.getBoolean(NBT_STORMING);
        String productItemId = data.getString(NBT_PRODUCT_ITEM);

        if (productItemId.isEmpty()) {
            String key = storming
                ? "stardewcraft.tooltip.lightning_rod.waiting.lightning"
                : "stardewcraft.tooltip.lightning_rod.waiting";
            tooltip.add(Component.translatable(key)
                .withStyle(ChatFormatting.GRAY));
            return;
        }

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

        if (ready) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.lightning_rod.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        int days = data.getInt(NBT_DAYS);
        int hours = data.getInt(NBT_HOURS);
        int minutes = data.getInt(NBT_MINUTES);
        var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.lightning_rod.remaining", days, hours, minutes)
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
    private static LightningRodBlockEntity getRod(BlockAccessor accessor) {
        BlockEntity beDirect = accessor.getBlockEntity();
        if (beDirect instanceof LightningRodBlockEntity) {
            return (LightningRodBlockEntity) beDirect;
        }

        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof LightningRodBlock)) {
            return null;
        }

        BlockPos mainPos = LightningRodBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
        if (be instanceof LightningRodBlockEntity) {
            return (LightningRodBlockEntity) be;
        }
        return null;
    }
}
