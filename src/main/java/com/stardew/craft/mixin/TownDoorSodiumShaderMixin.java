package com.stardew.craft.mixin;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.interior.TownDoorClipping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium clipping-uniform hook adapted from Immersive Portals (Apache-2.0). */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface", remap = false)
public abstract class TownDoorSodiumShaderMixin {
    @Unique private int stardewcraft$clipProgram = Integer.MIN_VALUE;
    @Unique private int stardewcraft$clipLocation = Integer.MIN_VALUE;
    @Unique private boolean stardewcraft$reportedClipUniform;

    @Inject(method = "setupState", at = @At("RETURN"), require = 1)
    private void stardewcraft$uploadClipPlane(CallbackInfo ci) {
        int program = TownDoorClipping.activeProgram();
        if (program != stardewcraft$clipProgram) {
            stardewcraft$clipProgram = program;
            stardewcraft$clipLocation = TownDoorClipping.findInActiveProgram();
        }
        if (!stardewcraft$reportedClipUniform) {
            stardewcraft$reportedClipUniform = true;
            if (stardewcraft$clipLocation >= 0) {
                StardewCraft.LOGGER.info("[TOWN-DOOR] Sodium terrain front-clipping uniform bound");
            } else {
                StardewCraft.LOGGER.error("[TOWN-DOOR] Sodium terrain shader has no doorway clipping uniform");
            }
        }
        TownDoorClipping.uploadRendererModUniform(stardewcraft$clipLocation);
    }
}
