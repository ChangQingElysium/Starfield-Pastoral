package com.stardew.craft.item.block;

import com.stardew.craft.secretnote.ShadowFootprintData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.io.IOException;

/** Places footprint decals; sneak-use exports all authored footprints in this dimension. */
public final class ShadowFootprintBlockItem extends BlockItem {
    public ShadowFootprintBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        export(level, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) {
            return super.useOn(context);
        }
        export(context.getLevel(), context.getPlayer());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private static void export(Level level, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            try {
                ShadowFootprintData.ExportResult result = ShadowFootprintData.get(serverLevel).export(serverLevel);
                serverPlayer.sendSystemMessage(Component.translatable(
                        "stardewcraft.shadow_footprint.exported", result.count(), result.path().toString()));
            } catch (IOException exception) {
                serverPlayer.sendSystemMessage(Component.translatable(
                        "stardewcraft.shadow_footprint.export_failed", exception.getMessage()));
            }
        }
    }
}
