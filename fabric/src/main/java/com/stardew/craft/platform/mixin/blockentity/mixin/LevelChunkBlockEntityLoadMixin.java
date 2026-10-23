package com.stardew.craft.platform.mixin.blockentity.mixin;


import com.stardew.craft.platform.fabric.blockentity.LevelBlockEntityLoads;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FAPI's immediate setBlockEntity event precedes NBT loading; use full registration. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockEntityLoadMixin {
    @Shadow
    public abstract Level getLevel();

    @Shadow
    public abstract Map<BlockPos, BlockEntity> getBlockEntities();

    @Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            shift = At.Shift.AFTER))
    private void stardewcraft$queueRegisteredBlockEntity(BlockEntity blockEntity, CallbackInfo callback) {
        ((LevelBlockEntityLoads) getLevel()).stardewcraft$blockEntityLoads()
                .enqueue(blockEntity, getBlockEntities());
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("HEAD"))
    private void stardewcraft$queueLoadedChunk(CallbackInfo callback) {
        var queue = ((LevelBlockEntityLoads) getLevel()).stardewcraft$blockEntityLoads();
        Map<BlockPos, BlockEntity> residents = getBlockEntities();
        residents.values().forEach(blockEntity -> queue.enqueue(blockEntity, residents));
    }
}
