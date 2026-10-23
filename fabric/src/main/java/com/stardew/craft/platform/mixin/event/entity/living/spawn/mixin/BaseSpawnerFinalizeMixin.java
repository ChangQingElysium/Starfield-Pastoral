package com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.stardew.craft.platform.fabric.event.entity.living.spawn.MobSpawnHooks;
import com.stardew.craft.platform.fabric.event.entity.living.spawn.OwnedSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerFinalizeMixin implements OwnedSpawner.Bindable {
    @Unique private Either<BlockEntity, Entity> stardewcraft$owner;
    @Override public Either<BlockEntity, Entity> stardewcraft$spawnerOwner() { return stardewcraft$owner; }
    @Override public void stardewcraft$spawnerOwner(Either<BlockEntity, Entity> owner) { stardewcraft$owner = owner; }

    @WrapOperation(method = "serverTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;"), require = 1)
    private SpawnGroupData stardewcraft$finalize(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, Operation<SpawnGroupData> original) {
        return MobSpawnHooks.finalizeMobSpawn(mob, level, difficulty, reason, group, this, original);
    }

    @Inject(method = "serverTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/SpawnData;getEquipment()Ljava/util/Optional;"), require = 1)
    private void stardewcraft$nbtSpawn(ServerLevel level, BlockPos pos, CallbackInfo callback,
            @Local(index = 20) Mob mob, @Local(index = 21) boolean normallyFinalizes) {
        if (!normallyFinalizes) MobSpawnHooks.finalizeSpawner(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.SPAWNER, null, this, false, null);
    }
}
