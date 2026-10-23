package com.stardew.craft.platform.mixin.event.entity.projectile;

import com.stardew.craft.platform.fabric.event.entity.projectile.NativeProjectileImpacts;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FireworkRocketEntity.class)
abstract class FireworkImpactMixin extends Projectile {
    protected FireworkImpactMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }

    /** Match the added NeoForge override: deflection has already run before this point. */
    @Override protected void onHit(HitResult result) {
        if (NativeProjectileImpacts.allowDirectHit(this, result)) super.onHit(result);
    }
}
