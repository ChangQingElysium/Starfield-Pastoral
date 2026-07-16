package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mastery.TallMasteryBlock;
import com.stardew.craft.blockentity.MasteryStatueBlockEntity;
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

public enum MasteryStatueJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mastery_statue_owner");

    private static final String NBT_OWNER_NAME = "OwnerName";
    private static final String NBT_HAS_OWNER = "HasOwner";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        MasteryStatueBlockEntity statue = getStatue(accessor);
        if (statue != null) {
            tag.putBoolean(NBT_HAS_OWNER, statue.hasOwner());
            if (statue.hasOwner()) {
                String ownerName = statue.getOwnerName();
                if (statue.getOwnerUUID() != null && accessor.getLevel() instanceof ServerLevel serverLevel) {
                    ownerName = com.stardew.craft.player.PlayerDisplayName.get(
                            serverLevel.getServer(), statue.getOwnerUUID());
                }
                tag.putString(NBT_OWNER_NAME, ownerName);
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (tag.getBoolean(NBT_HAS_OWNER)) {
            tooltip.add(Component.translatable("stardewcraft.jade.mailbox.owner", tag.getString(NBT_OWNER_NAME))
                .withStyle(ChatFormatting.GRAY));
        }
    }

    @SuppressWarnings("null")
    private static MasteryStatueBlockEntity getStatue(BlockAccessor accessor) {
        BlockEntity beDirect = accessor.getBlockEntity();
        if (beDirect instanceof MasteryStatueBlockEntity statue) {
            return statue;
        }
        BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof TallMasteryBlock)) {
            return null;
        }
        BlockPos mainPos = TallMasteryBlock.getMainPos(accessor.getPosition(), state);
        BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
        return be instanceof MasteryStatueBlockEntity statue ? statue : null;
    }
}
