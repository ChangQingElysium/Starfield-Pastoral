package com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.living.spawn.MobSpawnHooks;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exact 1.21.1 virtual call sites. The separate invokespecial super chain remains intact. */
@Mixin(targets = "net.minecraft.server.commands.RaidCommand")
public abstract class NativeRaidCommandFinalizeMixin {
    @WrapOperation(method = "spawnLeader(Lnet/minecraft/commands/CommandSourceStack;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raider;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;"), require = 1)
    private static SpawnGroupData stardewcraft$finalize0(net.minecraft.world.entity.raid.Raider mob, ServerLevelAccessor level,
            DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData group, Operation<SpawnGroupData> original) {
        return MobSpawnHooks.finalizeMobSpawn(mob, level, difficulty, reason, group, original);
    }
}
