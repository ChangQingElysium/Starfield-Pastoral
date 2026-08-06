package com.stardew.craft.item;

import com.stardew.craft.block.FertilizerType;
import com.stardew.craft.farming.FertilizerApplicationService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 肥料物品 - 可以在耕地上施加肥料
 */
public class FertilizerItem extends SimpleStardewItem {
    private final FertilizerType fertilizerType;

    public FertilizerItem(FertilizerType fertilizerType, int sellPrice, Properties properties) {
        super("stardewcraft.type.fertilizer", sellPrice, properties);
        this.fertilizerType = fertilizerType;
    }

    public FertilizerType fertilizerType() {
        return fertilizerType;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Level level = context.getLevel();
        if (FertilizerApplicationService.resolveTarget(level, context.getClickedPos()) == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        ServerPlayer player = context.getPlayer() instanceof ServerPlayer serverPlayer
                ? serverPlayer
                : null;
        FertilizerApplicationService.Result result = FertilizerApplicationService.apply(
                serverLevel, player, context.getClickedPos(), fertilizerType);
        if (!result.applied()) {
            if (result.status() == FertilizerApplicationService.Status.NO_PERMISSION && player != null) {
                player.displayClientMessage(
                        Component.translatable("stardewcraft.farm.build_farm_only"), true);
            }
            return InteractionResult.FAIL;
        }

        level.playSound(
                null,
                result.soilPos(),
                SoundEvents.GRAVEL_PLACE,
                SoundSource.BLOCKS,
                1.0F,
                1.0F);
        if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
