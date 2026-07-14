package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.client.hud.MiningFloorHud;
import com.stardew.craft.client.mining.ClientMiningState;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 楼梯穿墙高亮渲染器
 *
 * 当楼梯被发现后，在同一层中以 Portal-Hint 风格渲染：
 * - 紫色 x-ray 轮廓（穿墙可见）
 * - 深度测试轮廓（不穿墙时全不透明）
 * - 浮动 "▼ 楼梯" 气泡（billboard 始终面向摄像机）
 * - 呼吸脉冲动画（alpha 缓慢波动）
 *
 * 无距离限制：同一层内始终可见。
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class LadderHighlightRenderer {

    // 边缘半宽（世界单位）
    private static final float EDGE_HALF = 0.025f;

    // 紫色高亮色调（常规矿 1-120）
    private static final int R = 180, G = 80, B = 255;
    private static final int EDGE_A = 200;
    private static final int FACE_A = 30;
    private static final int BG_R = 50, BG_G = 20, BG_B = 80, BG_A = 190;

    // 红色高亮色调（骷髅矿 121+）
    private static final int SKULL_R = 255, SKULL_G = 60, SKULL_B = 60;
    private static final int SKULL_BG_R = 80, SKULL_BG_G = 20, SKULL_BG_B = 20;

    private static final Component LADDER_TEXT = Component.translatable("stardewcraft.mining.hint.ladder");
    private static final Component SHAFT_TEXT = Component.translatable("stardewcraft.mining.hint.shaft");

    @SuppressWarnings("null")
    private static final RenderType QUAD_TYPE = makeQuadType("stardew_ladder_hint", false);
    @SuppressWarnings("null")
    private static final RenderType QUAD_XRAY = makeQuadType("stardew_ladder_hint_xr", true);

    private LadderHighlightRenderer() {}

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 仅矿井维度
        if (!ModMiningDimensions.STARDEW_MINING.equals(mc.level.dimension())) return;

        // 检查楼梯是否存在
        if (!ClientMiningState.hasLadder()) return;

        // 仅同层渲染
        int currentFloor = MiningFloorHud.getCurrentFloor();
        if (currentFloor != ClientMiningState.getLadderFloor()) return;

        BlockPos ladderPos = ClientMiningState.getLadderPos();
        if (ladderPos == null) return;
        if (!mc.level.getChunkSource().hasChunk(ladderPos.getX() >> 4, ladderPos.getZ() >> 4)) return;

        var ladderState = mc.level.getBlockState(ladderPos);
        if (!ladderState.is(ModBlocks.MINE_LADDER.get())) {
            ClientMiningState.reset();
            return;
        }
        boolean isShaft = ladderState.hasProperty(MineLadderBlock.SHAFT)
            && ladderState.getValue(MineLadderBlock.SHAFT);

        // 呼吸脉冲
        float time = (System.currentTimeMillis() % 3000) / 3000.0f;
        float breath = 0.6f + 0.4f * (float) Math.sin(time * Math.PI * 2);

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        renderGlowOutline(buf, ps, cam, ladderPos, breath, isShaft);
        renderBubble(ps, buf, cam, ladderPos, breath, mc.font, isShaft);

        ps.popPose();
    }

    // ======================== glow outline ========================

    @SuppressWarnings("null")
    private static void renderGlowOutline(MultiBufferSource.BufferSource buf, PoseStack ps, Vec3 cam,
                                           BlockPos pos, float alpha, boolean isShaft) {
        AABB box = new AABB(pos).inflate(0.03);

        int r = isShaft ? SKULL_R : R;
        int g = isShaft ? SKULL_G : G;
        int b = isShaft ? SKULL_B : B;
        int edgeA = (int) (EDGE_A * alpha);
        int faceA = (int) (FACE_A * alpha);

        // X-ray 层（穿墙）
        VertexConsumer xr = buf.getBuffer(QUAD_XRAY);
        renderFaces(ps, xr, box, r, g, b, faceA / 2);
        renderEdgeQuads(ps, xr, box, cam, r, g, b, edgeA / 3, EDGE_HALF * 0.6f);
        buf.endBatch(QUAD_XRAY);

        // 深度测试层
        VertexConsumer vc = buf.getBuffer(QUAD_TYPE);
        renderFaces(ps, vc, box, r, g, b, faceA);
        renderEdgeQuads(ps, vc, box, cam, r, g, b, edgeA, EDGE_HALF);
        buf.endBatch(QUAD_TYPE);
    }

    // ======================== floating bubble ========================

    @SuppressWarnings("null")
    private static void renderBubble(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam,
                                      BlockPos pos, float alpha, Font font, boolean isShaft) {
        int cr = isShaft ? SKULL_R : R;
        int cg = isShaft ? SKULL_G : G;
        int cb = isShaft ? SKULL_B : B;
        int bgr = isShaft ? SKULL_BG_R : BG_R;
        int bgg = isShaft ? SKULL_BG_G : BG_G;
        int bgb = isShaft ? SKULL_BG_B : BG_B;
        double bx = pos.getX() + 0.5;
        double by = pos.getY() + 1.6;
        double bz = pos.getZ() + 0.5;

        // 浮动动画
        float time = (System.currentTimeMillis() % 4000) / 4000.0f;
        by += Math.sin(time * Math.PI * 2) * 0.06;

        ps.pushPose();
        ps.translate(bx, by, bz);

        // Billboard：面向摄像机
        float dx = (float) (cam.x - bx);
        float dz = (float) (cam.z - bz);
        float yaw = (float) Math.atan2(dx, dz);
        ps.mulPose(com.mojang.math.Axis.YP.rotation(yaw));

        float scale = 0.025f;
        ps.scale(-scale, -scale, scale);

        Component displayText = isShaft ? SHAFT_TEXT : LADDER_TEXT;
        int textWidth = font.width(displayText);
        int textHeight = 9;

        int padX = 6, padY = 4;
        int bubbleW = textWidth + padX * 2;
        int bubbleH = textHeight + padY * 2;
        float left = -bubbleW / 2.0f;
        float top = -bubbleH / 2.0f;

        int bgA = (int) (BG_A * alpha);
        int borderA = (int) (220 * alpha);

        VertexConsumer vc = buf.getBuffer(QUAD_TYPE);
        PoseStack.Pose pose = ps.last();
        float z = 0.0f;

        // 背景填充
        fillRect(pose, vc, left + 1, top + 1, left + bubbleW - 1, top + bubbleH - 1, z, bgr, bgg, bgb, bgA);
        fillRect(pose, vc, left, top + 1, left + 1, top + bubbleH - 1, z, bgr, bgg, bgb, bgA);
        fillRect(pose, vc, left + bubbleW - 1, top + 1, left + bubbleW, top + bubbleH - 1, z, bgr, bgg, bgb, bgA);
        fillRect(pose, vc, left + 1, top, left + bubbleW - 1, top + 1, z, bgr, bgg, bgb, bgA);
        fillRect(pose, vc, left + 1, top + bubbleH - 1, left + bubbleW - 1, top + bubbleH, z, bgr, bgg, bgb, bgA);

        // 边框
        fillRect(pose, vc, left + 1, top - 0.5f, left + bubbleW - 1, top + 0.5f, z, cr, cg, cb, borderA);
        fillRect(pose, vc, left + 1, top + bubbleH - 0.5f, left + bubbleW - 1, top + bubbleH + 0.5f, z, cr, cg, cb, borderA);
        fillRect(pose, vc, left - 0.5f, top + 1, left + 0.5f, top + bubbleH - 1, z, cr, cg, cb, borderA);
        fillRect(pose, vc, left + bubbleW - 0.5f, top + 1, left + bubbleW + 0.5f, top + bubbleH - 1, z, cr, cg, cb, borderA);

        // 三角指针
        float triW = 3.0f, triH = 3.0f;
        triQuad(pose, vc, -triW, top + bubbleH, triW, top + bubbleH, 0, top + bubbleH + triH, z, cr, cg, cb, borderA);

        buf.endBatch(QUAD_TYPE);

        // 渲染文字 —— 重置 GL 状态，避免自定义 QUAD_TYPE 的 shader/blend 状态污染字体渲染
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        MultiBufferSource.BufferSource textBuf = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        int textAlpha = (int) (255 * alpha);
        int textColor = (textAlpha << 24) | (cr << 16) | (cg << 8) | cb;

        font.drawInBatch(
            displayText,
            -textWidth / 2.0f, -textHeight / 2.0f,
            textColor,
            true,
            ps.last().pose(),
            textBuf,
            Font.DisplayMode.NORMAL,
            0,
            0xF000F0
        );
        textBuf.endBatch();

        ps.popPose();
    }

    // ======================== geometry helpers ========================

    @SuppressWarnings("null")
    private static void fillRect(PoseStack.Pose pose, VertexConsumer vc,
                                  float x0, float y0, float x1, float y1, float z,
                                  int r, int g, int b, int a) {
        vc.addVertex(pose, x0, y0, z).setColor(r, g, b, a);
        vc.addVertex(pose, x0, y1, z).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y0, z).setColor(r, g, b, a);
    }

    @SuppressWarnings("null")
    private static void triQuad(PoseStack.Pose pose, VertexConsumer vc,
                                 float ax, float ay, float bx, float by, float cx, float cy, float z,
                                 int r, int g, int b, int a) {
        vc.addVertex(pose, ax, ay, z).setColor(r, g, b, a);
        vc.addVertex(pose, bx, by, z).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, z).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, z).setColor(r, g, b, a);
    }

    // ======================== 3D box rendering ========================

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
        v.addVertex(p, ax, ay, az).setColor(r, g, b, a);
        v.addVertex(p, bx, by, bz).setColor(r, g, b, a);
        v.addVertex(p, cx, cy, cz).setColor(r, g, b, a);
        v.addVertex(p, dx, dy, dz).setColor(r, g, b, a);
    }

    @SuppressWarnings("null")
    private static void renderEdgeQuads(PoseStack ps, VertexConsumer vc, AABB b, Vec3 cam,
                                         int r, int g, int bl, int a, float halfW) {
        if (a <= 0) return;
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        // bottom
        edgeQuad(ps, vc, x0,y0,z0, x1,y0,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z0, x1,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y0,z1, x0,y0,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y0,z1, x0,y0,z0, halfW, cam, r,g,bl,a);
        // top
        edgeQuad(ps, vc, x0,y1,z0, x1,y1,z0, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z0, x1,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x1,y1,z1, x0,y1,z1, halfW, cam, r,g,bl,a);
        edgeQuad(ps, vc, x0,y1,z1, x0,y1,z0, halfW, cam, r,g,bl,a);
        // verticals
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
        vc.addVertex(pose, ax - px, ay - py, az - pz).setColor(r, g, b, a);
        vc.addVertex(pose, ax + px, ay + py, az + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx + px, by + py, bz + pz).setColor(r, g, b, a);
        vc.addVertex(pose, bx - px, by - py, bz - pz).setColor(r, g, b, a);
    }

    // ======================== RenderType ========================

    @SuppressWarnings("null")
    private static RenderType makeQuadType(String name, boolean xray) {
        return RenderType.create(name,
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1024, false, true,
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
