package com.stardew.craft.client.auction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.stardew.craft.port.PortVertex;
/** Physical auction ledger: concise live data, with full item tooltips available in the bid screen. */
public final class AuctionTooltipBoardRenderer {
    private static final RenderType MATERIAL = RenderType.text(new ResourceLocation(
            StardewCraft.MODID, "textures/gui/auction/board.png"));
    private static final int LIGHT = 0xF000F0;
    private static final int INK = 0xFF472F2B, MUTED = 0xFF79563F, GOLD = 0xFF8A5429;
    private AuctionTooltipBoardRenderer() { }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var board = AuctionClientState.board();
        Minecraft mc = Minecraft.getInstance();
        if (!board.active() || mc.player == null || mc.level == null
                || !ModDimensions.STARDEW_VALLEY.equals(mc.level.dimension())
                || mc.player.distanceToSqr(71, 51, -8.9) > 28 * 28) return;
        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(71 - cam.x, 51.35 - cam.y, -8.94 - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        // Higher native texture resolution, same physical width as the original room board.
        float scale = 188 * 0.0175f / 256;
        ps.scale(scale, -scale, scale);
        ps.translate(-128, -100, 0);
        texture(ps, buffers);
        buffers.endBatch(MATERIAL);
        ps.translate(0, 0, 0.1);
        Font font = StardewFonts.small();
        int remaining = AuctionClientState.liveRemainingSeconds();
        text(font, ps, buffers, board.auctionName(), 16, 17, 224, 0xFFFFEED0);
        text(font, ps, buffers, board.stack().getHoverName().getString(), 16, 42, 224, INK);
        text(font, ps, buffers, tr("board.lot", board.lotIndex(), board.lotCount()), 16, 62, 224, GOLD);
        text(font, ps, buffers, tr("bid.seller", board.sellerName()), 16, 82, 224, MUTED);
        text(font, ps, buffers, board.bidderName().isBlank() ? tr("bid.no_bidder")
                : tr("bid.bidder", board.bidderName()), 16, 102, 224, INK);
        price(font, ps, buffers, "bid.current", board.currentPrice(), 128, INK);
        price(font, ps, buffers, "bid.next", board.nextBid(), 150, GOLD);
        boolean closing = remaining <= AuctionService.FINAL_EXTENSION_SECONDS;
        text(font, ps, buffers, tr("board.remaining", remaining), 16, 172, 224, closing ? 0xFF993D3B : MUTED);
        // Draw the timer from the same depth-aware material pipeline as the board; zero means no fill.
        float ratio = Math.max(0, Math.min(1, remaining / (float) AuctionService.LOT_SECONDS));
        bar(ps, buffers, Math.round(224 * ratio), closing ? 0xFF993D3B : GOLD);
        buffers.endBatch(MATERIAL);
        ps.popPose();
    }
    private static String tr(String key, Object... args) {
        return Component.translatable("stardewcraft.auction." + key, args).getString();
    }
    private static void text(Font font, PoseStack ps, MultiBufferSource buffers, String value,
                             int x, int y, int width, int color) {
        String shown = font.width(value) <= width ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
        font.drawInBatch(shown, x, y, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }
    private static void price(Font font, PoseStack ps, MultiBufferSource buffers, String key, int amount, int y, int color) {
        String value = amount + "g";
        int scale = key.equals("bid.current") && StardewFonts.lineHeight(font) * 2 <= 20
                && font.width(value) * 2 <= 140 ? 2 : 1;
        int valueWidth = font.width(value) * scale;
        // Preserve every price digit; only the label can be abbreviated in a long locale.
        text(font, ps, buffers, tr(key, ""), 16, y, 224 - valueWidth - 12, MUTED);
        ps.pushPose();
        ps.translate(240 - valueWidth, y, 0);
        ps.scale(scale, scale, 1);
        text(font, ps, buffers, value, 0, 0, font.width(value), color);
        ps.popPose();
    }
    private static void texture(PoseStack ps, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(MATERIAL);
        vertex(vc, ps, 0, 200, 0, 1, -1);
        vertex(vc, ps, 256, 200, 1, 1, -1);
        vertex(vc, ps, 256, 0, 1, 0, -1);
        vertex(vc, ps, 0, 0, 0, 0, -1);
    }
    private static void bar(PoseStack ps, MultiBufferSource buffers, int width, int color) {
        if (width <= 0) return;
        VertexConsumer vc = buffers.getBuffer(MATERIAL);
        // Sample an authored white brass highlight, not a transparent gutter.
        float u = 4.5f / 256, v = 4.5f / 200;
        vertex(vc, ps, 16, 191, u, v, color);
        vertex(vc, ps, 16 + width, 191, u, v, color);
        vertex(vc, ps, 16 + width, 188, u, v, color);
        vertex(vc, ps, 16, 188, u, v, color);
    }
    private static void vertex(VertexConsumer vc, PoseStack ps, float x, float y, float u, float v, int color) {
        PortVertex.of(vc).addVertex(ps.last().pose(), x, y, 0).setColor(color).setUv(u, v).setLight(LIGHT).endVertex();
    }
}
