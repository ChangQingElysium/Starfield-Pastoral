package com.stardew.craft.client.gui.quest;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.client.gui.specialorder.ClientSpecialOrderBoardData;
import com.stardew.craft.client.gui.specialorder.SpecialOrderQuestView;
import com.stardew.craft.client.hud.QuestIconHud;
import com.stardew.craft.network.payload.SpecialOrderRewardClaimPayload;
import com.stardew.craft.quest.StardewQuest;
import com.stardew.craft.quest.network.ClientQuestData;
import com.stardew.craft.quest.network.CancelQuestPayload;
import com.stardew.craft.quest.network.ClaimRewardPayload;
import com.stardew.craft.quest.network.MarkQuestViewedPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * SDV QuestLog.cs 的 1:1 复刻。
 *
 * 核心映射规则（与项目 StardewRenderMapping 一致）:
 *   SDV 屏幕像素 → MC GUI 坐标: mapping.ui(N) = round(N / guiScale)
 *   SDV 4× atlas 缩放 → MC:      s4 = mapping.s4() = 4.0f / guiScale
 *
 * s4 仅用于 drawFromCursors() 的缩放参数（将 atlas 源像素放大到 GUI 空间）。
 * 所有位置、尺寸偏移量统一使用 mapping.ui()。
 */
@SuppressWarnings("null")
public class QuestLogScreen extends Screen {

    private static final int SDV_WIDTH = 832;
    private static final int SDV_HEIGHT = 576;
    private static final int QUESTS_PER_PAGE = 6;

    private static final int SBAR_H = 10;

    private static final int TEXT_COLOR = 0xFF222222;
    private static final int DARK_BLUE = 0xFF00008B;

    // ─── 布局（由 init() 计算） ───
    private StardewRenderMapping mapping;
    private float s4;
    private int winX, winY, winW, winH;
    private int entryX, entryW;
    private final int[] entryY = new int[QUESTS_PER_PAGE];
    private int entryH;
    private int closeX, closeY, closeSz;
    private int backX, backY, backSzW, backSzH;
    private int fwdX, fwdY, fwdSzW, fwdSzH;
    private int cancelX, cancelY, cancelSz;
    private int scrollBarOuterX;
    private int upArrowY, dnArrowY;
    private int scrollTrackTop, scrollTrackBottom;

    // ─── 状态 ───
    private final List<List<StardewQuest>> pages = new ArrayList<>();
    private int currentPage;
    private int questPage = -1;
    private StardewQuest shownQuest;
    // 动态生成：每次 render 都从 quest 里重新拉，反映最新进度
    private List<Component> objectiveText;
    private float scrollAmount;
    private float contentHeight;
    private float scissorRectHeight;
    private int lastRewardBoxY = -1;

    public QuestLogScreen() {
        super(Component.translatable("gui.stardewcraft.quest_log"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        QuestIconHud.dismissQuestPing();

        float guiScale = (float) minecraft.getWindow().getGuiScale();
        mapping = new StardewRenderMapping(width, height, guiScale);
        s4 = mapping.s4();

        // SDV: base(viewport.W/2 - 832/2, viewport.H/2 - 576/2 + 32, 832, 576)
        winW = mapping.ui(SDV_WIDTH);
        winH = mapping.ui(SDV_HEIGHT);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2 + mapping.ui(32);

        // SDV: questLogButtons[i] = Rectangle(xPos+16, yPos+16+i*((h-32)/6), w-32, (h-32)/6+4)
        entryX = winX + mapping.ui(16);
        entryW = winW - mapping.ui(32);
        int entrySlotH = (winH - mapping.ui(32)) / QUESTS_PER_PAGE;
        entryH = entrySlotH + mapping.ui(4);
        for (int i = 0; i < QUESTS_PER_PAGE; i++) {
            entryY[i] = winY + mapping.ui(16) + i * entrySlotH;
        }

        // SDV: upperRightCloseButton = Rectangle(xPos+w-20, yPos-8, 48, 48)
        closeSz = mapping.ui(48);
        closeX = winX + winW - mapping.ui(20);
        closeY = winY - mapping.ui(8);

        // SDV: backButton = Rectangle(xPos-64, yPos+8, 48, 44)
        backSzW = mapping.ui(48);
        backSzH = mapping.ui(44);
        backX = winX - mapping.ui(64);
        backY = winY + mapping.ui(8);

        // SDV: forwardButton = Rectangle(xPos+w+64-48, yPos+h-48, 48, 44)
        fwdSzW = mapping.ui(48);
        fwdSzH = mapping.ui(44);
        fwdX = winX + winW + mapping.ui(64) - fwdSzW;
        fwdY = winY + winH - mapping.ui(48);

        // SDV: cancelQuestButton = Rectangle(xPos+4, yPos+h+4, 48, 48)
        cancelSz = mapping.ui(48);
        cancelX = winX + mapping.ui(4);
        cancelY = winY + winH + mapping.ui(4);

        // SDV: scrollbar_x = xPos+w+16
        // upArrow = Rectangle(scrollbar_x, yPos+96, 44, 48)
        // downArrow = Rectangle(scrollbar_x, yPos+h-64, 44, 48)
        scrollBarOuterX = winX + winW + mapping.ui(16);
        upArrowY = winY + mapping.ui(96);
        dnArrowY = winY + winH - mapping.ui(64);
        // SDV: scrollBar.Y = upArrow.Y + upArrow.H + 4 = upArrowY + 48 + 4 = upArrowY + 52
        scrollTrackTop = upArrowY + mapping.ui(52);
        scrollTrackBottom = dnArrowY - mapping.ui(4);

        playSound(ModSounds.BIG_SELECT);
        paginateQuests();
    }

    private void paginateQuests() {
        pages.clear();
        List<StardewQuest> all = new ArrayList<>();
        all.addAll(ClientSpecialOrderBoardData.activeQuestLogEntries());
        ClientQuestData.getQuestLog().stream()
                .filter(quest -> !quest.isSecretQuest())
                .forEach(all::add);
        int idx = 0;
        while (idx < all.size()) {
            List<StardewQuest> page = new ArrayList<>();
            for (int i = 0; i < QUESTS_PER_PAGE && idx < all.size(); i++, idx++) {
                StardewQuest q = all.get(idx);
                if (!q.isDestroy()) page.add(q);
            }
            pages.add(page);
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
        currentPage = Math.min(currentPage, pages.size() - 1);
        questPage = -1;
        shownQuest = null;
    }

    // ─── 渲染 ───

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xBF000000);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        if (questPage == -1) {
            renderListPage(g, mouseX, mouseY);
        } else {
            renderDetailPage(g, mouseX, mouseY);
        }

        // 滚动条（详情页需滚动时）
        if (needsScroll()) {
            CommonGuiTextures.drawScrollArrowUp(g, scrollBarOuterX, upArrowY, s4);
            CommonGuiTextures.drawScrollArrowDown(g, scrollBarOuterX, dnArrowY, s4);
            int barH = Math.round(SBAR_H * s4);
            float maxScroll = Math.max(1, contentHeight - scissorRectHeight);
            float frac = scrollAmount / maxScroll;
            int barY = scrollTrackTop + Math.round((scrollTrackBottom - scrollTrackTop - barH) * frac);
            // SDV: scrollBar.X = upArrow.X + 12
            int barX = scrollBarOuterX + mapping.ui(12);
            CommonGuiTextures.drawScrollBarThumb(g, barX, barY, s4);
        }

        // 前进/后退箭头
        if (questPage == -1 && currentPage < pages.size() - 1) {
            CommonGuiTextures.drawForwardArrow(g, fwdX, fwdY, s4);
        }
        if (currentPage > 0 || questPage != -1) {
            CommonGuiTextures.drawBackArrow(g, backX, backY, s4);
        }

        // 关闭按钮
        CommonGuiTextures.drawCloseButton(g, closeX, closeY, s4);
    }

    private void renderListPage(GuiGraphics g, int mouseX, int mouseY) {
        // SDV: drawTextureBox (384,373,18,18) 4×
        CommonGuiTextures.drawTextureBox(g, winX, winY, winW, winH, s4, true);

        // SDV: SpriteText.drawStringWithScrollCenteredAt at (xPos+w/2, yPos-64)
        Component title = Component.translatable("gui.stardewcraft.quest_log");
        GuiText.drawCenteredClamped(g, font, title, winX + winW / 2,
                winY - mapping.ui(64), winW - mapping.ui(64), 0xFFFFFFFF, false);

        if (pages.get(currentPage).isEmpty()) {
            Component empty = Component.translatable("gui.stardewcraft.quest_log.empty");
            GuiText.drawCenteredClamped(g, font, empty, winX + winW / 2,
                    winY + winH / 2, winW - mapping.ui(96), 0xFFAAAAAA, false);
            return;
        }

        for (int i = 0; i < QUESTS_PER_PAGE; i++) {
            if (pages.get(currentPage).size() <= i) break;
            StardewQuest q = pages.get(currentPage).get(i);

            int ex = entryX;
            int ey = entryY[i];
            boolean hovered = isIn(mouseX, mouseY, ex, ey, entryW, entryH);

            // SDV: drawTextureBox (384,396,15,15), hover → Color.Wheat
                CommonGuiTextures.drawEntryBox(g, ex, ey, entryW, entryH, s4, false);
            if (hovered) {
                g.fill(ex, ey, ex + entryW, ey + entryH, 0x30F5DEB3);
            }

            // SDV: new/complete marker or dot/timed icon
            if (q.shouldDisplayAsNew() || q.shouldDisplayAsComplete()) {
                // SDV: at (bounds.X+68, bounds.Y+44) with origin(11,4) at 4×
                // Visual top-left = (bounds.X+68 - 11*4, bounds.Y+44 - 4*4)
                //                 = (bounds.X+24, bounds.Y+28)
                int markX = ex + mapping.ui(24);
                int markY = ey + mapping.ui(28);
                if (q.shouldDisplayAsComplete()) {
                    CommonGuiTextures.drawQuestDone(g, markX, markY, s4);
                } else {
                    CommonGuiTextures.drawQuestNew(g, markX, markY, s4);
                }
            } else {
                if (q.isTimedQuest()) {
                    CommonGuiTextures.drawQuestTimed(g, ex + mapping.ui(32), ey + mapping.ui(28), s4);
                } else {
                    CommonGuiTextures.drawQuestDot(g, ex + mapping.ui(32), ey + mapping.ui(28), s4);
                }
            }

            // SDV: SpriteText.drawString at (bounds.X+132, bounds.Y+20)
                g.drawString(font, GuiText.ellipsize(font, q.getTitleComponent(), entryW - mapping.ui(164)),
                    ex + mapping.ui(132), ey + mapping.ui(20), TEXT_COLOR, false);
        }
    }

    private void renderDetailPage(GuiGraphics g, int mouseX, int mouseY) {
        if (shownQuest == null) return;

        // SDV: 背景框
        CommonGuiTextures.drawTextureBox(g, winX, winY, winW, winH, s4, true);

        // SDV: 标题居中 at (xPos+w/2, yPos+32)
        Component questTitleComp = shownQuest.getTitleComponent();
        String questTitle = questTitleComp.getString();
        GuiText.drawCenteredClamped(g, font, questTitleComp,
            winX + winW / 2, winY + mapping.ui(32), winW - mapping.ui(128), TEXT_COLOR, false);

        // SDV: 计时任务时钟图标 + 剩余天数
        int extraYOffset = 0;
        if (shownQuest.isTimedQuest() && shownQuest.getDaysLeft() > 0) {
            int titleWidth = font.width(questTitle);
            int xOffset = 0;
            if (titleWidth > winW / 2) {
                xOffset = mapping.ui(28);
                extraYOffset = mapping.ui(48);
            }
            // SDV: clock at (xPos+xOffset+32, yPos+48-8+extraY) = (xPos+xOffset+32, yPos+40+extraY)
            int clockX = winX + xOffset + mapping.ui(32);
            int clockY = winY + mapping.ui(40) + extraYOffset;
            CommonGuiTextures.drawQuestTimed(g, clockX, clockY, s4);
            // SDV: text at (xPos+xOffset+80, yPos+40+extraY)
            String daysText = Component.translatable("gui.stardewcraft.quest_log.days_left",
                    shownQuest.getDaysLeft()).getString();
            g.drawString(font, daysText, winX + xOffset + mapping.ui(80), clockY, TEXT_COLOR, false);
        }

        // ─── Scissor 区域 ───
        // SDV: scissor.X = xPos+32, scissor.Y = yPos+96+extraY
        //      scissor.Height = yPos+h-32 - scissor.Y, scissor.Width = w-64
        int scissorX = winX + mapping.ui(32);
        int scissorY = winY + mapping.ui(96) + extraYOffset;
        int scissorW = winW - mapping.ui(64);
        int scissorBottom = winY + winH - mapping.ui(32);
        scissorRectHeight = scissorBottom - scissorY;

        g.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorBottom);

        // SDV: description at (xPos+64, yPos+96+extraY-scroll)
        int contentX = winX + mapping.ui(64);
        int descWidth = winW - mapping.ui(128);
        float yPos = scissorY - scrollAmount + mapping.ui(4);

        List<FormattedCharSequence> descLines = font.split(
                shownQuest.getDescriptionComponent(), descWidth);
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, contentX, (int) yPos, TEXT_COLOR, false);
            yPos += font.lineHeight + 1;
        }

        yPos += mapping.ui(32);

        // SDV: 完成状态 → 奖励区
        if (shownQuest.shouldDisplayAsComplete()) {
            g.disableScissor();

            if (shownQuest.hasMoneyReward()) {
                // SDV: rewardBox at (xPos+w/2-80, yPos+h-32-96)
                int rboxX = winX + winW / 2 - mapping.ui(80);
                int rboxY = winY + winH - mapping.ui(128);
                lastRewardBoxY = rboxY;
                int rboxSz = mapping.ui(96);

                // SDV: "Reward" label at (xPos+36, rewardBox.Y+25) — 加粗 Component 代替阴影
                Component rewardLabel = Component.translatable("gui.stardewcraft.quest_log.reward")
                    .withStyle(net.minecraft.ChatFormatting.BOLD);
                g.drawString(font, GuiText.ellipsize(font, rewardLabel, mapping.ui(220)),
                        winX + mapping.ui(36), rboxY + mapping.ui(25), TEXT_COLOR, false);

                // SDV: rewardBox.draw — (293,360,24,24) at 4×
                CommonGuiTextures.drawRewardSlot(g, rboxX, rboxY, s4);

                // SDV: coin at (rewardBox.X+16, rewardBox.Y+16) at 4×
                CommonGuiTextures.drawQuestCoin(g, rboxX + mapping.ui(16), rboxY + mapping.ui(16), s4);
                // SDV: money text at (xPos+448, rewardBox.Y+25)
                g.drawString(font, shownQuest.getMoneyReward() + "g",
                        winX + mapping.ui(448), rboxY + mapping.ui(25), 0xFF2C6E0F, false);

                if (isIn(mouseX, mouseY, rboxX, rboxY, rboxSz, rboxSz)) {
                    Component collectTip = Component.translatable("gui.stardewcraft.quest_log.collect")
                        .withStyle(net.minecraft.ChatFormatting.BOLD);
                    GuiText.drawCenteredClamped(g, font, collectTip,
                            winX + winW / 2, rboxY + rboxSz + mapping.ui(8), winW - mapping.ui(128), 0xFFFFD700, false);
                }
            }
        } else {
            // SDV: Objectives — 每帧重新获取，反映最新 N/M 进度
            List<Component> liveObjectives = shownQuest != null
                    ? shownQuest.getObjectiveComponents()
                    : objectiveText;
            if (liveObjectives != null) {
                for (int j = 0; j < liveObjectives.size(); j++) {
                    Component objComp = liveObjectives.get(j);
                    int objTextWidth = descWidth - mapping.ui(64);
                    List<FormattedCharSequence> objLines = font.split(
                            objComp, objTextWidth);
                    boolean specialComplete = shownQuest instanceof SpecialOrderQuestView specialOrder
                            && specialOrder.isObjectiveComplete(j);
                    int objectiveColor = specialComplete ? 0xFF7B6A58 : DARK_BLUE;

                    // SDV: arrow at (xPos+96+8*dialogueButtonScale/10, yPos) rotated PI/2
                    // We skip the oscillation; draw rotated arrow.
                    if (!specialComplete) {
                        int arrowX = winX + mapping.ui(96);
                        g.pose().pushPose();
                        g.pose().translate(arrowX, yPos + 2, 0);
                        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
                        g.pose().scale(s4, s4, 1.0f);
                        CommonGuiTextures.drawQuestObjectiveArrowAtCurrentPose(g, 0, 0);
                        g.pose().popPose();
                    }

                    // SDV: text at (xPos+128, yPos-8)
                    int objTextX = winX + mapping.ui(128);
                    for (FormattedCharSequence objLine : objLines) {
                        g.drawString(font, objLine, objTextX, (int) yPos, objectiveColor, false);
                        yPos += font.lineHeight + 1;
                    }
                    if (shownQuest instanceof SpecialOrderQuestView specialOrder
                            && specialOrder.shouldShowProgress(j)) {
                        yPos = drawSpecialOrderProgress(g, specialOrder, j, yPos);
                    }
                    yPos += mapping.ui(8);
                }
            }

            // 进度计数
            int curCount = shownQuest.getCurrentObjectiveCount();
            int totalCount = shownQuest.getTotalObjectiveCount();
            if (!(shownQuest instanceof SpecialOrderQuestView) && totalCount > 0 && curCount >= 0) {
                String countStr = curCount + "/" + totalCount;
                int countX = winX + winW - mapping.ui(64) - font.width(countStr);
                g.drawString(font, countStr, countX, (int) yPos, DARK_BLUE, false);
                yPos += font.lineHeight + mapping.ui(8);
            }

            contentHeight = yPos + scrollAmount - scissorY;
            g.disableScissor();

            // SDV: cancelQuestButton
            if (shownQuest.isCanBeCancelled() && !shownQuest.isCompleted()) {
                CommonGuiTextures.drawSmallCancelButton(g, cancelX, cancelY, s4);
            }

            // 滚动边缘渐隐
            if (needsScroll()) {
                if (scrollAmount > 0) {
                    g.fill(scissorX, scissorY, scissorX + scissorW, scissorY + 4, 0x26000000);
                }
                if (scrollAmount < contentHeight - scissorRectHeight) {
                    g.fill(scissorX, scissorBottom - 4, scissorX + scissorW, scissorBottom, 0x26000000);
                }
            }
        }
    }

    private float drawSpecialOrderProgress(GuiGraphics g, SpecialOrderQuestView order, int objectiveIndex, float yPos) {
        int current = order.objectiveProgress(objectiveIndex);
        int max = Math.max(1, order.objectiveRequired(objectiveIndex));
        String countText = current + "/" + max;
        String maxText = max + "/" + max;
        int inset = mapping.ui(64);
        int countDrawWidth = mapping.ui(160);
        int countWidth = font.width(countText);
        int maxTextWidth = font.width(maxText);
        int countX = winX + winW - inset - countWidth;
        int maxTextX = winX + winW - inset - maxTextWidth;
        g.drawString(font, countText, countX, (int) yPos, DARK_BLUE, false);

        int barX = winX + inset;
        int barY = (int) yPos;
        int barW = winW - inset * 2 - countDrawWidth;
        int barH = Math.round(12 * s4);
        if (barX + barW > maxTextX - mapping.ui(16)) {
            barW -= (barX + barW) - (maxTextX - mapping.ui(16));
        }
        barW = Math.max(mapping.ui(48), barW);
        CommonGuiTextures.drawQuestProgressBarBackground(g, barX, barY, barW, s4);

        int notches = Math.min(4, max);
        for (int n = 1; n < notches; n++) {
            int notchX = barX + Math.round(barW * (n / (float) notches));
            CommonGuiTextures.drawQuestProgressNotch(g, notchX, barY, s4);
        }

        int paddingX = Math.round(3 * s4);
        int paddingY = Math.round(3 * s4);
        int innerX = barX + paddingX;
        int innerY = barY + paddingY;
        int innerW = Math.max(1, barW - paddingX * 2);
        int innerH = Math.max(1, barH - paddingY * 2);
        float progress = Math.max(0.0F, Math.min(1.0F, current / (float) max));
        int fillW = Math.max(0, Math.round(innerW * progress) - mapping.ui(4));
        int stripW = Math.min(mapping.ui(4), Math.max(0, innerW - fillW));
        int barColor = current >= max ? 0xFF32CD32 : 0xFFFF0000;
        int darkBarColor = current >= max ? 0xFF008000 : 0xFF8B0000;
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + innerH, barColor);
        }
        if (stripW > 0 && fillW > 0) {
            g.fill(innerX + fillW, innerY, innerX + fillW + stripW, innerY + innerH, darkBarColor);
        }
        return yPos + Math.round((12 + 4) * s4);
    }

    private boolean needsScroll() {
        if (shownQuest != null && shownQuest.shouldDisplayAsComplete()) return false;
        return questPage != -1 && contentHeight > scissorRectHeight;
    }

    // ─── 输入处理 ───

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // Close button
        if (isIn(mx, my, closeX, closeY, closeSz, closeSz)) {
            playSound(ModSounds.BIG_DESELECT);
            onClose();
            return true;
        }

        if (questPage == -1) {
            // ─── 列表页 ───
            for (int i = 0; i < QUESTS_PER_PAGE; i++) {
                if (pages.get(currentPage).size() <= i) break;
                if (isIn(mx, my, entryX, entryY[i], entryW, entryH)) {
                    playSound(ModSounds.SMALL_SELECT);
                    questPage = i;
                    shownQuest = pages.get(currentPage).get(i);
                    objectiveText = shownQuest.getObjectiveComponents();
                    if (shownQuest instanceof SpecialOrderQuestView) {
                        ClientSpecialOrderBoardData.markViewed(shownQuest.getId());
                    } else {
                        shownQuest.setShowNew(false);
                        // 通知服务端清除 showNew 标记，避免下次同步覆盖
                        PacketDistributor.sendToServer(new MarkQuestViewedPayload(shownQuest.getId()));
                    }
                    scrollAmount = 0;
                    return true;
                }
            }

            // SDV: forwardButton
            if (currentPage < pages.size() - 1 && isIn(mx, my, fwdX, fwdY, fwdSzW, fwdSzH)) {
                playSound(ModSounds.SHWIP);
                currentPage++;
                return true;
            }
            // SDV: backButton
            if (currentPage > 0 && isIn(mx, my, backX, backY, backSzW, backSzH)) {
                playSound(ModSounds.SHWIP);
                currentPage--;
                return true;
            }

            // SDV: 点击窗口外关闭 + "bigDeSelect"
            playSound(ModSounds.BIG_DESELECT);
            onClose();
            return true;
        } else {
            // ─── 详情页 ───
            StardewQuest q = shownQuest;
            if (q == null) return false;

            // 奖励领取 — SDV: "purchaseRepeat" + OnMoneyRewardClaimed()
            if (q.shouldDisplayAsComplete() && q.hasMoneyReward() && lastRewardBoxY >= 0) {
                int rboxX = winX + winW / 2 - mapping.ui(80);
                int rboxSz = mapping.ui(96);
                if (isIn(mx, my, rboxX, lastRewardBoxY, rboxSz, rboxSz)) {
                    playSound(ModSounds.PURCHASE_REPEAT);
                    if (q instanceof SpecialOrderQuestView) {
                        PacketDistributor.sendToServer(new SpecialOrderRewardClaimPayload(q.getId()));
                        q.setMoneyReward(0);
                    } else {
                        PacketDistributor.sendToServer(new ClaimRewardPayload(q.getId()));
                        // SDV OnMoneyRewardClaimed: 客户端立即更新状态，不等服务端同步
                        q.setMoneyReward(0);
                        q.setDestroy(true);
                    }
                    // SDV 不在此处 exitQuestPage，而是等玩家手动返回
                    // 但由于奖励已清零，UI 会自动不再显示奖励框
                    return true;
                }
            }

            // 取消任务 — SDV: "trashcan"
            if (q.isCanBeCancelled() && !q.isCompleted()
                    && isIn(mx, my, cancelX, cancelY, cancelSz, cancelSz)) {
                playSound(ModSounds.TRASHCAN);
                PacketDistributor.sendToServer(new CancelQuestPayload(q.getId()));
                exitQuestPage();
                return true;
            }

            // 滚动条
            if (needsScroll()) {
                int arrowW = mapping.ui(44);
                int arrowH = mapping.ui(48);
                float maxScroll = contentHeight - scissorRectHeight;
                if (isIn(mx, my, scrollBarOuterX, upArrowY, arrowW, arrowH) && scrollAmount > 0) {
                    playSound(ModSounds.SHWIP);
                    scrollAmount = Math.max(0, scrollAmount - 64);
                    return true;
                }
                if (isIn(mx, my, scrollBarOuterX, dnArrowY, arrowW, mapping.ui(48))
                        && scrollAmount < maxScroll) {
                    playSound(ModSounds.SHWIP);
                    scrollAmount = Math.min(maxScroll, scrollAmount + 64);
                    return true;
                }
            }

            // 后退按钮或无滚动时点击 → 返回列表
            if (isIn(mx, my, backX, backY, backSzW, backSzH) || !needsScroll()) {
                exitQuestPage();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void exitQuestPage() {
        playSound(ModSounds.SHWIP);
        questPage = -1;
        shownQuest = null;
        scrollAmount = 0;
        paginateQuests();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (questPage >= 0) {
                exitQuestPage();
                return true;
            }
            playSound(ModSounds.BIG_DESELECT);
            onClose();
            return true;
        }
        if (com.stardew.craft.client.ModKeyMappings.QUEST_LOG.matches(keyCode, scanCode)) {
            playSound(ModSounds.BIG_DESELECT);
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (needsScroll()) {
            float maxScroll = Math.max(0, contentHeight - scissorRectHeight);
            float delta = (float) (-Math.signum(scrollY) * 32);
            scrollAmount = Math.max(0, Math.min(scrollAmount + delta, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void playSound(net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> sound) {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0f));
        }
    }

    private boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
