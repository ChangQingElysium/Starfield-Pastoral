package com.stardew.craft.client.gui;

import com.stardew.craft.Config;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.menu.MenuPageArt;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Readable, scrollable settings with native keyboard widgets and the menu's authored artwork. */
public final class StardewSettingsScreen extends Screen {
    private final Screen parent;
    private final List<Setting> settings = new ArrayList<>();
    private int x, y, panelW, panelH, listY, listBottom, scroll;
    private EditBox clockInput;
    private int clockY, clockH;
    private String clockDraft;
    private record Setting(String key, BooleanSupplier getter, Runnable action, boolean clock) { }

    public StardewSettingsScreen(Screen parent) {
        super(Component.translatable("stardewcraft.settings.title"));
        this.parent = parent;
    }
    @Override protected void init() {
        font = StardewFonts.small();
        settings.clear();
        toggle("config.stardewcraft.client.weapon_special_effects", Config.ENABLE_WEAPON_SPECIAL_EFFECTS::get, Config.ENABLE_WEAPON_SPECIAL_EFFECTS::set, Config.CLIENT_SPEC::save);
        toggle("config.stardewcraft.client.weapon_post_effects", Config.ENABLE_WEAPON_POST_EFFECTS::get, Config.ENABLE_WEAPON_POST_EFFECTS::set, Config.CLIENT_SPEC::save);
        toggle("config.stardewcraft.client.show_monster_hp_bar", Config.SHOW_MONSTER_HP_BAR::get, Config.SHOW_MONSTER_HP_BAR::set, Config.CLIENT_SPEC::save);
        toggle("config.stardewcraft.client.enable_stardew_fonts", Config.ENABLE_STARDEW_FONTS::get, Config.ENABLE_STARDEW_FONTS::set, Config.CLIENT_SPEC::save);
        settings.add(new Setting("config.stardewcraft.client.reading_text_scale", null,
                () -> minecraft.setScreen(new ReadingTextSettingsScreen(this)), false));
        settings.add(new Setting("stardewcraft.settings.hud_layout", null, () -> minecraft.setScreen(new StardewHudLayoutEditorScreen(this)), false));
        settings.add(new Setting("stardewcraft.settings.key_bindings", null, () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)), false));
        if (Config.SERVER_SPEC.isLoaded() && minecraft.getSingleplayerServer() != null) {
            toggle("config.stardewcraft.server.enable_fishing_minigame", Config.ENABLE_FISHING_MINIGAME::get, Config.ENABLE_FISHING_MINIGAME::set, Config.SERVER_SPEC::save);
            toggle("config.stardewcraft.server.enable_update_checks", Config.ENABLE_UPDATE_CHECKS::get, Config.ENABLE_UPDATE_CHECKS::set, Config.SERVER_SPEC::save);
            toggle("config.stardewcraft.server.show_community_announcement", Config.SHOW_COMMUNITY_ANNOUNCEMENT::get, Config.SHOW_COMMUNITY_ANNOUNCEMENT::set, Config.SERVER_SPEC::save);
            settings.add(new Setting("config.stardewcraft.server.time_speed_multiplier", null, null, true));
            if (clockDraft == null) clockDraft = Double.toString(Config.TIME_SPEED_MULTIPLIER.get());
        }
        rebuild();
    }
    private void toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, Runnable save) {
        settings.add(new Setting(key, getter, () -> { setter.accept(!getter.getAsBoolean()); save.run(); }, false));
    }
    private int line() { return StardewFonts.lineHeight(font); }
    private int rowHeight(Setting setting) {
        int textW = panelW - (setting.clock() ? 146 : 76);
        return Math.max(34, font.split(Component.translatable(setting.key()), textW).size() * (line() + 2) + 14);
    }
    private int maxScroll() {
        int first = settings.size(), used = 0;
        while (first > 0 && used + rowHeight(settings.get(first - 1)) + 4 <= listBottom - listY) {
            used += rowHeight(settings.get(--first)) + 4;
        }
        return Math.min(first, Math.max(0, settings.size() - 1));
    }
    private void rebuild() {
        boolean clockFocused = clockInput != null && clockInput.isFocused();
        int cursor = clockInput == null ? 0 : clockInput.getCursorPosition();
        clearWidgets(); clockInput = null;
        panelW = Math.min(460, width - 28); panelH = Math.min(344, height - 28);
        x = (width - panelW) / 2; y = (height - panelH) / 2;
        listY = y + line() + 30; listBottom = y + panelH - 44;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        int rowY = listY;
        for (int i = scroll; i < settings.size(); i++) {
            var setting = settings.get(i); int h = rowHeight(setting);
            if (rowY + h > listBottom) break;
            if (setting.clock()) {
                clockY = rowY; clockH = h;
                clockInput = new EditBox(font, x + panelW - 108, rowY + (h - line()) / 2, 82, line(), Component.translatable(setting.key()));
                clockInput.setBordered(false); clockInput.setTextShadow(false); clockInput.setMaxLength(12);
                clockInput.setValue(clockDraft);
                clockInput.setTextColor(validSpeed(clockDraft) ? MenuPageArt.INK : 0xFFA13E30);
                clockInput.setResponder(this::updateClock);
                addRenderableWidget(clockInput);
                clockInput.setCursorPosition(Math.min(cursor, clockDraft.length()));
                if (clockFocused) setFocused(clockInput);
            } else addRenderableWidget(new SettingButton(x + 14, rowY, panelW - 36, h, setting));
            rowY += h + 4;
        }
        addRenderableWidget(new SettingButton(x + (panelW - 120) / 2, y + panelH - 34, 120, 24,
                new Setting("gui.back", null, this::onClose, false)));
    }
    private static boolean validSpeed(String value) {
        try { double n = Double.parseDouble(value); return Double.isFinite(n) && n >= .1 && n <= 100; }
        catch (NumberFormatException ignored) { return false; }
    }
    private void updateClock(String value) {
        clockDraft = value;
        boolean valid = validSpeed(value);
        clockInput.setTextColor(valid ? MenuPageArt.INK : 0xFFA13E30);
        if (valid) { Config.TIME_SPEED_MULTIPLIER.set(Double.parseDouble(value)); Config.SERVER_SPEC.save(); }
    }
    @Override public boolean mouseScrolled(double mx, double my, double dy) {
        if (mx < x + 14 || mx >= x + panelW - 14 || my < listY || my >= listBottom) return false;
        int next = Math.max(0, Math.min(maxScroll(), scroll + (dy > 0 ? -1 : dy < 0 ? 1 : 0)));
        if (next != scroll) { scroll = next; rebuild(); }
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 266 || key == 267) { scroll = Math.max(0, Math.min(maxScroll(), scroll + (key == 266 ? -1 : 1))); rebuild(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { Config.CLIENT_SPEC.save(); super.removed(); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0413028);
        MenuPageArt.paper(g, x, y, panelW, panelH);
        g.drawString(font, title, x + 18, y + 14, MenuPageArt.INK, false);
        if (clockInput != null) {
            MenuPageArt.box(g, "row", x + 14, clockY, panelW - 36, clockH);
            var lines = font.split(Component.translatable("config.stardewcraft.server.time_speed_multiplier"), panelW - 146);
            int textY = clockY + (clockH - lines.size() * (line() + 2)) / 2;
            for (var text : lines) { g.drawString(font, text, x + 22, textY, MenuPageArt.INK, false); textY += line() + 2; }
            MenuPageArt.box(g, "inset", x + panelW - 114, clockY + (clockH - line() - 12) / 2, 94, line() + 12);
        }
        int max = maxScroll();
        if (max > 0) {
            int h = listBottom - listY, thumb = Math.max(16, h / (max + 1));
            int sy = listY + (h - thumb) * scroll / max;
            g.fill(x + panelW - 14, listY, x + panelW - 11, listBottom, 0xFFC7AD80);
            g.fill(x + panelW - 15, sy, x + panelW - 10, sy + thumb, 0xFF977447);
        }
        super.render(g, mx, my, pt);
    }
    private final class SettingButton extends Button {
        private final Setting setting;
        SettingButton(int x, int y, int w, int h, Setting setting) {
            super(x, y, w, h, Component.translatable(setting.key()), button -> setting.action().run(), DEFAULT_NARRATION);
            this.setting = setting;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean back = setting.key().equals("gui.back");
            MenuPageArt.box(g, isHoveredOrFocused() ? "tab_hover" : back ? "button" : "row", getX(), getY(), getWidth(), getHeight());
            var lines = font.split(getMessage(), getWidth() - (back ? 16 : 40));
            int ty = getY() + (getHeight() - lines.size() * (line() + 2)) / 2;
            for (var text : lines) { g.drawString(font, text, back ? getX() + (getWidth() - font.width(text)) / 2 : getX() + 8, ty, MenuPageArt.INK, false); ty += line() + 2; }
            if (setting.getter() != null) MenuPageArt.sprite(g, "select_" + (setting.getter().getAsBoolean() ? "on" : "off"), getX() + getWidth() - 25, getY() + (getHeight() - 12) / 2, 12, 12);
            else if (!back) CommonGuiTextures.drawForwardArrow(g, getX() + getWidth() - 24, getY() + (getHeight() - 11) / 2, 1f);
            if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF8B754A);
        }
    }
}
