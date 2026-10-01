package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.AutoPetterBlockEntity;
import com.stardew.craft.blockentity.HeaterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum AnimalUtilityJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "animal_utility");
    private static final String NBT_WORKING = "working";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof AutoPetterBlockEntity autoPetter) {
            tag.putBoolean(NBT_WORKING, autoPetter.isWorking());
            return;
        }

        if (accessor.getBlockEntity() instanceof HeaterBlockEntity heater) {
            tag.putBoolean(NBT_WORKING, heater.isWorking());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) {
            return;
        }

        boolean working = data.getBoolean(NBT_WORKING);
        tooltip.add(Component.translatable(working
                ? "stardewcraft.tooltip.animal_utility.working"
                : "stardewcraft.tooltip.animal_utility.idle")
            .withStyle(working ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }
}
