package com.stardew.craft.client.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.client.TooltipConstants;
import com.stardew.craft.book.BookDefinition;
import com.stardew.craft.book.BookDefinition.BookKind;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.tooltip.BookTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BookClientTooltipComponent implements ClientTooltipComponent {
    private static final int PANEL_WIDTH = 198;
    private static final int PADDING = 7;
    private static final int SPINE_WIDTH = 14;
    private static final int ICON_FRAME = 22;
    private static final int HEADER_TOP = 7;
    private static final int DESCRIPTION_TOP = 43;
    private static final int PRICE_HEIGHT = 15;

    private static final int PAGE = 0xFFFFDFA3;
    private static final int PAGE_SOFT = 0xFFFFE9BB;
    private static final int PAGE_SHADOW = 0xFFE8B96D;
    private static final int BORDER_DARK = 0xFF70411B;
    private static final int BORDER_MID = 0xFFD1903D;
    private static final int INK = 0xFF4C2A12;
    private static final int MUTED_INK = 0xFF7B5128;
    private static final int RULE = 0x44B97A3F;
    private static final int SPINE = 0xFFB76322;
    private static final int SPINE_DARK = 0xFF6A3516;
    private static final int SPINE_LIGHT = 0xFFE09A49;
    private static final int ACCENT = 0xFFD99124;
    private static final int BOOKMARK = 0xFFD64D3B;

    private final ItemStack stack;
    private final BookDefinition definition;

    public BookClientTooltipComponent(BookTooltipComponent component) {
        this.stack = component.stack();
        this.definition = component.definition();
    }

    @Override
    public int getHeight() {
        Font font = Minecraft.getInstance().font;
        int descriptionHeight = Math.max(1, descriptionLines(font).size()) * font.lineHeight;
        int effectHeight = Math.max(16, effectLines(font).size() * (font.lineHeight + 1) + 7);
        return Math.max(108, DESCRIPTION_TOP + descriptionHeight + 6 + effectHeight + 5 + PRICE_HEIGHT + 6);
    }

    @Override
    public int getWidth(@SuppressWarnings("null") Font font) {
        return PANEL_WIDTH;
    }

    @Override
    public void renderImage(@SuppressWarnings("null") Font font, int x, int y,
                            @SuppressWarnings("null") GuiGraphics graphics) {
        int height = getHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        renderPage(graphics, x, y, height);
        renderHeader(font, graphics, x, y);
        int descY = renderDescription(font, graphics, x, y + 42);
        renderEffects(font, graphics, x, descY + 6);
        renderPrice(font, graphics, x, y + height - 18);

        RenderSystem.disableBlend();
    }

    private void renderPage(GuiGraphics graphics, int x, int y, int height) {
        graphics.fill(x, y, x + PANEL_WIDTH, y + height, BORDER_DARK);
        graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + height - 1, BORDER_MID);
        graphics.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + height - 2, PAGE);
        graphics.fill(x + 4, y + 4, x + PANEL_WIDTH - 4, y + Math.min(height - 4, 24), PAGE_SOFT);
        graphics.fill(x + 4, y + height - 9, x + PANEL_WIDTH - 4, y + height - 4, PAGE_SHADOW);

        int spineRight = x + 3 + SPINE_WIDTH;
        graphics.fill(x + 3, y + 3, spineRight, y + height - 3, SPINE);
        graphics.fill(x + 5, y + 5, x + 7, y + height - 5, SPINE_LIGHT);
        graphics.fill(spineRight - 2, y + 4, spineRight, y + height - 4, SPINE_DARK);
        graphics.fill(spineRight + 1, y + 4, spineRight + 2, y + height - 4, 0x88FFFFFF);

        graphics.fill(x + PANEL_WIDTH - 18, y + 2, x + PANEL_WIDTH - 11, y + 25, BOOKMARK);
        graphics.fill(x + PANEL_WIDTH - 16, y + 22, x + PANEL_WIDTH - 13, y + 29, PAGE);

        graphics.hLine(x + 4, x + PANEL_WIDTH - 5, y + height - 4, 0x55FFFFFF);
        graphics.hLine(x + 4, x + PANEL_WIDTH - 5, y + 3, 0x66FFFFFF);
    }

    private void renderHeader(Font font, GuiGraphics graphics, int x, int y) {
        int contentX = contentX(x);
        int iconX = contentX;
        int iconY = y + HEADER_TOP;
        graphics.fill(iconX, iconY, iconX + ICON_FRAME, iconY + ICON_FRAME, BORDER_DARK);
        graphics.fill(iconX + 1, iconY + 1, iconX + ICON_FRAME - 1, iconY + ICON_FRAME - 1, 0xFFE3A14A);
        graphics.fill(iconX + 3, iconY + 3, iconX + ICON_FRAME - 3, iconY + ICON_FRAME - 3, 0xFFFFE7B1);
        graphics.renderItem(stack, iconX + 3, iconY + 3);

        int textX = iconX + ICON_FRAME + 7;
        int textY = y + HEADER_TOP + 1;
        Component kind = kindLabel().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFF4D2)).withBold(true));
        int badgeWidth = Math.min(contentRight(x) - textX, font.width(kind) + 12);
        graphics.fill(textX, textY, textX + badgeWidth, textY + 12, 0xFF6F3B18);
        graphics.fill(textX + 1, textY + 1, textX + badgeWidth - 1, textY + 11, 0xFFC67A25);
        graphics.drawString(font, kind, textX + 6, textY + 2, 0xFFFFFFFF, false);

        Component subtitle = Component.translatable("stardewcraft.book.tooltip.bookplate")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MUTED_INK & 0xFFFFFF)));
        graphics.drawString(font, subtitle, textX, textY + 16, MUTED_INK, false);

        graphics.hLine(contentX, contentRight(x), y + 36, 0x99A5652F);
        graphics.hLine(contentX, contentRight(x), y + 37, 0x33FFFFFF);
    }

    private int renderDescription(Font font, GuiGraphics graphics, int x, int y) {
        int textX = contentX(x);
        int lineWidth = contentWidth();
        List<FormattedCharSequence> lines = descriptionLines(font);
        for (int i = 0; i < lines.size(); i++) {
            int lineY = y + i * font.lineHeight;
            graphics.hLine(textX, textX + lineWidth, lineY + font.lineHeight + 1, RULE);
            graphics.drawString(font, lines.get(i), textX, lineY, INK, false);
        }
        return y + Math.max(1, lines.size()) * font.lineHeight;
    }

    private void renderEffects(Font font, GuiGraphics graphics, int x, int y) {
        int textX = contentX(x);
        List<FormattedCharSequence> lines = effectLines(font);
        if (lines.isEmpty()) {
            return;
        }

        int boxHeight = Math.max(16, lines.size() * (font.lineHeight + 1) + 7);
        graphics.fill(textX, y, contentRight(x), y + boxHeight, 0x33FFF4CF);
        graphics.fill(textX, y, textX + 4, y + boxHeight, ACCENT);
        graphics.hLine(textX + 4, contentRight(x) - 1, y, 0x66B97A3F);
        graphics.hLine(textX + 4, contentRight(x) - 1, y + boxHeight - 1, 0x66B97A3F);
        int lineY = y + 4;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, textX + 9, lineY, MUTED_INK, false);
            lineY += font.lineHeight + 1;
        }
    }

    private void renderPrice(Font font, GuiGraphics graphics, int x, int y) {
        if (definition.price() <= 0) {
            return;
        }

        MutableComponent price = Component.literal(TooltipConstants.ICON_MONEY)
            .withStyle(Style.EMPTY.withBold(true))
                .append(Component.literal(" " + definition.price() + " G")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x6E3F18)).withBold(true)));
        int textWidth = font.width(price);
        int boxWidth = textWidth + 10;
        int boxX = x + PANEL_WIDTH - PADDING - boxWidth;
        graphics.fill(boxX, y, boxX + boxWidth, y + PRICE_HEIGHT, 0x44FFF5D0);
        graphics.hLine(boxX + 1, boxX + boxWidth - 2, y, 0x88B97A3F);
        graphics.hLine(boxX + 1, boxX + boxWidth - 2, y + PRICE_HEIGHT - 1, 0x88B97A3F);
        graphics.drawString(font, price, boxX + 5, y + 4, 0xFFFFFFFF, false);
    }

    private List<FormattedCharSequence> descriptionLines(Font font) {
        Component description = Component.translatable(stack.getDescriptionId() + ".desc")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(INK & 0xFFFFFF)));
        return font.split(description, contentWidth());
    }

    private Component kindLabel() {
        String key = switch (definition.kind()) {
            case SKILL -> "stardewcraft.book.tooltip.kind.skill";
            case PURPLE -> "stardewcraft.book.tooltip.kind.special";
            case POWER -> "stardewcraft.book.tooltip.kind.power";
            case ANIMAL_CATALOGUE -> "stardewcraft.book.tooltip.kind.special";
            case QUEEN_OF_SAUCE -> "stardewcraft.book.tooltip.kind.cookbook";
        };
        return Component.translatable(key);
    }

    private List<FormattedCharSequence> effectLines(Font font) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : rawEffectLines()) {
            wrapped.addAll(font.split(line, contentWidth() - 15));
        }
        return wrapped;
    }

    private List<Component> rawEffectLines() {
        List<Component> lines = new ArrayList<>();
        if (definition.kind() == BookKind.SKILL && definition.skill() != null) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.skill_xp", skillName(definition.skill()))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7A4B1E)).withBold(true)));
        } else if (definition.kind() == BookKind.PURPLE) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.all_skill_xp")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7A4B1E)).withBold(true)));
        } else if (definition.kind() == BookKind.QUEEN_OF_SAUCE) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.queen")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7A4B1E)).withBold(true)));
        } else if (definition.wellReadPower()) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.permanent")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x7A4B1E)).withBold(true)));
        }

        if (definition.repeatSkill() != null) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.repeat_skill", skillName(definition.repeatSkill()))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MUTED_INK & 0xFFFFFF))));
        } else if (definition.repeatAllSkills()) {
            lines.add(Component.translatable("stardewcraft.book.tooltip.effect.repeat_all")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MUTED_INK & 0xFFFFFF))));
        }
        return lines;
    }

    private Component skillName(SkillType skill) {
        return Component.translatable("stardewcraft.skill." + skill.getName());
    }

    private static int contentX(int x) {
        return x + PADDING + SPINE_WIDTH + 5;
    }

    private static int contentRight(int x) {
        return x + PANEL_WIDTH - PADDING;
    }

    private static int contentWidth() {
        return PANEL_WIDTH - PADDING * 2 - SPINE_WIDTH - 5;
    }
}
