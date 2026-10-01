package com.stardew.craft.block.mine;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.mining.MineRockClumpMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** The source 2x2 mine rocks: one bound and one settlement across every occupied cell. */
@SuppressWarnings("null")
public final class MineRockClumpBlock extends MapDecorStaticBlock {
    private final int sourceId;

    public MineRockClumpBlock(int sourceId, Properties properties) {
        super(properties, "mine/nodes/stone_" + sourceId, true);
        this.sourceId = sourceId;
    }

    public int sourceId() { return sourceId; }

    @Override protected VoxelShape canonicalShape() {
        AABB bounds = ModelVoxelShapeCache.shape("stardewcraft:block/mine/nodes/stone_" + sourceId).bounds();
        return Shapes.create(new AABB(bounds.minX, 0, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ));
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(PART) == Part.EXTENSION) return super.canSurvive(state, level, pos);
        for (var offset : occupiedOffsets(state.getValue(FACING))) {
            if (offset.dy() != 0) continue;
            BlockPos soil = pos.offset(offset.dx(), -1, offset.dz());
            if (!level.getBlockState(soil).isFaceSturdy(level, soil, Direction.UP)) return false;
        }
        return true;
    }

    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return MineRockClumpMining.canMine(sourceId, player.getMainHandItem())
                ? Math.nextUp(1.0F / MineRockClumpMining.breakTicks(sourceId, player.getMainHandItem())) : 0;
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        // Generic decor cleanup would drop its placeable item when an extension disappears.
        runWithDropsSuppressed(() -> super.onRemove(state, level, pos, replacement, moving));
    }

    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            MineRockClumpMining.rememberBreakOrigin(serverPlayer, pos, state);
        // Let the actual destroyed cell emit BlockDropsEvent; onRemove cleans the whole bound.
        level.levelEvent(player, 2001, pos, Block.getId(state));
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
    }
}
