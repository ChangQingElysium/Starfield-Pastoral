package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.FishSmokerBlock;
import com.stardew.craft.blockentity.FishSmokerBlockEntity;
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

public enum FishSmokerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "fish_smoker");

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
        FishSmokerBlockEntity fishSmoker = getFishSmoker(accessor);
        if (fishSmoker == null) {
            return;
        }

        tag.putBoolean(NBT_READY, fishSmoker.isReady());

        ItemStack input = fishSmoker.getInput();
        ItemStack product = fishSmoker.getProduct();

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

        FishSmokerBlockEntity.RemainingTime rt = fishSmoker.getRemainingTime();
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
            tooltip.add(Component.translatable("stardewcraft.tooltip.fish_smoker.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        if (inputItemId.isEmpty()) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.fish_smoker.input")
                .append(": ")
                .append(Component.translatable("stardewcraft.tooltip.fish_smoker.input.none"))
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
                helper.text(Component.translatable("stardewcraft.tooltip.fish_smoker.input")
                    .append(": ")
                    .append(inputStack.getHoverName())
                    .withStyle(ChatFormatting.WHITE))
            ));
        } else {
            tooltip.add(Component.translatable("stardewcraft.tooltip.fish_smoker.input")
                .append(": ")
                .append(Component.literal(inputItemId))
                .withStyle(ChatFormatting.WHITE));
        }

        int days = data.getInt(NBT_DAYS);
        int hours = data.getInt(NBT_HOURS);
        int minutes = data.getInt(NBT_MINUTES);
        var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.fish_smoker.remaining", days, hours, minutes)
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
    private static FishSmokerBlockEntity getFishSmoker(BlockAccessor accessor) {
        BlockEntity beDirect = accessor.getBlockEntity();
        if (beDirect instanceof FishSmokerBlockEntity) {
            return (FishSmokerBlockEntity) beDirect;
        }

        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof FishSmokerBlock)) {
            return null;
        }

        BlockPos mainPos = FishSmokerBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
        if (be instanceof FishSmokerBlockEntity) {
            return (FishSmokerBlockEntity) be;
        }
        return null;
    }
}
