package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.festival.FestivalOfIceService;
import com.stardew.craft.festival.desert.DesertFestivalRaceService;
import com.stardew.craft.festival.desert.DesertFestivalService;
import com.stardew.craft.festival.desert.DesertFestivalSpecialInteractionService;
import com.stardew.craft.festival.desert.DesertFestivalWillyFishingService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Identifiers;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum PortalTriggerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(StardewCraft.MODID, "portal_trigger");
    private static final String NBT_TARGET_ID = "TargetId";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @SuppressWarnings("null")
    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (blockEntity instanceof PortalTriggerBlockEntity portalTrigger) {
            tag.putString(NBT_TARGET_ID, portalTrigger.getTargetId());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        String targetId = accessor.getServerData().getString(NBT_TARGET_ID);
        if (DesertFestivalService.EGG_SHOP_TARGET_ID.equals(targetId)) {
            tooltip.add(Component.translatable("stardewcraft.location.desert_festival_egg_shop")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("stardewcraft.portal.hint.open")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (DesertFestivalRaceService.RACE_MAN_TARGET_ID.equals(targetId)) {
            tooltip.add(Component.translatable("stardewcraft.location.desert_festival_race_man")
                .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("stardewcraft.portal.hint.desert_race")
                .withStyle(ChatFormatting.GRAY));
        }
        if (DesertFestivalRaceService.SHADY_GUY_TARGET_ID.equals(targetId)) {
            tooltip.add(Component.translatable("stardewcraft.location.desert_festival_shady_guy")
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("stardewcraft.portal.hint.shady_guy")
                .withStyle(ChatFormatting.GRAY));
        }
        if (DesertFestivalSpecialInteractionService.SCHOLAR_TARGET_ID.equals(targetId)) {
            tooltip.add(Component.translatable("stardewcraft.location.desert_festival_scholar")
                .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("stardewcraft.portal.hint.interact")
                .withStyle(ChatFormatting.GRAY));
        }
        if (DesertFestivalWillyFishingService.TARGET_ID.equals(targetId)) {
            tooltip.remove(Identifiers.CORE_OBJECT_NAME);
            tooltip.add(Component.translatable("stardewcraft.portal.hint.willy_challenge")
                .withStyle(ChatFormatting.GRAY));
        }
        if (FestivalOfIceService.TRAVELING_MERCHANT_TARGET_ID.equals(targetId)) {
            tooltip.add(Component.translatable("stardewcraft.location.festival_of_ice_traveling_merchant")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("stardewcraft.portal.hint.open")
                .withStyle(ChatFormatting.GRAY));
        }
    }
}
