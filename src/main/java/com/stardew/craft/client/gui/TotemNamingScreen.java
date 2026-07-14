package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.network.payload.OpenTotemNamingScreenPayload;
import com.stardew.craft.network.payload.TotemNamingSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * 图腾柱命名界面 — 完全复刻 SDV NamingMenu + AnimalPurchaseScreen 的文本输入系统。
 * <p>
 * 布局：全屏黑色半透明背景，居中显示标题、文本输入区域、确认按钮、骰子按钮。
 */
@SuppressWarnings({"null", "unused"})
public class TotemNamingScreen extends Screen {

    // ---- 图标资源（复用 AnimalPurchaseScreen 的 rename/dice 图标） ----
    private static final ResourceLocation RENAME_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/rename.png");
    private static final ResourceLocation DICE_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/dice_icon.png");
    private static final ResourceLocation OK_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/animal_query/ok_yes_tile46.png");

    // ---- 随机名称池（完全复刻SDV Dialogue.randomName风格） ----
    private static final String[] TOTEM_NAME_KEYS = createTotemNameKeys();

    private static String[] createTotemNameKeys() {
        String[] keys = new String[24];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "stardewcraft.totem.random_name." + i;
        }
        return keys;
    }

    // ---- UI 缩放 ----
    private static final float UI_SCALE = 0.5f;

    // ---- Payload 数据 ----
    private final long blockPos;
    private final String currentName;
    private final TotemType totemType;
    private final int poleId;

    // ---- 文本编辑状态（完全复刻 AnimalPurchaseScreen） ----
    private String editName = "";
    private int editCursor = 0;
    private boolean editingName = true; // 默认进入编辑模式

    // ---- 动画状态 ----
    private float okScale = 1.6f;
    private float diceScale = 1.15f;
    private float renameScale = 0.94f;
    private long openedAtMs;

    // ---- 布局缓存 ----
    private int centerX, centerY;
    private final Random random = new Random();

    public TotemNamingScreen(OpenTotemNamingScreenPayload payload) {
        super(Component.translatable("gui.stardewcraft.totem_naming.title"));
        this.blockPos = payload.blockPos();
        this.currentName = payload.currentName();
        this.totemType = TotemType.fromId(payload.totemTypeId());
        this.poleId = payload.poleId();
    }

    @Override
    protected void init() {
        super.init();
        this.centerX = this.width / 2;
        this.centerY = this.height / 2;
        this.editName = currentName.isEmpty() ? rerollNameString() : currentName;
        this.editCursor = editName.length();
        this.openedAtMs = System.currentTimeMillis();
    }

    /* ========== 键盘输入（完全复刻 AnimalPurchaseScreen） ========== */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingName) {
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                submitName();
                return true;
            }
            if (keyCode == InputConstants.KEY_ESCAPE) {
                editingName = false;
                this.onClose();
                return true;
            }
            if (keyCode == InputConstants.KEY_LEFT && editCursor > 0) {
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_RIGHT && editCursor < editName.length()) {
                editCursor++;
                return true;
            }
            if (keyCode == InputConstants.KEY_HOME) {
                editCursor = 0;
                return true;
            }
            if (keyCode == InputConstants.KEY_END) {
                editCursor = editName.length();
                return true;
            }
            if (keyCode == InputConstants.KEY_BACKSPACE && editCursor > 0) {
                editName = editName.substring(0, editCursor - 1) + editName.substring(editCursor);
                editCursor--;
                return true;
            }
            if (keyCode == InputConstants.KEY_DELETE && editCursor < editName.length()) {
                editName = editName.substring(0, editCursor) + editName.substring(editCursor + 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingName) {
            if (codePoint >= ' ' && codePoint != '\u00A7' && editName.length() < 48) {
                editName = editName.substring(0, editCursor) + codePoint + editName.substring(editCursor);
                editCursor++;
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    /* ========== 鼠标输入 ========== */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        NameLayout nl = computeNameLayout();

        // 点击文本区域或重命名图标 → 进入编辑模式
        if (inside(mx, my, nl.renameCx - si(9), nl.renameCy - si(9), si(18), si(18))
                || inside(mx, my, nl.lineX, nl.lineY - si(20), nl.lineW, si(24))) {
            editingName = true;
            playUi(ModSounds.SMALL_SELECT.get(), 0.7f, 1.05f);
            return true;
        }

        // 点击骰子图标 → 随机名称
        if (inside(mx, my, nl.diceCx - si(11), nl.diceCy - si(11), si(22), si(22))) {
            editName = rerollNameString();
            editCursor = editName.length();
            playUi(ModSounds.DRUMKIT6.get(), 0.75f, 1.1f);
            return true;
        }

        // 点击确认按钮
        int okCx = centerX + si(80);
        int okCy = centerY + si(60);
        if (inside(mx, my, okCx - si(28), okCy - si(28), si(56), si(56))) {
            submitName();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* ========== 渲染 ========== */

    @SuppressWarnings("null")
    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 更新 hover 动画
        updateHover(mouseX, mouseY);

        // SDV: 黑色半透明背景
        this.renderTransparentBackground(graphics);
        graphics.fillGradient(0, 0, this.width, this.height, 0xAA0A0A0E, 0xEA050508);

        // ---- 标题 ----
        String titleText = Component.translatable("gui.stardewcraft.totem_naming.title").getString();
        int titleColor = totemType.getTextColor();
        drawScaledBoldTextCentered(graphics, titleText, centerX, centerY - si(80), 1.2f, titleColor);

        // ---- 文本输入区域 ----
        drawNameEditor(graphics, mouseX, mouseY);

        // ---- 确认按钮 ----
        int okCx = centerX + si(80);
        int okCy = centerY + si(60);
        drawScaledIcon(graphics, OK_ICON, okCx, okCy, okScale, 0xFFFFFFFF);

        // 不调用 super.render() — 避免 MC 1.21 二次模糊渲染
    }

    /* ========== 文本编辑器渲染（完全复刻 AnimalPurchaseScreen.drawNameEditor） ========== */

    private void drawNameEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        NameLayout nl = computeNameLayout();
        boolean focused = inside(mouseX, mouseY, nl.lineX, nl.lineY - si(20), nl.lineW, si(24)) || editingName;
        int underline = focused ? 0xFFEADB8C : 0xAA8B8490;

        // 下划线 + 辉光
        if (focused) {
            graphics.fill(nl.lineX, nl.lineY, nl.lineX + nl.lineW, nl.lineY + si(2), underline);
            graphics.fillGradient(nl.lineX, nl.lineY + si(2), nl.lineX + nl.lineW, nl.lineY + si(8),
                    0x44EADB8C, 0x00EADB8C);
        } else {
            graphics.fill(nl.lineX, nl.lineY, nl.lineX + nl.lineW, nl.lineY + 1, underline);
        }

        // 文字（奶油色、加粗、0.90f 缩放）
        drawScaledBoldText(graphics, nl.shown, nl.textX, nl.textY, nl.textScale, 0xFFFFF7D0);

        // 闪烁光标（450ms 周期）
        if (editingName && ((System.currentTimeMillis() / 450L) & 1L) == 0L) {
            int visibleCursor = Math.max(0, Math.min(editCursor, nl.shown.length()));
            String prefix = nl.shown.substring(0, visibleCursor);
            int cx = nl.textX + Math.round(this.font.width(prefix) * nl.textScale) + 1;
            graphics.fill(cx, nl.lineY - si(17), cx + 1, nl.lineY - si(5), 0xFFFFFFFF);
        }

        // 重命名图标 + 骰子图标
        drawScaledIcon(graphics, RENAME_ICON, nl.renameCx, nl.renameCy, renameScale, 0xFFFFFFFF);
        drawScaledIcon(graphics, DICE_ICON, nl.diceCx, nl.diceCy, diceScale, 0xFFFFFFFF);
    }

    /* ========== 布局计算（改编自 AnimalPurchaseScreen.computeBuildingNameLayout） ========== */

    private static final class NameLayout {
        int lineX, lineY, lineW;
        int textX, textY;
        float textScale;
        String shown;
        int renameCx, renameCy;
        int diceCx, diceCy;
    }

    private NameLayout computeNameLayout() {
        NameLayout nl = new NameLayout();
        nl.lineW = si(172);
        nl.lineX = centerX - nl.lineW / 2;
        nl.lineY = centerY + si(10);
        nl.textScale = 0.90f;

        String raw = editName == null ? "" : editName;
        int maxFontPx = Math.max(1, (int) (nl.lineW / nl.textScale) - 2);
        nl.shown = this.font.plainSubstrByWidth(raw, maxFontPx);

        int textW = Math.round(this.font.width(nl.shown) * nl.textScale);
        nl.textX = centerX - textW / 2;
        nl.textY = nl.lineY - si(16);

        int lineRight = nl.lineX + nl.lineW;
        int textRight = nl.textX + textW;
        nl.renameCy = nl.lineY - si(17);
        nl.diceCy = nl.lineY + si(20);
        nl.diceCx = centerX;

        int minRename = textRight + si(16);
        int maxDice = lineRight - si(14);
        nl.renameCx = Math.max(minRename, maxDice);
        return nl;
    }

    /* ========== Hover 动画更新 ========== */

    private void updateHover(int mouseX, int mouseY) {
        NameLayout nl = computeNameLayout();
        int okCx = centerX + si(80);
        int okCy = centerY + si(60);
        okScale = approach(okScale, inside(mouseX, mouseY, okCx - si(28), okCy - si(28), si(56), si(56)) ? 1.74f : 1.6f);
        renameScale = approach(renameScale, inside(mouseX, mouseY, nl.renameCx - si(9), nl.renameCy - si(9), si(18), si(18)) ? 1.02f : 0.94f);
        diceScale = approach(diceScale, inside(mouseX, mouseY, nl.diceCx - si(11), nl.diceCy - si(11), si(22), si(22)) ? 1.26f : 1.15f);
    }

    /* ========== 提交名称 ========== */

    private void submitName() {
        String finalName = editName == null ? "" : editName.trim();
        if (finalName.isEmpty()) {
            playUi(ModSounds.SMALL_SELECT.get(), 0.72f, 0.84f);
            return;
        }
        PacketDistributor.sendToServer(new TotemNamingSubmitPayload(blockPos, finalName));
        playUi(ModSounds.NEW_RECIPE.get(), 0.88f, 1.0f);
        this.onClose();
    }

    /* ========== 随机名称 ========== */

    private String rerollNameString() {
        return Component.translatable(TOTEM_NAME_KEYS[random.nextInt(TOTEM_NAME_KEYS.length)]).getString();
    }

    /* ========== 工具方法（与 AnimalPurchaseScreen 一致） ========== */

    private int si(int value) {
        return Math.max(1, Math.round(value * UI_SCALE));
    }

    private float approach(float current, float target) {
        if (current < target) return Math.min(target, current + 0.06f);
        if (current > target) return Math.max(target, current - 0.06f);
        return current;
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawScaledIcon(GuiGraphics graphics, ResourceLocation icon, int centerX, int centerY, float scale, int tint) {
        int a = (tint >>> 24) & 0xFF;
        int r = (tint >>> 16) & 0xFF;
        int g = (tint >>> 8) & 0xFF;
        int b = tint & 0xFF;
        graphics.setColor(r / 255f, g / 255f, b / 255f, a / 255f);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(icon, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawScaledBoldText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(this.font, Component.literal(text).withStyle(ChatFormatting.BOLD), 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawScaledBoldTextCentered(GuiGraphics graphics, String text, int cx, int cy, float scale, int color) {
        int textW = Math.round(this.font.width(text) * scale);
        drawScaledBoldText(graphics, text, cx - textW / 2, cy, scale, color);
    }

    private void playUi(SoundEvent event, float volume, float pitch) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(event, volume, pitch);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
