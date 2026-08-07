package com.stardew.craft.client.auction;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stardew.craft.auction.AuctionService;
import com.stardew.craft.network.payload.SyncAuctionBoardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

public final class AuctionTooltipBoardRenderer {
    private AuctionTooltipBoardRenderer() {
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        SyncAuctionBoardPayload board = AuctionClientState.board();
        if (!board.active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.distanceToSqr(71.0D, 51.0D, -8.9D) > 28.0D * 28.0D) {
            return;
        }

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(71.0D - cam.x, 51.35D - cam.y, -8.94D - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = 0.0175F;
        ps.scale(scale, -scale, scale);

        Font font = StardewFonts.small();
        drawBoard(ps, buffers);
        drawCentered(font, ps, buffers, fit(font, board.auctionName(), 154), -76, 0xFFF4C975);
        drawCentered(font, ps, buffers, ComponentText.lotMeta(board), -61, 0xFFD19A57);
        fill(ps, buffers, -80, -49, 80, -47, 0xE0B07136);

        renderTooltip(font, ps, buffers, mc, player, board, -72, -39);
        drawLeft(font, ps, buffers, ComponentText.seller(board), -72, -8, 0xFFC8873E);
        String bidder = board.bidderName().isBlank()
            ? net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.no_bidder").getString()
            : net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.bidder", board.bidderName()).getString();
        drawLeft(font, ps, buffers, fit(font, bidder, 144), -72, 5, 0xFFD9A968);

        drawPricePanel(font, ps, buffers, -72, 22, 69,
            net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.current", "").getString(),
            board.currentPrice(), 0xFFFFD16D);
        drawPricePanel(font, ps, buffers, 3, 22, 69,
            net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.next", "").getString(),
            board.nextBid(), 0xFFFFE3A2);

        int timerX = -72;
        int timerY = 55;
        int timerW = 144;
        int remaining = AuctionClientState.liveRemainingSeconds();
        float ratio = Math.max(0.0F, Math.min(1.0F, remaining / (float) AuctionService.LOT_SECONDS));
        fill(ps, buffers, timerX, timerY, timerX + timerW, timerY + 6, 0x96553221);
        int timerColor = remaining <= AuctionService.FINAL_EXTENSION_SECONDS
            ? blend(0xFFF3C86D, 0xFFE06A3C, pulse())
            : 0xFFD89532;
        fill(ps, buffers, timerX, timerY, timerX + Math.max(4, Math.round(timerW * ratio)), timerY + 6, timerColor);
        drawCentered(font, ps, buffers,
            net.minecraft.network.chat.Component.translatable("stardewcraft.auction.board.remaining", remaining).getString(),
            66, 0xFFD8AA70);
        buffers.endBatch();
        ps.popPose();
    }

    private static void drawBoard(PoseStack ps, MultiBufferSource buffers) {
        fill(ps, buffers, -94, -74, 94, 74, 0xEC2A1B12);
        fill(ps, buffers, -90, -70, 90, 70, 0xF0482818);
        fill(ps, buffers, -84, -64, 84, 64, 0xFF6A3E1F);
        fill(ps, buffers, -80, -60, 80, 60, 0xF03C2518);
        fill(ps, buffers, -74, 19, -1, 54, 0xC8613A20);
        fill(ps, buffers, 1, 19, 74, 54, 0xC8613A20);
        float glint = pulse();
        int line = blend(0xFFC8873E, 0xFFFFD98B, glint);
        fill(ps, buffers, -80, -64, 80, -62, line);
        fill(ps, buffers, -80, 62, 80, 64, line);
    }

    private static void renderTooltip(Font font, PoseStack ps, MultiBufferSource buffers, Minecraft mc, Player player,
                                      SyncAuctionBoardPayload board, int x, int y) {
        List<Component> tooltip = board.stack().getTooltipLines(
            mc.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(mc.level),
            player,
            mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        int drawn = 0;
        for (Component component : tooltip) {
            for (FormattedCharSequence line : font.split(component, 144)) {
                if (drawn >= 4) {
                    return;
                }
                int color = drawn == 0 ? 0xFFFFD98B : 0xFFD9A968;
                font.drawInBatch(line, x, y + drawn * 10, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                drawn++;
            }
        }
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("..."))) + "...";
    }

    private static void drawCentered(Font font, PoseStack ps, MultiBufferSource buffers, String text, int y, int color) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, y, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    private static void drawLeft(Font font, PoseStack ps, MultiBufferSource buffers, String text, int x, int y, int color) {
        font.drawInBatch(text, x, y, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    private static void drawPricePanel(Font font, PoseStack ps, MultiBufferSource buffers, int x, int y, int w,
                                       String label, int amount, int valueColor) {
        drawLeft(font, ps, buffers, fit(font, label, w - 8), x + 7, y + 6, 0xFFD19A57);
        drawLeft(font, ps, buffers, fit(font, amount + "g", w - 8), x + 7, y + 21, valueColor);
    }

    private static float pulse() {
        return 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 330.0D);
    }

    private static int blend(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void fill(PoseStack ps, MultiBufferSource buffers, float x1, float y1, float x2, float y2, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.gui());
        vc.addVertex(m, x1, y2, 0).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, 0).setColor(r, g, b, a);
        vc.addVertex(m, x2, y1, 0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, 0).setColor(r, g, b, a);
    }

    private static final class ComponentText {
        private ComponentText() {
        }

        private static String lotMeta(SyncAuctionBoardPayload board) {
            return net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.lot_meta",
                board.lotIndex(), board.lotCount(), Math.max(0, board.remainingSeconds())).getString();
        }

        private static String seller(SyncAuctionBoardPayload board) {
            return net.minecraft.network.chat.Component.translatable("stardewcraft.auction.bid.seller", board.sellerName()).getString();
        }
    }
}
