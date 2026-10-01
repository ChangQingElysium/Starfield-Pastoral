package com.stardew.craft.client.tooltip;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.item.weapon.WeaponIcons;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.item.weapon.WeaponTooltipBuilder;
import com.stardew.craft.tooltip.WeaponTooltipComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Weapon content hosted by the native tooltip pipeline; only the body scrolls. */
public final class WeaponClientTooltipComponent implements ClientTooltipComponent {
    static final int INK = 0xFFFFFFFF;
    static final int MUTED = 0xFFAAAAAA;
    static final int ACCENT = 0xFFFFAA00;
    private static final int PADDING = 2;
    private static WeaponClientTooltipComponent cached;
    private static net.minecraft.locale.Language cachedLanguage;
    private static long cachedAt;
    private int scroll;
    private final WeaponTooltipComponent model;
    private final List<Text> body = new ArrayList<>();
    private final List<Integer> rules = new ArrayList<>();
    private List<FormattedCharSequence> title;
    private List<FormattedCharSequence> subtitle;
    private List<FormattedCharSequence> footer;
    private Font measuredFont;
    private int width, height, headerHeight, bodyHeight, viewportHeight, step;

    private record Text(FormattedCharSequence text, int x, int y, int color) {}

    public WeaponClientTooltipComponent(WeaponTooltipComponent model) { this.model = model; }
    public static WeaponClientTooltipComponent create(WeaponTooltipComponent model) {
        var language = net.minecraft.locale.Language.getInstance();
        long now = net.minecraft.Util.getMillis();
        if (cached == null || language != cachedLanguage || now - cachedAt >= 500
                || !sameContent(model, cached.model)) {
            cached = new WeaponClientTooltipComponent(model);
            cachedLanguage = language;
            cachedAt = now;
        }
        return cached;
    }

    private static boolean sameContent(WeaponTooltipComponent a, WeaponTooltipComponent b) {
        return a.data() == b.data() && a.expanded() == b.expanded()
                && a.screenWidth() == b.screenWidth() && a.screenHeight() == b.screenHeight()
                && a.title().equals(b.title()) && a.extras().equals(b.extras())
                && net.minecraft.world.item.ItemStack.isSameItemSameTags(a.stack(), b.stack());
    }

    public void setScroll(int scroll) { this.scroll = scroll; }
    public WeaponTooltipComponent model() { return model; }

    public void measure(Font font) {
        if (measuredFont == font) return;
        measuredFont = font;
        width = WeaponTooltipLayout.width(model.screenWidth(), model.expanded());
        int contentWidth = Math.max(1, width - PADDING * 2 - 5);
        step = StardewFonts.lineHeight(font) + 2;
        title = font.split(model.title(), contentWidth);
        Component kind = Component.translatable(model.data().getWeaponType().getTranslationKey())
                .append(" · ").append(Component.translatable("stardewcraft.weapon.tooltip.card_level", model.data().getLevel()));
        subtitle = font.split(kind, contentWidth);
        headerHeight = PADDING + (title.size() + subtitle.size()) * step + 6;
        body.clear();
        rules.clear();
        WeaponTooltipBuilder builder = new WeaponTooltipBuilder(model.stack(), model.data(), model.expanded());
        int y = addStats(font, builder.cardStats(), contentWidth);
        List<Component> forge = builder.cardForgeLines();
        if (!forge.isEmpty()) {
            y += 4;
            for (Component line : forge) y = addParagraph(font, line, 0, y, contentWidth, MUTED);
        }
        WeaponSkillData[] skills = {model.data().getSkill1(), model.data().getSkill2()};
        for (int index = 0; index < skills.length; index++) {
            WeaponSkillData skill = skills[index];
            if (skill == null) continue;
            rules.add(y + 4);
            y += 11;
            Component icon = WeaponIcons.icon(skill.getIconChar() == null ? WeaponIcons.ICON_SKILL : skill.getIconChar());
            body.add(new Text(icon.getVisualOrderText(), 0, y, 0xFFFFFFFF));
            y = addParagraph(font, Component.translatable(skill.getNameKey()).withStyle(Style.EMPTY.withBold(true)),
                    16, y, contentWidth - 16, ACCENT);
            Component binding = WeaponTooltipBuilder.skillBinding(skill, index == 0);
            Component control = skill.isPassive() ? binding : Component.literal("[").append(binding).append("]");
            if (!skill.isPassive() && skill.getCooldown() > 0) {
                control = control.copy().append("  ·  ").append(Component.translatable(
                        "stardewcraft.weapon.tooltip.card_cooldown", skill.getCooldown()));
            }
            y = addParagraph(font, control, 16, y, contentWidth - 16, MUTED) + 4;
            for (Component paragraph : WeaponTooltipBuilder.skillParagraphs(skill, model.expanded())) {
                y = addParagraph(font, paragraph, 0, y, contentWidth, INK) + (model.expanded() ? 2 : 0);
            }
        }
        if (model.expanded() && (skills[0] != null || skills[1] != null)) {
            y += 5;
            y = addParagraph(font, Component.translatable("stardewcraft.weapon.tooltip.damage_basis"), 0, y, contentWidth, MUTED);
        }
        if (!model.extras().isEmpty()) {
            rules.add(y + 5);
            y += 13;
            for (Component extra : model.extras()) {
                if (!extra.getString().isBlank()) y = addParagraph(font, extra, 0, y, contentWidth, MUTED);
            }
        }
        bodyHeight = y;
        boolean hasSkills = skills[0] != null || skills[1] != null;
        Component hint = hasSkills ? Component.translatable(model.expanded()
                ? "stardewcraft.weapon.tooltip.hint_summary" : "stardewcraft.weapon.tooltip.hint_details") : Component.empty();
        footer = hasSkills ? font.split(hint, contentWidth) : List.of();
        int footerHeight = footer.size() * step + PADDING + 7;
        height = WeaponTooltipLayout.height(model.screenHeight(), headerHeight + bodyHeight + footerHeight);
        if (height < headerHeight + bodyHeight + footerHeight) {
            hint = hint.copy().append(hasSkills ? "  ·  " : "").append(Component.translatable("stardewcraft.weapon.tooltip.hint_scroll"));
            footer = font.split(hint, contentWidth);
            footerHeight = footer.size() * step + PADDING + 7;
        }
        viewportHeight = Math.max(0, height - headerHeight - footerHeight);
    }

    private int addStats(Font font, List<WeaponTooltipBuilder.Stat> stats, int availableWidth) {
        int columns = availableWidth >= 216 ? 2 : 1;
        int columnWidth = (availableWidth - (columns - 1) * 18) / columns;
        int y = 0;
        for (int index = 0; index < stats.size(); index += columns) {
            int rowHeight = 0;
            for (int col = 0; col < columns && index + col < stats.size(); col++) {
                var stat = stats.get(index + col);
                int x = col * (columnWidth + 18);
                body.add(new Text(WeaponIcons.icon(stat.icon()).getVisualOrderText(), x, y, 0xFFFFFFFF));
                Component value = stat.value().copy().withStyle(Style.EMPTY.withBold(true));
                int valueWidth = font.width(value);
                int labelWidth = Math.max(1, columnWidth - valueWidth - 22);
                int end = addParagraph(font, stat.label(), x + 14, y, labelWidth, MUTED);
                body.add(new Text(value.getVisualOrderText(), x + columnWidth - valueWidth, y,
                        index + col == 0 ? ACCENT : INK));
                rowHeight = Math.max(rowHeight, end - y);
            }
            y += rowHeight + 4;
        }
        return y;
    }

    private int addParagraph(Font font, Component text, int x, int y, int maxWidth, int color) {
        // Native tooltip colors and external metadata styles remain intact.
        for (FormattedCharSequence line : font.split(text, Math.max(1, maxWidth))) {
            body.add(new Text(line, x, y, color));
            y += step;
        }
        return y;
    }

    public int maximumScroll() { return Math.max(0, bodyHeight - viewportHeight); }
    @Override public int getWidth(Font font) { measure(font); return width; }
    // Vanilla subtracts 2px for a single-component tooltip.
    @Override public int getHeight() { return height + 2; }
    @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        render(font, graphics, x, y, scroll);
    }

    public void render(Font font, GuiGraphics graphics, int x, int y, int scroll) {
        measure(font);
        int tx = x + PADDING;
        int ty = y + PADDING;
        for (var line : title) { graphics.drawString(font, line, tx, ty, INK, true); ty += step; }
        for (var line : subtitle) { graphics.drawString(font, line, tx, ty + 1, MUTED, true); ty += step; }
        int top = y + headerHeight;
        int offset = WeaponTooltipLayout.clampScroll(scroll, bodyHeight, viewportHeight);
        graphics.enableScissor(tx, top, x + width - PADDING, top + viewportHeight);
        try {
            for (int rule : rules) graphics.hLine(tx, x + width - PADDING - 5, top + rule - offset, 0xFF393342);
            for (Text text : body) {
                int drawY = top + text.y() - offset;
                if (drawY + step >= top && drawY < top + viewportHeight) {
                    graphics.drawString(font, text.text(), tx + text.x(), drawY, text.color(), true);
                }
            }
        } finally { graphics.disableScissor(); }
        if (maximumScroll() > 0 && viewportHeight > 0) {
            int trackX = x + width - 2;
            int thumb = Math.min(viewportHeight, Math.max(12, viewportHeight * viewportHeight / Math.max(1, bodyHeight)));
            int thumbY = top + (viewportHeight - thumb) * offset / maximumScroll();
            graphics.fill(trackX, top, trackX + 2, top + viewportHeight, 0xFF302C3B);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0xFFAAAAAA);
        }
        int footerY = top + viewportHeight + 7;
        for (var line : footer) { graphics.drawString(font, line, tx, footerY, MUTED, true); footerY += step; }
    }
}
