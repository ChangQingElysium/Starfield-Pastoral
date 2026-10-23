package com.stardew.craft.platform.fabric.client.render;

import com.stardew.craft.platform.mixin.client.render.mixin.LevelRenderStateAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class LevelFrustums {
    private LevelFrustums() {}
    @Nullable public static Frustum getFrustum(LevelRenderer renderer) {
        var state = (LevelRenderStateAccessor) renderer;
        return select(state.stardewcraft$getCapturedFrustum(), state.stardewcraft$getCullingFrustum());
    }
    @Nullable static Frustum select(@Nullable Frustum captured, @Nullable Frustum culling) { return captured != null ? captured : culling; }
}
