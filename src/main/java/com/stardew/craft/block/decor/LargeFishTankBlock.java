package com.stardew.craft.block.decor;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.aquarium.AquariumRules;
import com.stardew.craft.blockentity.AquariumBlockEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** The approved 64 x 40 x 32 model occupies 4 x 2 floor cells and three height cells. */
public final class LargeFishTankBlock extends MapDecorStaticBlock implements EntityBlock {
    public LargeFishTankBlock(Properties properties) { super(properties, "stardewcraft:block/large_fish_tank"); }
    @Override protected VoxelShape canonicalShape() { return Block.box(0, 0, 0, 64, 40, 32); }
    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos target = pos.offset(cell.dx(), cell.dy(), cell.dz());
            if (!level.hasChunkAt(target) || level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)) return false;
            if (cell.dy() == 0 && !Block.canSupportCenter(level, target.below(), Direction.UP)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
        }
        return level.isUnobstructed(defaultBlockState().setValue(FACING, facing), pos,
                context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer()));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new AquariumBlockEntity(pos, state) : null;
    }
    @Override public ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        interact(held, state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        interact(ItemStack.EMPTY, state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private void interact(ItemStack held, BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !player.mayBuild()) return;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null || !(level.getBlockEntity(main) instanceof AquariumBlockEntity tank)) return;
        if (!player.isShiftKeyDown() && AquariumRules.kind(held) != AquariumRules.Kind.INVALID) {
            if (tank.insert(held) >= 0) {
                com.stardew.craft.port.PortItemStacks.consume(held, 1, player);
                level.playSound(null, main, ModSounds.DROP_ITEM_IN_WATER.get(), SoundSource.BLOCKS, .65f, 1);
            } else player.displayClientMessage(Component.translatable("stardewcraft.aquarium.full"), true);
        } else player.openMenu(tank);
    }
    private ItemStack packed(AquariumBlockEntity tank) {
        var stack = new ItemStack(this);
        if (!tank.isEmpty()) {
            // BlockItem restores these components on placement; no custom packet or item-ID conversion.
            var data = tank.saveWithoutMetadata();
            data.putString("id", "stardewcraft:large_fish_tank");
            PortItemData.set(stack, DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        }
        return stack;
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }
    @Override protected ItemStack extensionRemovalDrop(Level level, BlockPos mainPos) { return ItemStack.EMPTY; }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = findMainPos(level, pos, state);
        if (main != null && level.getBlockEntity(main) instanceof AquariumBlockEntity tank)
            tank.discardEmptyOnRemoval = player.isCreative();
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !dropsSuppressed()
                && level.getBlockEntity(pos) instanceof AquariumBlockEntity tank) {
            if (!tank.discardEmptyOnRemoval || !tank.isEmpty()) Block.popResource(level, pos, packed(tank));
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
