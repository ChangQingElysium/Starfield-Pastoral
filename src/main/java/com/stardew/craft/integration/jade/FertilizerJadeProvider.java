package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.client.ClientFertilizerCache;
import com.stardew.craft.farming.FertilizerApplicationService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.FertilizerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.Nullable;

/** One Jade path for farmland, vanilla crops, core crops and registered addon crop parts. */
public enum FertilizerJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "fertilizer");
    private static final String DATA_CHECKED = "stardewcraft_fertilizer_checked";
    private static final String DATA_SOIL_POS = "stardewcraft_fertilizer_soil_pos";
    private static final String DATA_TYPE = "stardewcraft_fertilizer";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)
                || isDecorativeFlower(accessor)) {
            return;
        }
        FertilizerApplicationService.Target target =
                FertilizerApplicationService.resolveTarget(level, accessor.getPosition());
        if (target == null) {
            return;
        }

        BlockPos soilPos = target.soilPos();
        tag.putBoolean(DATA_CHECKED, true);
        tag.put(DATA_SOIL_POS, NbtUtils.writeBlockPos(soilPos));
        FertilizerType type = FertilizerManager.get(level).getFertilizer(level, soilPos);
        if (type != null) {
            tag.putString(DATA_TYPE, type.getSerializedName());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (isDecorativeFlower(accessor)) {
            return;
        }
        CompoundTag serverData = accessor.getServerData();
        boolean checked = serverData.getBoolean(DATA_CHECKED);
        BlockPos soilPos = checked
                ? NbtUtils.readBlockPos(serverData, DATA_SOIL_POS).orElse(null)
                : resolveClientSoil(accessor);
        if (soilPos == null) {
            return;
        }

        FertilizerType type;
        if (checked) {
            type = FertilizerType.bySerializedName(serverData.getString(DATA_TYPE));
            if (type == null) {
                ClientFertilizerCache.removeFertilizer(accessor.getLevel(), soilPos);
                return;
            }
            ClientFertilizerCache.setFertilizer(accessor.getLevel(), soilPos, type);
        } else {
            type = ClientFertilizerCache.getFertilizer(accessor.getLevel(), soilPos);
        }

        ItemStack stack = new ItemStack(itemFor(type));
        tooltip.add(Component.translatable("stardewcraft.jade.fertilizer", stack.getHoverName())
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }

    @Nullable
    private static BlockPos resolveClientSoil(BlockAccessor accessor) {
        FertilizerApplicationService.Target target = FertilizerApplicationService.resolveTarget(
                accessor.getLevel(), accessor.getPosition());
        return target == null ? null : target.soilPos();
    }

    private static boolean isDecorativeFlower(BlockAccessor accessor) {
        return StardewCropBlock.isDecorativeFlowerState(accessor.getBlockState());
    }

    private static Item itemFor(FertilizerType type) {
        return switch (type) {
            case BASIC_FERTILIZER -> ModItems.BASIC_FERTILIZER.get();
            case QUALITY_FERTILIZER -> ModItems.QUALITY_FERTILIZER.get();
            case DELUXE_FERTILIZER -> ModItems.DELUXE_FERTILIZER.get();
            case BASIC_RETAINING_SOIL -> ModItems.BASIC_RETAINING_SOIL.get();
            case QUALITY_RETAINING_SOIL -> ModItems.QUALITY_RETAINING_SOIL.get();
            case DELUXE_RETAINING_SOIL -> ModItems.DELUXE_RETAINING_SOIL.get();
            case SPEED_GRO -> ModItems.SPEED_GRO.get();
            case DELUXE_SPEED_GRO -> ModItems.DELUXE_SPEED_GRO.get();
            case HYPER_SPEED_GRO -> ModItems.HYPER_SPEED_GRO.get();
        };
    }
}
