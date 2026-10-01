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

    private record FadingVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, (int)(a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
