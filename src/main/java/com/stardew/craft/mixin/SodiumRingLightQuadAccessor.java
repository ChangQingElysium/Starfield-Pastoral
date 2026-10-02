package com.stardew.craft.mixin;

import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.model.quad.ModelQuadView", remap = false)
public interface SodiumRingLightQuadAccessor extends EmbeddiumQuadAccess.Coordinates {
    @Shadow float getX(int vertex);
    @Shadow float getY(int vertex);
    @Shadow float getZ(int vertex);

    // Mixin 0.8.5's Invoker generator emits INVOKEVIRTUAL even for interface targets.
    // A compiled default bridge preserves the required INVOKEINTERFACE instruction.
    default float stardewcraft$getX(int vertex) { return getX(vertex); }
    default float stardewcraft$getY(int vertex) { return getY(vertex); }
    default float stardewcraft$getZ(int vertex) { return getZ(vertex); }
}
