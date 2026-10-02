package com.stardew.craft.mixin;

import com.stardew.craft.port.PortInheritance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PORT(1.20.1): 1.21.1 {@code EntityRenderer#shouldShowName} is
 * {@code entity.shouldShowName() || entity.hasCustomName() && entity == crosshairPickEntity} (a named entity shows its
 * tag while looked at, an entity that forces {@code shouldShowName} shows it without a custom name); 1.20.1 requires
 * {@code shouldShowName() && hasCustomName()}. Most StardewCraft renderers (NPCs, livestock, Blockbench entities,
 * seats, minecarts) extend {@code EntityRenderer} directly and inherit it; they get the 1.21.1 rule. Living-entity
 * renderers override it in both versions and are unaffected; vanilla and other mods' entities are unchanged.
 */
@Mixin(EntityRenderer.class)
public abstract class PortEntityRendererNameTagMixin {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$shouldShowName121(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!PortInheritance.isModEntity(entity)) return;
        cir.setReturnValue(entity.shouldShowName()
                || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity);
    }
}
