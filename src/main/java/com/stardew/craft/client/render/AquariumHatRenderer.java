package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;

/** Reuses the same authored head display as the player cosmetic layer. */
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class AquariumHatRenderer {
    private AquariumHatRenderer() {}
    public static void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(stack.getItem() instanceof StardewHatItem hat)) return;
        pose.pushPose(); pose.translate(0, .1, 0); pose.scale(8, -8, -8);
        BlockbenchElementRenderer.renderHat(hat, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
