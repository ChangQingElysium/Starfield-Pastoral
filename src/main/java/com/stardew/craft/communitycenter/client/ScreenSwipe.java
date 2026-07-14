package com.stardew.craft.communitycenter.client;

import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.client.gui.LocalizedGuiAssets;
import net.minecraft.client.gui.GuiGraphics;

/**
 * SDV ScreenSwipe parity — a banner that:
 *  1. Expands from left to fill the screen width
 *  2. Pauses for {@code durationAfterSwipe} ms (click to skip)
 *  3. Slides off to the right
 *
 * Only type 0 (bundle complete) is implemented.
 */
@SuppressWarnings("null")
public class ScreenSwipe {

    // Cursors source rects (SDV: ScreenSwipe ctor, which == 0)
    private static final int BG_U = 128, BG_V = 1296, BG_W = 1, BG_H = 71;
    private static final int FLAIR_U = 144, FLAIR_V = 1303, FLAIR_W = 144, FLAIR_H = 58;
    private static final int MSG_W = 150, MSG_H = 14;
    private static final int MFLAIR_U = 643, MFLAIR_V = 768, MFLAIR_W = 8, MFLAIR_H = 13;

    // State
    private int bgDestX;
    private int bgDestWidth;
    private final int bgDestY;
    private final int bgDestHeight;

    private int durationAfterSwipe;
    private final float swipeVelocity;

    // Flair / message positions
    private float flairLeftX, flairLeftY;
    private float flairRightX, flairRightY;
    private float messageX, messageY;
    private float movingFlairX, movingFlairY;

    private final int viewportWidth;

    private final float scale; // SDV uses 4.0f; MC needs 4.0f / guiScale

    private boolean finished = false;
    private boolean playedExpandSound = false;
    private boolean playedSwipeOutSound = false;

    /** Callback to play sounds — avoids direct sound dependency. */
    public interface SoundPlayer {
        void play(String soundId);
    }

    private final SoundPlayer soundPlayer;

    public ScreenSwipe(int viewportWidth, int viewportHeight, float scale, SoundPlayer soundPlayer) {
        this.viewportWidth = viewportWidth;
        this.scale = scale;
        this.soundPlayer = soundPlayer;

        this.swipeVelocity = 5.0f;
        this.durationAfterSwipe = 2700;

        float cx = viewportWidth / 2.0f;
        float cy = viewportHeight / 2.0f;

        this.bgDestY = (int) (cy - BG_H * scale / 2.0f);
        this.bgDestHeight = (int) (BG_H * scale);
        this.bgDestX = 0;
        this.bgDestWidth = (int) (BG_W * scale);

        this.messageX = cx - MSG_W * scale / 2.0f;
        this.messageY = cy - MSG_H * scale / 2.0f;

        this.flairLeftX = messageX - FLAIR_W * scale - 64 * scale / 4;
        this.flairLeftY = bgDestY + 28 * scale / 4;
        this.flairRightX = messageX + MSG_W * scale + 64 * scale / 4;
        this.flairRightY = bgDestY + 28 * scale / 4;

        this.movingFlairX = messageX + MSG_W * scale + 192 * scale / 4;
        this.movingFlairY = cy + 32 * scale / 4;

        soundPlayer.play("throw");
    }

    /**
     * @param deltaMs elapsed time in ms
     * @param clicked true if user clicked this frame (skip pause)
     * @return true if the swipe animation is fully done
     */
    public boolean update(float deltaMs, boolean clicked) {
        if (finished) return true;

        // Phase 1: expand background to fill screen
        if (durationAfterSwipe > 0 && bgDestWidth <= viewportWidth) {
            bgDestWidth += (int) (swipeVelocity * deltaMs);
            if (bgDestWidth > viewportWidth && !playedExpandSound) {
                playedExpandSound = true;
                soundPlayer.play("newRecord");
            }
        }
        // Phase 3: slide everything to the right and off screen
        else if (durationAfterSwipe <= 0) {
            int shift = (int) (swipeVelocity * deltaMs);
            bgDestX += shift;
            if (bgDestX > flairLeftX) flairLeftX = bgDestX;
            if (bgDestX > flairRightX) flairRightX = bgDestX;
            if (bgDestX > messageX) messageX = bgDestX;
            if (bgDestX > movingFlairX) movingFlairX = bgDestX;
        }

        // Phase 2: stay for durationAfterSwipe (or click to skip)
        if (bgDestWidth > viewportWidth && durationAfterSwipe > 0) {
            if (clicked) {
                durationAfterSwipe = 0;
            }
            durationAfterSwipe -= (int) deltaMs;
            if (durationAfterSwipe <= 0 && !playedSwipeOutSound) {
                playedSwipeOutSound = true;
                soundPlayer.play("tinyWhip");
            }
        }

        // Moving flair bobbing (SDV: motion (0, -0.5) per tick)
        movingFlairY -= 0.5f * (scale / 4.0f) * deltaMs / 16.67f;

        // Done when bg slid off screen
        if (bgDestX > viewportWidth) {
            finished = true;
            return true;
        }
        return false;
    }

    public void draw(GuiGraphics g) {
        if (finished) return;

        // Background bar (1px wide source stretched to bgDestWidth)
        g.pose().pushPose();
        g.pose().translate(0, 0, 10.0f); // draw on top of everything
        // SDV: b.Draw(texture, bgDest, bgSource, Color.White)
        // bgDest = (bgDestX, bgDestY, bgDestWidth, bgDestHeight)
        g.blit(StardewGuiUtil.CURSORS,
                bgDestX, bgDestY, bgDestWidth, bgDestHeight,
                BG_U, BG_V, BG_W, BG_H,
                StardewGuiUtil.CURSORS_WIDTH, StardewGuiUtil.CURSORS_HEIGHT);

        // Left flair
        drawClipped(g, (int) flairLeftX, (int) flairLeftY, FLAIR_U, FLAIR_V, FLAIR_W, FLAIR_H);

        // Right flair
        drawClipped(g, (int) flairRightX, (int) flairRightY, FLAIR_U, FLAIR_V, FLAIR_W, FLAIR_H);

        // Official localized message texture.
        drawLocalizedMessage(g, (int) messageX, (int) messageY);

        // Moving flair (small star)
        drawClipped(g, (int) movingFlairX, (int) movingFlairY, MFLAIR_U, MFLAIR_V, MFLAIR_W, MFLAIR_H);

        g.pose().popPose();
    }

    private void drawClipped(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        // Only draw if within the expanding bg region
        if (x + w * scale < bgDestX || x > bgDestX + bgDestWidth) return;

        // Calculate how much of the source to show (clip to bg width)
        int visibleW = (int) Math.min(w, (bgDestWidth - (x - bgDestX)) / scale);
        if (visibleW <= 0) return;

        g.pose().pushPose();
        g.pose().translate(x, y, 0.1f);
        g.pose().scale(scale, scale, 1.0f);
        g.blit(StardewGuiUtil.CURSORS, 0, 0, u, v, visibleW, h,
                StardewGuiUtil.CURSORS_WIDTH, StardewGuiUtil.CURSORS_HEIGHT);
        g.pose().popPose();
    }

    private void drawLocalizedMessage(GuiGraphics g, int x, int y) {
        if (x + MSG_W * scale < bgDestX || x > bgDestX + bgDestWidth) return;

        int visibleW = (int) Math.min(MSG_W, (bgDestWidth - (x - bgDestX)) / scale);
        if (visibleW <= 0) return;

        g.pose().pushPose();
        g.pose().translate(x, y, 0.1f);
        g.pose().scale(scale, scale, 1.0f);
        g.blit(LocalizedGuiAssets.texture("bundle/bundle_complete.png"),
                0, 0, 0, 0, visibleW, MSG_H, MSG_W, MSG_H);
        g.pose().popPose();
    }
}
