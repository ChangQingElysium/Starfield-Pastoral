package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.farm.FarmJoinClientState;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.payload.FarmSelectionSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

/**
 * 农场选择和命名界面 — SDV 原版风格。
 * <p>
 * 左栏：带滚动条的农场类型列表（图标 + 名称），scissor 裁剪
 * 右栏：选中类型描述 + EditBox 名称编辑（支持中文 IME）
 * 竖向分隔线使用 drawVerticalIntersectingPartition 与标题分隔线 T 形相连
 * 不可通过 ESC 关闭——玩家必须完成选择。
 */
@SuppressWarnings({"null", "unused"})
public class FarmSelectionScreen extends Screen {

    private static final ResourceLocation DICE_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/dice_icon.png");
    private static final ResourceLocation OK_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/ok_yes_tile46.png");
    private static final ResourceLocation LOCK_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/locked.png");

    private static final int SDV_W = 1100;
    private static final int SDV_H = 880;

    // 随机农场名生成池
    private static final int FARM_NAME_PREFIX_COUNT = 16;
    private static final int FARM_NAME_SUFFIX_COUNT = 8;

    // 状态
    private final List<FarmType> farmTypes = FarmType.allTypes();
    private int selectedIndex = 0;
    private final float[] typeHighlight;
    private final Random random = new Random();

    // 滚动
    private int scrollOffset = 0;
    private int maxVisible;

    // 布局缓存
    private float guiScale;
    private int panelX, panelY, panelW, panelH;
    private int borderUnit;
    private int contentX, contentY, contentW, contentH;
    private int partY;   // 标题分隔线 Y
    private int listX, listY, listW, listH, rowH;
    private int dividerX;
    private int rightX, rightY, rightW;
    private int nameAreaY;
    private int okCx, okCy, diceCx, diceCy;

    // 动画
    private float okScale = 1.0f;
    private float diceScale = 1.0f;
    private float joinBtnScale = 1.0f;

    // "加入农场" 按钮位置
    private int joinBtnX, joinBtnY, joinBtnW, joinBtnH;

    // 名称输入 — 使用 Minecraft EditBox，支持中文 IME
    private EditBox nameField;
    private String savedName = "";

    public FarmSelectionScreen() {
        super(Component.translatable("gui.stardewcraft.farm_selection.title"));
        typeHighlight = new float[farmTypes.size()];
    }

    @Override
    protected void init() {
        super.init();
        guiScale = (float) Math.max(1, this.minecraft.getWindow().getGuiScale());

        panelW = ui(SDV_W);
        panelH = ui(SDV_H);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        borderUnit = Math.max(1, Math.round(64.0f / guiScale));

        contentX = panelX + borderUnit;
        contentY = panelY + borderUnit;
        contentW = panelW - borderUnit * 2;
        contentH = panelH - borderUnit * 2;

        // 标题分隔线
        int titleH = ui(48);
        partY = contentY + titleH;

        // 左栏宽度 ≈38%
        int leftColW = (int) (contentW * 0.38f);
        dividerX = contentX + leftColW;

        // 左列表（分隔线下方，留出 borderUnit 间距）
        listX = contentX + ui(8);
        listY = partY + borderUnit;
        listW = leftColW - ui(20);
        rowH = ui(72);
        listH = contentY + contentH - listY - ui(8);
        maxVisible = Math.max(1, listH / rowH);
        scrollOffset = Math.min(scrollOffset, Math.max(0, farmTypes.size() - maxVisible));

        // 右栏
        rightX = dividerX + borderUnit + ui(12);
        rightY = partY + borderUnit;
        rightW = contentX + contentW - rightX - ui(8);

        // 名称输入区域（右栏底部，留足空间给标签 + 输入 + 下划线 + OK）
        nameAreaY = contentY + contentH - ui(100);
        int fieldX = rightX;
        int fieldY = nameAreaY + ui(24);
        int fieldW = rightW - ui(64);
        int fieldH = this.font.lineHeight + 6;

        // 保留旧输入内容
        String currentName = (nameField != null) ? nameField.getValue() : savedName;

        nameField = new EditBox(this.font, fieldX, fieldY, fieldW, fieldH,
                Component.translatable("gui.stardewcraft.farm_selection.farm_name"));
        nameField.setMaxLength(48);
        nameField.setBordered(false);
        nameField.setTextColor(0xFF3E2723);
        if (currentName.isEmpty()) {
            currentName = generateDefaultName();
        }
        nameField.setValue(currentName);
        addWidget(nameField);
        setFocused(nameField);

        // 骰子（名称输入行右端，紧贴 field）
        diceCx = fieldX + fieldW + ui(24);
        diceCy = fieldY + fieldH / 2;

        // OK 按钮（名称输入下方，右栏底部居中）
        okCx = rightX + rightW / 2;
        okCy = contentY + contentH - ui(20);

        // "加入农场" 按钮（左栏底部）
        joinBtnW = listW;
        joinBtnH = ui(36);
        joinBtnX = listX;
        joinBtnY = contentY + contentH - joinBtnH - ui(4);
        // 缩短列表高度为按钮留出空间
        listH = joinBtnY - listY - ui(4);
        maxVisible = Math.max(1, listH / rowH);
        scrollOffset = Math.min(scrollOffset, Math.max(0, farmTypes.size() - maxVisible));
    }

    private int ui(int sdvPixels) {
        return Math.max(1, Math.round(sdvPixels / guiScale));
    }

    private float s4() {
        return 4.0f / guiScale;
    }

    // ═══════════════════════════════════════════
    //  键盘输入 — 转发给 EditBox 处理中文等
    // ═══════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) return true;
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            submitSelection();
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            cycleNextUnlockedType();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    // ═══════════════════════════════════════════
    //  鼠标输入
    // ═══════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // 左栏农场类型行
        for (int i = 0; i < Math.min(maxVisible, farmTypes.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            int ry = listY + i * rowH;
            if (inside(mx, my, listX, ry, listW, rowH)) {
                FarmType type = farmTypes.get(idx);
                if (type.isUnlocked()) {
                    selectedIndex = idx;
                    playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
                } else {
                    playUi(ModSounds.SMALL_SELECT.get(), 0.4f, 0.7f);
                }
                return true;
            }
        }

        // 骰子
        int diceS = ui(40);
        if (inside(mx, my, diceCx - diceS / 2, diceCy - diceS / 2, diceS, diceS)) {
            nameField.setValue(generateRandomName());
            playUi(ModSounds.DRUMKIT6.get(), 0.75f, 1.1f);
            return true;
        }

        // OK
        int okS = ui(48);
        if (inside(mx, my, okCx - okS / 2, okCy - okS / 2, okS, okS)) {
            submitSelection();
            return true;
        }

        // 加入农场按钮
        if (inside(mx, my, joinBtnX, joinBtnY, joinBtnW, joinBtnH)) {
            requestJoinList();
            return true;
        }

        // 其余交给 EditBox 等 widget
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= contentX && mouseX <= dividerX && mouseY >= listY && mouseY <= listY + listH) {
            int maxScroll = Math.max(0, farmTypes.size() - maxVisible);
            if (scrollY > 0 && scrollOffset > 0) scrollOffset--;
            if (scrollY < 0 && scrollOffset < maxScroll) scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ═══════════════════════════════════════════
    //  渲染
    // ═══════════════════════════════════════════

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateHover(mouseX, mouseY);
        this.renderTransparentBackground(graphics);

        // 主面板
        StardewGuiUtil.drawDialogueBoxFrame(graphics, panelX, panelY, panelW, panelH);

        // 标题
        Component title = Component.translatable("gui.stardewcraft.farm_selection.title")
                .withStyle(ChatFormatting.BOLD);
        GuiText.drawCenteredClamped(graphics, this.font, title,
            panelX + panelW / 2, contentY + ui(12), contentW, 0x582A11, false);

        // 标题下方水平分隔线
        StardewGuiUtil.drawHorizontalPartition(graphics, panelX, partY, panelW, s4());

        // 竖向分隔线 — T 形交叉，连接标题分隔线和底部边框
        StardewGuiUtil.drawVerticalIntersectingPartition(graphics, dividerX, partY, panelY, panelH, s4());

        // 左栏：农场类型列表（scissor 裁剪）
        drawFarmTypeList(graphics, mouseX, mouseY);

        // 右栏：描述 + 名称编辑
        drawRightPanel(graphics);

        // EditBox 手动渲染
        nameField.render(graphics, mouseX, mouseY, partialTick);

        // 骰子按钮
        drawIconButton(graphics, DICE_ICON, diceCx, diceCy, diceScale);

        // OK 按钮
        drawIconButton(graphics, OK_ICON, okCx, okCy, okScale);

        // "加入农场" 文字按钮（左栏底部）
        drawJoinButton(graphics, mouseX, mouseY);
    }

    // ═══════════════════════════════════════════
    //  左栏：农场类型列表（带滚动条，scissor 裁剪）
    // ═══════════════════════════════════════════

    private void drawFarmTypeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int clipY2 = listY + maxVisible * rowH;

        // scissor 裁剪：确保列表内容不溢出边框
        graphics.enableScissor(contentX, listY, dividerX - ui(4), clipY2);

        for (int i = 0; i < Math.min(maxVisible, farmTypes.size() - scrollOffset); i++) {
            int idx = i + scrollOffset;
            FarmType type = farmTypes.get(idx);
            boolean isSelected = (idx == selectedIndex);
            boolean isUnlocked = type.isUnlocked();
            int ry = listY + i * rowH;

            // 选中高亮
            if (isSelected) {
                graphics.fill(listX, ry + 1, listX + listW, ry + rowH - 1, 0x44EADB8C);
            }
            // Hover
            float hi = (idx < typeHighlight.length) ? typeHighlight[idx] : 0;
            if (hi > 0.01f && !isSelected) {
                int alpha = (int) (hi * 0x22);
                graphics.fill(listX, ry + 1, listX + listW, ry + rowH - 1,
                        (alpha << 24) | 0xEADB8C);
            }

            // 图标
            int iconH = rowH - ui(16);
            int iconW = Math.round(iconH * 22f / 20f);
            int iconX = listX + ui(8);
            int iconY = ry + (rowH - iconH) / 2;

            if (isUnlocked) {
                graphics.blit(type.getIconTexture(), iconX, iconY, 0, 0, iconW, iconH, iconW, iconH);
            } else {
                graphics.setColor(0.5f, 0.5f, 0.5f, 0.4f);
                graphics.blit(type.getIconTexture(), iconX, iconY, 0, 0, iconW, iconH, iconW, iconH);
                graphics.setColor(1f, 1f, 1f, 1f);
                int lockS = iconH / 2;
                graphics.blit(LOCK_ICON, iconX + (iconW - lockS) / 2, iconY + (iconH - lockS) / 2,
                        0, 0, lockS, lockS, lockS, lockS);
            }

            // 类型名称
            String name = type.getDisplayName().getString();
            int nameColor = isSelected ? 0x582A11 : (isUnlocked ? 0x3E2723 : 0x9E9E9E);
            int nameX = iconX + iconW + ui(10);
            int nameY = ry + (rowH - this.font.lineHeight) / 2;
            int checkW = isSelected && isUnlocked ? this.font.width("\u2714") + ui(8) : 0;
            int nameMaxW = Math.max(1, listX + listW - ui(8) - checkW - nameX);
            graphics.drawString(this.font, GuiText.ellipsize(this.font, Component.literal(name), nameMaxW), nameX, nameY, nameColor, false);

            // 选中勾号
            if (isSelected && isUnlocked) {
                graphics.drawString(this.font, "\u2714",
                        listX + listW - ui(8) - this.font.width("\u2714"),
                        nameY, 0x2E7D32, false);
            }
        }

        graphics.disableScissor();

        // 滚动条
        if (farmTypes.size() > maxVisible) {
            int barX = dividerX - ui(10);
            int barTotalH = maxVisible * rowH;
            int thumbH = Math.max(ui(20), barTotalH * maxVisible / farmTypes.size());
            int maxScroll = Math.max(1, farmTypes.size() - maxVisible);
            int thumbY = listY + (barTotalH - thumbH) * scrollOffset / maxScroll;
            graphics.fill(barX, listY, barX + ui(4), listY + barTotalH, 0x22000000);
            graphics.fill(barX, thumbY, barX + ui(4), thumbY + thumbH, 0x66582A11);
        }
    }

    // ═══════════════════════════════════════════
    //  右栏：描述 + 名称编辑
    // ═══════════════════════════════════════════

    private void drawRightPanel(GuiGraphics graphics) {
        FarmType selectedType = farmTypes.get(selectedIndex);

        // 类型大标题（与右栏顶端留出间距）
        Component displayName = Component.literal(selectedType.getDisplayName().getString())
                .withStyle(ChatFormatting.BOLD);
        graphics.drawString(this.font, GuiText.ellipsize(this.font, displayName, rightW - ui(8)), rightX, rightY + ui(8), 0x582A11, false);

        // 描述文字（标题下方留出 28sdv px 间距，自动折行）
        int descY = rightY + ui(36);
        String desc = selectedType.getDescription().getString();
        List<net.minecraft.util.FormattedCharSequence> lines =
                this.font.split(Component.literal(desc), rightW - ui(8));
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, rightX, descY, 0x5D4037, false);
            descY += this.font.lineHeight + 2;
        }

        // ---- 名称输入区域 ----
        // "农场名称：" 标签
        String nameLabel = Component.translatable("gui.stardewcraft.farm_selection.farm_name").getString();
        graphics.drawString(this.font, GuiText.ellipsize(this.font, Component.literal(nameLabel), rightW - ui(8)), rightX, nameAreaY, 0x582A11, false);

        // 下划线（位于 EditBox 下方）
        int lineY = nameField.getY() + nameField.getHeight() + 2;
        int lineW = nameField.getWidth();
        boolean focused = nameField.isFocused();
        int lineColor = focused ? 0xFFEADB8C : 0xAA8B7D63;
        graphics.fill(nameField.getX(), lineY, nameField.getX() + lineW, lineY + (focused ? 2 : 1), lineColor);
        if (focused) {
            graphics.fillGradient(nameField.getX(), lineY + 2,
                    nameField.getX() + lineW, lineY + ui(6),
                    0x44EADB8C, 0x00EADB8C);
        }
    }

    // ═══════════════════════════════════════════
    //  Hover 动画
    // ═══════════════════════════════════════════

    private void updateHover(int mouseX, int mouseY) {
        int okS = ui(48);
        okScale = approach(okScale,
                inside(mouseX, mouseY, okCx - okS / 2, okCy - okS / 2, okS, okS) ? 1.12f : 1.0f);
        int diceS = ui(40);
        diceScale = approach(diceScale,
                inside(mouseX, mouseY, diceCx - diceS / 2, diceCy - diceS / 2, diceS, diceS) ? 1.15f : 1.0f);
        joinBtnScale = approach(joinBtnScale,
                inside(mouseX, mouseY, joinBtnX, joinBtnY, joinBtnW, joinBtnH) ? 1.0f : 0.0f);

        for (int i = 0; i < farmTypes.size(); i++) {
            boolean visible = (i >= scrollOffset && i < scrollOffset + maxVisible);
            if (visible) {
                int vi = i - scrollOffset;
                int ry = listY + vi * rowH;
                boolean hovered = inside(mouseX, mouseY, listX, ry, listW, rowH);
                typeHighlight[i] = approach(typeHighlight[i], hovered ? 1.0f : 0.0f);
            } else {
                typeHighlight[i] = approach(typeHighlight[i], 0.0f);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  "加入农场" 按钮
    // ═══════════════════════════════════════════

    private void drawJoinButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = joinBtnScale > 0.5f;
        int bgColor = hovered ? 0x66EADB8C : 0x33EADB8C;
        graphics.fill(joinBtnX, joinBtnY, joinBtnX + joinBtnW, joinBtnY + joinBtnH, bgColor);

        // 边框
        int borderColor = hovered ? 0xAA582A11 : 0x44582A11;
        graphics.fill(joinBtnX, joinBtnY, joinBtnX + joinBtnW, joinBtnY + 1, borderColor);
        graphics.fill(joinBtnX, joinBtnY + joinBtnH - 1, joinBtnX + joinBtnW, joinBtnY + joinBtnH, borderColor);
        graphics.fill(joinBtnX, joinBtnY, joinBtnX + 1, joinBtnY + joinBtnH, borderColor);
        graphics.fill(joinBtnX + joinBtnW - 1, joinBtnY, joinBtnX + joinBtnW, joinBtnY + joinBtnH, borderColor);

        Component text = Component.translatable("gui.stardewcraft.farm_selection.join_farm");
        int textColor = hovered ? 0x582A11 : 0x8D6E63;
        Component shown = GuiText.ellipsize(this.font, text, joinBtnW - ui(12));
        int tw = this.font.width(shown);
        graphics.drawString(this.font, shown,
            joinBtnX + (joinBtnW - tw) / 2,
                joinBtnY + (joinBtnH - this.font.lineHeight) / 2,
                textColor, false);
    }

    private void requestJoinList() {
        PacketDistributor.sendToServer(
                new com.stardew.craft.network.payload.FarmJoinListRequestPayload());
        playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
    }

    // ═══════════════════════════════════════════
    //  提交
    // ═══════════════════════════════════════════

    private void submitSelection() {
        String finalName = nameField.getValue().trim();
        if (finalName.isEmpty()) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 0.84f);
            return;
        }
        FarmType selectedType = farmTypes.get(selectedIndex);
        if (!selectedType.isUnlocked()) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.4f, 0.7f);
            return;
        }

        if (FarmJoinClientState.hasPendingJoinRequest()) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                sendSelection(selectedType.getId(), finalName, true);
                            } else if (this.minecraft != null) {
                                this.minecraft.setScreen(this);
                            }
                        },
                        Component.translatable("gui.stardewcraft.farm_selection.pending_create.title"),
                        Component.translatable("gui.stardewcraft.farm_selection.pending_create.message"),
                        Component.translatable("gui.stardewcraft.farm_selection.pending_create.confirm"),
                        Component.translatable("gui.stardewcraft.farm_selection.pending_create.cancel")
                ));
            }
            return;
        }

        sendSelection(selectedType.getId(), finalName, false);
    }

    private void sendSelection(String farmTypeId, String farmName, boolean forceCancelPending) {
        PacketDistributor.sendToServer(new FarmSelectionSubmitPayload(farmTypeId, farmName, forceCancelPending));
        playUi(ModSounds.NEW_RECIPE.get(), 0.88f, 1.0f);
        this.onClose();
    }

    // ═══════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════

    private void cycleNextUnlockedType() {
        int start = selectedIndex;
        for (int i = 1; i <= farmTypes.size(); i++) {
            int idx = (start + i) % farmTypes.size();
            if (farmTypes.get(idx).isUnlocked()) {
                selectedIndex = idx;
                // 确保选中项在可见范围内
                if (idx < scrollOffset) scrollOffset = idx;
                if (idx >= scrollOffset + maxVisible) scrollOffset = idx - maxVisible + 1;
                playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
                return;
            }
        }
    }

    private String generateDefaultName() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return Component.translatable("gui.stardewcraft.farm_selection.default_name.player",
                    this.minecraft.player.getName()).getString();
        }
        return Component.translatable("gui.stardewcraft.farm_selection.default_name").getString();
    }

    private String generateRandomName() {
        String prefix = Component.translatable("gui.stardewcraft.farm_selection.random_name.prefix."
                + random.nextInt(FARM_NAME_PREFIX_COUNT)).getString();
        String suffix = Component.translatable("gui.stardewcraft.farm_selection.random_name.suffix."
                + random.nextInt(FARM_NAME_SUFFIX_COUNT)).getString();
        return Component.translatable("gui.stardewcraft.farm_selection.random_name", prefix, suffix).getString();
    }

    private float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.06f);
        if (current > target) return Math.max(target, current - 0.06f);
        return current;
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawIconButton(GuiGraphics graphics, ResourceLocation icon, int cx, int cy, float scale) {
        float baseScale = s4() * 0.8f;
        float finalScale = baseScale * scale;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(finalScale, finalScale, 1.0f);
        graphics.blit(icon, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (nameField != null) {
            savedName = nameField.getValue();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
