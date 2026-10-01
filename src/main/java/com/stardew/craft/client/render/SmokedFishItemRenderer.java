package com.stardew.craft.client.render;

import com.stardew.craft.port.PortItemData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.artisan.SmokedFishItem;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;

public class SmokedFishItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation PUFF_TEXTURE = new ResourceLocation(
            StardewCraft.MODID, "textures/item/artisan/smoke_puff.png");
    private static final float PX = 1.0F / 16.0F;

    public SmokedFishItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

        @SuppressWarnings("null")
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        if (!(stack.getItem() instanceof SmokedFishItem smokedItem)) {
            return;
        }

        int quality = QualityHelper.getQuality(stack);
        ItemStack sourceStack = new ItemStack(smokedItem.getSourceItem());
        QualityHelper.setQuality(sourceStack, quality);
        if (quality != QualityHelper.NORMAL) {
            PortItemData.set(sourceStack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData(quality));
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation baseModelId = new ResourceLocation(
            itemId.getNamespace(), "item/" + itemId.getPath() + "_base"
        );
        BakedModel model = mc.getModelManager().getModel(new ModelResourceLocation(baseModelId, "standalone"));
        model = model.getOverrides().resolve(model, sourceStack, mc.level, null, 0);

        poseStack.pushPose();

        @SuppressWarnings("deprecation")
        RenderType baseType = ItemBlockRenderTypes.getRenderType(sourceStack, true);
        VertexConsumer baseVc = buffer.getBuffer(baseType);
        mc.getItemRenderer().renderModelLists(
            model,
            sourceStack,
            packedLight,
            packedOverlay,
            poseStack,
            baseVc
        );

        VertexConsumer overlayVc = new TintingVertexConsumer(
            buffer.getBuffer(baseType),
            95, 55, 35, (int) (255 * 0.35f)
        );
        mc.getItemRenderer().renderModelLists(
            model,
            sourceStack,
            packedLight,
            packedOverlay,
            poseStack,
            overlayVc
        );

        long nowMs = System.currentTimeMillis();
        int price = smokedItem.getSellPrice(stack);
        int interval = 700 + (price + 17) * 7777 % 200;

        float baseScale = getPuffScale(context);
        float baseY = 8.0F;
        float baseX = 8.0F;

        poseStack.translate(0.0F, 0.0F, 0.02F);

        drawPuff(buffer, poseStack, packedLight, packedOverlay,
            baseX, baseY, baseScale, nowMs, 0);
        drawPuff(buffer, poseStack, packedLight, packedOverlay,
            6.0F, 10.0F, baseScale, nowMs, interval);
        drawPuff(buffer, poseStack, packedLight, packedOverlay,
            12.0F, 5.25F, baseScale, nowMs, interval * 2);

        poseStack.popPose();
    }


    private static float getPuffScale(ItemDisplayContext context) {
        return 1.0F;
    }




        @SuppressWarnings("null")
        private static void drawPuff(MultiBufferSource buffer, PoseStack poseStack, int packedLight, int packedOverlay,
                                     float x, float y, float scale, long timeMs, int offsetMs) {
        float age = (float) ((timeMs + offsetMs) % 2000L);
        float localTime = -age;
        float rise = -localTime * 0.03F * 0.25F;
        float alpha = 0.53F * (1.0F - (age / 2000.0F));
        float rotation = localTime * 0.001F;

            float size = 5.0F * scale * PX;
            float half = size * 0.5F;
            float xPos = x * PX;
            float yPos = (y + rise) * PX;

        poseStack.pushPose();
        poseStack.translate(xPos, yPos, 0.0F);
        poseStack.translate(half, half, 0.0F);
        poseStack.mulPose(Axis.ZP.rotation(rotation));
        poseStack.translate(-half, -half, 0.0F);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(PUFF_TEXTURE));
        emitQuad(vc, poseStack, 0.0F, 0.0F, size, size,
            120, 120, 120, (int) (255 * alpha * 0.7f),
            packedLight, packedOverlay, 0.0F, 0.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    @SuppressWarnings("null")
        private static void emitQuad(VertexConsumer vc, PoseStack poseStack, float x, float y, float w, float h,
                     int r, int g, int b, int a, int packedLight, int packedOverlay,
                     float u0, float v0, float u1, float v1) {
        var pose = poseStack.last().pose();
        vc.addVertex(pose, x, y + h, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u0, v0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x + w, y + h, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u1, v0)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x + w, y, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u1, v1)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, x, y, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u0, v1)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static final class TintingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int r;
        private final int g;
        private final int b;
        private final int a;

        private TintingVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
            this.delegate = delegate;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return delegate.setColor(this.r, this.g, this.b, (int) (this.a * (a / 255.0f)));
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return delegate.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return delegate.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return delegate.setUv2(u, v);
        }

        @Override
        public VertexConsumer setOverlay(int overlay) {
            return delegate.setOverlay(overlay);
        }

        @Override
        public VertexConsumer setLight(int light) {
            return delegate.setLight(light);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return delegate.setNormal(x, y, z);
        }
    }
}
