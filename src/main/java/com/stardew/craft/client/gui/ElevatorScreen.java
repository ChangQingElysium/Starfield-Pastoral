package com.stardew.craft.client.gui;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.menu.ElevatorMenu;
import com.stardew.craft.network.payload.ElevatorActionPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿井电梯 GUI — 逐行映射自 SDV MineElevatorMenu.cs。
 *
 * 渲染管线：
 *   SDV 在屏幕像素空间直接绘制（viewport = 屏幕分辨率）。
 *   MC GUI 空间 = 屏幕像素 / guiScale。
 *   转换：所有 SDV 像素值用 px() 映射到 GUI 空间，精灵缩放用 s4()。
 *
 * 正确做法（同 StardewConfirmDialogScreen）：
 *   1. 模拟 SDV viewport = (this.width * guiScale) × (this.height * guiScale)
 *   2. 在 SDV 像素空间计算完整布局（菜单尺寸、按钮坐标）
 *   3. 用 px() 统一转为 MC GUI 空间
 *   4. 精灵 scale 用 s4() = 4/guiScale
 */
@SuppressWarnings("null")
public class ElevatorScreen extends AbstractContainerScreen<ElevatorMenu> {

    // ── SDV IClickableMenu 常量（SDV 像素） ──
    /** IClickableMenu.borderWidth = 40 */
    private static final int BW = 40;
    /** IClickableMenu.spaceToClearSideBorder = 16 */
    private static final int STC = 16;
    /** 每个按钮单元格像素：10×10 sprite × 4 scale = 40 + 4 gap = 44 */
    private static final int CELL = 44;

    // ── 运行时状态 ──
    private StardewRenderMapping mapping;
    private final List<ElevatorButton> buttons = new ArrayList<>();
    /** drawDialogueBox 的参数（GUI 像素） */
    private int frameX, frameY, frameW, frameH;
    /** 按钮 cell 大小（GUI 像素），用于点击检测 */
    private int cellGui;
    private int lastMaxFloor = Integer.MIN_VALUE;
    private boolean soundPlayed;

    /** 按钮坐标已转为 GUI 像素 */
    private record ElevatorButton(int floor, int x, int y) {}

    public ElevatorScreen(ElevatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 0;
        this.imageHeight = 0;
    }

    private float guiScale() {
        return (float) StardewGuiViewport.REFERENCE_SCALE;
    }

    /** SDV 像素 → MC GUI 像素 */
    private int px(int sdvPx) {
        return mapping.ui(sdvPx);
    }

    @Override
    protected void init() {
        super.init();
        rebuildLayout();
        if (!soundPlayed) {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.CRYSTAL.get(), 1.0f, 1.0f));
            soundPlayed = true;
        }
    }

    /**
     * 逐行映射自 SDV MineElevatorMenu 构造函数。
     * 所有计算先在 SDV 像素空间完成，最后用 px() 转为 GUI 空间。
     */
    private void rebuildLayout() {
        float gs = guiScale();
        mapping = new StardewRenderMapping(this.width, this.height, gs);
        buttons.clear();

        // SDV viewport（模拟）
        int viewW = Math.round(this.width * gs);
        int viewH = Math.round(this.height * gs);

        int numElevators = Math.min(this.menu.getMaxFloorReached(), 120) / 5;

        // SDV: width = numElevators > 50 ? (484 + BW*2) : min(220 + BW*2, numElevators*44 + BW*2)
        int sdvW;
        if (numElevators > 50) {
            sdvW = 484 + BW * 2;
        } else {
            sdvW = Math.min(220 + BW * 2, numElevators * CELL + BW * 2);
        }

        // SDV: height = max(64 + BW*3, numElevators*44 / (width - BW) * 44 + 64 + BW*3)
        int sdvH = Math.max(64 + BW * 3,
            numElevators * CELL / (sdvW - BW) * CELL + 64 + BW * 3);

        // SDV: 居中于 viewport
        int sdvX = viewW / 2 - sdvW / 2;
        int sdvY = viewH / 2 - sdvH / 2;

        // SDV: buttonsPerRow = width / 44 - 1
        int buttonsPerRow = sdvW / CELL - 1;
        if (buttonsPerRow < 1) buttonsPerRow = 1;

        // SDV: 按钮起始位置
        // x = xPos + BW + STC * 3/4 = sdvX + 40 + 12 = sdvX + 52
        // y = yPos + BW + BW/3     = sdvY + 40 + 13 = sdvY + 53
        int bx = sdvX + BW + STC * 3 / 4;
        int by = sdvY + BW + BW / 3;
        int rightEdge = sdvX + sdvW - BW;

        // 0层按钮
        buttons.add(new ElevatorButton(0, px(bx), px(by)));
        bx += CELL; // SDV: x = x + 64 - 20 = x + 44
        if (bx > rightEdge) {
            bx = sdvX + BW + STC * 3 / 4;
            by += CELL;
        }

        // 楼层按钮
        for (int i = 1; i <= numElevators; i++) {
            buttons.add(new ElevatorButton(i * 5, px(bx), px(by)));
            bx += CELL;
            if (bx > rightEdge) {
                bx = sdvX + BW + STC * 3 / 4;
                by += CELL;
            }
        }

        // SDV: Game1.drawDialogueBox(xPos, yPos - 64 + 8, width + 21, height + 64, drawOnlyBox: true)
        frameX = px(sdvX);
        frameY = px(sdvY - 64 + 8);
        frameW = px(sdvW + 21);
        frameH = px(sdvH + 64);

        // Native container integrations (including JEI) need the actual occupied rectangle.
        this.leftPos = frameX;
        this.topPos = frameY;
        this.imageWidth = frameW;
        this.imageHeight = frameH;

        // 按钮 cell 大小（GUI 像素）— 用于点击检测
        cellGui = px(CELL);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int maxFloor = this.menu.getMaxFloorReached();
        if (maxFloor != lastMaxFloor) {
            lastMaxFloor = maxFloor;
            rebuildLayout();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        for (ElevatorButton btn : buttons) {
            if (mx >= btn.x && mx < btn.x + cellGui && my >= btn.y && my < btn.y + cellGui) {
                int currentFloor = this.menu.getCurrentFloor();
                if (btn.floor == currentFloor) return true;

                Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f));
                PacketDistributor.sendToServer(new ElevatorActionPayload(btn.floor));
                this.onClose();
                return true;
            }
        }

        // SDV: 点击对话框外关闭
        if (mx < frameX || mx > frameX + frameW || my < frameY || my > frameY + frameH) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── 渲染 ──

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // SDV: b.Draw(fadeToBlackRect, Viewport.Bounds, Color.Black * 0.4f)
        graphics.fill(0, 0, this.width, this.height, 0x66000000);

        // SDV: Game1.drawDialogueBox(xPos, yPos-56, width+21, height+64, drawOnlyBox:true)
        StardewGuiUtil.drawDialogueBoxFrame(graphics, frameX, frameY, frameW, frameH);

        float s4 = mapping.s4();
        int currentFloor = this.menu.getCurrentFloor();

        for (ElevatorButton btn : buttons) {
            boolean hovered = mouseX >= btn.x && mouseX < btn.x + cellGui
                           && mouseY >= btn.y && mouseY < btn.y + cellGui;
            // SDV: shadow at (bounds.X - 4, bounds.Y + 4), Color.Black * 0.5f, scale 4f
            ElevatorTextures.drawButtonTint(graphics, btn.x - px(4), btn.y + px(4), hovered, s4, 0.0f, 0.0f, 0.0f, 0.5f);

            // SDV: normal at (bounds.X, bounds.Y), Color.White, scale 4f
            ElevatorTextures.drawButtonTint(graphics, btn.x, btn.y, hovered, s4, 1.0f, 1.0f, 1.0f, 1.0f);

            // SDV: NumberSprite.draw(...)
            boolean isCurrent = (btn.floor == currentFloor);
            drawNumber(graphics, btn.floor, btn.x, btn.y, isCurrent, s4);
        }
    }

    /**
     * 逐行映射 SDV NumberSprite.draw。
     *
     * SDV 调用：
     *   position = (bounds.X + 16 + numberOfDigits(num)*6, bounds.Y + 24 - getHeight()/4)
     *   b.Draw(mouseCursors, position, srcRect, color, 0, origin(4,4), 4f*0.5f=2f, ...)
     *   position.X -= 8f * 0.5f * 4f - 4f = 12
     *
     * origin(4,4) at drawScale=2 → SDV 像素偏移 8px
     * 我们的 drawFromCursors 以 top-left 为锚点，需减去 origin 偏移。
     *
     * SDV position(中心) → top-left = position - origin*drawScale = position - 8
     * 数字 scale: SDV 4f*0.5f=2f → MC s4()*0.5f
     */
    private void drawNumber(GuiGraphics graphics, int number, int btnX, int btnY, boolean isCurrent, float s4) {
        float digitScale = s4 * 0.5f;
        int numDigits = numberOfDigits(number);

        // SDV position (center): bounds.X + 16 + numDigits*6, bounds.Y + 24 - 8/4 = bounds.Y + 22
        // → top-left: subtract origin*drawScale = 8 SDV px
        // SDV top-left offset from bounds: (16 + numDigits*6 - 8, 22 - 8) = (8 + numDigits*6, 14)
        int x = btnX + px(8 + numDigits * 6);
        int y = btnY + px(14);

        // SDV color: isCurrent ? Color.Gray * 0.75f : Color.Gold
        float r, g, b, a;
        if (isCurrent) {
            // Color.Gray(128,128,128) * 0.75 → (96, 96, 96, 191)
            r = 96f / 255f; g = 96f / 255f; b = 96f / 255f; a = 191f / 255f;
        } else {
            // Color.Gold = (255, 215, 0)
            r = 1.0f; g = 215f / 255f; b = 0.0f; a = 1.0f;
        }

        int num = number;
        do {
            int digit = num % 10;
            num /= 10;

            ElevatorTextures.drawDigitTint(graphics, x, y, digit, digitScale, r, g, b, a);

            // SDV: position.X -= 8f * 0.5f * 4f - 4f = 12 SDV px
            x -= px(12);
        } while (num > 0);
    }

    private static int numberOfDigits(int number) {
        int count = 1;
        number /= 10;
        while (number != 0) {
            number /= 10;
            count++;
        }
        return count;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 由 render() 完全接管
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // SDV 原版不绘制标题
    }
}
