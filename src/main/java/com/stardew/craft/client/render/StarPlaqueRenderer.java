package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.communitycenter.network.BundleClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 社区中心星盘渲染器 — 根据已完成区域数切换 0-6 星纹理。
 * 单张纹理渲染, 不做 quad 叠加。
 */
@SuppressWarnings("null")
public final class StarPlaqueRenderer {

    private StarPlaqueRenderer() {}

    // ── 7 张预烘焙纹理 (0-6 星) ─────────────────────────────────────────
    private static final RenderType[] STAGE_RTS = new RenderType[7];
    static {
        for (int i = 0; i <= 6; i++) {
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", "textures/gui/star_plaque_" + i + "stars.png");
            STAGE_RTS[i] = RenderType.create(
                "stardew_star_plaque_" + i,
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderType.TextureStateShard(tex, false, false))
                    .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency",
                        () -> { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); },
                        RenderSystem::disableBlend))
                    .setWriteMaskState(new RenderType.WriteMaskStateShard(true, false))
                    .setCullState(new RenderType.CullStateShard(false))
                    .setDepthTestState(RenderType.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
        }
    }

    // ── 渲染中心相对每位玩家自己的 CC 原点 (西墙面朝东) ───────────────
    private static final double CENTER_OFFSET_X = 4.02;
    private static final double CENTER_OFFSET_Y = 5.0;
    private static final double CENTER_OFFSET_Z = 35.0;

    // ── 纹理尺寸: 108×96 @3× = 2.25×2 blocks ──────────────────────────
    private static final float HALF_Z = 1.125f;
    private static final float HALF_Y = 1.0f;

    private static final double RENDER_RANGE_SQ = 32.0 * 32.0;

    // ── 主渲染入口 ──────────────────────────────────────────────────────
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BundleClientData data = BundleClientData.INSTANCE;
        net.minecraft.core.BlockPos origin = data.getCCOrigin();
        double centerX = origin.getX() + CENTER_OFFSET_X;
        double centerY = origin.getY() + CENTER_OFFSET_Y;
        double centerZ = origin.getZ() + CENTER_OFFSET_Z;
        Vec3 cam = event.getCamera().getPosition();
        double dx = centerX - cam.x;
        double dy = centerY - cam.y;
        double dz = centerZ - cam.z;
        if (dx * dx + dy * dy + dz * dz > RENDER_RANGE_SQ) return;

        // 统计已显示的星星数（由 StarPlacedPayload 驱动，Junimo 放完才递增）
        int litCount = data.getDisplayStarCount();

        RenderType rt = STAGE_RTS[litCount];
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(dx, dy, dz);
        Matrix4f mat = ps.last().pose();

        VertexConsumer vc = buf.getBuffer(rt);
        // YZ 平面 quad, 面朝 +X (东)
        vc.addVertex(mat, 0, -HALF_Y,  HALF_Z).setUv(0, 1).setColor(255, 255, 255, 255);
        vc.addVertex(mat, 0, -HALF_Y, -HALF_Z).setUv(1, 1).setColor(255, 255, 255, 255);
        vc.addVertex(mat, 0,  HALF_Y, -HALF_Z).setUv(1, 0).setColor(255, 255, 255, 255);
        vc.addVertex(mat, 0,  HALF_Y,  HALF_Z).setUv(0, 0).setColor(255, 255, 255, 255);

        ps.popPose();
        buf.endBatch(rt);
    }
}
