package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.SolarPanelBlock;
import com.stardew.craft.blockentity.SolarPanelBlockEntity;
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

public enum SolarPanelJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "solar_panel");

    private static final String NBT_READY = "ready";
    private static final String NBT_PAUSED = "paused";
    private static final String NBT_PRODUCT_ITEM = "productItem";
    private static final String NBT_DAYS = "days";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        SolarPanelBlockEntity panel = getPanel(accessor);
        if (panel == null) {
            return;
        }

        tag.putBoolean(NBT_READY, panel.isReady());
        tag.putBoolean(NBT_PAUSED, panel.isPaused());

        ItemStack product = panel.getProduct();
        if (!product.isEmpty()) {
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product.getItem());
            if (productId != null) {
                tag.putString(NBT_PRODUCT_ITEM, productId.toString());
            }
        }

        SolarPanelBlockEntity.RemainingTime rt = panel.getRemainingTime();
        tag.putInt(NBT_DAYS, rt.days());
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) {
            return;
        }

        boolean ready = data.getBoolean(NBT_READY);
        boolean paused = data.getBoolean(NBT_PAUSED);
        String productItemId = data.getString(NBT_PRODUCT_ITEM);

        if (productItemId.isEmpty()) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.solar_panel.waiting")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        ItemStack productStack = stackFromId(productItemId);
        if (!productStack.isEmpty()) {
            IElementHelper helper = IElementHelper.get();
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
            tooltip.add(Component.translatable("stardewcraft.tooltip.solar_panel.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        if (paused) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.solar_panel.stopped")
                .withStyle(ChatFormatting.RED));
            return;
        }

        int days = data.getInt(NBT_DAYS);
        tooltip.add(Component.translatable("stardewcraft.tooltip.remaining_days", days)
            .withStyle(ChatFormatting.GRAY));
    }

    @SuppressWarnings("null")
    private static SolarPanelBlockEntity getPanel(BlockAccessor accessor) {
        BlockEntity beDirect = accessor.getBlockEntity();
        if (beDirect instanceof SolarPanelBlockEntity) {
            return (SolarPanelBlockEntity) beDirect;
        }

        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) {
            return null;
        }

        if (!state.hasProperty(SolarPanelBlock.PART)) {
            return null;
        }

        BlockPos mainPos = SolarPanelBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
        if (be instanceof SolarPanelBlockEntity) {
            return (SolarPanelBlockEntity) be;
        }
        return null;
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
