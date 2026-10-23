package com.stardew.craft.platform.mixin.event.level.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.level.ExplosionEvent;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Explosion.class)
public abstract class ExplosionDetonateMixin {
    @Shadow @Final private Level level;
    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> stardewcraft$detonate(List<Entity> entities) {
        FabricEvents.GAME.post(new ExplosionEvent.Detonate(level, (Explosion) (Object) this, entities));
        return entities;
    }
}
