package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;

/**
 * 蓄水池 — 永远可以作为水源使用。
 * <ul>
 *   <li>空桶右键 → 变成水桶</li>
 *   <li>喷壶右键 → 由 {@code WateringCanItem} 的 {@code isWaterSource} 检测处理</li>
 * </ul>
 */
public class ReservoirBlock extends MapDecorStaticBlock {

    public ReservoirBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @Override
    @SuppressWarnings("null")
    protected @Nonnull ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state,
            @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
            @Nonnull BlockHitResult hitResult) {

        // 空桶 → 水桶
        if (stack.is(Items.BUCKET)) {
            if (!level.isClientSide) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                    ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                    if (!player.getInventory().add(waterBucket)) {
                        player.drop(waterBucket, false);
                    }
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
