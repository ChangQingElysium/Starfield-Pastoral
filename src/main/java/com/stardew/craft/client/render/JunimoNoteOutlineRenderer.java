package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.communitycenter.block.JunimoNoteBlock;
import com.stardew.craft.communitycenter.network.BundleClientData;
import com.stardew.craft.communitycenter.restore.CCAreaRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.stardew.craft.port.PortVertex;
/**
 * 为 JunimoNote 方块渲染隔墙可见的描边高光。
 * <p>
 * 与 {@link PortalHintRenderer} 使用相同的双层渲染:
 * - X-ray 层 (depth test = always): 穿墙可见, 半透明
 * - 正常深度层: 不被遮挡时更亮
 */
@SuppressWarnings("null")
public final class JunimoNoteOutlineRenderer {

    private JunimoNoteOutlineRenderer() {}

    // Junimo Note 金色辉光
    private static final int R = 255, G = 200, B = 50;
    private static final int EDGE_A = 200;
    private static final int FACE_A = 30;
    private static final float EDGE_HALF = 0.02f;

    // 可见距离
    private static final double HINT_RANGE = 32.0;
    private static final double HINT_RANGE_SQ = HINT_RANGE * HINT_RANGE;
    // 渐隐开始距离 (距离最大可见距离 6 格开始渐隐)
    private static final double FADE_START = HINT_RANGE - 6.0;

    @SuppressWarnings("null")
    private static final RenderType QUAD_TYPE = makeQuadType("stardew_junimo_note", false);
    @SuppressWarnings("null")
    private static final RenderType QUAD_XRAY = makeQuadType("stardew_junimo_note_xr", true);

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 只在 stardew 维度的 CC 内部区域渲染
        if (mc.level.dimension() != com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) return;
        // 粗略检查: CC 内部在 X >= 18000 的高坐标区域
        if (player.blockPosition().getX() < 18000) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        boolean anyRendered = false;

        for (var entry : CCAreaRegistry.ALL_AREAS.entrySet()) {
            int areaId = entry.getKey();
            CCAreaRegistry.AreaBounds bounds = entry.getValue();

            // 已完成的区域不显示高光
            if (BundleClientData.INSTANCE.isAreaComplete(areaId)) continue;

            BlockPos notePos = bounds.noteWorldPos(BundleClientData.INSTANCE.getCCOrigin());

            // 只对实际存在的 JunimoNote 方块显示高光（分步解锁）
            if (!(mc.level.getBlockState(notePos).getBlock() instanceof JunimoNoteBlock)) continue;

            double distSq = player.position().distanceToSqr(
                notePos.getX() + 0.5, notePos.getY() + 0.5, notePos.getZ() + 0.5);
            if (distSq > HINT_RANGE_SQ) continue;

            float alpha = calcFadeAlpha(distSq);
            if (alpha < 0.01f) continue;

            // 呼吸脉冲效果
            float time = (System.currentTimeMillis() % 3000) / 3000.0f;
            float pulse = 0.7f + 0.3f * (float) Math.sin(time * Math.PI * 2);
            alpha *= pulse;

            renderNoteOutline(buf, ps, cam, notePos, alpha);
            anyRendered = true;
        }

        ps.popPose();

        if (anyRendered) {
            buf.endBatch(QUAD_XRAY);
            buf.endBatch(QUAD_TYPE);
        }
    }

    private static float calcFadeAlpha(double distSq) {
        double dist = Math.sqrt(distSq);
        if (dist > HINT_RANGE) return 0.0f;
        if (dist > FADE_START) {
            return (float) ((HINT_RANGE - dist) / (HINT_RANGE - FADE_START));
        }
        return 1.0f;
    }

    private static void renderNoteOutline(MultiBufferSource.BufferSource buf, PoseStack ps, Vec3 cam,
                                           BlockPos notePos, float alpha) {
        // JunimoNoteBlock shape: Block.box(-1, 0, -1, 17, 2, 17) = 略大于1格的扁平
        // 像素: -1/16 到 17/16, y: 0 到 2/16
        double x = notePos.getX() - 1.0 / 16.0;
        double y = notePos.getY();
        double z = notePos.getZ() - 1.0 / 16.0;
        double w = 18.0 / 16.0; // 1.125
        double h = 2.0 / 16.0;  // 0.125

        AABB box = new AABB(x, y, z, x + w, y + h, z + w).inflate(0.03);

        int edgeA = (int) (EDGE_A * alpha);
        int faceA = (int) (FACE_A * alpha);

        // X-ray 层 — 穿墙可见, 更淡
        VertexConsumer xr = buf.getBuffer(QUAD_XRAY);
        renderFaces(ps, xr, box, R, G, B, faceA / 2);
        renderEdgeQuads(ps, xr, box, cam, R, G, B, edgeA / 3, EDGE_HALF * 0.6f);

        // 正常深度层 — 不被遮挡时更亮
        VertexConsumer vc = buf.getBuffer(QUAD_TYPE);
        renderFaces(ps, vc, box, R, G, B, faceA);
        renderEdgeQuads(ps, vc, box, cam, R, G, B, edgeA, EDGE_HALF);
    }

    // ======================== geometry (same as PortalHintRenderer) ========================

    @SuppressWarnings("null")
    private static void renderFaces(PoseStack ps, VertexConsumer vc, AABB b, int r, int g, int bl, int a) {
        if (a <= 0) return;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        PoseStack.Pose p = ps.last();
        q(p, vc, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,bl,a);
        q(p, vc, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, r,g,bl,a);
        q(p, vc, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,bl,a);
        q(p, vc, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, r,g,bl,a);
        q(p, vc, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, r,g,bl,a);
        q(p, vc, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,bl,a);
    }

    @SuppressWarnings("null")
    private static void q(PoseStack.Pose p, VertexConsumer v,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          int r, int g, int b, int a) {
        PortVertex.of(v).addVertex(p, ax, ay, az).setColor(r, g, b, a).endVertex();
        PortVertex.of(v).addVertex(p, bx, by, bz).setColor(r, g, b, a).endVertex();
        PortVertex.of(v).addVertex(p, cx, cy, cz).setColor(r, g, b, a).endVertex();
        PortVertex.of(v).addVertex(p, dx, dy, dz).setColor(r, g, b, a).endVertex();
    }

    @SuppressWarnings("null")
    private static void renderEdgeQuads(PoseStack ps, VertexConsumer vc, AABB b, Vec3 cam,
                                         int r, int g, int bl, int a, float halfW) {
        if (a <= 0) return;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        // bottom edges
        edgeQuad(ps, vc, x0,y0,z0, x1,y0,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z0, x1,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z1, x0,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y0,z1, x0,y0,z0, halfW, cam, r,g,bl,a);
        // top edges
        edgeQuad(ps, vc, x0,y1,z0, x1,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z0, x1,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z1, x0,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y1,z1, x0,y1,z0, halfW, cam, r,g,bl,a);
        // vertical edges
        edgeQuad(ps, vc, x0,y0,z0, x0,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z0, x1,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z1, x1,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y0,z1, x0,y1,z1, halfW, cam, r,g,bl,a);
    }

    @SuppressWarnings("null")
    private static void edgeQuad(PoseStack ps, VertexConsumer vc,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float halfW, Vec3 cam,
                                  int r, int g, int b, int a) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float ex = dx / len, ey = dy / len, ez = dz / len;

        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float cx = (float) cam.x - mx, cy = (float) cam.y - my, cz = (float) cam.z - mz;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6f) return;
        cx /= cl; cy /= cl; cz /= cl;

        float px = ey * cz - ez * cy;
        float py = ez * cx - ex * cz;
        float pz = ex * cy - ey * cx;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1e-6f) {
            if (Math.abs(ex) < 0.9f) { px = 0; py = -ez; pz = ey; }
            else                     { px = ez; py = 0; pz = -ex; }
            pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        }
        px = px / pl * halfW;
        py = py / pl * halfW;
        pz = pz / pl * halfW;

        PoseStack.Pose pose = ps.last();
        PortVertex.of(vc).addVertex(pose, ax - px, ay - py, az - pz).setColor(r, g, b, a).endVertex();
        PortVertex.of(vc).addVertex(pose, ax + px, ay + py, az + pz).setColor(r, g, b, a).endVertex();
        PortVertex.of(vc).addVertex(pose, bx + px, by + py, bz + pz).setColor(r, g, b, a).endVertex();
        PortVertex.of(vc).addVertex(pose, bx - px, by - py, bz - pz).setColor(r, g, b, a).endVertex();
    }

    // ======================== RenderType ========================

    @SuppressWarnings("null")
    private static RenderType makeQuadType(String name, boolean xray) {
        return RenderType.create(name,
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(new RenderType.TransparencyStateShard("translucent", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }, RenderSystem::disableBlend))
                .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
                .setCullState(new RenderType.CullStateShard(false))
                .setDepthTestState(xray
                    ? new RenderType.DepthTestStateShard("always", 519)
                    : com.stardew.craft.port.PortRenderStateShards.LEQUAL_DEPTH_TEST)
                .createCompositeState(false));
    }
}
