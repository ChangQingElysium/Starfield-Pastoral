package com.stardew.craft.mixin;
import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.model.quad.ModelQuadView",remap=false)
public interface SodiumColoredLightQuadAccessor extends EmbeddiumQuadAccess.Lighting {
    @Shadow int getLight(int vertex);
    @Shadow Direction getLightFace();
    default int stardewcraft$getLight(int vertex) { return getLight(vertex); }
    default Direction stardewcraft$getLightFace() { return getLightFace(); }
}
