package com.stardew.craft.client.interior;

import com.stardew.craft.mixin.TownDoorFogAccessor;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

/** Vanilla's five-second underwater biome blend is global; each view needs its own history. */
record TownDoorFogState(int target, int previous, long changedAt) {
    static final TownDoorFogState INITIAL = new TownDoorFogState(-1, -1, -1);

    static TownDoorFogState capture() {
        return new TownDoorFogState(TownDoorFogAccessor.stardewcraft$getTarget(),
                TownDoorFogAccessor.stardewcraft$getPrevious(), TownDoorFogAccessor.stardewcraft$getChangedAt());
    }

    void restore() {
        TownDoorFogAccessor.stardewcraft$setTarget(target);
        TownDoorFogAccessor.stardewcraft$setPrevious(previous);
        TownDoorFogAccessor.stardewcraft$setChangedAt(changedAt);
    }

    /** Preserve the caller's exact shader fog, including the no-fog state before HUD rendering. */
    record Shader(float start, float end, float red, float green, float blue, float alpha, FogShape shape) {
        static Shader capture() {
            float[] color = RenderSystem.getShaderFogColor();
            return new Shader(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                    color[0], color[1], color[2], color[3], RenderSystem.getShaderFogShape());
        }

        void restore() {
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogColor(red, green, blue, alpha);
            RenderSystem.setShaderFogShape(shape);
        }
    }
}
