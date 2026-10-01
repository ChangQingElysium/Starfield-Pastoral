package com.stardew.craft.integration.jade;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.AbstractTwoBlockUtilityBlock;
import com.stardew.craft.blockentity.UtilityMachineInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

public enum GenericUtilityMachineJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "utility_machine");

    private static final String NBT_KEY = "key";
    private static final String NBT_READY = "ready";
    private static final String NBT_WORKING = "working";
    private static final String NBT_SHOW_INPUT = "showInput";
    private static final String NBT_INPUT_ITEM = "inputItem";
    private static final String NBT_PRODUCT_ITEM = "productItem";
    private static final String NBT_INPUT_STACK = "inputStack";
    private static final String NBT_PRODUCT_STACK = "productStack";
    private static final String NBT_HAS_REMAINING = "hasRemaining";
    private static final String NBT_DAYS = "days";
    private static final String NBT_HOURS = "hours";
    private static final String NBT_MINUTES = "minutes";
    private static final String NBT_IDLE_KEY = "idleKey";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        UtilityMachineInfo info = resolveInfo(accessor);
        if (info == null) {
            return;
        }

        tag.putString(NBT_KEY, info.getUtilityTooltipKey());
        tag.putBoolean(NBT_READY, info.isReadyForDisplay());
        tag.putBoolean(NBT_WORKING, info.isWorkingForDisplay());
        tag.putBoolean(NBT_SHOW_INPUT, info.shouldShowInputInDisplay());

        ItemStack input = info.getDisplayInput();
        ItemStack output = info.getDisplayOutput();
        if (!input.isEmpty()) {
            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(input.getItem());
            if (inputId != null) {
                tag.putString(NBT_INPUT_ITEM, inputId.toString());
            }
            tag.put(NBT_INPUT_STACK, PortItemStacks.save(input, accessor.getLevel().registryAccess()));
        }
        if (!output.isEmpty()) {
            ResourceLocation productId = BuiltInRegistries.ITEM.getKey(output.getItem());
            if (productId != null) {
                tag.putString(NBT_PRODUCT_ITEM, productId.toString());
            }
            tag.put(NBT_PRODUCT_STACK, PortItemStacks.save(output, accessor.getLevel().registryAccess()));
        }

        if (info.hasRemainingTimeForDisplay()) {
            UtilityMachineInfo.RemainingTime remaining = info.getRemainingTimeForDisplay();
            tag.putBoolean(NBT_HAS_REMAINING, true);
            tag.putInt(NBT_DAYS, remaining.days());
            tag.putInt(NBT_HOURS, remaining.hours());
            tag.putInt(NBT_MINUTES, remaining.minutes());
        }
        String idleKey = info.getIdleTooltipKey();
        if (idleKey != null && !idleKey.isBlank()) {
            tag.putString(NBT_IDLE_KEY, idleKey);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(NBT_KEY)) {
            return;
        }

        String key = data.getString(NBT_KEY);
        boolean ready = data.getBoolean(NBT_READY);
        boolean working = data.getBoolean(NBT_WORKING);
        boolean showInput = data.getBoolean(NBT_SHOW_INPUT);
        String inputItemId = data.getString(NBT_INPUT_ITEM);
        String productItemId = data.getString(NBT_PRODUCT_ITEM);
        ItemStack inputStack = stackFromTag(data.getCompound(NBT_INPUT_STACK), accessor);
        ItemStack productStack = stackFromTag(data.getCompound(NBT_PRODUCT_STACK), accessor);

        if (ready && !productItemId.isEmpty()) {
            if (productStack.isEmpty()) {
                productStack = stackFromId(productItemId);
            }
            addItemLine(tooltip, productStack, Component.translatable("stardewcraft.tooltip.utility.product"));
            tooltip.add(Component.translatable("stardewcraft.tooltip." + key + ".ready")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            return;
        }

        if (showInput) {
            if (inputItemId.isEmpty()) {
                tooltip.add(Component.translatable("stardewcraft.tooltip." + key + ".input")
                    .append(": ")
                    .append(Component.translatable("stardewcraft.tooltip." + key + ".input.none"))
                    .withStyle(ChatFormatting.WHITE));
            } else {
                if (inputStack.isEmpty()) {
                    inputStack = stackFromId(inputItemId);
                }
                addItemLine(tooltip, inputStack, Component.translatable("stardewcraft.tooltip." + key + ".input"));
            }
        }

        if (working && data.getBoolean(NBT_HAS_REMAINING)) {
            tooltip.add(RemainingTimeTooltip.build("stardewcraft.tooltip." + key + ".remaining",
                    data.getInt(NBT_DAYS), data.getInt(NBT_HOURS), data.getInt(NBT_MINUTES))
                .withStyle(ChatFormatting.GRAY));
        } else if (data.contains(NBT_IDLE_KEY)) {
            tooltip.add(Component.translatable(data.getString(NBT_IDLE_KEY)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void addItemLine(ITooltip tooltip, ItemStack stack, Component label) {
        if (stack.isEmpty()) {
            return;
        }
        var helper = IElementHelper.get();
        tooltip.add(List.of(
            helper.item(stack, 1.0f),
            helper.spacer(4, 0),
            helper.text(label.copy()
                .append(": ")
                .append(stack.getHoverName())
                .withStyle(ChatFormatting.WHITE))
        ));
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
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static UtilityMachineInfo resolveInfo(BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (blockEntity instanceof UtilityMachineInfo info) {
            return info;
        }

        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        BlockState state = accessor.getBlockState();
        if (state.getBlock() instanceof AbstractTwoBlockUtilityBlock<?>) {
            BlockEntity main = level.getBlockEntity(AbstractTwoBlockUtilityBlock.getMainPos(pos, state));
            if (main instanceof UtilityMachineInfo info) {
                return info;
            }
        }
        return null;
    }
}
