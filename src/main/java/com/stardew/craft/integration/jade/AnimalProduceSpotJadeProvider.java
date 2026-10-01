package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.animal.AnimalProduceSpotBlock;
import com.stardew.craft.blockentity.AnimalProduceSpotBlockEntity;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

import java.util.List;

public enum AnimalProduceSpotJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "animal_produce_spot");
    private static final String NBT_ITEM = "item";
    private static final String NBT_QUALITY = "quality";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    @SuppressWarnings("null")
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof AnimalProduceSpotBlock)) {
            return;
        }
        BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof AnimalProduceSpotBlockEntity spotBe)) {
            return;
        }

        ItemStack stack = spotBe.getProduceStack();
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            tag.putString(NBT_ITEM, id.toString());
        }
        tag.putInt(NBT_QUALITY, QualityHelper.getQuality(stack));
    }

    @Override
    @SuppressWarnings("null")
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(NBT_ITEM)) {
            return;
        }

        ItemStack stack = stackFromId(data.getString(NBT_ITEM));
        if (stack.isEmpty()) {
            return;
        }

        int quality = data.getInt(NBT_QUALITY);
        QualityHelper.setQuality(stack, quality);

        MutableComponent name = Component.empty();
        name.append(QualityHelper.getQualityPrefix(quality));
        name.append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        var helper = IElementHelper.get();
        tooltip.add(List.of(
            helper.item(stack, 1.0f),
            helper.spacer(4, 0),
            helper.text(name)
        ));
    }

    @SuppressWarnings("null")
    private static ItemStack stackFromId(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
