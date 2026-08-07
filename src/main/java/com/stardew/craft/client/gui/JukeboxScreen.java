package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.network.payload.JukeboxSelectPayload;
import com.stardew.craft.sound.JukeboxTrackRegistry;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 唱片机选曲界面 — 1:1 复刻 Stardew Valley ChooseFromListMenu (isJukebox=true)。
 * <p>
 * SDV 原版布局（SDV 像素空间）：
 * <ul>
 *   <li>菜单宽 640, 高 192</li>
 *   <li>位置：屏幕中央偏下 (viewport.W/2 - 320, viewport.H - 64 - 192)</li>
 *   <li>标题："Jukebox" 卷轴文字 @ (menuX + 320, menuY - 32)</li>
 *   <li>曲名框：drawTextureBox, 居中于菜单内 (y + 64 - 4, h = 80)</li>
 *   <li>曲名文字：drawTextWithShadow, 居中 (y + h/2 - 16)</li>
 *   <li>← 按钮：(menuX - 128 - 4, menuY + 85), sprite 352,495 12×11 × 4</li>
 *   <li>→ 按钮：(menuX + 640 + 16 + 64, menuY + 85), sprite 365,495 12×11 × 4</li>
 *   <li>✓ 确认：(menuX + 640 + 128 + 8, menuY + 192 - 128), sprite 175,379 16×15 × 4</li>
 *   <li>✕ 取消：(menuX + 640 + 192 + 12, menuY + 192 - 128), standardTileSheet[47] = (192,256,64,64) × 1</li>
 * </ul>
 */
@SuppressWarnings("null")
public class JukeboxScreen extends Screen {

    // ── SDV 常量（SDV 像素空间）──
    private static final int MENU_W = 640;
    private static final int MENU_H = 192;
    private static final int BOTTOM_MARGIN = 64;

    // ── 按钮精灵 UV ──
    private static final int BACK_W = 12, BACK_H = 11;
    private static final int FWD_W = 12, FWD_H = 11;
    private static final int OK_W = 16, OK_H = 15;

    // ── 曲名 drawTextureBox (小面板) UV: (384, 373, 18, 18) from Cursors ──
    // Stardew 的 IClickableMenu.drawTextureBox 默认使用 menuTexture，
    // 但 ChooseFromListMenu 传 Color.White → 使用默认 Cursors (384,373,18,18)

    // ── 状态 ──
    private final BlockPos jukeboxPos;
    private int currentIndex;
    private final int optionCount;

    // ── 布局（GUI 像素）──
    private StardewRenderMapping mapping;
    private float s4;
    private int menuX, menuY, menuW, menuH;
    // Buttons (GUI pixel coords & sizes)
    private int backX, backY, backW, backH;
    private int fwdX, fwdY, fwdW, fwdH;
    private int okX, okY, okW, okH;
    private int cancelX, cancelY, cancelW, cancelH;

    // ── 最大曲名宽度（用于居中 textBox）──
    private static final String MAX_WIDTH_STRING = "Summer (The Sun Can Bend An Orange Sky)";

    public JukeboxScreen(BlockPos jukeboxPos, String currentTrack) {
        super(Component.empty());
        this.jukeboxPos = jukeboxPos;
        this.optionCount = JukeboxTrackRegistry.optionCount();

        // 找到当前曲目的 index
        if (currentTrack == null || currentTrack.isEmpty()) {
            this.currentIndex = 0; // turn_off
        } else {
            this.currentIndex = findIndexForTrack(currentTrack);
        }
    }

    private int findIndexForTrack(String trackId) {
        var tracks = JukeboxTrackRegistry.getAllTracks();
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).id().equals(trackId)) {
                return i + 1; // offset by 1 for turn_off
            }
        }
        return 0;
    }

    // ─── Layout ───

    @Override
    protected void init() {
        super.init();

        float guiScale = (float) minecraft.getWindow().getGuiScale();
        mapping = new StardewRenderMapping(width, height, guiScale);
        s4 = mapping.s4();

        // SDV menu position: centered horizontally, near bottom
        int sdvViewW = Math.round(width * guiScale);
        int sdvViewH = Math.round(height * guiScale);
        int sdvMenuX = sdvViewW / 2 - MENU_W / 2;
        int sdvMenuY = sdvViewH - BOTTOM_MARGIN - MENU_H;

        // Convert to GUI space
        menuX = px(sdvMenuX);
        menuY = px(sdvMenuY);
        menuW = px(MENU_W);
        menuH = px(MENU_H);

        // Back button: (menuX - 128 - 4, menuY + 85)
        int sdvBackX = sdvMenuX - 128 - 4;
        int sdvBackY = sdvMenuY + 85;
        backX = px(sdvBackX);
        backY = px(sdvBackY);
        backW = (int)(BACK_W * s4);
        backH = (int)(BACK_H * s4);

        // Forward button: (menuX + 640 + 16 + 64, menuY + 85)
        int sdvFwdX = sdvMenuX + 640 + 16 + 64;
        int sdvFwdY = sdvMenuY + 85;
        fwdX = px(sdvFwdX);
        fwdY = px(sdvFwdY);
        fwdW = (int)(FWD_W * s4);
        fwdH = (int)(FWD_H * s4);

        // OK button: (menuX + 640 + 128 + 8, menuY + 192 - 128)
        int sdvOkX = sdvMenuX + 640 + 128 + 8;
        int sdvOkY = sdvMenuY + 192 - 128;
        okX = px(sdvOkX);
        okY = px(sdvOkY);
        okW = (int)(OK_W * s4);
        okH = (int)(OK_H * s4);

        // Cancel button: (menuX + 640 + 192 + 12, menuY + 192 - 128)
        // standardTileSheet[47] is 64×64 at 1× scale (already at SDV scale)
        int sdvCancelX = sdvMenuX + 640 + 192 + 12;
        int sdvCancelY = sdvMenuY + 192 - 128;
        cancelX = px(sdvCancelX);
        cancelY = px(sdvCancelY);
        cancelW = (int)(64 * s4 / 4.0f); // standardTileSheet is at 1× not 4×
        cancelH = (int)(64 * s4 / 4.0f);

        // Play "bigSelect" on open
        playSound(ModSounds.BIG_SELECT);
    }

    /** SDV pixel → GUI pixel. */
    private int px(int sdvPx) {
        return Math.round(sdvPx / (float) minecraft.getWindow().getGuiScale());
    }

    // ─── Rendering ───

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制 MC 默认模糊/灰色背景
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明暗色背景
        graphics.fill(0, 0, width, height, 0x66000000);

        // ── 标题卷轴："Jukebox" ──
        drawScrollTitle(graphics);

        // ── 曲名面板 (drawTextureBox) ──
        drawSongPanel(graphics);

        // ── 曲名文字 ──
        drawSongName(graphics);

        // ── 按钮 ──
        drawButtons(graphics, mouseX, mouseY);
    }

    /**
     * 绘制标题卷轴 — SDV: SpriteText.drawStringWithScrollCenteredAt
     * 使用 Cursors (325,318,12,18) / (337,318,1,18) / (338,318,12,18)
     */
    private void drawScrollTitle(GuiGraphics graphics) {
        Component title = GuiText.ellipsize(font, StardewFonts.bannerTitle(
                Component.translatable("stardewcraft.jukebox.title")), Math.max(1, menuW - px(64)));
        int textWidth = font.width(title);

        int centerX = menuX + menuW / 2;
        // SDV: y = menuY - 32 (above the menu)
        int sdvTitleY = Math.round(menuY * (float) minecraft.getWindow().getGuiScale()) - 32;
        int titleY = px(sdvTitleY);

        int textX = centerX - textWidth / 2;

        CommonGuiTextures.drawScrollBanner(graphics, textX, titleY - (int)(3 * s4), textWidth, s4);

        // Default SpriteText uses Stardew's baked gold/orange title sheet.
        graphics.drawString(font, title, centerX - font.width(title) / 2, titleY, 0xFFFFFFFF, false);
    }

    /**
     * 绘制曲名背景面板 — SDV: drawTextureBox (Cursors 384,373,18,18 × 4)
     * 位置: menuX + menuW/2 - stringWidth/2 - 16, menuY + 64 - 4, stringWidth + 32, 80
     */
    private void drawSongPanel(GuiGraphics graphics) {
        // 使用最大宽度字符串计算面板宽度（和 SDV 一样固定宽度）
        int maxTextWidth = font.width(MAX_WIDTH_STRING);
        int panelW = Math.min(maxTextWidth + px(32), Math.max(px(96), menuW - px(32)));
        int panelH = px(80);
        int panelX = menuX + menuW / 2 - panelW / 2;
        int panelY = menuY + px(64 - 4);

        CommonGuiTextures.drawTextureBoxNoShadow(graphics, panelX, panelY, panelW, panelH, s4);
    }

    /**
     * 绘制当前曲名 — SDV: Utility.drawTextWithShadow, centered
     * 位置: menuX + menuW/2, menuY + menuH/2 - 16
     */
    private void drawSongName(GuiGraphics graphics) {
        String optionId = JukeboxTrackRegistry.getOptionId(currentIndex);
        Component songName = getSongDisplayName(optionId);

        int centerX = menuX + menuW / 2;
        int textY = menuY + menuH / 2 - px(16);
        int maxWidth = Math.min(font.width(MAX_WIDTH_STRING), Math.max(1, menuW - px(64)));

        // SDV: drawTextWithShadow — Game1.textColor (0xFF5B5045)
        GuiText.drawCenteredClamped(graphics, font, songName, centerX, textY, maxWidth, 0xFF5B5045, false);
    }

    /**
     * 获取曲目显示名（翻译后）。
     */
    private Component getSongDisplayName(String optionId) {
        if (JukeboxTrackRegistry.TURN_OFF.equals(optionId)) {
            return Component.translatable("stardewcraft.jukebox.turn_off");
        }
        if (JukeboxTrackRegistry.RANDOM.equals(optionId)) {
            return Component.translatable("stardewcraft.jukebox.random");
        }
        JukeboxTrackRegistry.Track track = JukeboxTrackRegistry.getTrack(optionId);
        if (track != null) {
            return Component.translatable(track.translationKey());
        }
        return Component.literal(optionId);
    }

    /**
     * 绘制 4 个按钮：← → ✓ ✕
     */
    private void drawButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        // Back button ←
        float backScale = isHovering(mouseX, mouseY, backX, backY, backW, backH)
                ? s4 * 1.1f : s4;
        CommonGuiTextures.drawBackArrow(graphics, backX, backY, backScale);

        // Forward button →
        float fwdScale = isHovering(mouseX, mouseY, fwdX, fwdY, fwdW, fwdH)
                ? s4 * 1.1f : s4;
        CommonGuiTextures.drawForwardArrow(graphics, fwdX, fwdY, fwdScale);

        // OK button ✓
        float okScale = isHovering(mouseX, mouseY, okX, okY, okW, okH)
                ? s4 * 1.1f : s4;
        CommonGuiTextures.drawOkCheckSmall(graphics, okX, okY, okScale);

        // Cancel button ✕ — standardTileSheet[47] (192,256,64,64) at 1× scale
        float cancelScale = isHovering(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH)
                ? s4 / 4.0f * 1.1f : s4 / 4.0f;
        CommonGuiTextures.drawLargeCancelButton(graphics, cancelX, cancelY, cancelScale);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ─── Input ───

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // OK button
        if (isHovering(mx, my, okX, okY, okW, okH)) {
            String selected = JukeboxTrackRegistry.getOptionId(currentIndex);
            PacketDistributor.sendToServer(new JukeboxSelectPayload(jukeboxPos, selected));
            playSound(ModSounds.SELECT);
            onClose();
            return true;
        }

        // Cancel button
        if (isHovering(mx, my, cancelX, cancelY, cancelW, cancelH)) {
            onClose();
            return true;
        }

        // Back button ←
        if (isHovering(mx, my, backX, backY, backW, backH)) {
            currentIndex--;
            if (currentIndex < 0) currentIndex = optionCount - 1;
            playSound(ModSounds.SHWIP);
            return true;
        }

        // Forward button →
        if (isHovering(mx, my, fwdX, fwdY, fwdW, fwdH)) {
            currentIndex++;
            currentIndex %= optionCount;
            playSound(ModSounds.SHWIP);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC closes
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── Helpers ───

    private void playSound(net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0F));
    }
}
