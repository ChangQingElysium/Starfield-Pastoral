package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.DecorBlockEntity;
import com.stardew.craft.deco.DecorationStyle;
import com.stardew.craft.deco.DecorationStyleRegistry;
import com.stardew.craft.deco.DecorationType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import com.stardew.craft.port.PortVertex;
@SuppressWarnings("null")
public class DecorBlockEntityRenderer implements BlockEntityRenderer<DecorBlockEntity> {
    public DecorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DecorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be == null || be.getLevel() == null) {
            return;
        }

        BlockState state = be.getBlockState();
        DecorationType type = state.is(ModBlocks.WALLPAPER_BLOCK.get()) ? DecorationType.WALLPAPER : DecorationType.FLOORING;
        DecorationStyle style = DecorationStyleRegistry.getStyle(type, be.getStyleId());
        if (style == null) {
            style = DecorationStyleRegistry.getStyle(type, DecorationStyleRegistry.getDefaultStyleId(type));
            if (style == null) {
                return;
            }
        }

        ResourceLocation texture = style.texture();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        if (type == DecorationType.WALLPAPER) {
            renderWallpaper(vc, mat, packedLight, be.getBlockPos(), style);
        } else {
            renderFlooring(vc, mat, packedLight, be.getBlockPos(), style);
        }

        poseStack.popPose();
    }

    private void renderWallpaper(VertexConsumer vc, Matrix4f mat, int light, BlockPos pos, DecorationStyle style) {
        int index = parseStyleIndex(style.id());
        int baseX = (index % 16) * 16;
        int baseY = (index / 16) * 48;
        int segment = Math.floorMod(pos.getY(), 3);
        int srcX = baseX;
        int srcY = baseY + segment * 16;

        float u0 = srcX / (float) style.texWidth();
        float v0 = srcY / (float) style.texHeight();
        float u1 = (srcX + 16) / (float) style.texWidth();
        float v1 = (srcY + 16) / (float) style.texHeight();

        emitCube(vc, mat, light, u0, v0, u1, v1);
    }

    private void renderFlooring(VertexConsumer vc, Matrix4f mat, int light, BlockPos pos, DecorationStyle style) {
        int index = parseStyleIndex(style.id());
        int baseX = (index % 8) * 32;
        int baseY = style.id().startsWith("MoreFloors:") ? (index / 8) * 32 : 336 + (index / 8) * 32;

        int px = Math.floorMod(pos.getX(), 2);
        int pz = Math.floorMod(pos.getZ(), 2);

        int srcX = baseX + px * 16;
        int srcY = baseY + pz * 16;

        float u0 = srcX / (float) style.texWidth();
        float v0 = srcY / (float) style.texHeight();
        float u1 = (srcX + 16) / (float) style.texWidth();
        float v1 = (srcY + 16) / (float) style.texHeight();

        emitCube(vc, mat, light, u0, v0, u1, v1);
    }

    private static int parseStyleIndex(String styleId) {
        int split = styleId.indexOf(':');
        try {
            if (split >= 0 && split + 1 < styleId.length()) {
                return Integer.parseInt(styleId.substring(split + 1));
            }
            return Integer.parseInt(styleId);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static void emitCube(VertexConsumer vc, Matrix4f m, int light, float u0, float v0, float u1, float v1) {
        // North
        quad(vc, m, 0f, 0f, 0f, 1f, 1f, 0f, u0, v0, u1, v1, light, 0f, 0f, -1f);
        // South
        quad(vc, m, 1f, 0f, 1f, 0f, 1f, 1f, u0, v0, u1, v1, light, 0f, 0f, 1f);
        // West
        quadZ(vc, m, 0f, 0f, 1f, 0f, 1f, 0f, u0, v0, u1, v1, light, -1f, 0f, 0f);
        // East
        quadZ(vc, m, 1f, 0f, 0f, 1f, 1f, 1f, u0, v0, u1, v1, light, 1f, 0f, 0f);
        // Top
        topQuad(vc, m, 0f, 1f, 0f, 1f, 1f, 1f, u0, v0, u1, v1, light);
        // Bottom
        bottomQuad(vc, m, 0f, 0f, 1f, 1f, 0f, 0f, u0, v0, u1, v1, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z, float x1, float y1, float z1, float u0, float v0, float u1, float v1, int light, float nx, float ny, float nz) {
        PortVertex.of(vc).addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x0, y1, z1).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
    }

    private static void quadZ(VertexConsumer vc, Matrix4f m, float x, float y0, float z0, float x1, float y1, float z1, float u0, float v0, float u1, float v1, int light, float nx, float ny, float nz) {
        PortVertex.of(vc).addVertex(m, x, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y1, z0).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
        PortVertex.of(vc).addVertex(m, x, y0, z1).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz).endVertex();
    }

    private static void topQuad(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y1, float z1, float u0, float v0, float u1, float v1, int light) {
        PortVertex.of(vc).addVertex(m, x0, y, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x0, y1, z1).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y, z0).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0).endVertex();
    }

    private static void bottomQuad(VertexConsumer vc, Matrix4f m, float x0, float y, float z0, float x1, float y1, float z1, float u0, float v0, float u1, float v1, int light) {
        PortVertex.of(vc).addVertex(m, x0, y, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x0, y1, z1).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0).endVertex();
        PortVertex.of(vc).addVertex(m, x1, y, z0).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0).endVertex();
    }
}
