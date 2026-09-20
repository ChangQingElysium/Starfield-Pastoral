package com.stardew.craft.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Render-context field access adapted from Immersive Portals (Apache-2.0). */
@Mixin(GameRenderer.class)
public interface TownDoorGameRendererAccessor {
    @Accessor("mainCamera") @Mutable
    void stardewcraft$setMainCamera(Camera camera);

    @Accessor("renderHand")
    boolean stardewcraft$rendersHand();

    /** Restores the caller's projection after Iris or a nested level pass changes it. */
    @Invoker("resetProjectionMatrix")
    void stardewcraft$resetProjectionMatrix(org.joml.Matrix4f projection);
}
