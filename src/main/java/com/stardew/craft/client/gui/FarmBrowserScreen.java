package com.stardew.craft.client.gui;

import com.stardew.craft.api.v1.farm.StardewFarmLayout;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.client.farm.FarmJoinClientState;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.payload.FarmEntryRequestPayload;
import com.stardew.craft.network.payload.FarmJoinRequestPayload;
import com.stardew.craft.network.payload.FarmListSyncPayload.FarmEntry;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;

/** Shared farm directory. Server-authored eligibility and identifiers remain authoritative. */
@SuppressWarnings("null")
public class FarmBrowserScreen extends Screen {
    final FarmBrowserModel model;
    private final String entryTag;
    private final Screen returnScreen;
    private FarmBrowserLayout layout;
    private EditBox search;
    private DirectoryButton confirm;
    private boolean submitted, pending, draggingScrollbar;
    private double scrollbarGrab;
    private long lastSound;
    private int permissionWidth;
    private List<FarmEntry> shown = List.of();

    protected FarmBrowserScreen(List<FarmEntry> farms, boolean joining, String entryTag, Screen returnScreen) {
        super(Component.translatable(joining ? "gui.stardewcraft.farm_join.title" : "gui.stardewcraft.farm_entry.title"));
        model = new FarmBrowserModel(farms, joining);
        this.entryTag = entryTag;
        this.returnScreen = returnScreen;
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.stardewcraft.farm_browser." + key, args);
    }

    @Override protected void init() {
        font = StardewFonts.small();
        pending = model.joining && FarmJoinClientState.hasPendingJoinRequest();
        rebuild();
    }

    private int lineHeight() { return StardewFonts.lineHeight(font); }

    private void rebuild() {
        boolean editing = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        clearWidgets();
        int actionWidth = Math.min(150, (Math.min(440, width - 16) - 36) / 2);
        int actionHeight = Math.max(24, Math.max(font.split(actionLabel(), actionWidth - 16).size(), font.split(Component.translatable("gui.back"), actionWidth - 16).size()) * (lineHeight() + 2) + 8);
        layout = FarmBrowserLayout.fit(width, height, lineHeight(), actionHeight, model.filtered().size());
        shown = model.entries(layout.visibleRows());
        permissionWidth = layout.permissionWidth(model.farms.stream().mapToInt(f -> font.width(state(f))).max().orElse(0));
        search = new EditBox(font, layout.left() + 7, layout.searchY() + 5, layout.innerWidth() - 14, lineHeight(), tr("search"));
        search.setBordered(false); search.setTextShadow(false);
        search.setTextColor(FarmSetupArt.INK); search.setMaxLength(96); search.setValue(model.query);
        // Hint follows the current language font and remains unscaled.
        search.setHint(tr("search").copy().withStyle(style -> style.withColor(FarmSetupArt.MUTED)));
        search.setResponder(value -> { model.search(value); rebuild(); });
        addRenderableWidget(search);
        search.setCursorPosition(Math.min(cursor, model.query.length()));
        if (editing) { setFocused(search); search.setFocused(true); }

        int y = layout.listY();
        for (var farm : shown) {
            var row = new DirectoryButton(layout.left(), y, layout.rowWidth(), layout.rowHeight() - 6,
                    Component.literal(farm.farmName()), () -> { model.selected = farm.ownerUUID(); updateAction(); }, "row");
            row.farm = farm;
            row.setTooltip(Tooltip.create(Component.literal(farm.farmName()).append("\n")
                    .append(tr("owner", farm.ownerName())).append("\n").append(state(farm))));
            addRenderableWidget(row);
            y += layout.rowHeight();
        }
        var back = new DirectoryButton(layout.left(), layout.footerY(), actionWidth, actionHeight,
                Component.translatable("gui.back"), this::onClose, "back");
        addRenderableWidget(back);
        confirm = new DirectoryButton(layout.left() + layout.innerWidth() - actionWidth, layout.footerY(),
                actionWidth, actionHeight, actionLabel(), this::submit, "primary");
        addRenderableWidget(confirm);
        updateAction();
    }

    private Component actionLabel() { return tr(model.joining ? "apply" : "enter"); }

    private Component state(FarmEntry farm) {
        if (model.joining) return tr("approval");
        if (farm.isMember()) return tr("member");
        return Component.translatable("gui.stardewcraft.farm_entry." + switch (farm.permission()) {
            case 2 -> "perm_full";
            case 1 -> "perm_visit";
            default -> "perm_none";
        });
    }

    private Component status() {
        if (pending) return Component.translatable("stardewcraft.farm.join.already_pending");
        var selected = model.selection();
        return selected == null ? tr("choose") : tr("selected", selected.farmName()).copy().append(" · ").append(state(selected));
    }

    private void updateAction() {
        if (confirm == null) return;
        confirm.active = !submitted && model.canSubmit(pending);
        confirm.setTooltip(Tooltip.create(status()));
    }

    private void scrollTo(int offset) {
        int next = Math.max(0, Math.min(model.maxScroll(layout.visibleRows()), offset));
        if (next == model.scrollOffset) return;
        model.scrollOffset = next;
        rebuild();
    }

    private int thumbHeight() {
        return Math.min(layout.listHeight(), Math.max(20,
                layout.listHeight() * layout.visibleRows() / Math.max(1, model.filtered().size())));
    }
    private int thumbY() {
        int range = model.maxScroll(layout.visibleRows());
        return layout.listY() + (range == 0 ? 0 : Math.round((float) model.scrollOffset
                * (layout.listHeight() - thumbHeight()) / range));
    }
    private void dragScrollbar(double y) {
        int track = layout.listHeight() - thumbHeight();
        if (track > 0) scrollTo((int) Math.round((y - scrollbarGrab - layout.listY())
                * model.maxScroll(layout.visibleRows()) / track));
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && model.maxScroll(layout.visibleRows()) > 0
                && x >= layout.scrollbarX() - 2 && x < layout.scrollbarX() + 10
                && y >= layout.listY() && y < layout.listBottom()) {
            draggingScrollbar = true;
            scrollbarGrab = y >= thumbY() && y < thumbY() + thumbHeight() ? y - thumbY() : thumbHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScrollbar) { dragScrollbar(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(x, y, button);
    }

    private void submit() {
        // Re-read pending state at the action boundary rather than trusting a stale frame.
        pending = model.joining && FarmJoinClientState.hasPendingJoinRequest();
        if (submitted || !model.canSubmit(pending)) { updateAction(); return; }
        var selected = model.selection();
        submitted = true;
        if (model.joining) {
            FarmJoinClientState.setPendingJoinRequest(true);
            PacketDistributor.sendToServer(new FarmJoinRequestPayload(selected.ownerUUID()));
        } else PacketDistributor.sendToServer(new FarmEntryRequestPayload(selected.ownerUUID(), entryTag));
        minecraft.setScreen(null);
    }

    @Override public void tick() {
        boolean current = model.joining && FarmJoinClientState.hasPendingJoinRequest();
        if (current != pending) { pending = current; updateAction(); }
    }
    @Override public void onClose() {
        minecraft.setScreen(model.joining ? returnScreen == null ? new FarmSelectionScreen() : returnScreen : null);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { onClose(); return true; }
        if (key == 266 || key == 267) { scrollTo(model.scrollOffset + (key == 266 ? -layout.visibleRows() : layout.visibleRows())); return true; }
        // Enter on a row selects it; sending an application is an explicit button action.
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < layout.left() || x >= layout.left() + layout.innerWidth() || y < layout.listY() || y >= layout.listBottom()) return false;
        if (dy != 0) scrollTo(model.scrollOffset + (dy > 0 ? -1 : 1));
        return true;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0413028);
        var frame = new FarmSetupLayout(layout.x(), layout.y(), layout.width(), layout.height(), false, layout.actionHeight());
        FarmSetupArt.panel(g, frame, Math.min(font.width(title), layout.width() - 52));
        g.drawString(font, clip(title, layout.width() - 52), layout.x() + 24,
                layout.y() + (28 - lineHeight()) / 2, FarmSetupArt.LIGHT, false);
        FarmSetupArt.paper(g, layout.left() - 4, layout.searchY() - 3, layout.innerWidth() + 8,
                layout.listBottom() - layout.searchY() + 3);
        FarmSetupArt.field(g, layout.left(), layout.searchY(), layout.innerWidth(), lineHeight() + 10, search.isFocused());
        if (shown.isEmpty()) {
            Component empty = model.farms.isEmpty() ? tr("empty") : tr("no_matches");
            drawLines(g, empty, layout.left() + 10, layout.listY() + 9, layout.innerWidth() - 20, 2, FarmSetupArt.MUTED);
        }
        if (model.maxScroll(layout.visibleRows()) > 0) {
            int sx = layout.scrollbarX();
            g.fill(sx + 2, layout.listY(), sx + 6, layout.listBottom(), 0xFFCBA16B);
            g.fill(sx, thumbY(), sx + 8, thumbY() + thumbHeight(), 0xFF805D3C);
            g.fill(sx + 1, thumbY() + 1, sx + 7, thumbY() + thumbHeight() - 1, 0xFFD4A462);
            g.fill(sx + 2, thumbY() + 2, sx + 6, thumbY() + 3, 0xFFFFE4AC);
        }
        drawLines(g, status(), layout.left(), layout.statusY(), layout.innerWidth(), 2, FarmSetupArt.LIGHT);
        if (my >= layout.statusY() && my < layout.footerY() && mx >= layout.left() && mx < layout.left() + layout.innerWidth())
            g.renderTooltip(font, status(), mx, my);
        super.render(g, mx, my, pt);
    }

    private Component clip(Component label, int width) {
        if (font.width(label) <= width) return label;
        return Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(0, width - font.width("..."))) + "...");
    }
    private void drawLines(GuiGraphics g, Component label, int x, int y, int width, int max, int color) {
        var lines = font.split(label, width);
        for (int i = 0; i < Math.min(max, lines.size()); i++) {
            if (i == max - 1 && lines.size() > max) {
                var plain = new StringBuilder();
                lines.get(i).accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                String shortened = font.plainSubstrByWidth(plain.toString(), Math.max(0, width - font.width("..."))) + "...";
                g.drawString(font, shortened, x, y + i * (lineHeight() + 2), color, false);
            } else g.drawString(font, lines.get(i), x, y + i * (lineHeight() + 2), color, false);
        }
    }

    private void sound() {
        long now = System.currentTimeMillis();
        if (minecraft != null && now - lastSound > 80) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, .35f));
            lastSound = now;
        }
    }

    private final class DirectoryButton extends Button {
        private final String kind;
        private FarmEntry farm;
        DirectoryButton(int x, int y, int w, int h, Component label, Runnable action, String kind) {
            super(x, y, w, h, label, b -> { sound(); action.run(); }, DEFAULT_NARRATION);
            this.kind = kind;
            setTooltip(Tooltip.create(label));
        }
        @Override public void playDownSound(SoundManager manager) { }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            if (farm != null) {
                boolean selected = farm.ownerUUID().equals(model.selected);
                FarmSetupArt.box(g, selected || isHoveredOrFocused() ? "packet_selected" : "packet",
                        getX(), getY(), getWidth(), getHeight(), 4);
                ResourceLocation id = ResourceLocation.tryParse(farm.farmTypeId());
                ResourceLocation icon = id == null ? FarmType.STANDARD.getIconTexture() : StardewFarmLayouts.find(id)
                        .map(StardewFarmLayout::iconTexture).orElse(FarmType.STANDARD.getIconTexture());
                g.blit(icon, getX() + 7, getY() + (getHeight() - 20) / 2, 0, 0, 22, 20, 22, 20);
                int textY = getY() + (getHeight() - lineHeight() * 2 - 3) / 2;
                g.drawString(font, clip(getMessage(), layout.rowTextWidth(permissionWidth)), getX() + 36, textY, FarmSetupArt.INK, false);
                g.drawString(font, clip(tr("owner", farm.ownerName()), layout.rowTextWidth(permissionWidth)), getX() + 36,
                        textY + lineHeight() + 3, FarmSetupArt.MUTED, false);
                // A shared right column separates access from identity without adding a third text row.
                int ink = model.joining ? 0xFF715335 : farm.isMember() ? 0xFF8A491F
                        : farm.permission() == 2 ? 0xFF486039 : farm.permission() == 1 ? 0xFF416474 : 0xFF884A49;
                String asset = model.joining ? "approval" : farm.isMember() ? "member"
                        : farm.permission() == 2 ? "full" : farm.permission() == 1 ? "visit" : "none";
                int bx = getX() + getWidth() - 28 - permissionWidth;
                int bh = lineHeight() + 14, by = getY() + (getHeight() - bh) / 2;
                FarmBrowserArt.permission(g, asset, bx, by, permissionWidth, bh);
                var label = clip(state(farm), permissionWidth - 16);
                g.drawString(font, label, bx + (permissionWidth - font.width(label)) / 2, by + 7, ink, false);
                if (selected) FarmSetupArt.sprite(g, "check_on", getX() + getWidth() - 19, getY() + (getHeight() - 12) / 2, 12, 12);
            } else {
                FarmSetupArt.box(g, active ? isHoveredOrFocused() ? "button_hover" : kind.equals("primary") ? "button" : "tab" : "packet",
                        getX(), getY(), getWidth(), getHeight(), 4);
                var lines = font.split(getMessage(), getWidth() - 16);
                int y = getY() + (getHeight() - lines.size() * (lineHeight() + 2) + 2) / 2;
                for (var line : lines) {
                    g.drawString(font, line, getX() + (getWidth() - font.width(line)) / 2, y, active ? FarmSetupArt.INK : FarmSetupArt.MUTED, false);
                    y += lineHeight() + 2;
                }
            }
            if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF7B643C);
        }
    }
}
