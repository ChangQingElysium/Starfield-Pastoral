package com.stardew.craft.block.crop;

import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.function.Supplier;

/**
 * 玩家手持彩色花物品右键，将成熟阶段的作物方块直接放到耕地上。
 * 放置后立即从 CropGrowthManager 移除，避免季节变换时枯萎。
 */
public final class FlowerPlacement {

    private FlowerPlacement() {}

    public static InteractionResult place(UseOnContext context, Supplier<? extends Block> flowerBlockSupplier) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos basePos = context.getClickedPos();
        BlockState baseState = level.getBlockState(basePos);
        if (!StardewCropBlock.isNaturalSoil(baseState)) {
            return InteractionResult.PASS;
        }

        BlockPos lowerPos = basePos.above();
        BlockState lowerExisting = level.getBlockState(lowerPos);
        if (!lowerExisting.canBeReplaced()) {
            return InteractionResult.PASS;
        }

        Block flowerBlock = flowerBlockSupplier.get();
        BlockState defaultState = flowerBlock.defaultBlockState();
        if (!defaultState.hasProperty(StardewCropBlock.AGE)) {
            return InteractionResult.PASS;
        }

        BlockState matureState = defaultState.setValue(StardewCropBlock.AGE, StardewCropBlock.MAX_AGE);
        if (matureState.hasProperty(StardewCropBlock.PLACED_BY_PLAYER)) {
            matureState = matureState.setValue(StardewCropBlock.PLACED_BY_PLAYER, Boolean.TRUE);
        }
        ItemStack stack = context.getItemInHand();
        matureState = applyFlowerColor(matureState, stack, flowerBlock);

        boolean isDouble = matureState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        BlockPos upperPos = lowerPos.above();
        if (isDouble) {
            BlockState upperExisting = level.getBlockState(upperPos);
            if (!upperExisting.canBeReplaced()) {
                return InteractionResult.PASS;
            }
        }

        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockState placedLower = isDouble
                ? matureState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                : matureState;
        level.setBlock(lowerPos, placedLower, Block.UPDATE_ALL);
        if (isDouble) {
            BlockState placedUpper = matureState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
            level.setBlock(upperPos, placedUpper, Block.UPDATE_ALL);
        }

        // 玩家放置的花不参与日常生长与季节枯萎：放置完立即从管理器移除。
        if (level instanceof ServerLevel serverLevel) {
            CropGrowthManager manager = CropGrowthManager.get(serverLevel);
            manager.removeCrop(serverLevel, lowerPos);
            if (isDouble) {
                manager.removeCrop(serverLevel, upperPos);
            }
        }

        Player player = context.getPlayer();
    Entity soundContext = player != null
        ? player
        : new ItemEntity(level, lowerPos.getX() + 0.5D, lowerPos.getY() + 0.5D, lowerPos.getZ() + 0.5D, ItemStack.EMPTY);
    SoundType soundType = placedLower.getBlock().getSoundType(placedLower, level, lowerPos, soundContext);
        level.playSound(null, lowerPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
            (soundType.getVolume() + 1f) / 2f, soundType.getPitch() * 0.8f);

        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(false);
    }

    private static BlockState applyFlowerColor(BlockState state, ItemStack stack, Block flowerBlock) {
        Integer color = readFlowerColor(stack);
        if (color == null) {
            return state;
        }
        int blockColor = flowerBlock instanceof FairyRoseCropBlock
            ? FairyRoseCropBlock.itemColorToBlockColor(color)
            : color;
        for (Property<?> prop : state.getProperties()) {
            if ("color".equals(prop.getName()) && prop instanceof IntegerProperty ip) {
                int clamped = clampToProperty(ip, blockColor);
                return state.setValue(ip, clamped);
            }
        }
        return state;
    }

    private static int clampToProperty(IntegerProperty ip, int value) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Integer v : ip.getPossibleValues()) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min == Integer.MAX_VALUE) return value;
        return Math.max(min, Math.min(max, value));
    }

    private static Integer readFlowerColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains("FlowerColor")) {
            return null;
        }
        return tag.getInt("FlowerColor");
    }
}
