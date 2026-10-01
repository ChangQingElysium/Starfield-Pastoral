package com.stardew.craft.block.mastery;

import com.stardew.craft.menu.MiniForgeMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings("null")
public class MiniForgeBlock extends HorizontalMasteryBlock {
    private static final Component TITLE = Component.translatable("container.stardewcraft.mini_forge");

    public MiniForgeBlock(Properties properties) {
        super(properties, "stardewcraft:block/mastery/mini_forge", Direction.SOUTH);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        openMenu(level, pos, player);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        openMenu(level, pos, player);
        return InteractionResult.CONSUME;
    }

    private static void openMenu(Level level, BlockPos pos, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, pos, ModSounds.BIG_SELECT.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new MiniForgeMenu(containerId, inventory), TITLE));
        }
    }
}
