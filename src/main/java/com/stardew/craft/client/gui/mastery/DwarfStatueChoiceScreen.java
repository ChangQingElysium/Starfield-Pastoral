package com.stardew.craft.client.gui.mastery;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.ChooseDwarfStatueBuffPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 矮人王雕像 2 选 1 buff 菜单 —— 严格按 SDV ChooseFromIconsMenu.cs:118-145, 326-354
 * 的布局与 UV：
 *
 *   背景框（dwarfStatue 路径）：cursors_1_6 (127, 123, 21, 21) → hover (127, 144, 21, 21)
 *   前景图标：cursors_1_6 (148 + i*17, 123, 17, 17) → hover (148 + i*17, 140, 17, 17)
 *   描述框 9-slice：cursors_1_6 (96, 145, 15, 15)
 *
 *   每个 icon 槽位宽 272 sdv-px（21×4 背框 + 240 描述区，居中），高 = 84 + 描述高
 *   两 icon 居中排开，间距 32 sdv-px
 *   标题 "选择一个" 居中放在 +20 sdv-px 处
 *   黑色 fade overlay 0.7 透明
 */
public final class DwarfStatueChoiceScreen extends Screen {

    private static final ResourceLocation CURSORS_1_6 =
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/cursors_1_6.png");
    private static final int TEX_SIZE = 512;

    // SDV 常量（sdv-px = src px × 4）
    private static final int ICON_BACK_SDV     = 84;   // 21 × 4
    private static final int ICON_FRONT_SDV    = 68;   // 17 × 4
    private static final int ICON_FRONT_OFFSET = 32;   // 用于把 17×4 居中到 21×4 — (84-68)/2 = 8 但 SDV 用 iconOffsetXMargin=12 src=48 sdv; 我们改为最小化校准
    private static final int TIP_BOX_PADDING_SDV = 24; // 12 src 每边
    private static final int TIP_WIDTH_SDV     = 240;
    private static final int ICON_SPACING_SDV  = 32;

    private final int icon1;
    private final int icon2;

    private float guiScale = 1f;
    private int panelX, panelY, panelW, panelH;
    private int slot1X, slot2X, slotY;
    private int slotW, slotH;
    private int tipBoxH;             // 描述框总高度（含内边距）
    private int tipTextW;            // 描述文字最大宽度（GUI 像素）
    private int hoverIndex = -1;
    private int lastHover = -1;
    private List<FormattedCharSequence> tip1, tip2;
    private final List<Debris> debris = new ArrayList<>();
    private int destroyTicks;
    private int delayedTapTicks;
    private int delayedDiscoverTicks;
    private boolean choiceSent;

    private DwarfStatueChoiceScreen(int icon1, int icon2) {
        super(Component.translatable("stardewcraft.mastery.dwarf_statue.choose"));
        this.icon1 = icon1;
        this.icon2 = icon2;
    }

    public static void open(int icon1, int icon2) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new DwarfStatueChoiceScreen(icon1, icon2));
        if (mc.player != null) {
            mc.player.playSound(ModSounds.STONE_BUTTON.get(), 1.0f, 1.0f);
        }
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();

        // 描述文字换行（用 GUI px 宽度）
        tipTextW = ui(TIP_WIDTH_SDV - TIP_BOX_PADDING_SDV);
        Component d1 = Component.translatable(descKey(icon1));
        Component d2 = Component.translatable(descKey(icon2));
        tip1 = this.font.split(d1, tipTextW);
        tip2 = this.font.split(d2, tipTextW);

        int textH = Math.max(tip1.size(), tip2.size()) * this.font.lineHeight;
        tipBoxH = textH + ui(TIP_BOX_PADDING_SDV);

        slotW = Math.max(ui(ICON_BACK_SDV), ui(TIP_WIDTH_SDV));
        slotH = ui(ICON_BACK_SDV) + ui(8) + tipBoxH;

        int totalW = slotW * 2 + ui(ICON_SPACING_SDV);
        panelW = totalW + ui(80);
        panelH = slotH + ui(120);
        panelX = this.width / 2 - panelW / 2;
        panelY = this.height / 2 - panelH / 2;

        slotY = panelY + ui(80);
        slot1X = panelX + (panelW - totalW) / 2;
        slot2X = slot1X + slotW + ui(ICON_SPACING_SDV);
    }

    private static String descKey(int idx) {
        return "stardewcraft.mastery.dwarf_statue.desc_" + idx;
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // SDV: black × 0.7
        g.fill(0, 0, this.width, this.height, 0xB3000000);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        float s4 = s4();

        // 主背景框 9-slice — cursors_1_6 (1, 85, 21, 21)，与 MasteryTrackerMenu 同款
        StardewGuiUtil.drawTextureBox(g, CURSORS_1_6, TEX_SIZE, TEX_SIZE,
            1, 85, 21, 21,
            panelX, panelY, panelW, panelH, s4, true);

        // 标题 — 居中
        Component title = StardewFonts.title(
                Component.translatable("stardewcraft.mastery.dwarf_statue.choose"));
        Component source = Component.translatable("stardewcraft.mastery.dwarf_statue.source");
        int tw = this.font.width(title);
        g.drawString(this.font, title, panelX + panelW / 2 - tw / 2, panelY + ui(24), 0x000000, false);
        int sw = this.font.width(source);
        g.drawString(this.font, source, panelX + panelW / 2 - sw / 2, panelY + ui(48), 0x554433, false);

        // 计算 hover（基于背框 84×84 命中即可，不含描述）
        int backSz = ui(ICON_BACK_SDV);
        hoverIndex = -1;
        if (mouseY >= slotY && mouseY < slotY + backSz) {
            int back1X = slot1X + (slotW - backSz) / 2;
            int back2X = slot2X + (slotW - backSz) / 2;
            if (mouseX >= back1X && mouseX < back1X + backSz) hoverIndex = 0;
            else if (mouseX >= back2X && mouseX < back2X + backSz) hoverIndex = 1;
        }
        if (hoverIndex != -1 && hoverIndex != lastHover) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.BOULDER_CRACK.get(), 1.0f, 1.0f);
            }
        }
        lastHover = hoverIndex;

        // 两个槽
        drawSlot(g, slot1X, icon1, tip1, hoverIndex == 0, s4);
        drawSlot(g, slot2X, icon2, tip2, hoverIndex == 1, s4);
        renderDebris(g, partialTick, s4);
    }

    private void drawSlot(GuiGraphics g, int slotX, int iconId, List<FormattedCharSequence> tip, boolean hover, float s4) {
        int backSz = ui(ICON_BACK_SDV);
        int frontSz = ui(ICON_FRONT_SDV);

        int backX = slotX + (slotW - backSz) / 2;
        int backY = slotY;

        // 背框：cursors_1_6 (127, 123, 21, 21) normal / (127, 144, 21, 21) hover
        int bgSrcY = hover ? 144 : 123;
        blit(g, backX, backY, 127, bgSrcY, 21, 21, s4);

        // 前景图标：(148 + iconId*17, 123, 17, 17) / hover y+=17 → 140
        int frontSrcX = 148 + iconId * 17;
        int frontSrcY = hover ? 140 : 123;
        int frontX = backX + (backSz - frontSz) / 2;
        int frontY = backY + (backSz - frontSz) / 2;
        blit(g, frontX, frontY, frontSrcX, frontSrcY, 17, 17, s4);

        // 描述框 — cursors_1_6 (96, 145, 15, 15)
        int boxX = slotX;
        int boxY = backY + backSz + ui(8);
        int boxW = slotW;
        int boxH = tipBoxH;
        StardewGuiUtil.drawTextureBox(g, CURSORS_1_6, TEX_SIZE, TEX_SIZE,
            96, 145, 15, 15,
            boxX, boxY, boxW, boxH, s4, false);

        // 描述文字
        int textX = boxX + ui(TIP_BOX_PADDING_SDV / 2);
        int textY = boxY + ui(TIP_BOX_PADDING_SDV / 2);
        for (FormattedCharSequence line : tip) {
            g.drawString(this.font, line, textX, textY, 0x1A1A2B, false); // SDV (26, 26, 43)
            textY += this.font.lineHeight;
        }
    }

    private void blit(GuiGraphics g, int x, int y, int srcX, int srcY, int srcW, int srcH, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.blit(CURSORS_1_6, 0, 0, srcX, srcY, srcW, srcH, TEX_SIZE, TEX_SIZE);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hoverIndex != -1 && destroyTicks <= 0) {
            int chosen = (hoverIndex == 0) ? icon1 : icon2;
            PacketDistributor.sendToServer(new ChooseDwarfStatueBuffPayload(chosen));
            choiceSent = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.BUTTON_TAP.get(), 1.0f, 1.0f);
            }
            delayedTapTicks = 2;
            delayedDiscoverTicks = 15;
            destroyTicks = 16;
            spawnDebris(hoverIndex == 0 ? slot1X : slot2X);
            return true;
        }
        if (button == 1) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        tickDelayedSound();
        tickDebris();
        if (destroyTicks > 0) {
            destroyTicks--;
            if (destroyTicks <= 0 && choiceSent) {
                this.onClose();
            }
        }
    }

    private void tickDelayedSound() {
        Minecraft mc = Minecraft.getInstance();
        if (delayedTapTicks > 0 && --delayedTapTicks == 0 && mc.player != null) {
            mc.player.playSound(ModSounds.BUTTON_TAP.get(), 1.0f, 1.0f);
        }
        if (delayedDiscoverTicks > 0 && --delayedDiscoverTicks == 0 && mc.player != null) {
            mc.player.playSound(ModSounds.DISCOVER_MINERAL.get(), 1.0f, 1.0f);
        }
    }

    private void spawnDebris(int slotX) {
        int backSz = ui(ICON_BACK_SDV);
        int centerX = slotX + slotW / 2;
        int centerY = slotY + backSz / 2;
        Minecraft mc = this.minecraft;
        long gameTime = mc != null && mc.level != null ? mc.level.getGameTime() : 0L;
        Random random = new Random(((long) icon1 << 32) ^ icon2 ^ gameTime);
        debris.clear();
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 1.4D + random.nextDouble() * 2.2D;
            debris.add(new Debris(centerX, centerY, Math.cos(angle) * speed, Math.sin(angle) * speed - 1.6D, random.nextInt(3), 16));
        }
    }

    private void tickDebris() {
        for (int i = debris.size() - 1; i >= 0; i--) {
            Debris particle = debris.get(i);
            particle.x += particle.vx;
            particle.y += particle.vy;
            particle.vy += 0.35D;
            particle.life--;
            if (particle.life <= 0) {
                debris.remove(i);
            }
        }
    }

    private void renderDebris(GuiGraphics g, float partialTick, float s4) {
        for (Debris particle : debris) {
            int alpha = Math.max(0, Math.min(255, particle.life * 16));
            g.setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
            blit(g, (int) Math.round(particle.x), (int) Math.round(particle.y), 98 + particle.variant * 4, 161, 4, 4, s4);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private int ui(int sdvPx) { return Math.round(sdvPx / guiScale); }
    private float s4() { return 4.0f / guiScale; }

    @SuppressWarnings("unused")
    private int unusedFrontOffset() { return ICON_FRONT_OFFSET; }

    private static final class Debris {
        private double x;
        private double y;
        private double vx;
        private double vy;
        private final int variant;
        private int life;

        private Debris(double x, double y, double vx, double vy, int variant, int life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.variant = variant;
            this.life = life;
        }
    }
}
