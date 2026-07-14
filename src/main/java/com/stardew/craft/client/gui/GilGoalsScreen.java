package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.GilClaimRewardPayload;
import com.stardew.craft.network.payload.OpenGilGoalsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Client-side screen for monster slayer goals.
 * SDV-style dialog box with progress bars and claim buttons.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class GilGoalsScreen extends Screen {

    private static final int BG_TINT = 0xBF000000;
    private static final int ROW_HEIGHT = 32;
    private static final int INNER_PAD = 20;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_WIDTH = 120;

    // Colors
    private static final int COL_TITLE      = 0xFF8B0000; // dark red
    private static final int COL_NAME       = 0xFF3E2723; // brown
    private static final int COL_PROGRESS   = 0xFF5D4037; // lighter brown
    private static final int COL_CLAIMED    = 0xFF888888;
    private static final int COL_READY      = 0xFF2E7D32; // green
    private static final int COL_BAR_BG     = 0xFF4A3728; // dark brown bar background
    private static final int COL_BAR_FILL   = 0xFF8BC34A; // green fill
    private static final int COL_BAR_DONE   = 0xFF4CAF50; // completed green
    private static final int COL_BAR_BORDER = 0xFF2E1B0E; // dark border
    private static final int COL_CLAIM_BG   = 0xFFFFD54F; // gold button
    private static final int COL_CLAIM_HOVER= 0xFFFFF176;
    private static final int COL_CLAIM_TEXT = 0xFF3E2723;
    private static final int COL_CHECK      = 0xFF4CAF50;
    private static final int COL_REWARD     = 0xFFFF8F00; // amber

    private final List<OpenGilGoalsPayload.GoalEntry> goals;

    private int boxX, boxY, boxW, boxH;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public GilGoalsScreen(List<OpenGilGoalsPayload.GoalEntry> goals) {
        super(Component.translatable("stardewcraft.gil.title"));
        this.goals = goals;
    }

    @Override
    protected void init() {
        super.init();
        boxW = Math.min(440, width - 40);
        boxH = Math.min(height - 40, 60 + goals.size() * ROW_HEIGHT + 40);
        boxX = (width - boxW) / 2;
        boxY = (height - boxH) / 2;
        maxScroll = Math.max(0, (goals.size() * ROW_HEIGHT + 40) - (boxH - 60));
        scrollOffset = 0;
    }

    // ── suppress MC dirt/blur background ──
    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty — we draw our own
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dark overlay
        g.fill(0, 0, width, height, BG_TINT);

        // SDV-style dialog frame
        StardewGuiUtil.drawDialogueBoxFrame(g, boxX, boxY, boxW, boxH);

        // Title — centered
        int titleY = boxY + 20;
        GuiText.drawCenteredClamped(g, font, title, boxX + boxW / 2, titleY, boxW - INNER_PAD * 2, COL_TITLE, false);

        // Separator line below title
        int sepY = titleY + font.lineHeight + 6;
        StardewGuiUtil.drawHorizontalPartitionSmall(g, boxX, sepY, boxW, 1.0f);

        // Clip region for goal rows
        int clipTop = sepY + 12;
        int clipBottom = boxY + boxH - 24;
        g.enableScissor(boxX, clipTop, boxX + boxW, clipBottom);

        // Goal rows
        int rowStartY = clipTop - scrollOffset;
        for (int i = 0; i < goals.size(); i++) {
            int rowY = rowStartY + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < clipTop || rowY > clipBottom) continue;
            drawGoalRow(g, i, rowY, mouseX, mouseY);
        }

        g.disableScissor();

        // Scroll indicator when there's more content
        if (maxScroll > 0) {
            float scrollFrac = (float) scrollOffset / maxScroll;
            int trackH = clipBottom - clipTop - 8;
            int thumbH = Math.max(12, trackH * (clipBottom - clipTop) / (goals.size() * ROW_HEIGHT + 40));
            int thumbY = clipTop + 4 + (int) ((trackH - thumbH) * scrollFrac);
            int scrollX = boxX + boxW - 14;
            g.fill(scrollX, clipTop + 4, scrollX + 4, clipTop + 4 + trackH, 0x40000000);
            g.fill(scrollX, thumbY, scrollX + 4, thumbY + thumbH, 0x80FFFFFF);
        }

        // Footer hint
        String hint = goals.stream().anyMatch(g2 -> g2.currentKills() >= g2.requiredKills() && !g2.claimed())
            ? "\u2728 " + Component.translatable("stardewcraft.gil.hint_ready").getString()
            : Component.translatable("stardewcraft.gil.hint_keep").getString();
        GuiText.drawCenteredClamped(g, font, Component.literal(hint), boxX + boxW / 2, boxY + boxH - 18,
            boxW - INNER_PAD * 2, 0xFF9E9E9E, false);
    }

    private void drawGoalRow(GuiGraphics g, int index, int rowY, int mouseX, int mouseY) {
        OpenGilGoalsPayload.GoalEntry goal = goals.get(index);

        int leftX = boxX + INNER_PAD + 8;
        int rightEdge = boxX + boxW - INNER_PAD - 8;

        boolean completed = goal.currentKills() >= goal.requiredKills();
        boolean claimed = goal.claimed();

        // Alternating row background
        if (index % 2 == 0) {
            g.fill(boxX + INNER_PAD, rowY, rightEdge + 8, rowY + ROW_HEIGHT, 0x18000000);
        }

        // Goal name
        Component goalName = goal.translationKey().isBlank()
            ? Component.literal(goal.goalKey())
            : Component.translatable(goal.translationKey());
        int nameColor = claimed ? COL_CLAIMED : COL_NAME;
        int nameMaxW = Math.max(20, rightEdge - leftX - BAR_WIDTH - 52);
        g.drawString(font, GuiText.ellipsize(font, goalName, nameMaxW), leftX, rowY + 4, nameColor);

        // Progress bar
        int barX = leftX;
        int barY = rowY + 4 + font.lineHeight + 2;
        float progress = Math.min(1.0f, (float) goal.currentKills() / Math.max(1, goal.requiredKills()));

        // Bar border
        g.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, COL_BAR_BORDER);
        // Bar background
        g.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, COL_BAR_BG);
        // Bar fill
        int fillW = (int) (BAR_WIDTH * progress);
        if (fillW > 0) {
            int fillColor = completed ? COL_BAR_DONE : COL_BAR_FILL;
            g.fill(barX, barY, barX + fillW, barY + BAR_HEIGHT, fillColor);
        }

        // Kill count text (right of bar)
        int kills = Math.min(goal.currentKills(), goal.requiredKills());
        String countText = kills + "/" + goal.requiredKills();
        int countX = barX + BAR_WIDTH + 6;
        int countColor = claimed ? COL_CLAIMED : (completed ? COL_READY : COL_PROGRESS);
        g.drawString(font, countText, countX, barY, countColor);

        // Claim button or checkmark
        if (claimed) {
            // Checkmark ✓
            g.drawString(font, "\u2713", rightEdge - font.width("\u2713"), rowY + 10, COL_CHECK);
        } else if (completed) {
            // Draw claim button
            int btnW = Math.min(font.width(Component.translatable("stardewcraft.gil.claim").getString()) + 12, Math.max(40, rightEdge - (barX + BAR_WIDTH + 50)));
            int btnH = 16;
            int btnX = rightEdge - btnW;
            int btnY = rowY + 8;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, hovered ? COL_CLAIM_HOVER : COL_CLAIM_BG);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0x40FFFFFF); // highlight top
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0x40000000); // shadow bottom
            GuiText.drawCenteredClamped(g, font, Component.translatable("stardewcraft.gil.claim"),
                btnX + btnW / 2, btnY + 4, btnW - 6, COL_CLAIM_TEXT, false);
        } else {
            // Reward preview text
            if (goal.hasReward()) {
                g.drawString(font, "\u2605", rightEdge - font.width("\u2605"), rowY + 10, COL_REWARD);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clipTop = boxY + 52 + font.lineHeight + 18;
            int clipBottom = boxY + boxH - 24;
            int rowStartY = clipTop - scrollOffset;

            for (int i = 0; i < goals.size(); i++) {
                OpenGilGoalsPayload.GoalEntry goal = goals.get(i);
                if (goal.claimed() || goal.currentKills() < goal.requiredKills()) continue;

                int rowY = rowStartY + i * ROW_HEIGHT;
                if (rowY < clipTop || rowY + ROW_HEIGHT > clipBottom) continue;

                int rightEdge = boxX + boxW - INNER_PAD - 8;
                int leftX = boxX + INNER_PAD + 8;
                int barX = leftX;
                int btnW = Math.min(font.width(Component.translatable("stardewcraft.gil.claim").getString()) + 12,
                    Math.max(40, rightEdge - (barX + BAR_WIDTH + 50)));
                int btnX = rightEdge - btnW;
                int btnY = rowY + 8;

                if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + 16) {
                    PacketDistributor.sendToServer(new GilClaimRewardPayload(goal.goalKey()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 16)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
