package com.stardew.craft.client.gui;

import com.stardew.craft.client.farm.FarmJoinClientState;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.payload.FarmJoinRequestPayload;
import com.stardew.craft.network.payload.FarmListSyncPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 加入农场选择 GUI — 从可加入的农场列表中选择一个发送加入请求。
 * 单击选中，双击发送请求。ESC 返回农场创建界面。
 */
@SuppressWarnings("null")
public class FarmJoinSelectScreen extends Screen {

    private static final int SDV_W = 900;
    private static final int SDV_H = 720;

    private final List<FarmListSyncPayload.FarmEntry> farms;
    private List<FarmListSyncPayload.FarmEntry> sortedFarms;

    // 布局
    private float guiScale;
    private int panelX, panelY, panelW, panelH;
    private int borderUnit;
    private int contentX, contentY, contentW, contentH;
    private int partY, listY, listH;
    private int rowH;
    private int scrollOffset = 0;
    private int maxVisible;
    private int selectedIndex = -1;

    // 动画
    private float[] rowHighlight;
    // 双击检测
    private long lastClickTime = 0;
    private int lastClickIndex = -1;

    public FarmJoinSelectScreen(List<FarmListSyncPayload.FarmEntry> farms) {
        super(Component.translatable("gui.stardewcraft.farm_join.title"));
        this.farms = farms;
    }

    @Override
    protected void init() {
        super.init();
        guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());

        sortedFarms = new ArrayList<>(farms);
        rowHighlight = new float[sortedFarms.size()];

        panelW = ui(SDV_W);
        panelH = ui(SDV_H);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        borderUnit = Math.max(1, Math.round(64.0f / guiScale));

        contentX = panelX + borderUnit;
        contentY = panelY + borderUnit;
        contentW = panelW - borderUnit * 2;
        contentH = panelH - borderUnit * 2;

        rowH = this.font.lineHeight * 2 + ui(24);
        int titleAreaH = ui(48);
        partY = contentY + titleAreaH + borderUnit / 2;
        listY = contentY + titleAreaH + borderUnit + Math.max(4, ui(16));
        int hintAreaH = this.font.lineHeight + ui(12);
        listH = contentY + contentH - listY - hintAreaH;
        maxVisible = Math.max(1, listH / rowH);
        scrollOffset = Math.min(scrollOffset, Math.max(0, sortedFarms.size() - maxVisible));
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / guiScale));
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    // ═══════════════════════════════════
    //  输入
    // ═══════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        long now = System.currentTimeMillis();

        for (int i = 0; i < Math.min(maxVisible, sortedFarms.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            int ry = listY + i * rowH;
            if (inside(mx, my, contentX, ry, contentW - ui(12), rowH)) {
                // 双击发送请求
                if (idx == lastClickIndex && idx == selectedIndex && (now - lastClickTime) < 400) {
                    submitJoinRequest();
                    return true;
                }
                selectedIndex = idx;
                lastClickIndex = idx;
                lastClickTime = now;
                playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, sortedFarms.size() - maxVisible);
        if (scrollY > 0 && scrollOffset > 0) scrollOffset--;
        if (scrollY < 0 && scrollOffset < maxScroll) scrollOffset++;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC — 返回农场创建界面
            this.minecraft.setScreen(new FarmSelectionScreen());
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { submitJoinRequest(); return true; }
        if (keyCode == 265 && selectedIndex > 0) { // UP
            selectedIndex--;
            ensureVisible(selectedIndex);
            playUi(ModSounds.SMALL_SELECT.get(), 0.5f, 1.1f);
            return true;
        }
        if (keyCode == 264 && selectedIndex < sortedFarms.size() - 1) { // DOWN
            selectedIndex++;
            ensureVisible(selectedIndex);
            playUi(ModSounds.SMALL_SELECT.get(), 0.5f, 1.1f);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible(int idx) {
        if (idx < scrollOffset) scrollOffset = idx;
        if (idx >= scrollOffset + maxVisible) scrollOffset = idx - maxVisible + 1;
    }

    // ═══════════════════════════════════
    //  渲染
    // ═══════════════════════════════════

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHover(mouseX, mouseY);
        this.renderTransparentBackground(graphics);

        StardewGuiUtil.drawDialogueBoxFrame(graphics, panelX, panelY, panelW, panelH);

        // 标题
        Component title = this.getTitle().copy().withStyle(ChatFormatting.BOLD);
        GuiText.drawCenteredClamped(graphics, this.font, title,
            panelX + panelW / 2, contentY + ui(12), contentW, 0x582A11, false);

        // 标题下方分隔线
        StardewGuiUtil.drawHorizontalPartition(graphics, panelX, partY, panelW, s4());

        if (sortedFarms.isEmpty()) {
            // 没有可加入的农场
            Component empty = Component.translatable("gui.stardewcraft.farm_join.empty");
                GuiText.drawWrappedCentered(graphics, this.font, empty,
                    panelX + panelW / 2, listY + ui(40), contentW, 0x9E9E9E, false, 2);
        } else {
            drawFarmList(graphics, mouseX, mouseY);
        }

        // 底部提示
        Component hint = Component.translatable("gui.stardewcraft.farm_join.hint");
        GuiText.drawCenteredClamped(graphics, this.font, hint,
            panelX + panelW / 2, contentY + contentH - ui(4) - this.font.lineHeight,
            contentW, 0x8D6E63, false);
    }

    private void drawFarmList(GuiGraphics graphics, int mouseX, int mouseY) {
        int clipBottom = Math.min(listY + maxVisible * rowH, contentY + contentH - ui(20));
        graphics.enableScissor(contentX, listY, contentX + contentW, clipBottom);

        for (int i = 0; i < Math.min(maxVisible, sortedFarms.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            FarmListSyncPayload.FarmEntry entry = sortedFarms.get(idx);
            boolean isSelected = (idx == selectedIndex);
            int ry = listY + i * rowH;

            // 选中高亮
            if (isSelected) {
                graphics.fill(contentX + ui(4), ry + 1,
                        contentX + contentW - ui(12), ry + rowH - 1, 0x44EADB8C);
            }
            // Hover
            float hi = idx < rowHighlight.length ? rowHighlight[idx] : 0;
            if (hi > 0.01f && !isSelected) {
                int alpha = (int) (hi * 0x22);
                graphics.fill(contentX + ui(4), ry + 1,
                        contentX + contentW - ui(12), ry + rowH - 1,
                        (alpha << 24) | 0xEADB8C);
            }

            // 左侧色彩条（绿色表示可加入）
            graphics.fill(contentX + ui(8), ry + ui(6),
                    contentX + ui(8) + ui(5), ry + rowH - ui(6), 0xFF4CAF50);

            // 图标
            int iconH = rowH - ui(12);
            int iconW = Math.round(iconH * 22f / 20f);
            int iconX = contentX + ui(24);
            int iconY = ry + (rowH - iconH) / 2;
            FarmType farmType = FarmType.fromId(entry.farmTypeId());
            graphics.blit(farmType.getIconTexture(), iconX, iconY, 0, 0, iconW, iconH, iconW, iconH);

            // 文字区域
            int textX = iconX + iconW + ui(14);
                int textMaxW = Math.max(1, contentX + contentW - ui(18) - textX);
            int nameY = ry + ui(6);
            int ownerY = nameY + this.font.lineHeight + ui(4);

            // 农场名（粗体）
            graphics.drawString(this.font,
                    GuiText.ellipsize(this.font, Component.literal(entry.farmName()).withStyle(ChatFormatting.BOLD), textMaxW),
                    textX, nameY, 0x582A11, false);

            // 主人名（第二行）
                graphics.drawString(this.font, GuiText.ellipsize(this.font, Component.literal(entry.ownerName()), textMaxW), textX, ownerY, 0x8D6E63, false);
        }

        graphics.disableScissor();

        // 滚动条
        if (sortedFarms.size() > maxVisible) {
            int barX = contentX + contentW - ui(6);
            int barTotalH = maxVisible * rowH;
            int thumbH = Math.max(ui(20), barTotalH * maxVisible / sortedFarms.size());
            int maxScroll = Math.max(1, sortedFarms.size() - maxVisible);
            int thumbY = listY + (barTotalH - thumbH) * scrollOffset / maxScroll;
            graphics.fill(barX, listY, barX + ui(3), listY + barTotalH, 0x22000000);
            graphics.fill(barX, thumbY, barX + ui(3), thumbY + thumbH, 0x66582A11);
        }
    }

    // ═══════════════════════════════════
    //  提交
    // ═══════════════════════════════════

    private void submitJoinRequest() {
        if (selectedIndex < 0 || selectedIndex >= sortedFarms.size()) return;
        FarmListSyncPayload.FarmEntry entry = sortedFarms.get(selectedIndex);
        FarmJoinClientState.setPendingJoinRequest(true);
        PacketDistributor.sendToServer(new FarmJoinRequestPayload(entry.ownerUUID()));
        playUi(ModSounds.NEW_RECIPE.get(), 0.88f, 1.0f);
        this.onClose();
    }

    // ═══════════════════════════════════
    //  辅助
    // ═══════════════════════════════════

    private void updateHover(int mouseX, int mouseY) {
        for (int i = 0; i < Math.min(maxVisible, sortedFarms.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            int ry = listY + i * rowH;
            boolean hovered = inside(mouseX, mouseY, contentX, ry, contentW, rowH);
            if (idx < rowHighlight.length) {
                rowHighlight[idx] = approach(rowHighlight[idx], hovered ? 1.0f : 0.0f);
            }
        }
        for (int idx = 0; idx < rowHighlight.length; idx++) {
            if (idx < scrollOffset || idx >= scrollOffset + maxVisible) {
                rowHighlight[idx] = approach(rowHighlight[idx], 0.0f);
            }
        }
    }

    private float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.08f);
        if (current > target) return Math.max(target, current - 0.08f);
        return current;
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
