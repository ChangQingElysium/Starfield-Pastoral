package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.blockentity.ShippingBinBlockEntity;
import com.stardew.craft.client.model.block.ShippingBinGeoModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ShippingBinBlockEntityRenderer extends StardewGeoBlockRenderer<ShippingBinBlockEntity> {
    public ShippingBinBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShippingBinGeoModel());
    }

    @Override protected void renderExtras(ShippingBinBlockEntity bin,
            com.stardew.craft.client.model.nativebb.BlockbenchFrame frame, PoseStack pose,
            MultiBufferSource buffers, float partialTick, int light, int overlay) {
        ItemStack item = bin.shipmentItem();
        if (item.isEmpty()) return;
        pose.pushPose();
        try {
            if (!frame.attach(pose, "shipment_item")) return;
            float alpha = Math.max(0, 1 - bin.shipmentAge(partialTick) / .38f);
            MultiBufferSource fading = type -> {
                var target = type == Sheets.cutoutBlockSheet() || type == Sheets.solidBlockSheet()
                        ? Sheets.translucentItemSheet() : type;
                return new FadingVertexConsumer(buffers.getBuffer(target), alpha);
            };
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(item,
                    ItemDisplayContext.NONE, light, overlay, pose, fading, bin.getLevel(), 0);
        } finally { pose.popPose(); }
    }

    // PORT(1.20.1): 1.20.1 VertexConsumer API; forwards every attribute (and the explicit endVertex) to the
    // delegate and fades the alpha, like the 1.21 wrapper.
    private record FadingVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, (int)(a * alpha)); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
