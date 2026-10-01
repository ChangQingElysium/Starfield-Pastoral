package com.stardew.craft.mixin;

import com.google.common.collect.Maps;
import com.stardew.craft.port.PortGameTestHarness121;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import java.util.Collection;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestBatchRunner;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 {@code StructureGridSpawner} + {@code GameTestRunner#runBatch} (see {@link PortGameTestHarness121}).
 * <ul>
 * <li>The grid row (test count and row bounds) carries over between batches. 1.20.1 restarts both for every batch,
 * so small batches extend one row indefinitely and a later row can be laid over the leftovers of earlier, deeper
 * tests.</li>
 * <li>Each structure is prepared with the 1.21.1 steps (structure block below the corner, clearing, encasing) and
 * placed later by the {@code GameTestInfo} mixin; the spacing uses the 1.21.1 whole-block bounds.</li>
 * <li>When a batch finishes, every forced chunk is released before the next batch starts.</li>
 * </ul>
 */
@Mixin(GameTestBatchRunner.class)
public abstract class PortGameTestBatchRunnerHarnessMixin {
    @Shadow @Final private BlockPos firstTestNorthWestCorner;
    @Shadow @Final ServerLevel level;
    @Shadow @Final private int testsPerRow;
    @Shadow @Final private BlockPos.MutableBlockPos nextTestNorthWestCorner;

    @Unique private AABB stardewcraft$rowBounds;
    @Unique private int stardewcraft$currentRowCount;

    @Inject(method = "runBatch", at = @At("HEAD"))
    private void stardewcraft$releaseForcedChunks121(int index, CallbackInfo ci) {
        if (index == 0) return;
        new LongArraySet(this.level.getForcedChunks())
                .forEach(chunk -> this.level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false));
    }

    @Inject(method = "createStructuresForBatch", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$createStructuresForBatch121(Collection<GameTestInfo> infos,
            CallbackInfoReturnable<Map<GameTestInfo, BlockPos>> cir) {
        if (this.stardewcraft$rowBounds == null) this.stardewcraft$rowBounds = new AABB(this.firstTestNorthWestCorner);
        Map<GameTestInfo, BlockPos> structureBlocks = Maps.newHashMap();
        for (GameTestInfo info : infos) {
            ((PortGameTestHarness121) info).stardewcraft$prepareTestStructure121(new BlockPos(this.nextTestNorthWestCorner));
            AABB bounds = PortGameTestHarness121.structureBounds(
                    (net.minecraft.world.level.block.entity.StructureBlockEntity) this.level.getBlockEntity(info.getStructureBlockPos()));
            this.stardewcraft$rowBounds = this.stardewcraft$rowBounds.minmax(bounds);
            this.nextTestNorthWestCorner.move((int) bounds.getXsize() + 5, 0, 0);
            if (++this.stardewcraft$currentRowCount >= this.testsPerRow) {
                this.stardewcraft$currentRowCount = 0;
                this.nextTestNorthWestCorner.move(0, 0, (int) this.stardewcraft$rowBounds.getZsize() + 6);
                this.nextTestNorthWestCorner.setX(this.firstTestNorthWestCorner.getX());
                this.stardewcraft$rowBounds = new AABB(this.nextTestNorthWestCorner);
            }
            structureBlocks.put(info, info.getStructureBlockPos());
        }
        cir.setReturnValue(structureBlocks);
    }
}
