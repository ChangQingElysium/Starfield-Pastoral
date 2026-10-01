package com.stardew.craft.client.gui.auction;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.menu.MenuPageArt;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared viewport, native-size controls and final-pass tooltips for auction screens. */
@SuppressWarnings("null")
abstract class AuctionScreen extends Screen {
    protected int left, top, panelW, panelH, bodyTop, bodyBottom, contentW, line, controlH;
    protected int contentHeight, scroll;
    protected int mx, my;
    private int footerH;
    private boolean draggingScroll, opened;
    private Component hoverText;
    private ItemStack hoverItem = ItemStack.EMPTY;
    private final List<BodyWidget> bodyWidgets = new ArrayList<>();
    private final List<AbstractWidget> footerWidgets = new ArrayList<>();
    private record BodyWidget(AbstractWidget widget, int y) { }

    protected AuctionScreen(String titleKey) { super(Component.translatable(titleKey)); }
    protected Component tr(String suffix, Object... args) {
        return Component.translatable("stardewcraft.auction." + suffix, args);
    }
    protected int preferredWidth() { return 396; }
    protected int preferredHeight() { return 318; }
    protected Component heading() { return title; }
    protected abstract void layout();
    protected abstract void drawBody(GuiGraphics g, float partialTick);
    protected void updateState() { }

    @Override protected final void init() {
        font = StardewFonts.small();
        line = StardewFonts.lineHeight(font);
        controlH = line + 14;
        footerH = controlH + 8;
        var viewport = AuctionLayout.viewport(width, height, preferredWidth(), preferredHeight(), line, footerH);
        panelW = viewport.width(); panelH = viewport.height();
        left = viewport.x(); top = viewport.y(); contentW = viewport.contentWidth();
        bodyTop = viewport.bodyTop(); bodyBottom = viewport.bodyBottom();
        if (getFocused() != null) getFocused().setFocused(false);
        setFocused(null);
        bodyWidgets.clear(); footerWidgets.clear(); clearWidgets();
        layout();
        bodyBottom = top + panelH - 12 - footerH;
        setScroll(scroll);
        if (!opened) {
            opened = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOK_READ.get(), 1f, 0.35f));
        }
    }
    protected void rebuild() { init(); }
    protected void resetScroll() { scroll = 0; }
    protected <T extends AbstractWidget> T body(T widget, int y) {
        bodyWidgets.add(new BodyWidget(widget, y));
        addWidget(widget);
        return widget;
    }
    protected Button button(int x, int y, int w, Component label, Runnable action) {
        return body(new AuctionButton(left + 14 + x, bodyTop + y, w, buttonHeight(label, w), label, false, action), y);
    }
    protected Button actionArea(int x, int y, int w, int h, Component label, Runnable action,
                                java.util.function.BiConsumer<GuiGraphics, Button> painter) {
        return body(new Button(left + 14 + x, bodyTop + y, w, h, label, b -> action.run(), supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g, int mx, int my, float tick) {
                painter.accept(g, this);
                if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), AuctionUi.GOLD);
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
                manager.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, 0.3f));
            }
        }, y);
    }
    protected void tooltip(Component text) { hoverText = text; }
    protected void number(GuiGraphics g, String text, int x, int y, int scale, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false); g.pose().popPose();
    }
    protected Button inventoryButton(int slot, int x, int y, Runnable action) {
        Button b = new Button(left + 14 + x, bodyTop + y, 20, 20, inventory(slot).getHoverName(),
                pressed -> { if (!inventory(slot).isEmpty()) action.run(); }, supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                ItemStack stack = inventory(slot);
                active = !stack.isEmpty();
                setMessage(stack.isEmpty() ? tr("create.item_empty") : stack.getHoverName());
                CommonGuiTextures.drawItemSlot18(g, getX() + 1, getY() + 1, 1f);
                if (isHoveredOrFocused()) g.renderOutline(getX(), getY(), 20, 20, AuctionUi.GOLD);
                g.renderItem(stack, getX() + 2, getY() + 2);
                g.renderItemDecorations(minecraft.font, stack, getX() + 2, getY() + 2);
                if (isHovered() && !stack.isEmpty()) hoverItem = stack;
            }
        };
        b.active = !inventory(slot).isEmpty();
        return body(b, y);
    }
    protected int buttonHeight(Component label, int w) {
        return Math.max(controlH, font.split(label, Math.max(1, w - 16)).size() * (line + 2) + 10);
    }
    protected Button footer(Component label, boolean primary, boolean right, Runnable action) {
        int backWidth = Math.min((contentW - 8) / 3, Math.max(56, font.width(tr("picker.cancel")) + 20));
        int w = right ? contentW - backWidth - 10 : backWidth;
        int h = buttonHeight(label, w);
        footerH = Math.max(footerH, h + 8);
        AuctionButton b = new AuctionButton(left + 14 + (right ? contentW - w : 0),
                top + panelH - 12 - h, w, h, label, primary, action);
        footerWidgets.add(b); addWidget(b);
        return b;
    }
    protected EditBox field(int x, int y, int w, Component label, String value, int max,
                            boolean numeric, Consumer<String> responder) {
        EditBox box = new EditBox(font, left + 14 + x + 7, bodyTop + y + 7, w - 14, line, label);
        box.setBordered(false); box.setTextShadow(false);
        box.setTextColor(AuctionUi.INK); box.setTextColorUneditable(AuctionUi.MUTED);
        box.setMaxLength(max);
        if (numeric) box.setFilter(s -> s.matches("[0-9]*"));
        box.setValue(value); box.setResponder(responder);
        body(box, y + 7);
        return box;
    }
    protected int wrappedHeight(Component text, int w) {
        return font.split(text, Math.max(1, w)).size() * (line + 3);
    }
    protected int paragraph(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (var entry : font.split(text, Math.max(1, w))) {
            g.drawString(font, entry, x, y, color, false); y += line + 3;
        }
        return y;
    }
    protected void text(GuiGraphics g, Component text, int x, int y, int w, int color) {
        String raw = text.getString();
        boolean clipped = font.width(raw) > w;
        String shown = clipped ? font.plainSubstrByWidth(raw, Math.max(0, w - font.width("…"))) + "…" : raw;
        g.drawString(font, shown, x, y, color, false);
        if (clipped && hit(x, y, w, line + 2)) hoverText = text;
    }
    protected void item(GuiGraphics g, ItemStack stack, int x, int y, int scale) {
        CommonGuiTextures.drawItem(g, stack, x, y, scale);
        if (hit(x, y, 16 * scale, 16 * scale) && !stack.isEmpty()) hoverItem = stack;
    }
    protected boolean hit(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    protected ItemStack inventory(int slot) {
        return minecraft.player != null && slot >= 0 && slot < 36
                ? minecraft.player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }
    protected int amount(String value) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (NumberFormatException ex) { return 0; }
    }
    protected void cancelSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 1f, 0.3f));
    }
    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateState();
        if (minecraft.screen != this) return;
        renderTransparentBackground(g);
        AuctionUi.frame(g, left, top, panelW, panelH);
        AuctionUi.sprite(g, "paddle", left + 13, top + 8, 24, 28);
        Component head = heading();
        String name = head.getString();
        int hw = panelW - 64;
        String shown = font.width(name) > hw ? font.plainSubstrByWidth(name, hw - font.width("…")) + "…" : name;
        g.drawString(font, shown, left + 44, top + 14, AuctionUi.INK, false);
        AuctionUi.rule(g, left + 14, bodyTop - 5, contentW);
        hoverText = null; hoverItem = ItemStack.EMPTY;
        boolean inBody = mouseY >= bodyTop && mouseY < bodyBottom;
        mx = inBody ? mouseX - left - 14 : -10000;
        my = inBody ? mouseY - bodyTop + scroll : -10000;
        g.enableScissor(left + 12, bodyTop, left + panelW - 12, bodyBottom);
        g.pose().pushPose(); g.pose().translate(left + 14, bodyTop - scroll, 0);
        drawBody(g, partialTick);
        g.pose().popPose();
        for (BodyWidget entry : bodyWidgets) {
            AbstractWidget widget = entry.widget;
            if (widget.getY() + widget.getHeight() <= bodyTop || widget.getY() >= bodyBottom) continue;
            if (widget instanceof EditBox field) {
                AuctionUi.box(g, "field", field.getX() - 7, field.getY() - 7, field.getWidth() + 14, line + 14);
                if (field.isFocused()) g.renderOutline(field.getX() - 7, field.getY() - 7,
                        field.getWidth() + 14, line + 14, AuctionUi.GOLD);
            }
            widget.render(g, inBody ? mouseX : -10000, inBody ? mouseY : -10000, partialTick);
        }
        g.disableScissor();
        AuctionUi.rule(g, left + 14, bodyBottom + 3, contentW);
        for (AbstractWidget widget : footerWidgets) widget.render(g, mouseX, mouseY, partialTick);
        if (maxScroll() > 0) {
            var bar = AuctionLayout.scrollbar(bodyTop, bodyBottom - bodyTop, contentHeight, scroll);
            g.fill(left + panelW - 15, bodyTop, left + panelW - 12, bodyBottom, 0xFFC6AD88);
            AuctionUi.box(g, "secondary", left + panelW - 17, bar.thumbY(), 7, bar.thumbHeight());
        }
        if (!name.equals(shown) && mouseX >= left + 44 && mouseX < left + panelW - 14
                && mouseY >= top + 10 && mouseY < bodyTop - 5) hoverText = head;
        if (!hoverItem.isEmpty()) g.renderTooltip(minecraft.font, hoverItem, mouseX, mouseY);
        else if (hoverText != null) MenuPageArt.tooltip(g, font, hoverText, mouseX, mouseY);
    }
    private int maxScroll() { return Math.max(0, contentHeight - (bodyBottom - bodyTop)); }
    protected void setScroll(int value) {
        scroll = Math.max(0, Math.min(value, maxScroll()));
        for (BodyWidget entry : bodyWidgets) entry.widget.setY(bodyTop + entry.y - scroll);
    }
    private void scrollFromMouse(double y) {
        setScroll(AuctionLayout.scrollbar(bodyTop, bodyBottom - bodyTop, contentHeight, scroll).scrollAt(y));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return false;
        if (maxScroll() > 0 && x >= left + panelW - 20 && x < left + panelW - 8 && y >= bodyTop && y < bodyBottom) {
            draggingScroll = true; scrollFromMouse(y); return true;
        }
        List<AbstractWidget> targets = new ArrayList<>(footerWidgets);
        if (y >= bodyTop && y < bodyBottom) for (BodyWidget entry : bodyWidgets) targets.add(entry.widget);
        for (AbstractWidget widget : targets) {
            if (widget instanceof EditBox field && x >= field.getX() - 7 && x < field.getX() + field.getWidth() + 7
                    && y >= field.getY() - 7 && y < field.getY() + field.getHeight() + 7) {
                if (getFocused() != null) getFocused().setFocused(false);
                setFocused(field); field.setFocused(true);
                field.mouseClicked(x, Math.max(field.getY(), Math.min(y, field.getY() + field.getHeight() - 1)), button);
                setDragging(true); return true;
            }
            var previousFocus = getFocused();
            if (widget.mouseClicked(x, y, button)) {
                if (minecraft.screen == this && children().contains(widget) && getFocused() == previousFocus) {
                    if (getFocused() != null) getFocused().setFocused(false);
                    setFocused(widget); widget.setFocused(true); setDragging(true);
                }
                return true;
            }
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll) { scrollFromMouse(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        draggingScroll = false; return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double sy) {
        if (x >= left && x < left + panelW && y >= bodyTop && y < bodyBottom) {
            setScroll(scroll - (int) Math.signum(sy) * (line + 12)); return true;
        }
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        boolean handled = super.keyPressed(key, scan, mods);
        if (key == 258 || key >= 262 && key <= 265) {
            for (BodyWidget entry : bodyWidgets) if (entry.widget == getFocused()) {
                if (entry.widget.getY() < bodyTop) setScroll(entry.y);
                else if (entry.widget.getY() + entry.widget.getHeight() > bodyBottom)
                    setScroll(entry.y + entry.widget.getHeight() - (bodyBottom - bodyTop));
            }
        }
        return handled;
    }

    private final class AuctionButton extends Button {
        private final boolean primary;
        AuctionButton(int x, int y, int w, int h, Component label, boolean primary, Runnable action) {
            super(x, y, w, h, label, b -> action.run(), DEFAULT_NARRATION);
            this.primary = primary;
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = isHoveredOrFocused();
            AuctionUi.box(g, !active ? "button_disabled" : primary
                    ? hot ? "button_hover" : "button" : hot ? "secondary_hover" : "secondary",
                    getX(), getY(), getWidth(), getHeight());
            var lines = font.split(getMessage(), Math.max(1, getWidth() - 16));
            int y = getY() + (getHeight() - lines.size() * (line + 2) + 2) / 2;
            for (var text : lines) {
                g.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2, y,
                        !active ? AuctionUi.BODY : primary ? AuctionUi.CREAM : AuctionUi.INK, false);
                y += line + 2;
            }
            if (isFocused()) g.renderOutline(getX() + 2, getY() + 2, getWidth() - 4, getHeight() - 4, AuctionUi.GOLD);
        }
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
            manager.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, 0.3f));
        }
    }
}
