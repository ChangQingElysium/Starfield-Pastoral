package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.cooking.service.CookingPotService;
import com.stardew.craft.menu.MiniForgeMenu;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Existing recipe and inventory services, with the island's authored collision and placement. */
public final class IslandWorkstationBlock extends MapDecorStaticBlock {
    public enum Kind { KITCHEN, FORGE }
    private final Kind kind;

    public IslandWorkstationBlock(Properties properties, String model, Kind kind) {
        super(properties, model);
        this.kind = kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return open(state, level, pos, player).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return player.isShiftKeyDown() ? InteractionResult.PASS : open(state, level, pos, player);
    }

    private InteractionResult open(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer actor)) return InteractionResult.PASS;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return InteractionResult.PASS;
        if (kind == Kind.KITCHEN) {
            CookingPotService.openForPlayer(actor, main);
        } else {
            level.playSound(null, main, ModSounds.BIG_SELECT.get(), SoundSource.BLOCKS, 1, 1);
            actor.openMenu(new SimpleMenuProvider((id, inventory, who) -> new MiniForgeMenu(id, inventory)
                    .atWorkstation(ContainerLevelAccess.create(level, pos), this),
                    Component.translatable("container.stardewcraft.mini_forge")));
        }
        return InteractionResult.CONSUME;
    }
}
