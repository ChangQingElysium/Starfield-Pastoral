package com.stardew.craft.platform.mixin.client.event.mixin;


import com.stardew.craft.platform.fabric.client.event.FabricClientRegistrationEvents;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow private Map<EntityType<?>, EntityRenderer<?>> renderers;
    @Shadow private Map<PlayerSkin.Model, EntityRenderer<? extends Player>> playerRenderers;

    // MC 1.21.1 stores its sole local Context in slot 2, then assigns both
    // complete maps. Fail-hard capture avoids silently dispatching partial maps
    // or a reconstructed context if this lifecycle changes.
    @Inject(method = "onResourceManagerReload", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void stardewcraft$addLayers(ResourceManager manager, CallbackInfo callback,
            EntityRendererProvider.Context context) {
        FabricClientRegistrationEvents.onRenderersReloaded(renderers, playerRenderers, context);
    }
}
