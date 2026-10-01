package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.stardew.craft.port.PortVertex;
/**
 * Renders this player's active ore-pan point as a golden glowing column +
 * occasional enchant/end-rod sparkle particles.
 * <p>
 * Visible only to the owning player — {@link ClientPanPointState} is populated
 * exclusively from {@link com.stardew.craft.network.payload.PanPointSyncPayload}
 * which the server sends one-to-one.
 */
public final class PanPointRenderer {

    private static final double VISIBLE_RANGE = 40.0;
    private static final double VISIBLE_RANGE_SQ = VISIBLE_RANGE * VISIBLE_RANGE;

    // Gold color — matches SDV's "shiny" water sparkle
    private static final int R = 255, G = 215, B = 80;

    private static int particleTick = 0;

    private PanPointRenderer() {}

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        Vec3 target = ClientPanPointState.getCenterVec();
        if (target == null) return;

        if (player.position().distanceToSqr(target) > VISIBLE_RANGE_SQ) return;

        // Spawn sparkle particles occasionally
        particleTick++;
        if (particleTick % 4 == 0) {
            double ox = (player.getRandom().nextDouble() - 0.5) * 0.6;
            double oz = (player.getRandom().nextDouble() - 0.5) * 0.6;
            double oy = player.getRandom().nextDouble() * 0.4;
            mc.level.addParticle(ParticleTypes.END_ROD,
                target.x + ox, target.y + oy, target.z + oz,
                0, 0.02, 0);
            if (particleTick % 16 == 0) {
                mc.level.addParticle(ParticleTypes.ENCHANT,
                    target.x + ox, target.y + 0.8 + oy, target.z + oz,
                    0, -0.05, 0);
            }
        }

        // Render a soft glowing vertical beam
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        ps.pushPose();
        ps.translate(target.x - cam.x, target.y - cam.y - 0.1, target.z - cam.z);

        float pulse = 0.75f + 0.25f * (float) Math.sin((System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2);

        renderBeam(ps, pulse);

        ps.popPose();
    }

    @SuppressWarnings("null")
    private static void renderBeam(PoseStack ps, float alphaScale) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tess = Tesselator.getInstance();
        // PORT(1.20.1): the Tesselator's shared BufferBuilder is begun explicitly.
        com.mojang.blaze3d.vertex.BufferBuilder bb = tess.getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float r = R / 255f, g = G / 255f, b = B / 255f;
        float aCore = 0.65f * alphaScale;
        float aEdge = 0.15f * alphaScale;

        // Inner bright column — 0.15 wide, 1.6 tall
        quadColumn(bb, ps, 0.15f, 1.6f, r, g, b, aCore);
        // Outer halo — 0.45 wide, 1.8 tall
        quadColumn(bb, ps, 0.45f, 1.8f, r, g, b, aEdge);

        com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer mesh = bb.end();
        // PORT(1.20.1): 1.21 build() returns null (nothing drawn) for an empty buffer.
        if (!mesh.isEmpty()) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        } else {
            mesh.release();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quadColumn(com.mojang.blaze3d.vertex.BufferBuilder bb, PoseStack ps,
                                    float halfWidth, float height, float r, float g, float b, float a) {
        var pose = ps.last().pose();
        float h = halfWidth;
        float y0 = 0f;
        float y1 = height;

        // Two crossed billboards for a soft beam
        PortVertex.of(bb).addVertex(pose, -h, y0, 0).setColor(r, g, b, a).endVertex();
        PortVertex.of(bb).addVertex(pose,  h, y0, 0).setColor(r, g, b, a).endVertex();
        PortVertex.of(bb).addVertex(pose,  h, y1, 0).setColor(r, g, b, 0f).endVertex();
        PortVertex.of(bb).addVertex(pose, -h, y1, 0).setColor(r, g, b, 0f).endVertex();

        PortVertex.of(bb).addVertex(pose, 0, y0, -h).setColor(r, g, b, a).endVertex();
        PortVertex.of(bb).addVertex(pose, 0, y0,  h).setColor(r, g, b, a).endVertex();
        PortVertex.of(bb).addVertex(pose, 0, y1,  h).setColor(r, g, b, 0f).endVertex();
        PortVertex.of(bb).addVertex(pose, 0, y1, -h).setColor(r, g, b, 0f).endVertex();
    }
}
