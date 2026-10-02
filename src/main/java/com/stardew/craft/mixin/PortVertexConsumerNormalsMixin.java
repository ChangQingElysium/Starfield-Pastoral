package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.port.PortVertex;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(VertexConsumer.class)
public interface PortVertexConsumerNormalsMixin {
    /**
     * @author StardewCraft
     * @reason PORT(1.20.1): use 1.21 generated normals and integer-color ordering. Mixin 0.8.5
     * rejects interface injectors; implementations with their own override retain that override.
     */
    @Overwrite(remap = false) // Forge-added alpha overload has no SRG mapping.
    default void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
            float red, float green, float blue, float alpha, int[] lights, int overlay, boolean readColor) {
        PortVertex.putBulkData((VertexConsumer) (Object) this, pose, quad, brightness,
                red, green, blue, alpha, lights, overlay, readColor);
    }
}
