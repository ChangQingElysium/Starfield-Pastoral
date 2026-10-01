package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.blockentity.HayHopperBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.Nullable;
import java.util.UUID;

public enum HayHopperJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "hay_hopper");

    private static final String NBT_HAY = "hay";
    private static final String NBT_CAPACITY = "capacity";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        HayHopperBlockEntity hopper = resolveHopper(accessor);
        if (hopper == null) {
            return;
        }

        UUID owner = hopper.storageFarm();
        if (owner == null) {
            tag.putInt(NBT_HAY, 0);
            tag.putInt(NBT_CAPACITY, 0);
            return;
        }

        tag.putInt(NBT_HAY, com.stardew.craft.animal.runtime.FarmFeed.amount(serverLevel.getServer(), owner));
        tag.putInt(NBT_CAPACITY, com.stardew.craft.animal.runtime.FarmFeed.capacity(serverLevel.getServer(), owner));
    }

    @SuppressWarnings("null")
    @Nullable
    private static HayHopperBlockEntity resolveHopper(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof HayHopperBlockEntity direct) {
            return direct;
        }

        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof HayHopperBlock)) {
            return null;
        }
        if (state.getValue(HayHopperBlock.PART) != HayHopperBlock.Part.EXTENSION) {
            return null;
        }

        BlockPos mainPos = HayHopperBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity mainBe = accessor.getLevel().getBlockEntity(mainPos);
        if (mainBe instanceof HayHopperBlockEntity hopper) {
            return hopper;
        }
        return null;
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null) {
            return;
        }

        int hay = data.getInt(NBT_HAY);
        int capacity = data.getInt(NBT_CAPACITY);
        tooltip.add(Component.translatable("stardewcraft.tooltip.hay_hopper.hay", hay, capacity).withStyle(ChatFormatting.WHITE));
    }
}