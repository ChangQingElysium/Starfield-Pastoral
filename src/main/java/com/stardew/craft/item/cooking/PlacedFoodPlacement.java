package com.stardew.craft.item.cooking;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.PlacedFishBlock;
import com.stardew.craft.item.artisan.PlaceableArtisanDrinkItem;
import com.stardew.craft.item.fish.FishItem;
import com.stardew.craft.item.fish.crabpot.CrabPotItem;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/** Shared placement lookup and Shift-use path for dishes, bottles and catches; also drives the existing tooltip badge. */
public final class PlacedFoodPlacement {
    private PlacedFoodPlacement() {}

    @Nullable public static Block blockFor(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof FishItem || item instanceof CrabPotItem) return ModBlocks.PLACED_FISH.get();
        String id;
        if (item instanceof PlaceableArtisanDrinkItem drink) id = drink.getPlacedBlockId();
        else if (item instanceof CookingDishItem) id = BuiltInRegistries.ITEM.getKey(item).getPath();
        else return null;
        var block = ModBlocks.getPlacedCookingFoodBlock(id);
        return block == null ? null : block.get();
    }

    public static InteractionResult place(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        var block = blockFor(context.getItemInHand());
        if (block == null) return InteractionResult.PASS;
        var level = context.getLevel();
        var placement = new BlockPlaceContext(context);
        var pos = placement.getClickedPos();
        if (!player.mayBuild() || !placement.canPlace() || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)
                || !level.mayInteract(player, pos) || !level.mayInteract(player, context.getClickedPos())
                || !player.mayUseItemAt(pos, placement.getClickedFace(), context.getItemInHand())
                || !level.getFluidState(pos).isEmpty()) return InteractionResult.FAIL;
        var state = block.getStateForPlacement(placement);
        if (state == null || !state.canSurvive(level, pos)
                || !level.isUnobstructed(state, pos, CollisionContext.of(player))) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!level.setBlock(pos, state, 3)) return InteractionResult.FAIL;
            block.setPlacedBy(level, pos, state, player, context.getItemInHand());
            com.stardew.craft.port.PortItemStacks.consume(context.getItemInHand(), 1, player);
            level.playSound(null, pos, block instanceof PlacedFishBlock ? ModSounds.DWOP.get() : SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, .7F, 1F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
