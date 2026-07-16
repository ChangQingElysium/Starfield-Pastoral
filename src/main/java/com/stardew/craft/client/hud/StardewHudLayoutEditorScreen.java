package com.stardew.craft.client.hud;

import com.stardew.craft.Config;
import com.stardew.craft.client.ModClientEvents;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;

/** Direct-manipulation editor for the player-facing HUD groups selected for customization. */
public final class StardewHudLayoutEditorScreen extends Screen {
    private static final Config.HudElement[] EDITABLE_ELEMENTS = {
            Config.HudElement.MAIN,
            Config.HudElement.PLAYER_BARS,
            Config.HudElement.ITEM_PICKUP,
            Config.HudElement.TEXT_MESSAGE,
            Config.HudElement.WEAPON_SKILLS,
            Config.HudElement.SKILL_XP
    };

    private static final int SNAP_DISTANCE = 8;
    private static final int GRID_SIZE = 8;
    private static final int HANDLE_HIT_SIZE = 7;
    private static final int FRAME_IDLE = 0xFFFFE2A0;
    private static final int FRAME_ACTIVE = 0xFFFFC94D;
    private static final int FRAME_HOVER = 0x90FFFFFF;

    private final Screen parent;
    private final EnumMap<Config.HudElement, HudBox> boxes = new EnumMap<>(Config.HudElement.class);
    private Config.HudElement selected;
    private DragMode dragMode = DragMode.NONE;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private HudBox dragStartBox;

    public StardewHudLayoutEditorScreen(Screen parent) {
        super(Component.translatable("stardewcraft.hud_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        loadPlacements();
    }

    private void loadPlacements() {
        boxes.clear();
        for (Config.HudElement element : EDITABLE_ELEMENTS) {
            int baseWidth = element.baseWidth();
            int baseHeight = element.baseHeight();
            if (element == Config.HudElement.TEXT_MESSAGE) {
                StardewHudMessageManager.CornerBoxSize size =
                        StardewHudMessageManager.textMessagePreviewSize(font);
                baseWidth = size.width();
                baseHeight = size.height();
            }
            StardewHudLayout.Placement placement = StardewHudLayout.current(
                    element, width, height, baseWidth, baseHeight);
            boxes.put(element, new HudBox(placement.x(), placement.y(), placement.width(), placement.height(),
                    placement.scale(), baseWidth, baseHeight));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (resetButton().contains(mouseX, mouseY)) {
            if (selected == null) {
                StardewHudLayout.reset();
            } else {
                StardewHudLayout.reset(selected);
            }
            loadPlacements();
            playUi(ModSounds.SMALL_SELECT.get(), 0.7F, 0.9F);
            return true;
        }

        if (selected != null) {
            HudBox selectedBox = boxes.get(selected);
            DragMode handle = handleAt(selectedBox, mouseX, mouseY);
            if (handle != DragMode.NONE) {
                beginDrag(handle, mouseX, mouseY, selectedBox);
                return true;
            }
        }

        for (int i = EDITABLE_ELEMENTS.length - 1; i >= 0; i--) {
            Config.HudElement element = EDITABLE_ELEMENTS[i];
            HudBox box = boxes.get(element);
            if (box.contains(mouseX, mouseY)) {
                selected = element;
                beginDrag(DragMode.MOVE, mouseX, mouseY, box);
                return true;
            }
        }
        selected = null;
        dragMode = DragMode.NONE;
        dragStartBox = null;
        return true;
    }

    private void beginDrag(DragMode mode, double mouseX, double mouseY, HudBox box) {
        dragMode = mode;
        dragStartMouseX = mouseX;
        dragStartMouseY = mouseY;
        dragStartBox = box.copy();
        playUi(ModSounds.SMALL_SELECT.get(), 0.4F, 1.08F);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || selected == null || dragMode == DragMode.NONE || dragStartBox == null) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        HudBox box = boxes.get(selected);
        if (dragMode == DragMode.MOVE) {
            moveSelected(box, mouseX, mouseY);
        } else {
            resizeSelected(box, mouseX, mouseY);
        }
        return true;
    }

    private void moveSelected(HudBox box, double mouseX, double mouseY) {
        int rawX = (int) Math.round(dragStartBox.x + mouseX - dragStartMouseX);
        int rawY = (int) Math.round(dragStartBox.y + mouseY - dragStartMouseY);
        int x = snapToGrid(rawX);
        int y = snapToGrid(rawY);
        int centeredX = width / 2 - box.width / 2;
        int centeredY = height / 2 - box.height / 2;
        if (Math.abs(rawX - centeredX) <= SNAP_DISTANCE) x = centeredX;
        if (Math.abs(rawY - centeredY) <= SNAP_DISTANCE) y = centeredY;
        box.x = Mth.clamp(x, 0, Math.max(0, width - box.width));
        box.y = Mth.clamp(y, 0, Math.max(0, height - box.height));
    }

    private static int snapToGrid(int value) {
        return Math.round(value / (float) GRID_SIZE) * GRID_SIZE;
    }

    private void resizeSelected(HudBox box, double mouseX, double mouseY) {
        int baseWidth = box.baseWidth;
        int baseHeight = box.baseHeight;
        float startScale = dragStartBox.scale;
        float scaleX = startScale;
        float scaleY = startScale;
        if (dragMode.left) scaleX = (float) (dragStartBox.right() - mouseX) / baseWidth;
        if (dragMode.right) scaleX = (float) (mouseX - dragStartBox.x) / baseWidth;
        if (dragMode.top) scaleY = (float) (dragStartBox.bottom() - mouseY) / baseHeight;
        if (dragMode.bottom) scaleY = (float) (mouseY - dragStartBox.y) / baseHeight;

        float nextScale;
        if (dragMode.hasHorizontal() && dragMode.hasVertical()) {
            nextScale = Math.abs(scaleX - startScale) >= Math.abs(scaleY - startScale) ? scaleX : scaleY;
        } else {
            nextScale = dragMode.hasHorizontal() ? scaleX : scaleY;
        }
        nextScale = Mth.clamp(nextScale,
                StardewHudLayout.MIN_SCALE_PERCENT / 100.0F,
                StardewHudLayout.MAX_SCALE_PERCENT / 100.0F);

        int nextWidth = Math.max(1, Math.round(baseWidth * nextScale));
        int nextHeight = Math.max(1, Math.round(baseHeight * nextScale));
        int nextX = dragMode.left ? dragStartBox.right() - nextWidth
                : dragMode.right ? dragStartBox.x : dragStartBox.centerX() - nextWidth / 2;
        int nextY = dragMode.top ? dragStartBox.bottom() - nextHeight
                : dragMode.bottom ? dragStartBox.y : dragStartBox.centerY() - nextHeight / 2;

        box.scale = nextScale;
        box.width = nextWidth;
        box.height = nextHeight;
        box.x = Mth.clamp(nextX, 0, Math.max(0, width - nextWidth));
        box.y = Mth.clamp(nextY, 0, Math.max(0, height - nextHeight));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragMode != DragMode.NONE) {
            saveSelected();
            dragMode = DragMode.NONE;
            dragStartBox = null;
            playUi(ModSounds.SMALL_SELECT.get(), 0.35F, 0.94F);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveSelected() {
        if (selected == null) return;
        HudBox box = boxes.get(selected);
        StardewHudLayout.setScalePercent(selected, Math.round(box.scale * 100.0F));
        StardewHudLayout.saveDraggedPosition(selected, width, height,
                box.x, box.y, box.width, box.height);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selected == null) return super.keyPressed(keyCode, scanCode, modifiers);
        int step = hasShiftDown() ? 5 : 1;
        HudBox box = boxes.get(selected);
        if (keyCode == GLFW.GLFW_KEY_LEFT) box.x -= step;
        else if (keyCode == GLFW.GLFW_KEY_RIGHT) box.x += step;
        else if (keyCode == GLFW.GLFW_KEY_UP) box.y -= step;
        else if (keyCode == GLFW.GLFW_KEY_DOWN) box.y += step;
        else return super.keyPressed(keyCode, scanCode, modifiers);
        box.x = Mth.clamp(box.x, 0, Math.max(0, width - box.width));
        box.y = Mth.clamp(box.y, 0, Math.max(0, height - box.height));
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (selected != null && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            saveSelected();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x78000000);
        drawGrid(graphics);
        for (Config.HudElement element : EDITABLE_ELEMENTS) {
            renderActualPreview(graphics, element, boxes.get(element));
        }
        Config.HudElement hovered = elementAt(mouseX, mouseY);
        if (hovered != null && hovered != selected) {
            drawOutline(graphics, boxes.get(hovered), FRAME_HOVER);
        }
        if (selected != null) {
            drawSelectedFrame(graphics, boxes.get(selected));
        }
        drawResetButton(graphics, mouseX, mouseY);
    }

    private void drawGrid(GuiGraphics graphics) {
        int dotColor = 0x28FFFFFF;
        for (int y = 0; y < height; y += GRID_SIZE) {
            for (int x = 0; x < width; x += GRID_SIZE) {
                graphics.fill(x, y, x + 1, y + 1, dotColor);
            }
        }
        drawDashedVertical(graphics, width / 3, 0x407A5638);
        drawDashedVertical(graphics, width * 2 / 3, 0x407A5638);
        drawDashedHorizontal(graphics, height / 3, 0x407A5638);
        drawDashedHorizontal(graphics, height * 2 / 3, 0x407A5638);
        drawDashedVertical(graphics, width / 2, 0x70E5B85D);
        drawDashedHorizontal(graphics, height / 2, 0x70E5B85D);
    }

    private void drawDashedVertical(GuiGraphics graphics, int x, int color) {
        for (int y = 0; y < height; y += 8) {
            graphics.fill(x, y, x + 1, Math.min(y + 4, height), color);
        }
    }

    private void drawDashedHorizontal(GuiGraphics graphics, int y, int color) {
        for (int x = 0; x < width; x += 8) {
            graphics.fill(x, y, Math.min(x + 4, width), y + 1, color);
        }
    }

    private void renderActualPreview(GuiGraphics graphics, Config.HudElement element, HudBox box) {
        switch (element) {
            case MAIN -> StardewTimeHud.renderPreview(graphics, box.x, box.y, box.scale);
            case PLAYER_BARS -> StardewPlayerHud.renderPreview(graphics, box.x, box.y, box.scale);
            case ITEM_PICKUP -> StardewHudMessageManager.renderItemPickupPreview(
                    graphics, font, box.x, box.y, box.scale);
            case TEXT_MESSAGE -> StardewHudMessageManager.renderTextMessagePreview(
                    graphics, font, box.x, box.y, box.scale);
            case WEAPON_SKILLS -> ModClientEvents.renderWeaponSkillPreview(
                    graphics, box.x, box.y, box.scale);
            case SKILL_XP -> SkillExperienceHud.renderPreview(graphics, box.x, box.y, box.scale);
            default -> {
            }
        }
    }

    private Config.HudElement elementAt(double mouseX, double mouseY) {
        for (int i = EDITABLE_ELEMENTS.length - 1; i >= 0; i--) {
            Config.HudElement element = EDITABLE_ELEMENTS[i];
            if (boxes.get(element).contains(mouseX, mouseY)) return element;
        }
        return null;
    }

    private void drawSelectedFrame(GuiGraphics graphics, HudBox box) {
        int color = dragMode == DragMode.NONE ? FRAME_IDLE : FRAME_ACTIVE;
        drawOutline(graphics, new HudBox(box.x - 2, box.y - 2, box.width + 4, box.height + 4,
                box.scale, box.baseWidth, box.baseHeight), color);
        for (DragMode handle : DragMode.RESIZE_HANDLES) {
            Point point = handlePoint(box, handle);
            graphics.fill(point.x - 3, point.y - 3, point.x + 3, point.y + 3, 0xFF4A2815);
            graphics.fill(point.x - 2, point.y - 2, point.x + 2, point.y + 2, color);
        }
    }

    private static void drawOutline(GuiGraphics graphics, HudBox box, int color) {
        graphics.fill(box.x, box.y, box.right(), box.y + 1, color);
        graphics.fill(box.x, box.bottom() - 1, box.right(), box.bottom(), color);
        graphics.fill(box.x, box.y, box.x + 1, box.bottom(), color);
        graphics.fill(box.right() - 1, box.y, box.right(), box.bottom(), color);
    }

    private DragMode handleAt(HudBox box, double mouseX, double mouseY) {
        for (DragMode handle : DragMode.RESIZE_HANDLES) {
            Point point = handlePoint(box, handle);
            if (Math.abs(mouseX - point.x) <= HANDLE_HIT_SIZE
                    && Math.abs(mouseY - point.y) <= HANDLE_HIT_SIZE) return handle;
        }
        return DragMode.NONE;
    }

    private static Point handlePoint(HudBox box, DragMode mode) {
        int x = mode.left ? box.x : mode.right ? box.right() : box.centerX();
        int y = mode.top ? box.y : mode.bottom ? box.bottom() : box.centerY();
        return new Point(x, y);
    }

    private void drawResetButton(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect button = resetButton();
        boolean hovered = button.contains(mouseX, mouseY);
        graphics.fill(button.x, button.y, button.right(), button.bottom(), hovered ? 0xD04A2815 : 0xA02B180D);
        int border = hovered ? 0xFFFFD36B : 0xFFC89A52;
        graphics.fill(button.x, button.y, button.right(), button.y + 1, border);
        graphics.fill(button.x, button.bottom() - 1, button.right(), button.bottom(), border);
        graphics.fill(button.x, button.y, button.x + 1, button.bottom(), border);
        graphics.fill(button.right() - 1, button.y, button.right(), button.bottom(), border);
        Component label = resetButtonLabel();
        graphics.drawString(font, label, button.centerX() - font.width(label) / 2,
                button.y + (button.height - font.lineHeight) / 2, 0xFFFFE7B2, false);
    }

    private Rect resetButton() {
        int buttonWidth = Math.max(64, font.width(resetButtonLabel()) + 20);
        int buttonHeight = Math.max(22, font.lineHeight + 10);
        return new Rect(width - buttonWidth - 10, height - buttonHeight - 10, buttonWidth, buttonHeight);
    }

    private Component resetButtonLabel() {
        return Component.translatable(selected == null
                ? "stardewcraft.hud_editor.reset_all"
                : "stardewcraft.hud_editor.reset_selected");
    }

    private void playUi(SoundEvent sound, float volume, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
    }

    @Override
    public void onClose() {
        if (dragMode != DragMode.NONE) saveSelected();
        playUi(ModSounds.BIG_DESELECT.get(), 0.65F, 1.0F);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class HudBox {
        int x;
        int y;
        int width;
        int height;
        float scale;
        final int baseWidth;
        final int baseHeight;

        HudBox(int x, int y, int width, int height, float scale, int baseWidth, int baseHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.baseWidth = baseWidth;
            this.baseHeight = baseHeight;
        }

        HudBox copy() { return new HudBox(x, y, width, height, scale, baseWidth, baseHeight); }
        int right() { return x + width; }
        int bottom() { return y + height; }
        int centerX() { return x + width / 2; }
        int centerY() { return y + height / 2; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }

    private enum DragMode {
        NONE(false, false, false, false),
        MOVE(false, false, false, false),
        TOP_LEFT(true, false, true, false),
        TOP(false, false, true, false),
        TOP_RIGHT(false, true, true, false),
        RIGHT(false, true, false, false),
        BOTTOM_RIGHT(false, true, false, true),
        BOTTOM(false, false, false, true),
        BOTTOM_LEFT(true, false, false, true),
        LEFT(true, false, false, false);

        static final DragMode[] RESIZE_HANDLES = {
                TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT
        };
        final boolean left;
        final boolean right;
        final boolean top;
        final boolean bottom;

        DragMode(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        boolean hasHorizontal() { return left || right; }
        boolean hasVertical() { return top || bottom; }
    }

    private record Point(int x, int y) {
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        int centerX() { return x + width / 2; }
        boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
}
