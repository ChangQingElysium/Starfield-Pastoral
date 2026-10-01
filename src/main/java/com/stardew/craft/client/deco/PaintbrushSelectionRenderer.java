package com.stardew.craft.client.deco;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.deco.PaintbrushSelectionManager.Mode;
import com.stardew.craft.item.tool.PaintbrushItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Create-mod-style 3D selection box for paintbrush region-select mode.
 * Edges are rendered as camera-facing quad strips (not GL lines) so they
 * appear thick on all platforms including macOS where glLineWidth is capped at 1.
 * Two layers: faint X-ray behind walls + solid depth-tested on top.
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class PaintbrushSelectionRenderer {

    // Main selection color — warm amber/gold
    private static final float R = 1.0f, G = 0.78f, B = 0.24f;
    // First-corner marker — brighter orange
    private static final float CR = 1.0f, CG = 0.55f, CB = 0.1f;

    // Edge half-width in world units (~2.5 pixels at normal distance)
    private static final float EDGE_HALF = 0.025f;
    private static final float EDGE_HALF_XRAY = 0.015f;
    // Corner marker edge
    private static final float EDGE_HALF_CORNER = 0.03f;

    // ---- Depth-tested quad type (for both faces and thick edges) ----
    @SuppressWarnings("null")
    private static final RenderType QUAD_TYPE = makeQuadType("stardew_sel_quad", false);
    // ---- X-ray quad type ----
    @SuppressWarnings("null")
    private static final RenderType QUAD_XRAY = makeQuadType("stardew_sel_quad_xr", true);

    private PaintbrushSelectionRenderer() {}

    // ======================== render entry ========================

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        if (!(player.getMainHandItem().getItem() instanceof PaintbrushItem)
            && !(player.getOffhandItem().getItem() instanceof PaintbrushItem)) return;

        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
        if (mgr.getMode() != Mode.REGION_SELECT || !mgr.hasFirstPos()) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        AABB box = mgr.getDisplayAABB();
        if (box != null) {
            AABB b = box.inflate(0.005);

            // X-ray layer (faint, visible through walls)
            drawBox(buf, ps, b, R, G, B, 15, 180, EDGE_HALF_XRAY, cam, QUAD_XRAY);
            // Depth-tested layer (solid, on top of blocks)
            drawBox(buf, ps, b, R, G, B, 30, 240, EDGE_HALF, cam, QUAD_TYPE);
        }

        // First-corner highlight
        BlockPos first = mgr.getFirstPos();
        if (first != null) {
            AABB cb = new AABB(first).inflate(0.015);
            drawBox(buf, ps, cb, CR, CG, CB, 40, 255, EDGE_HALF_CORNER, cam, QUAD_TYPE);
        }

        ps.popPose();
    }

    // ======================== draw a full box ========================

    @SuppressWarnings("null")
    private static void drawBox(MultiBufferSource.BufferSource buf, PoseStack ps, AABB b,
                                float r, float g, float bl, int faceA, int edgeA,
                                float halfW, Vec3 cam, RenderType quadType) {
        int ri = (int)(r * 255), gi = (int)(g * 255), bi = (int)(bl * 255);
        VertexConsumer vc = buf.getBuffer(quadType);

        // Faces (very subtle tint)
        renderFaces(ps, vc, b, ri, gi, bi, faceA);

        // 12 edges as camera-facing quad strips
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;

        // bottom ring
        edgeQuad(ps, vc, x0,y0,z0, x1,y0,z0, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y0,z0, x1,y0,z1, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y0,z1, x0,y0,z1, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x0,y0,z1, x0,y0,z0, halfW, cam, ri,gi,bi,edgeA);
        // top ring
        edgeQuad(ps, vc, x0,y1,z0, x1,y1,z0, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y1,z0, x1,y1,z1, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y1,z1, x0,y1,z1, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x0,y1,z1, x0,y1,z0, halfW, cam, ri,gi,bi,edgeA);
        // verticals
        edgeQuad(ps, vc, x0,y0,z0, x0,y1,z0, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y0,z0, x1,y1,z0, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x1,y0,z1, x1,y1,z1, halfW, cam, ri,gi,bi,edgeA);
        edgeQuad(ps, vc, x0,y0,z1, x0,y1,z1, halfW, cam, ri,gi,bi,edgeA);

        buf.endBatch(quadType);
    }

    // ======================== camera-facing edge quad ========================

    /**
     * Renders a single edge (A→B) as a quad that always faces the camera.
     * The quad is perpendicular to the camera view direction at the edge's midpoint.
     */
    @SuppressWarnings("null")
    private static void edgeQuad(PoseStack ps, VertexConsumer vc,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float halfW, Vec3 cam,
                                  int r, int g, int b, int a) {
        // Edge direction
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float ex = dx / len, ey = dy / len, ez = dz / len;

        // Direction from edge midpoint to camera
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float cx = (float) cam.x - mx, cy = (float) cam.y - my, cz = (float) cam.z - mz;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6f) return;
        cx /= cl; cy /= cl; cz /= cl;

        // Perpendicular = cross(edge, toCamera), gives the offset direction for the quad width
        float px = ey * cz - ez * cy;
        float py = ez * cx - ex * cz;
        float pz = ex * cy - ey * cx;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1e-6f) {
            // Edge is parallel to camera direction — use an arbitrary perpendicular
            if (Math.abs(ex) < 0.9f) { px = 0; py = -ez; pz = ey; }
            else                     { px = ez; py = 0; pz = -ex; }
            pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        }
        px = px / pl * halfW;
        py = py / pl * halfW;
        pz = pz / pl * halfW;

        @SuppressWarnings("null") PoseStack.Pose pose = ps.last();
        // Quad: (A-p, A+p, B+p, B-p)
        vc.addVertex(pose, ax - px, ay - py, az - pz).setColor(r, g, b, a);
        vc.addVertex(pose, ax + px, ay + py, az + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx + px, by + py, bz + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx - px, by - py, bz - pz).setColor(r, g, b, a);
    }

    // ======================== 6 faces ========================

    @SuppressWarnings("null")
    private static void renderFaces(PoseStack ps, VertexConsumer vc, AABB b, int r, int g, int bl, int a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        PoseStack.Pose p = ps.last();
        q(p, vc, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,bl,a); // bottom
        q(p, vc, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, r,g,bl,a); // top
        q(p, vc, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,bl,a); // north
        q(p, vc, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, r,g,bl,a); // south
        q(p, vc, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0, r,g,bl,a); // west
        q(p, vc, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,bl,a); // east
    }

    @SuppressWarnings("null")
    private static void q(PoseStack.Pose p, VertexConsumer v,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          int r, int g, int b, int a) {
        v.addVertex(p, ax, ay, az).setColor(r, g, b, a);
        v.addVertex(p, bx, by, bz).setColor(r, g, b, a);
        v.addVertex(p, cx, cy, cz).setColor(r, g, b, a);
        v.addVertex(p, dx, dy, dz).setColor(r, g, b, a);
    }

    // ======================== RenderType factory ========================

    @SuppressWarnings("null")
    private static RenderType makeQuadType(String name, boolean xray) {
        // Buffer size: 6 faces * 4 verts + 12 edges * 4 verts = 72 verts, with room for corner too
        return RenderType.create(name,
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, true,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(new RenderType.TransparencyStateShard("translucent", () -> {
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                }, com.mojang.blaze3d.systems.RenderSystem::disableBlend))
                .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
                .setCullState(new RenderType.CullStateShard(false))
                .setDepthTestState(xray
                    ? new RenderType.DepthTestStateShard("always", 519)
                    : RenderType.LEQUAL_DEPTH_TEST)
                .createCompositeState(false));
    }
}
