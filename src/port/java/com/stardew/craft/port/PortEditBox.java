package com.stardew.craft.port;

import com.stardew.craft.mixin.PortEditBoxAccessor;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): an {@link EditBox} that behaves like 1.21.1's for StardewCraft screens.
 * <ul>
 *   <li>NeoForge's {@code setTextShadow/getTextShadow} (all text draws honour it).</li>
 *   <li>{@code moveCursorTo(int, boolean select)}.</li>
 *   <li>1.21 rendering: the bordered frame is the {@code widget/text_field[_highlighted]} nine-slice sprite drawn
 *       inside the widget bounds (1px #A0A0A0 / white border around black; 1.20.1 draws it one pixel outside),
 *       and the caret blinks every 300 ms from the moment of focus ({@code focusedTime}) instead of 1.20.1's
 *       tick counter, which 1.21-style screens never advance (they do not call {@code EditBox#tick}).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PortEditBox extends EditBox {
    private static final int TEXT_FIELD_BORDER = 0xFFA0A0A0;
    private static final int TEXT_FIELD_BORDER_FOCUSED = 0xFFFFFFFF;
    private static final int TEXT_FIELD_BACKGROUND = 0xFF000000;
    private boolean textShadow = true;
    private long focusedTime = Util.getMillis();

    public PortEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    public PortEditBox(Font font, int x, int y, int width, int height, EditBox editBox, Component message) {
        super(font, x, y, width, height, editBox, message);
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    public boolean getTextShadow() {
        return this.textShadow;
    }

    /** 1.21.1 {@code moveCursorTo(int, boolean)}: cursor, then highlight unless selecting, then the responder. */
    public void moveCursorTo(int pos, boolean select) {
        this.setCursorPosition(pos);
        if (!select) this.setHighlightPos(this.getCursorPosition());
        ((PortEditBoxAccessor) this).port$onValueChange(this.getValue());
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) this.focusedTime = Util.getMillis();
    }

    /** 1.21.1 {@code EditBox#renderWidget}. */
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible()) return;
        PortEditBoxAccessor box = (PortEditBoxAccessor) this;
        Font font = box.port$font();
        String value = box.port$value();
        int displayPos = box.port$displayPos();
        int cursorPos = box.port$cursorPos();
        boolean bordered = box.port$bordered();
        if (bordered) {
            // WidgetSprites(text_field, text_field_highlighted).get(isActive(), isFocused()); nine-slice, border 1.
            int border = this.isActive() && this.isFocused() ? TEXT_FIELD_BORDER_FOCUSED : TEXT_FIELD_BORDER;
            drawTextField(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), border);
        }

        int color = box.port$isEditable() ? box.port$textColor() : box.port$textColorUneditable();
        int i = cursorPos - displayPos;
        String s = font.plainSubstrByWidth(value.substring(displayPos), this.getInnerWidth());
        boolean cursorVisible = i >= 0 && i <= s.length();
        boolean blink = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L && cursorVisible;
        int j = bordered ? this.getX() + 4 : this.getX();
        int k = bordered ? this.getY() + (this.height - 8) / 2 : this.getY();
        int l = j;
        int i1 = Mth.clamp(box.port$highlightPos() - displayPos, 0, s.length());
        if (!s.isEmpty()) {
            String s1 = cursorVisible ? s.substring(0, i) : s;
            l = graphics.drawString(font, box.port$formatter().apply(s1, displayPos), j, k, color, this.textShadow);
        }

        boolean insert = cursorPos < value.length() || value.length() >= box.port$getMaxLength();
        int j1 = l;
        if (!cursorVisible) {
            j1 = i > 0 ? j + this.width : j;
        } else if (insert) {
            j1 = l - 1;
            l--;
        }

        if (!s.isEmpty() && cursorVisible && i < s.length()) {
            graphics.drawString(font, box.port$formatter().apply(s.substring(i), cursorPos), l, k, color, this.textShadow);
        }

        Component hint = box.port$hint();
        if (hint != null && s.isEmpty() && !this.isFocused()) {
            graphics.drawString(font, hint, l, k, color, this.textShadow);
        }

        String suggestion = box.port$suggestion();
        if (!insert && suggestion != null) {
            graphics.drawString(font, suggestion, j1 - 1, k, -8355712, this.textShadow);
        }

        if (blink) {
            if (insert) {
                graphics.fill(RenderType.guiOverlay(), j1, k - 1, j1 + 1, k + 1 + 9, -3092272);
            } else {
                graphics.drawString(font, "_", j1, k, color, this.textShadow);
            }
        }

        if (i1 != i) {
            int k1 = j + font.width(s.substring(0, i1));
            box.port$renderHighlight(graphics, j1, k - 1, k1 - 1, k + 1 + 9);
        }
    }

    /** The 200x20 text_field sprite: 1px border colour around a black body, nine-sliced with border 1. */
    private static void drawTextField(GuiGraphics graphics, int x, int y, int width, int height, int border) {
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, border);
        int left = Math.min(1, width / 2), right = Math.min(1, width / 2);
        int top = Math.min(1, height / 2), bottom = Math.min(1, height / 2);
        if (width - left - right > 0 && height - top - bottom > 0) {
            graphics.fill(x + left, y + top, x + width - right, y + height - bottom, TEXT_FIELD_BACKGROUND);
        }
    }
}
