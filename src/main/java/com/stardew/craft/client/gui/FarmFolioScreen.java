package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.client.font.StardewFonts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Shared stationery and controls; each task owns its layout and server actions. */
public abstract class FarmFolioScreen extends Screen {
    public static final int INK = 0xFF623E2A,
            MUTED = 0xFF805D3C,
            LIGHT = 0xFFFFF0C7,
            RED = 0xFF813B2B,
            CANVAS_INK = 0xFFFFF0C7,
            CANVAS_MUTED = 0xFFFFE4B3,
            CANVAS_RED = 0xFFFFE4DC;
    protected final Screen parent;
    protected int x, y, w, h, mouseX, mouseY;
    private Component hover;
    private final java.util.Map<Button,Component> disabledReasons=new java.util.IdentityHashMap<>();
    private float zoom = 1;

    protected FarmFolioScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    public static Component ui(String key, Object... args) {
        return Component.translatable("gui.stardewcraft.farm_ui." + key, args);
    }

    protected int preferredWidth() {
        return 548;
    }

    protected int preferredHeight() {
        return 340;
    }

    protected int maximumTitleWidth() {
        return Math.max(104, w - 140);
    }

    @Override
    protected final void init() {
        clearWidgets();
        disabledReasons.clear();
        font = StardewFonts.small();
        w = preferredWidth();
        h = preferredHeight();
        zoom = Math.min(1f, Math.min((width - 16f) / w, (height - 20f) / h));
        x = Math.round((width / zoom - w) / 2);
        y = Math.round((height / zoom - h) / 2);
        layout();
    }

    protected abstract void layout();

    protected abstract void paint(GuiGraphics g);

    protected void overlay(GuiGraphics g, int mx, int my) {}

    @Override
    public final void renderBackground(GuiGraphics g, int mx, int my, float tick) {}

    @Override
    public final void render(GuiGraphics g, int mx, int my, float tick) {
        int actualX = mx, actualY = my;
        mx = (int) (mx / zoom);
        my = (int) (my / zoom);
        mouseX = mx;
        mouseY = my;
        hover = null;
        g.fill(0, 0, width, height, 0x99000000);
        g.pose().pushPose();
        g.pose().scale(zoom, zoom, 1);
        FarmSetupArt.box(g, "folio", x, y, w, h, 6);
        FarmSetupArt.tile(g, "canvas", x + 6, y + 6, w - 12, h - 12, 64);
        int titleW =
                Math.min(
                        maximumTitleWidth(),
                        Math.max(130, Math.round(font.width(title) / zoom) + 28));
        FarmSetupArt.box(g, "plaque", x + 14, y - 8, titleW, 28, 4);
        label(g, title, x + 28, y + 2, titleW - 28, LIGHT);
        paint(g);
        // Never invoke a parent screen renderer after painting the paper: it can
        // repaint the background. Keep native widget input/narration, draw once.
        for (var child : children())
            if (child instanceof AbstractWidget widget) widget.render(g, mx, my, tick);
        overlay(g, mx, my);
        g.pose().popPose();
        if (hover != null) g.renderTooltip(font, hover, actualX, actualY);
    }

    protected final void paper(GuiGraphics g, int px, int py, int pw, int ph) {
        FarmSetupArt.paper(g, px, py, pw, ph);
    }

    protected final void box(GuiGraphics g, String material, int px, int py, int pw, int ph) {
        FarmSetupArt.box(g, material, px, py, pw, ph, 4);
    }

    protected final void rule(GuiGraphics g, int px, int py, int pw) {
        g.fill(px, py, px + pw, py + 1, 0xFFDFBB84);
        g.fill(px, py + 1, px + pw, py + 2, LIGHT);
    }

    /** Paper may shrink with GUI scale; text retains the game's readable font size. */
    private void text(GuiGraphics g, String value, float px, float py, int color) {
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(1 / zoom, 1 / zoom, 1);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    protected final void label(
            GuiGraphics g, Component value, int px, int py, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        int available = Math.max(0, (int) (maxWidth * zoom));
        String full = value.getString();
        String shown =
                font.width(full) <= available
                        ? full
                        : font.plainSubstrByWidth(full, Math.max(0, available - font.width("…")))
                                + "…";
        text(g, shown, px, py, color);
        if (!shown.equals(full)
                && mouseX >= px
                && mouseX < px + maxWidth
                && mouseY >= py
                && mouseY < py + StardewFonts.lineHeight(font) / zoom + 4) hover = value;
    }

    protected final int paragraph(
            GuiGraphics g, Component value, int px, int py, int pw, int bottom, int color) {
        int cy = py;
        int step = (int) Math.ceil((StardewFonts.lineHeight(font) + 3) / zoom);
        for (var line : font.split(value, Math.max(16, (int) (pw * zoom)))) {
            if (cy + StardewFonts.lineHeight(font) / zoom > bottom) {
                if (mouseX >= px && mouseX < px + pw && mouseY >= py && mouseY < bottom)
                    hover = value;
                break;
            }
            g.pose().pushPose();
            g.pose().translate(px, cy, 0);
            g.pose().scale(1 / zoom, 1 / zoom, 1);
            g.drawString(font, line, 0, 0, color, false);
            g.pose().popPose();
            cy += step;
        }
        return cy;
    }

    protected final void icon(GuiGraphics g, String name, int px, int py, int size) {
        image(
                g,
                new ResourceLocation("stardewcraft:textures/gui/farm_buildings/" + name + ".png"),
                16,
                16,
                px,
                py,
                size,
                size,
                false);
    }

    protected final void glyph(GuiGraphics g, String name, int px, int py) {
        FarmSetupArt.sprite(g, name, px, py, 16, 16);
    }

    public static void image(
            GuiGraphics g,
            ResourceLocation texture,
            int tw,
            int th,
            int px,
            int py,
            int pw,
            int ph,
            boolean black) {
        if (texture == null || tw <= 0 || th <= 0 || pw <= 0 || ph <= 0) return;
        if (net.minecraft.client.Minecraft.getInstance()
                .getResourceManager()
                .getResource(texture)
                .isEmpty()) return;
        float scale = Math.min((float) pw / tw, (float) ph / th);
        if (scale >= 1) scale = (float) Math.floor(scale);
        g.pose().pushPose();
        g.pose().translate(px + (pw - tw * scale) / 2, py + (ph - th * scale) / 2, 0);
        g.pose().scale(scale, scale, 1);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShaderColor(black ? 0 : 1, black ? 0 : 1, black ? 0 : 1, 1);
            g.blit(texture, 0, 0, 0, 0, tw, th, tw, th);
            g.flush();
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            g.pose().popPose();
        }
    }

    protected final void item(GuiGraphics g, ItemStack stack, int px, int py, int scale) {
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        if (mouseX >= px && mouseX < px + 16 * scale && mouseY >= py && mouseY < py + 16 * scale)
            hover = stack.getHoverName();
    }

    protected final void materialRow(GuiGraphics g, com.stardew.craft.shop.CarpenterBlueprint.MaterialEntry material,
                                     int px, int py, int width) {
        var stack = FarmMaterialCosts.stack(material);
        int owned = FarmMaterialCosts.owned(material);
        item(g, stack, px, py, 1);
        label(g, stack.isEmpty() ? Component.literal(material.itemId()) : stack.getHoverName(),
                px + 22, py + 4, width - 100, INK);
        label(g, Component.literal(owned + " / " + material.count()), px + width - 76, py + 4, 76,
                owned >= material.count() ? INK : RED);
    }

    protected final void money(GuiGraphics g, int amount, int px, int py, int color) {
        image(
                g,
                new ResourceLocation("stardewcraft:textures/gui/common/gold_coin_1_6.png"),
                14,
                13,
                px,
                py,
                16,
                16,
                false);
        label(
                g,
                Component.literal(String.format(java.util.Locale.ROOT, "%,d", amount)),
                px + 22,
                py + 4,
                90,
                color);
    }

    protected final void disabledReason(Button button, Component reason) {
        if(reason==null)disabledReasons.remove(button);else disabledReasons.put(button,reason);
    }

    protected final Button button(Component text, int px, int py, int pw, int ph, Runnable action) {
        return button(text, px, py, pw, ph, "button", null, action);
    }

    protected final Button button(
            Component text,
            int px,
            int py,
            int pw,
            int ph,
            String material,
            String icon,
            Runnable action) {
        return addRenderableWidget(
                new Button(px, py, pw, ph, text, b -> action.run(), s -> s.get()) {
                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                        String art =
                                !active
                                        ? "packet"
                                        : isHoveredOrFocused() && material.equals("button")
                                                ? "button_hover"
                                                : material;
                        box(g, art, getX(), getY(), getWidth(), getHeight());
                        if (icon != null)
                            icon(g, icon, getX() + 6, getY() + (getHeight() - 16) / 2, 16);
                        int inset = icon == null ? 8 : 28;
                        int textW = Math.max(1, (int) ((getWidth() - inset - 8) * zoom));
                        String full = getMessage().getString();
                        String shown =
                                font.width(full) <= textW
                                        ? full
                                        : font.plainSubstrByWidth(
                                                        full, Math.max(0, textW - font.width("…")))
                                                + "…";
                        text(
                                g,
                                shown,
                                getX() + inset + (textW - font.width(shown)) / (2 * zoom),
                                getY() + (getHeight() - StardewFonts.lineHeight(font) / zoom) / 2,
                                active ? INK : MUTED);
                        if (isHoveredOrFocused()) hover = !active ? disabledReasons.getOrDefault(this,getMessage()) : getMessage();
                        if (isFocused())
                            g.renderOutline(
                                    getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, INK);
                    }
                });
    }

    protected final Button tile(
            Component description,
            int px,
            int py,
            int pw,
            int ph,
            Runnable action,
            java.util.function.BiConsumer<GuiGraphics, Button> painter) {
        return addRenderableWidget(
                new Button(px, py, pw, ph, description, b -> action.run(), s -> s.get()) {
                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                        painter.accept(g, this);
                        if (isHoveredOrFocused()) hover = getMessage();
                        if (isFocused())
                            g.renderOutline(
                                    getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, INK);
                    }
                });
    }

    protected final Button arrow(boolean next, int px, int py, boolean enabled, Runnable action) {
        Button button =
                addRenderableWidget(
                        new Button(
                                px,
                                py,
                                24,
                                24,
                                ui(next ? "next" : "previous"),
                                b -> action.run(),
                                s -> s.get()) {
                            @Override
                            protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                                box(g, "tab", getX(), getY(), 24, 24);
                                FarmSetupArt.sprite(
                                        g,
                                        "arrow_"
                                                + (next ? "right" : "left")
                                                + (!active ? "_disabled" : ""),
                                        getX() + 6,
                                        getY() + 6,
                                        12,
                                        12);
                                if (isHoveredOrFocused()) hover = getMessage();
                            }
                        });
        button.active = enabled;
        return button;
    }

    protected final EditBox field(
            Component label, String value, int px, int py, int pw, int maxLength) {
        EditBox field =
                new EditBox(font, px + 8, py + 7, pw - 16, 20, label) {
                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                        box(
                                g,
                                isFocused() ? "packet_selected" : "packet",
                                getX() - 8,
                                getY() - 7,
                                getWidth() + 16,
                                32);
                        g.enableScissor(
                                getX(), getY() - 1, getX() + getWidth(), getY() + getHeight() + 1);
                        g.pose().pushPose();
                        g.pose().translate(getX(), getY(), 0);
                        g.pose().scale(1 / zoom, 1 / zoom, 1);
                        g.pose().translate(-getX(), -getY(), 0);
                        super.renderWidget(g, mx, my, tick);
                        g.pose().popPose();
                        g.disableScissor();
                    }

                    @Override
                    public int getInnerWidth() {
                        return Math.max(1, (int) (super.getInnerWidth() * zoom));
                    }

                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        return super.mouseClicked(
                                isMouseOver(mx, my) ? getX() + (mx - getX()) * zoom : mx,
                                my,
                                button);
                    }
                };
        field.setBordered(false);
        field.setTextColor(INK);
        field.setTextColorUneditable(MUTED);
        field.setMaxLength(maxLength);
        field.setValue(value);
        return addRenderableWidget(field);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(mx / zoom, my / zoom, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx / zoom, my / zoom, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx / zoom, my / zoom, button, dx / zoom, dy / zoom);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return super.mouseScrolled(mx / zoom, my / zoom, dx, dy);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(mx / zoom, my / zoom);
    }

    public final Screen parentScreen() {
        return parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
