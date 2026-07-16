package com.stardew.craft.mixin;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.service.AnimalEntityRecoveryState;
import com.stardew.craft.animal.service.ManagedAnimalEntitySanitizer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(EntityStorage.class)
public abstract class ManagedAnimalEntityLoadMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;

    @Redirect(
        method = "loadEntities",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
        )
    )
    private CompletableFuture<ChunkEntities<Entity>> stardewcraft$sanitizeAndPersistManagedAnimals(
        CompletableFuture<Optional<CompoundTag>> future,
        Function<? super Optional<CompoundTag>, ? extends ChunkEntities<Entity>> decoder,
        Executor executor,
        ChunkPos pos
    ) {
        return future.thenApplyAsync(optionalTag -> {
            if (optionalTag.isPresent()) {
                CompoundTag chunkTag = optionalTag.get();
                ListTag entities = chunkTag.getList("Entities", CompoundTag.TAG_COMPOUND);
                ManagedAnimalEntitySanitizer.Result result = ManagedAnimalEntitySanitizer.sanitize(entities);
                if (result.changed()) {
                    chunkTag.put("Entities", result.sanitized());
                    AnimalEntityRecoveryState.markRecovering(level, pos);
                    simpleRegionStorage.write(pos, chunkTag.copy()).whenComplete((ignored, error) -> {
                        if (error != null) {
                            StardewCraft.LOGGER.error(
                                "[ANIMAL_RECOVERY] Failed to persist repaired entity chunk {} in {}",
                                pos, level.dimension().location(), error);
                        }
                    });
                    StardewCraft.LOGGER.warn(
                        "[ANIMAL_RECOVERY] Repaired and queued entity chunk {} in {} for persistence: "
                            + "removed {} duplicates, {} invalid entries and {} excess entries; kept {} managed animals ({} total entries before repair)",
                        pos,
                        level.dimension().location(),
                        result.duplicateCount(),
                        result.invalidCount(),
                        result.excessCount(),
                        result.keptManagedAnimals(),
                        result.original().size());
                }
            }
            return decoder.apply(optionalTag);
        }, executor);
    }
}
