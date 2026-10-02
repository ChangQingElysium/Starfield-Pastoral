package com.stardew.craft.mixin;

import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.quad.ModelQuadView", remap = false)
public interface SodiumImmediateQuadNormalAccessor extends EmbeddiumQuadAccess.Normal {
    @Shadow int getComputedFaceNormal();
    default int stardewcraft$getComputedFaceNormal() { return getComputedFaceNormal(); }
}
