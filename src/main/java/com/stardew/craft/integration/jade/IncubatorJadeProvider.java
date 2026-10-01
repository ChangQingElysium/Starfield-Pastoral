package com.stardew.craft.integration.jade;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
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

public enum IncubatorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "incubator");

    private static final String NBT_READY = "ready";
    private static final String NBT_INPUT_ITEM = "inputItem";
    private static final String NBT_INPUT_STACK = "inputStack";
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
        IncubatorBlockEntity incubator = getIncubator(accessor);
        if (incubator == null) {
            return;
        }

        tag.putBoolean(NBT_READY, incubator.isReady());

        ItemStack input = incubator.getInput();
        if (!input.isEmpty()) {
            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
            if (inputId != null) {
                tag.putString(NBT_INPUT_ITEM, inputId.toString());
            }
            tag.put(NBT_INPUT_STACK, PortItemStacks.save(input, accessor.getLevel().registryAccess()));
        }

        IncubatorBlockEntity.RemainingTime rt = incubator.getRemainingTime();
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
        ItemStack inputStack = stackFromTag(data.getCompound(NBT_INPUT_STACK), accessor);

        if (inputItemId.isEmpty()) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.incubator.input")
                .append(": ")
                .append(Component.translatable("stardewcraft.tooltip.incubator.input.none"))
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
                helper.text(Component.translatable("stardewcraft.tooltip.incubator.input")
                    .append(": ")
                    .append(inputStack.getHoverName())
                    .withStyle(ChatFormatting.WHITE))
            ));
        } else {
            tooltip.add(Component.translatable("stardewcraft.tooltip.incubator.input")
                .append(": ")
                .append(Component.literal(inputItemId))
                .withStyle(ChatFormatting.WHITE));
        }

        if (ready) {
            tooltip.add(Component.translatable("stardewcraft.tooltip.incubator.ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        int days = data.getInt(NBT_DAYS);
        int hours = data.getInt(NBT_HOURS);
        int minutes = data.getInt(NBT_MINUTES);
        var remaining = RemainingTimeTooltip.build("stardewcraft.tooltip.incubator.remaining", days, hours, minutes)
            .withStyle(ChatFormatting.GRAY);
        tooltip.add(remaining);
    }

    @SuppressWarnings("null")
    private static ItemStack stackFromTag(CompoundTag tag, BlockAccessor accessor) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return PortItemStacks.parse(accessor.getLevel().registryAccess(), tag).orElse(ItemStack.EMPTY);
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
    private static IncubatorBlockEntity getIncubator(BlockAccessor accessor) {
        BlockEntity beDirect = accessor.getBlockEntity();
        if (beDirect instanceof IncubatorBlockEntity) {
            return (IncubatorBlockEntity) beDirect;
        }

        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof IncubatorBlock)) {
            return null;
        }

        BlockPos mainPos = IncubatorBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
        if (be instanceof IncubatorBlockEntity) {
            return (IncubatorBlockEntity) be;
        }
        return null;
    }
}
