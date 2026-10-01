package com.stardew.craft.client.gui;

import com.stardew.craft.Config;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.ReadingTextLayout;
import com.stardew.craft.client.gui.menu.MenuPageArt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Local, immediately saved reading preference with a preview in the selected language. */
public final class ReadingTextSettingsScreen extends Screen implements com.stardew.craft.port.PortScreen {
    private final Screen parent;
    private int x, y, panelW, panelH, previewY, previewBottom, previewScroll, previewHeight;
    private List<FormattedCharSequence> description;
    private SizeSlider slider;
    private Button smaller, larger, reset;

    public ReadingTextSettingsScreen(Screen parent) {
        super(Component.translatable("config.stardewcraft.client.reading_text_scale"));
        this.parent = parent;
    }

    @Override protected void init() {
        font = StardewFonts.small();
        panelW = Math.min(444, width - 28);
        panelH = Math.min(242, height - 28);
        x = (width - panelW) / 2;
        y = (height - panelH) / 2;
        description = font.split(Component.translatable("stardewcraft.settings.reading.description"), panelW - 32);
        previewY = y + 26 + StardewFonts.lineHeight(font) + description.size() * (StardewFonts.lineHeight(font) + 2);
        int controlsY = y + panelH - 62;
        previewBottom = controlsY - 8;
        smaller = addRenderableWidget(Button.builder(Component.literal("−"), b -> setPercent(percent() - 25))
                .bounds(x + 16, controlsY, 24, 20)
                .tooltip(Tooltip.create(Component.translatable("stardewcraft.settings.reading.smaller"))).build());
        slider = addRenderableWidget(new SizeSlider(x + 44, controlsY, panelW - 88));
        larger = addRenderableWidget(Button.builder(Component.literal("+"), b -> setPercent(percent() + 25))
                .bounds(x + panelW - 40, controlsY, 24, 20)
                .tooltip(Tooltip.create(Component.translatable("stardewcraft.settings.reading.larger"))).build());
        reset = addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> setPercent(100))
                .bounds(x + panelW / 2 - 124, controlsY + 30, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(x + panelW / 2 + 4, controlsY + 30, 120, 20).build());
        updateControls();
    }

    private int percent() { return Config.CLIENT.READING_TEXT_SCALE_PERCENT.get(); }

    private void setPercent(int value) {
        int next = com.stardew.craft.port.PortJava.clamp(value, ReadingTextLayout.MIN_PERCENT, ReadingTextLayout.MAX_PERCENT);
        if (next == percent()) { updateControls(); return; }
        Config.CLIENT.READING_TEXT_SCALE_PERCENT.set(next);
        Config.CLIENT_SPEC.save();
        previewScroll = 0;
        updateControls();
    }

    private void updateControls() {
        smaller.active = percent() > ReadingTextLayout.MIN_PERCENT;
        larger.active = percent() < ReadingTextLayout.MAX_PERCENT;
        reset.active = percent() != ReadingTextLayout.DEFAULT_PERCENT;
        slider.sync();
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0413028);
        MenuPageArt.paper(g, x, y, panelW, panelH);
        g.drawString(font, title, x + 16, y + 12, MenuPageArt.INK, false);
        int textY = y + 18 + StardewFonts.lineHeight(font);
        for (var line : description) {
            g.drawString(font, line, x + 16, textY, MenuPageArt.INK, false);
            textY += StardewFonts.lineHeight(font) + 2;
        }
        MenuPageArt.box(g, "inset", x + 16, previewY, panelW - 32, previewBottom - previewY);
        var previewFont = StardewFonts.dialogue();
        float textScale = .75F * StardewFonts.readingScale();
        int step = Math.max(1, (int) Math.ceil(StardewFonts.lineHeight(previewFont) * textScale)) + 1;
        var lines = previewFont.split(Component.translatable("stardewcraft.settings.reading.preview"),
                Math.max(1, (int) ((panelW - 52) / textScale)));
        previewHeight = lines.size() * step;
        previewScroll = com.stardew.craft.port.PortJava.clamp(previewScroll, 0, Math.max(0, previewHeight - (previewBottom - previewY - 12)));
        g.enableScissor(x + 22, previewY + 6, x + panelW - 22, previewBottom - 6);
        g.pose().pushPose();
        g.pose().translate(x + 26, previewY + 6 - previewScroll, 0);
        g.pose().scale(textScale, textScale, 1);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(previewFont, lines.get(i), 0, Math.round(i * step / textScale), MenuPageArt.INK, false);
        }
        g.pose().popPose();
        g.disableScissor();
        super.render(g, mx, my, pt);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dy) {
        double dx = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2
        if (mx >= x + 16 && mx < x + panelW - 16 && my >= previewY && my < previewBottom) {
            previewScroll = com.stardew.craft.port.PortJava.clamp(previewScroll - (int) (dy * 16), 0,
                    Math.max(0, previewHeight - (previewBottom - previewY - 12)));
            return true;
        }
        return super.mouseScrolled(mx, my, dy);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }

    private final class SizeSlider extends AbstractSliderButton {
        SizeSlider(int x, int y, int width) {
            super(x, y, width, 20, Component.empty(), 0);
            sync();
        }
        void sync() {
            value = (percent() - ReadingTextLayout.MIN_PERCENT) / 125.0D;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(Component.translatable("options.generic_value", title, percent() + "%"));
        }
        @Override protected void applyValue() {
            setPercent(ReadingTextLayout.MIN_PERCENT + (int) Math.round(value * 5) * ReadingTextLayout.STEP_PERCENT);
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == 262 || key == 263) {
                setPercent(percent() + (key == 262 ? 25 : -25));
                return true;
            }
            if (key == 268 || key == 269) {
                setPercent(key == 268 ? ReadingTextLayout.MIN_PERCENT : ReadingTextLayout.MAX_PERCENT);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
    }
}
