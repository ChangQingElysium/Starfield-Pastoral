package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class PetHatRenderer {
    private PetHatRenderer() {}
    public static void render(ItemStack item, PoseStack stack, MultiBufferSource buffers, int light, float scale) {
        if (!(item.getItem() instanceof StardewHatItem hat)) return;
        stack.pushPose();
        // The attachment is the pet's crown; CustomHeadLayer expects the neck, eight head pixels below it.
        stack.translate(0, -8 * scale, 0);
        // Native pet coordinates are model units, Y up; the cosmetic head renderer uses player-head units, Y down.
        stack.scale(16 * scale, -16 * scale, -16 * scale);
        BlockbenchElementRenderer.renderHat(hat, stack, buffers, light, OverlayTexture.NO_OVERLAY);
        stack.popPose();
    }
}
