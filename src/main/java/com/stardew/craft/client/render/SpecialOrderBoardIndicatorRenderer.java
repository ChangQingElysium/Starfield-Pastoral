package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.gui.specialorder.ClientSpecialOrderBoardData;
import com.stardew.craft.client.specialorder.ClientSpecialOrderUnlockState;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.specialorder.SpecialOrderBoardInstaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SpecialOrderBoardIndicatorRenderer {
    private static final double RENDER_RANGE = 16.0;
    private static final double RENDER_RANGE_SQ = RENDER_RANGE * RENDER_RANGE;
    private static final int EXCLAM_R = 0xFF;
    private static final int EXCLAM_G = 0xE6;
    private static final int EXCLAM_B = 0x55;

    private SpecialOrderBoardIndicatorRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        if (!ClientSpecialOrderUnlockState.isUnlocked()) return;
        if (!ClientSpecialOrderBoardData.hasUnclaimedAvailableOrders()) return;

        BlockPos pos = SpecialOrderBoardInstaller.BOARD_POS;
        if (!level.getBlockState(pos).is(ModBlocks.SPECIAL_ORDERS_BOARD.get())) return;

        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() + 2.0D - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();
        if (dx * dx + dy * dy + dz * dz > RENDER_RANGE_SQ) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        long nowMs = System.currentTimeMillis();
        float yOffset = 0.25F * (float) Math.sin(nowMs / 250.0);
        float scalePulse = 1.0F + Math.max(0.0F, (0.25F - yOffset) * 0.5F);

        ps.pushPose();
        ps.translate(pos.getX() + 0.5D - cam.x, pos.getY() + 2.25D + yOffset - cam.y, pos.getZ() + 0.5D - cam.z);
        ps.mulPose(event.getCamera().rotation());
        float baseScale = 0.025F * scalePulse;
        ps.scale(baseScale, -baseScale, baseScale);
        drawGlyph(mc.font, ps, buf, "!", EXCLAM_R, EXCLAM_G, EXCLAM_B);
        ps.popPose();

        buf.endBatch();
    }

    private static void drawGlyph(Font font, PoseStack ps, MultiBufferSource buf,
                                  String glyph, int r, int g, int b) {
        Component text = Component.literal(glyph);
        float x = -font.width(text) / 2.0F;
        float y = -font.lineHeight / 2.0F;
        int color = (0xFF << 24) | (r << 16) | (g << 8) | b;
        font.drawInBatch(text, x, y, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }
}
