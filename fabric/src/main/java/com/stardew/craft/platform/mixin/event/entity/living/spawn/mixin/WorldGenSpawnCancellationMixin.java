package com.stardew.craft.platform.mixin.event.entity.living.spawn.mixin;


import com.stardew.craft.platform.fabric.event.entity.living.spawn.SpawnCancellation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenSpawnCancellationMixin {
    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$rejectSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof Mob mob && SpawnCancellation.isCancelled(mob)) callback.setReturnValue(false);
    }
}
