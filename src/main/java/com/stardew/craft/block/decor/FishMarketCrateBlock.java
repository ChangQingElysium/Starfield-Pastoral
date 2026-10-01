package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.FishMarketCrateBlockEntity;
import com.stardew.craft.fishing.FishMarketCrateLayout;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

/** Two-cell furniture using the existing placement and exactly-once removal lifecycle. */
public final class FishMarketCrateBlock extends MapDecorStaticBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(.25, 0, .25, 31.75, 10, 15.75);

    public FishMarketCrateBlock(Properties properties) { super(properties, "stardewcraft:block/fish_market_crate"); }
    @Override protected VoxelShape canonicalShape() { return SHAPE; }

    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos target = pos.offset(cell.dx(), cell.dy(), cell.dz());
            if (!level.hasChunkAt(target) || !Block.canSupportCenter(level, target.below(), Direction.UP)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
        }
        return level.isUnobstructed(defaultBlockState().setValue(FACING, facing).setValue(PART, Part.MAIN),
                pos, context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer()));
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new FishMarketCrateBlockEntity(pos, state) : null;
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return interact(stack, state, level, pos, player, hit) == InteractionResult.FAIL
                ? ItemInteractionResult.FAIL : ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(player.getMainHandItem(), state, level, pos, player, hit);
    }

    private InteractionResult interact(ItemStack held, BlockState state, Level level, BlockPos pos,
                                       Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.FAIL;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null || !(level.getBlockEntity(main) instanceof FishMarketCrateBlockEntity crate)) return InteractionResult.FAIL;
        int slot = FishMarketCrateLayout.slotAt(hit.getLocation().subtract(Vec3.atLowerCornerOf(main)), state.getValue(FACING));
        if (!level.isClientSide) {
            if (!crate.fish(slot).isEmpty()) {
                ItemStack taken = crate.remove(slot);
                if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, taken);
                else {
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()), taken, false);
                    if (!remainder.isEmpty()) player.drop(remainder, false);
                }
                changed(level, main, player, true);
            } else if (crate.insert(slot, held) >= 0) {
                com.stardew.craft.port.PortItemStacks.consume(held, 1, player);
                changed(level, main, player, false);
            }
        }
        // One click addresses one slot; never fall through into eating or placing the held item.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void changed(Level level, BlockPos pos, Player player, boolean take) {
        level.playSound(null, pos, ModSounds.DWOP.get(), SoundSource.BLOCKS, .65F, take ? 1.15F : .9F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player));
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof FishMarketCrateBlockEntity crate) crate.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }
}
