package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.tree.fruit.FruitTreeBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeExtensionBlock;
import com.stardew.craft.block.tree.fruit.FruitTreeSaplingBlock;
import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.manager.FruitTreeGrowthManager;
import com.stardew.craft.tree.fruit.FruitTreeType;
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

import java.util.Objects;

public enum FruitTreeJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "fruit_tree_info");

    private static final String KIND = "Kind";
    private static final String KIND_SAPLING = "sapling";
    private static final String KIND_MATURE = "mature";
    private static final String STAGE = "Stage";
    private static final String DAYS_GROWN = "DaysGrown";
    private static final String DAYS_REMAINING = "DaysRemaining";
    private static final String BLOCKED = "Blocked";
    private static final String FRUIT_COUNT = "FruitCount";
    private static final String MAX_FRUIT_COUNT = "MaxFruitCount";
    private static final String DAYS_SINCE_MATURE = "DaysSinceMature";
    private static final String QUALITY = "Quality";
    private static final String LIGHTNING_DAYS = "LightningDays";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = accessor.getBlockState();
        BlockPos pos = Objects.requireNonNull(accessor.getPosition(), "position");
        if (state.getBlock() instanceof FruitTreeSaplingBlock) {
            BlockPos lowerPos = FruitTreeSaplingBlock.lowerPos(state, pos);
            FruitTreeGrowthManager manager = FruitTreeGrowthManager.get(serverLevel);
            tag.putString(KIND, KIND_SAPLING);
            tag.putInt(STAGE, manager.getGrowthStage(serverLevel, lowerPos));
            tag.putInt(DAYS_GROWN, manager.getDaysGrown(serverLevel, lowerPos));
            tag.putInt(DAYS_REMAINING, manager.getDaysRemaining(serverLevel, lowerPos));
            tag.putBoolean(BLOCKED, manager.isBlockedNow(serverLevel, lowerPos));
            return;
        }

        FruitTreeBlockEntity tree = resolveTree(accessor, pos);
        if (tree == null) {
            return;
        }
        tag.putString(KIND, KIND_MATURE);
        tag.putInt(FRUIT_COUNT, tree.getFruitCount());
        tag.putInt(MAX_FRUIT_COUNT, tree.getMaxStoredFruit());
        tag.putInt(DAYS_SINCE_MATURE, tree.getDaysSinceMature());
        tag.putInt(QUALITY, tree.getCurrentFruitQuality());
        tag.putInt(LIGHTNING_DAYS, tree.getLightningDays());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(KIND)) {
            return;
        }

        String kind = data.getString(KIND);
        if (KIND_SAPLING.equals(kind)) {
            int daysGrown = data.getInt(DAYS_GROWN);
            int daysRemaining = data.getInt(DAYS_REMAINING);
            int totalDays = daysGrown + daysRemaining;
            if (totalDays <= 0) {
                totalDays = FruitTreeType.DAYS_TO_MATURE;
            }
            tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_stage", data.getInt(STAGE) + 1, 4));
            tooltip.add(Component.translatable("stardewcraft.tooltip.growth_stage", daysGrown + "/" + totalDays));
            if (data.getBoolean(BLOCKED)) {
                tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_blocked").withStyle(ChatFormatting.RED));
            }
            return;
        }

        if (KIND_MATURE.equals(kind)) {
            int fruitCount = data.getInt(FRUIT_COUNT);
            int lightningDays = data.getInt(LIGHTNING_DAYS);
            tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_fruit", fruitCount,
                    data.getInt(MAX_FRUIT_COUNT)));
            tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_age", data.getInt(DAYS_SINCE_MATURE)));
            if (lightningDays > 0) {
                tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_lightning", lightningDays)
                        .withStyle(ChatFormatting.GRAY));
            } else if (fruitCount > 0) {
                tooltip.add(Component.translatable("stardewcraft.tooltip.fruit_tree_quality",
                        QualityHelper.getQualityName(data.getInt(QUALITY))));
            }
        }
    }

    private static FruitTreeBlockEntity resolveTree(BlockAccessor accessor, BlockPos pos) {
        if (accessor.getBlockEntity() instanceof FruitTreeBlockEntity tree) {
            return tree;
        }
        if (!(accessor.getBlockState().getBlock() instanceof FruitTreeBlock
                || accessor.getBlockState().getBlock() instanceof FruitTreeExtensionBlock)) {
            return null;
        }
        BlockPos root = FruitTreeBlock.findRoot(accessor.getLevel(), pos);
        if (root == null) {
            return null;
        }
        BlockEntity blockEntity = accessor.getLevel().getBlockEntity(root);
        return blockEntity instanceof FruitTreeBlockEntity tree ? tree : null;
    }
}
