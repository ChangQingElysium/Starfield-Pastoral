package com.stardew.craft.mixin;

import com.google.common.base.Stopwatch;
import com.stardew.craft.port.PortGameTestHarness121;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): 1.21.1 {@code GameTestInfo} lifecycle for tests laid out by the GameTest server (see
 * {@link PortGameTestHarness121}). 1.20.1 places the structure while the batch is created and starts the test one
 * tick later, while the far-away test chunks may still be loading and their light is not computed yet. 1.21.1 only
 * prepares the structure block, then waits until every chunk the structure intersects is entity-ticking, waits
 * another 20 ticks, places the structure and starts the test in that same tick ({@code startTick = now + setupTicks}).
 */
@Mixin(GameTestInfo.class)
public abstract class PortGameTestInfoHarnessMixin implements PortGameTestHarness121 {
    @Shadow @Final private TestFunction testFunction;
    @Shadow private BlockPos structureBlockPos;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Collection<GameTestListener> listeners;
    @Shadow private long startTick;
    @Shadow @Final private Stopwatch timer;
    @Shadow private StructureBlockEntity structureBlockEntity;

    @Shadow public abstract boolean isDone();
    @Shadow public abstract String getStructureName();
    @Shadow public abstract String getTestName();
    @Shadow public abstract Rotation getRotation();

    @Unique private boolean stardewcraft$harness121;
    @Unique private int stardewcraft$ticksToWaitForChunkLoading = 20;
    @Unique private boolean stardewcraft$placedStructure;
    @Unique private boolean stardewcraft$chunksLoaded;

    @Override
    public void stardewcraft$prepareTestStructure121(BlockPos northWestCorner) {
        Rotation rotation = this.getRotation();
        StructureTemplate template = this.level.getStructureManager().get(new ResourceLocation(this.getStructureName()))
                .orElseThrow(() -> new IllegalStateException("Missing test structure: " + this.getStructureName()));
        Vec3i size = template.getSize();
        BoundingBox boundingBox = PortGameTestHarness121.structureBoundingBox(northWestCorner, size, rotation);
        BlockPos blockPos;
        if (rotation == Rotation.NONE) {
            blockPos = northWestCorner;
        } else if (rotation == Rotation.CLOCKWISE_90) {
            blockPos = northWestCorner.offset(size.getZ() - 1, 0, 0);
        } else if (rotation == Rotation.CLOCKWISE_180) {
            blockPos = northWestCorner.offset(size.getX() - 1, 0, size.getZ() - 1);
        } else {
            blockPos = northWestCorner.offset(0, 0, size.getX() - 1);
        }
        PortGameTestHarness121.forceLoadChunks(boundingBox, this.level);
        PortGameTestHarness121.clearSpaceForStructure(boundingBox, this.level);

        // 1.21.1 StructureUtils#createStructureBlock: LOAD mode and the template size, nothing placed yet.
        BlockPos structurePos = blockPos.below();
        this.level.setBlockAndUpdate(structurePos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        StructureBlockEntity entity = (StructureBlockEntity) this.level.getBlockEntity(structurePos);
        entity.setMode(StructureMode.LOAD);
        entity.setRotation(rotation);
        entity.setIgnoreEntities(false);
        entity.setStructureName(new ResourceLocation(this.getStructureName()));
        entity.setStructureSize(size);
        entity.setChanged();
        this.structureBlockEntity = entity;
        this.structureBlockPos = structurePos;
        // 1.20.1 names the structure block after the test (its /test runthis looks the test up by that name);
        // 1.21.1 keeps the template name and stores the test name as metadata. The template is resolved by
        // getStructureName() when it is placed, so both are kept.
        entity.setMetaData(this.getTestName());
        entity.setStructureName(this.getTestName());
        StructureUtils.addCommandBlockAndButtonToStartTest(structurePos, new BlockPos(1, 0, -1), rotation, this.level);
        PortGameTestHarness121.encaseStructure(PortGameTestHarness121.structureBounds(entity), this.level, true);
        this.stardewcraft$harness121 = true;
    }

    /**
     * 1.20.1 {@code GameTestRunner#runTest} starts the clock here; 1.21.1 starts it when the structure is placed.
     */
    @Inject(method = "startExecution", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$deferStart121(CallbackInfo ci) {
        if (this.stardewcraft$harness121) ci.cancel();
    }

    /**
     * 1.20.1 {@code GameTestRunner#runTest} spawns (clears and places) the structure again after the batch created it;
     * a prepared test only reports {@code testStructureLoaded}, which 1.21.1 fires at the end of
     * {@code prepareTestStructure} (the report listener is only attached by {@code runTest} in 1.20.1).
     */
    @Inject(method = "spawnStructure", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$keepPreparedStructure121(BlockPos pos, int padding, CallbackInfo ci) {
        if (!this.stardewcraft$harness121) return;
        GameTestInfo self = (GameTestInfo) (Object) this;
        this.listeners.forEach(listener -> listener.testStructureLoaded(self));
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$waitForChunksAndPlace121(CallbackInfo ci) {
        if (!this.stardewcraft$harness121 || this.isDone()) return;
        BoundingBox boundingBox = PortGameTestHarness121.structureBoundingBox(this.structureBlockEntity);
        if (!this.stardewcraft$chunksLoaded && !PortGameTestHarness121.chunksEntityTicking(boundingBox, this.level)) {
            ci.cancel();
            return;
        }
        this.stardewcraft$chunksLoaded = true;
        if (this.stardewcraft$placedStructure) return;
        if (this.stardewcraft$ticksToWaitForChunkLoading > 0) {
            this.stardewcraft$ticksToWaitForChunkLoading--;
            ci.cancel();
            return;
        }
        // 1.21.1 GameTestInfo#placeStructure + startExecution(0); the vanilla tick then starts the test this tick.
        this.stardewcraft$ticksToWaitForChunkLoading = 0;
        this.stardewcraft$placedStructure = true;
        StructureTemplate template = this.level.getStructureManager().get(new ResourceLocation(this.getStructureName()))
                .orElseThrow(() -> new IllegalStateException("Missing test structure: " + this.getStructureName()));
        this.structureBlockEntity.loadStructure(this.level, false, template);
        this.level.getBlockTicks().clearArea(boundingBox);
        this.level.clearBlockEvents(boundingBox);
        this.startTick = this.level.getGameTime() + this.testFunction.getSetupTicks();
        if (!this.timer.isRunning()) this.timer.start();
    }
}
