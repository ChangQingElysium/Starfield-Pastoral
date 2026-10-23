package com.stardew.craft.platform.mixin.client.render.mixin;


import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public interface LevelRenderStateAccessor {
    @Accessor("ticks") int stardewcraft$getRenderTicks();
    @Accessor("cullingFrustum") Frustum stardewcraft$getCullingFrustum();
    @Accessor("capturedFrustum") @Nullable Frustum stardewcraft$getCapturedFrustum();
}
