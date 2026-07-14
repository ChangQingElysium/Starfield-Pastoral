package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.FarmAdminPayload;
import com.stardew.craft.network.payload.FarmAdminSyncPayload;
import com.stardew.craft.network.payload.FarmAdminSyncPayload.FarmEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * 星露谷管理面板 — 干净无贴图风格。
 * 通过 /stardew admin 或 FarmAdminSyncPayload 打开。
 */
@SuppressWarnings("null")
public class FarmAdminScreen extends Screen {

    // ── 颜色 ──
    private static final int BG_TINT       = 0xCC000000; // 深色背景遮罩
    private static final int PANEL_BG      = 0xF0181818; // 面板背景
    private static final int PANEL_BORDER  = 0xFF3A3A3A; // 面板边框
    private static final int HEADER_BG     = 0xFF202020;
    private static final int COL_TITLE     = 0xFF55AAFF; // 标题蓝
    private static final int COL_LABEL     = 0xFF999999; // 表头灰
    private static final int COL_TEXT      = 0xFFE0E0E0; // 正文白
    private static final int COL_DETAIL    = 0xFF777777; // 细节灰
    private static final int COL_ONLINE    = 0xFF66FF66; // 在线绿
    private static final int COL_OFFLINE   = 0xFFFF6666; // 离线红
    private static final int COL_FARM_NAME = 0xFFFFD54F; // 农场名金
    private static final int COL_TYPE      = 0xFFBB86FC; // 类型紫

    private static final int ROW_EVEN      = 0x18FFFFFF;
    private static final int ROW_ODD       = 0x0CFFFFFF;
    private static final int ROW_HOVER     = 0x30FFFFFF;
    private static final int ROW_SELECTED  = 0x3055AAFF;

    private static final int BTN_BG        = 0xFF2A2A2A;
    private static final int BTN_HOVER     = 0xFF3A3A3A;
    private static final int BTN_DANGER    = 0xFF442222;
    private static final int BTN_DANGER_HV = 0xFF663333;
    private static final int BTN_TEXT      = 0xFFDDDDDD;

    private static final int BAR_BG        = 0xFF333333;

    // ── 布局 ──
    private static final int ROW_HEIGHT    = 44;
    private static final int HEADER_H      = 36;
    private static final int BOTTOM_BAR_H  = 48;
    private static final int PANEL_PAD     = 16;

    private List<FarmEntry> farms;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int selectedRow = -1;

    // 面板区域
    private int panelX, panelY, panelW, panelH;
    private int listTop, listBottom;

    // 底部操作
    private enum Action { NONE, RENAME, TRANSFER }
    private Action currentAction = Action.NONE;
    private FarmEntry actionTarget = null;
    private EditBox inputBox = null;

    // 行按钮区域 (每行4个按钮)
    private static final int BTN_W_SM = 32;
    private static final int BTN_W_MD = 42;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;

    public FarmAdminScreen(List<FarmEntry> farms) {
        super(Component.translatable("stardewcraft.farm.admin.title"));
        this.farms = farms;
    }

    public static void openFromPayload(FarmAdminSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FarmAdminScreen existing) {
            existing.farms = payload.entries();
            existing.scrollOffset = Math.min(existing.scrollOffset, existing.calcMaxScroll());
            existing.clearAction();
            existing.init();
        } else {
            mc.setScreen(new FarmAdminScreen(payload.entries()));
        }
    }

    private int calcMaxScroll() {
        return Math.max(0, farms.size() * ROW_HEIGHT - (listBottom - listTop));
    }

    @Override
    protected void init() {
        clearWidgets();

        // 面板尺寸：宽度最大720，高度占满但留边
        panelW = Math.min(720, width - 32);
        panelH = Math.min(height - 24, 600);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        listTop = panelY + HEADER_H;
        listBottom = panelY + panelH - (currentAction != Action.NONE ? BOTTOM_BAR_H : 0);
        maxScroll = calcMaxScroll();
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // 底部输入框
        if (currentAction != Action.NONE && actionTarget != null) {
            int inputW = Math.min(220, panelW / 2);
            int inputX = panelX + PANEL_PAD + 100;
            int inputY = listBottom + (BOTTOM_BAR_H - 20) / 2;
            inputBox = new EditBox(this.font, inputX, inputY, inputW, 18,
                    Component.translatable(currentAction == Action.RENAME
                            ? "stardewcraft.farm.admin.new_name"
                            : "stardewcraft.farm.admin.target_player"));
            inputBox.setTextShadow(false);
            inputBox.setMaxLength(48);
            if (currentAction == Action.RENAME) {
                inputBox.setValue(actionTarget.farmName());
            }
            inputBox.setFocused(true);
            addRenderableWidget(inputBox);
        }
    }

    private void clearAction() {
        currentAction = Action.NONE;
        actionTarget = null;
        inputBox = null;
        selectedRow = -1;
    }

    // ── 禁用 MC 默认背景（避免 1.21 二次模糊） ──
    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 空 — 自己画
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 暗色全屏遮罩
        g.fill(0, 0, width, height, BG_TINT);

        // 面板背景
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);

        // ── 表头 ──
        g.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, HEADER_BG);
        // 标题
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.title").withStyle(style -> style.withBold(true)),
                panelX + PANEL_PAD, panelY + 6, COL_TITLE, false);
        // 农场数
        Component countStr = Component.translatable("stardewcraft.farm.admin.count", farms.size());
        g.drawString(font, countStr, panelX + panelW - PANEL_PAD - font.width(countStr),
                panelY + 6, COL_DETAIL, false);

        // 列标题
        int colY = panelY + 22;
        int col0 = panelX + PANEL_PAD;
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.column.status"), col0, colY, COL_LABEL, false);
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.column.player"), col0 + 30, colY, COL_LABEL, false);
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.column.name"), col0 + 150, colY, COL_LABEL, false);
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.column.type"), col0 + 300, colY, COL_LABEL, false);
        g.drawString(font, Component.translatable("stardewcraft.farm.admin.column.actions"),
                panelX + panelW - PANEL_PAD - 160, colY, COL_LABEL, false);

        // 表头下分隔线
        g.fill(panelX + 4, listTop - 1, panelX + panelW - 4, listTop, 0xFF333333);

        // ── 列表区 ──
        g.enableScissor(panelX, listTop, panelX + panelW, listBottom);

        for (int i = 0; i < farms.size(); i++) {
            FarmEntry farm = farms.get(i);
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;

            // 行背景
            boolean isHovered = mouseX >= panelX && mouseX < panelX + panelW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= listTop && mouseY < listBottom;
            boolean isSelected = (selectedRow == i);
            int rowBg;
            if (isSelected) rowBg = ROW_SELECTED;
            else if (isHovered) rowBg = ROW_HOVER;
            else rowBg = (i % 2 == 0) ? ROW_EVEN : ROW_ODD;

            g.fill(panelX + 1, rowY, panelX + panelW - 1, rowY + ROW_HEIGHT - 1, rowBg);

            int textX = panelX + PANEL_PAD;
            int lineY1 = rowY + 6;
            int lineY2 = rowY + 22;

            // 状态点
            g.drawString(font, farm.initialized() ? "●" : "○", textX + 4, lineY1,
                    farm.initialized() ? COL_ONLINE : COL_OFFLINE, false);

            // 玩家名
            g.drawString(font, truncate(farm.ownerName(), 18), textX + 30, lineY1, COL_TEXT, false);

            // 农场名
            g.drawString(font, truncate(farm.farmName(), 20), textX + 150, lineY1, COL_FARM_NAME, false);

            // 类型
            g.drawString(font, Component.translatable("gui.stardewcraft.farm_type." + farm.farmType() + ".name"),
                    textX + 300, lineY1, COL_TYPE, false);

            // 详情行
            Component memberText = farm.memberCount() > 1
                    ? Component.translatable("stardewcraft.farm.admin.member_count", farm.memberCount(), 4)
                    : Component.empty();
            Component detailText = Component.translatable("stardewcraft.farm.admin.details",
                    farm.slotIndex(),
                    farm.ownerUUID().toString().substring(0, 8),
                    farm.lastOnlineDay(),
                    farm.lastOnlineSeason(),
                    farm.origin().toShortString(),
                    memberText);
            g.drawString(font, detailText, textX + 30, lineY2, COL_DETAIL, false);

            // ── 行内按钮 ──
            int btnAreaX = panelX + panelW - PANEL_PAD - (BTN_W_SM + BTN_W_MD * 2 + BTN_W_MD + BTN_GAP * 3);
            int btnY = rowY + (ROW_HEIGHT - BTN_H) / 2;

            drawButton(g, Component.translatable("stardewcraft.farm.admin.teleport"), btnAreaX, btnY, BTN_W_SM, mouseX, mouseY, false);
            btnAreaX += BTN_W_SM + BTN_GAP;
            drawButton(g, Component.translatable("stardewcraft.farm.admin.rename"), btnAreaX, btnY, BTN_W_MD, mouseX, mouseY, false);
            btnAreaX += BTN_W_MD + BTN_GAP;
            drawButton(g, Component.translatable("stardewcraft.farm.admin.transfer"), btnAreaX, btnY, BTN_W_MD, mouseX, mouseY, false);
            btnAreaX += BTN_W_MD + BTN_GAP;
            drawButton(g, Component.translatable("stardewcraft.farm.admin.delete"), btnAreaX, btnY, BTN_W_MD, mouseX, mouseY, true);
        }
        g.disableScissor();

        // ── 滚动条 ──
        if (maxScroll > 0) {
            int barX = panelX + panelW - 5;
            int barH = listBottom - listTop;
            int thumbH = Math.max(16, barH * barH / (farms.size() * ROW_HEIGHT));
            int thumbY = listTop + (int) ((float) scrollOffset / maxScroll * (barH - thumbH));
            g.fill(barX, listTop, barX + 4, listBottom, BAR_BG);
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFF666666);
        }

        // ── 底部操作栏 ──
        if (currentAction != Action.NONE && actionTarget != null) {
            int barY = listBottom;
            g.fill(panelX, barY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);
            g.fill(panelX + 4, barY, panelX + panelW - 4, barY + 1, 0xFF333333);

            Component label = Component.translatable(currentAction == Action.RENAME
                    ? "stardewcraft.farm.admin.rename_target"
                    : "stardewcraft.farm.admin.transfer_target", actionTarget.ownerName());
            g.drawString(font, label, panelX + PANEL_PAD, barY + (BOTTOM_BAR_H - 8) / 2, COL_FARM_NAME, false);

            // 确认 / 取消按钮
            int confirmX = inputBox != null ? inputBox.getX() + inputBox.getWidth() + 8 : panelX + panelW - 120;
            int cancelX = confirmX + 50;
            int btnBY = barY + (BOTTOM_BAR_H - BTN_H) / 2;
            drawButton(g, Component.translatable("stardewcraft.dialog.confirm"), confirmX, btnBY, 44, mouseX, mouseY, false);
            drawButton(g, Component.translatable("stardewcraft.dialog.cancel"), cancelX, btnBY, 44, mouseX, mouseY, false);
        }

        // ── 渲染 widgets（EditBox 等）——直接渲染，不调用 super.render() ──
        for (var widget : this.renderables) {
            widget.render(g, mouseX, mouseY, partialTick);
        }
    }

    /** 绘制一个简单的小按钮（纯色） */
    private void drawButton(GuiGraphics g, Component text, int x, int y, int w, int mx, int my, boolean danger) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + BTN_H;
        int bg = danger ? (hovered ? BTN_DANGER_HV : BTN_DANGER) : (hovered ? BTN_HOVER : BTN_BG);
        g.fill(x, y, x + w, y + BTN_H, 0xFF000000); // 外框
        g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, bg);
        int textW = font.width(text);
        g.drawString(font, text, x + (w - textW) / 2, y + (BTN_H - 8) / 2, BTN_TEXT, false);
    }

    // ── 事件处理 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        // 底部操作栏按钮
        if (currentAction != Action.NONE && actionTarget != null && inputBox != null) {
            int confirmX = inputBox.getX() + inputBox.getWidth() + 8;
            int cancelX = confirmX + 50;
            int btnBY = listBottom + (BOTTOM_BAR_H - BTN_H) / 2;

            if (isInside(mx, my, confirmX, btnBY, 44, BTN_H)) {
                doConfirmAction();
                return true;
            }
            if (isInside(mx, my, cancelX, btnBY, 44, BTN_H)) {
                clearAction();
                init();
                return true;
            }
        }

        // 列表行内按钮
        for (int i = 0; i < farms.size(); i++) {
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;

            int btnAreaX = panelX + panelW - PANEL_PAD - (BTN_W_SM + BTN_W_MD * 2 + BTN_W_MD + BTN_GAP * 3);
            int btnY = rowY + (ROW_HEIGHT - BTN_H) / 2;
            FarmEntry farm = farms.get(i);

            // TP
            if (isInside(mx, my, btnAreaX, btnY, BTN_W_SM, BTN_H) && my >= listTop && my < listBottom) {
                PacketDistributor.sendToServer(new FarmAdminPayload(3, farm.ownerUUID(), ""));
                onClose();
                return true;
            }
            btnAreaX += BTN_W_SM + BTN_GAP;

            // 改名
            if (isInside(mx, my, btnAreaX, btnY, BTN_W_MD, BTN_H) && my >= listTop && my < listBottom) {
                startAction(Action.RENAME, farm, i);
                return true;
            }
            btnAreaX += BTN_W_MD + BTN_GAP;

            // 转移
            if (isInside(mx, my, btnAreaX, btnY, BTN_W_MD, BTN_H) && my >= listTop && my < listBottom) {
                startAction(Action.TRANSFER, farm, i);
                return true;
            }
            btnAreaX += BTN_W_MD + BTN_GAP;

            // 删除
            if (isInside(mx, my, btnAreaX, btnY, BTN_W_MD, BTN_H) && my >= listTop && my < listBottom) {
                PacketDistributor.sendToServer(new FarmAdminPayload(1, farm.ownerUUID(), ""));
                return true;
            }
        }

        // 让 EditBox 等处理点击
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void startAction(Action action, FarmEntry farm, int row) {
        currentAction = action;
        actionTarget = farm;
        selectedRow = row;
        init();
    }

    private void doConfirmAction() {
        if (actionTarget == null || inputBox == null) return;
        String val = inputBox.getValue().trim();
        if (val.isEmpty()) return;
        int actionId = currentAction == Action.RENAME ? 2 : 4;
        PacketDistributor.sendToServer(new FarmAdminPayload(actionId, actionTarget.ownerUUID(), val));
        clearAction();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int oldOffset = scrollOffset;
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 20));
        if (scrollOffset != oldOffset) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter → 确认
        if (keyCode == 257 && currentAction != Action.NONE && inputBox != null && inputBox.isFocused()) {
            doConfirmAction();
            return true;
        }
        // Escape → 取消操作 或 关闭
        if (keyCode == 256) {
            if (currentAction != Action.NONE) {
                clearAction();
                init();
                return true;
            }
            onClose();
            return true;
        }
        // R → 刷新
        if (keyCode == 82 && currentAction == Action.NONE) {
            PacketDistributor.sendToServer(new FarmAdminPayload(0, new UUID(0, 0), ""));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
