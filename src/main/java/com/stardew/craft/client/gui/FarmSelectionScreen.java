package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutPreview;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import com.stardew.craft.api.v1.farm.StardewFarmSelectionOptions;
import com.stardew.craft.api.v1.internal.farm.StardewFarmSelectionOptionRegistry;
import com.stardew.craft.client.farm.FarmJoinClientState;
import com.stardew.craft.client.farm.FarmLayoutClientCatalog;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.network.payload.FarmJoinListRequestPayload;
import com.stardew.craft.network.payload.PlayerProfileSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** One creation form: land, name, profile, and addon settings submit together. */
@SuppressWarnings("null")
public class FarmSelectionScreen extends Screen implements com.stardew.craft.port.PortScreen {
    protected final FarmSetupDraft draft;
    private final boolean profileOnly;
    private final List<StardewFarmSelectionOptions.Option> options;
    private final Map<AbstractWidget, Integer> content = new IdentityHashMap<>();
    private final List<AbstractWidget> footer = new ArrayList<>();
    private final List<TextLine> text = new ArrayList<>();
    private final List<Paper> papers = new ArrayList<>();
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Random random = new Random();
    private FarmSetupLayout layout;
    private int scroll, contentHeight, farmPage;
    private double scrollbarGrab;
    private boolean defaultsSet, submitted, draggingScrollbar, pendingJoin, showLocked;
    private long lastSound;
    private Component error = Component.empty();

    public FarmSelectionScreen() { this(false); }

    protected FarmSelectionScreen(boolean profileOnly) {
        this(profileOnly, profileOnly ? List.of() : clientLayouts(), StardewFarmSelectionOptionRegistry.options());
    }

    FarmSelectionScreen(boolean profileOnly, List<StardewFarmLayoutPreview> layouts,
                        List<StardewFarmSelectionOptions.Option> options) {
        super(profileOnly ? Component.translatable("gui.stardewcraft.player_profile.legacy_title") : tr("title"));
        this.profileOnly = profileOnly;
        this.options = List.copyOf(options);
        draft = new FarmSetupDraft(layouts, options);
    }

    private static List<StardewFarmLayoutPreview> clientLayouts() {
        return FarmLayoutClientCatalog.layouts().isEmpty()
                ? StardewFarmLayouts.allRegistrations().stream().map(StardewFarmLayoutPreview::from).toList()
                : FarmLayoutClientCatalog.layouts();
    }

    static Component tr(String key, Object... arguments) {
        return Component.translatable("gui.stardewcraft.farm_setup." + key, arguments);
    }

    @Override
    protected void init() {
        font = StardewFonts.small();
        layout = FarmSetupLayout.fit(width, height, profileOnly);
        if (!defaultsSet) {
            if (draft.preferredName.isEmpty() && minecraft.player != null) draft.preferredName = minecraft.player.getName().getString();
            if (draft.farmName.isEmpty()) draft.farmName = Component.translatable(
                    "gui.stardewcraft.farm_selection.default_name.player", draft.preferredName).getString();
            defaultsSet = true;
        }
        pendingJoin = !profileOnly && FarmJoinClientState.hasPendingJoinRequest();
        buildForm();
    }

    private void buildForm() {
        clearWidgets();
        content.clear(); footer.clear(); text.clear(); fields.clear(); papers.clear();
        layout = FarmSetupLayout.fit(width, height, profileOnly);
        Component createLabel = profileOnly ? Component.translatable("gui.stardewcraft.player_profile.confirm") : tr("create");
        Component joinLabel = Component.translatable("gui.stardewcraft.farm_selection.join_farm");
        int actionWidth = layout.primaryWidth();
        int actionLines = Math.max(font.split(createLabel, actionWidth - 16).size(),
                profileOnly ? 1 : font.split(joinLabel, layout.contentWidth() - actionWidth - 54).size());
        layout = layout.withActionHeight(actionLines * (lineHeight() + 2) + 6);
        int x = layout.contentX(), w = layout.columnWidth(), top = layout.bodyTop();
        int landBottom = profileOnly ? top : land(x, top, w);
        int formX = layout.rightX();
        int formY = profileOnly ? top : layout.columns() ? top + 12 : landBottom + 14;
        int end = profile(formX, formY, layout.rightWidth());
        if (!profileOnly) {
            end = farmNaming(formX, end + 12, layout.rightWidth());
            end = pets(formX, end + 8, layout.rightWidth());
            end = settings(formX, end + 8, layout.rightWidth());
        }
        papers.add(new Paper(formX - 6, formY, layout.rightWidth() + 12, end - formY + 8, -1));
        contentHeight = Math.max(landBottom, end) - top + 8;
        FarmSetupLayout fitted = layout.withContentHeight(contentHeight);
        int shift = fitted.bodyTop() - layout.bodyTop();
        content.replaceAll((widget, yy) -> yy + shift);
        text.replaceAll(line -> new TextLine(line.x(), line.y() + shift, line.text(), line.color()));
        papers.replaceAll(p -> new Paper(p.x(), p.y() + shift, p.w(), p.h(), p.artY() < 0 ? -1 : p.artY() + shift));
        layout = fitted;
        scroll = Math.min(scroll, layout.maxScroll(contentHeight));
        positionWidgets();
        int primaryWidth = layout.primaryWidth();
        var create = new SetupButton(layout.x() + layout.width() - 14 - primaryWidth,
                layout.footerY(), primaryWidth, layout.actionHeight(), createLabel,
                this::submit, () -> true);
        create.style = "primary";
        create.active = !submitted && (profileOnly || draft.layout() != null);
        footer.add(addRenderableWidget(create));
        if (!profileOnly) {
            var join = new SetupButton(x, layout.footerY(), layout.contentWidth() - primaryWidth - 12, layout.actionHeight(), joinLabel,
                    () -> PacketDistributor.sendToServer(new FarmJoinListRequestPayload()), () -> false);
            join.style = "link";
            join.icon = "envelope";
            join.active = !submitted;
            footer.add(addRenderableWidget(join));
        }
    }

    private int land(int x, int y, int w) {
        int start = y;
        y = heading(tr("farm"), x, y, w);
        if (draft.layouts.isEmpty()) return paragraph(tr("empty"), x, y, w, FarmSetupArt.BAD);
        int artY = y;
        y += 56;
        var page = draft.page(farmPage, showLocked);
        farmPage = page.number();
        int rowHeight = 30;
        // One column leaves room for translated and addon-provided farm names.
        for (var farm : draft.layouts) rowHeight = Math.max(rowHeight,
                8 + font.split(farm.displayName(), w - 54).size() * (lineHeight() + 2));
        for (int index : page.indices()) {
            farmRow(x, y, w, rowHeight, index);
            y += rowHeight + 3;
        }
        if (page.pages() > 1) {
            int pagerHeight = Math.max(22, font.split(tr("page", page.number() + 1, page.pages()), w - 68).size() * (lineHeight() + 2) + 6);
            var previous = new SetupButton(x, y, 26, pagerHeight, tr("previous"), () -> changePage(-1), () -> false);
            previous.style = "pager"; previous.icon = "arrow_left"; previous.active = page.number() > 0;
            addContent(previous);
            var next = new SetupButton(x + w - 26, y, 26, pagerHeight, tr("next"), () -> changePage(1), () -> false);
            next.style = "pager"; next.icon = "arrow_right"; next.active = page.number() + 1 < page.pages();
            addContent(next);
            paragraph(tr("page", page.number() + 1, page.pages()), x + 34, y + 5, w - 68, FarmSetupArt.MUTED);
            y += pagerHeight + 3;
        }
        long locked = draft.layouts.stream().filter(farm -> !farm.selectable()).count();
        if (locked > 0) {
            Component label = tr("unavailable", locked);
            int h = Math.max(18, font.split(label, w - 28).size() * (lineHeight() + 2) + 6);
            var toggle = new SetupButton(x, y, w, h, label, () -> {
                showLocked = !showLocked; farmPage = 0; buildForm();
            }, () -> showLocked);
            toggle.style = "disclosure";
            addContent(toggle);
            y += h + 2;
        }
        // Selection remains explicit even when browsing a different page.
        y = paragraph(draft.layout().displayName(), x + 4, y + 5, w - 8, FarmSetupArt.INK);
        y = paragraph(draft.layout().description(), x + 4, y + 2, w - 8, FarmSetupArt.MUTED) + 8;
        papers.add(new Paper(x - 6, start, w + 12, y - start + 6, artY));
        return y + 6;
    }

    private int farmNaming(int x, int y, int w) {
        y = field("farm_name", Component.translatable("gui.stardewcraft.farm_selection.farm_name"),
                x + 4, y, w - 36, 48, draft.farmName, value -> draft.farmName = value);
        var randomButton = new SetupButton(x + w - 26, y - lineHeight() - 14, 24, lineHeight() + 12, tr("random"), () -> {
            String prefix = Component.translatable("gui.stardewcraft.farm_selection.random_name.prefix." + random.nextInt(16)).getString();
            String suffix = Component.translatable("gui.stardewcraft.farm_selection.random_name.suffix." + random.nextInt(8)).getString();
            fields.get("farm_name").setValue(Component.translatable("gui.stardewcraft.farm_selection.random_name", prefix, suffix).getString());
        }, () -> false);
        randomButton.style = "dice";
        addContent(randomButton);
        return y + 4;
    }

    private void changePage(int delta) {
        farmPage += delta;
        buildForm();
        // Keep keyboard navigation on the pager after its widgets are rebuilt.
        SetupButton fallback = null;
        for (var widget : content.keySet()) if (widget instanceof SetupButton button && button.style.equals("pager") && button.active) {
            fallback = button;
            if (button.icon.equals(delta < 0 ? "arrow_left" : "arrow_right")) {
                setFocused(button); reveal(button); return;
            }
        }
        if (fallback != null) { setFocused(fallback); reveal(fallback); }
    }

    private void farmRow(int x, int y, int w, int h, int index) {
        var preview = draft.layouts.get(index);
        var button = new SetupButton(x, y, w, h, preview.displayName(), () -> {
            if (draft.selected == index) return;
            draft.selected = index;
            error = Component.empty();
            buildForm();
        }, () -> draft.selected == index);
        button.active = preview.selectable();
        button.farm = preview;
        button.setTooltip(Tooltip.create(preview.displayName().copy().append("\n").append(preview.description())));
        addContent(button);
    }

    private int profile(int x, int y, int w) {
        y = heading(tr("profile"), x + 4, y + 4, w - 34);
        x += 4; w -= 8;
        if (profileOnly) y = paragraph(Component.translatable("gui.stardewcraft.player_profile.legacy_intro"), x, y, w, FarmSetupArt.MUTED) + 6;
        y = field("name", tr("name"), x, y, w, 48, draft.preferredName, value -> draft.preferredName = value);
        y = field("favorite", tr("favorite"), x, y, w, 64, draft.favoriteThing, value -> draft.favoriteThing = value);
        y = paragraph(tr("gender"), x, y, w, FarmSetupArt.INK) + 3;
        int half = (w - 6) / 2;
        int genderHeight = Math.max(24, Math.max(
                font.split(Component.translatable("gui.stardewcraft.player_profile.gender.male"), half - 34).size(),
                font.split(Component.translatable("gui.stardewcraft.player_profile.gender.female"), half - 34).size()) * (lineHeight() + 2) + 6);
        for (boolean male : new boolean[]{true, false}) {
            var button = new SetupButton(x + (male ? 0 : half + 6), y, half, genderHeight,
                    Component.translatable("gui.stardewcraft.player_profile.gender." + (male ? "male" : "female")),
                    () -> draft.male = male, () -> draft.male == male);
            button.check = true;
            button.radio = true;
            addContent(button);
        }
        return paragraph(tr("profile_hint"), x, y + genderHeight + 8, w, FarmSetupArt.MUTED);
    }

    private int pets(int x, int y, int w) {
        y = heading(com.stardew.craft.client.pet.PetScreen.tr("choose_pet"), x, y, w);
        int cell = (w - 16) / 5;
        var choices = com.stardew.craft.pet.PetVariant.initialChoices();
        for (int row = 0; row < (choices.size() + 4) / 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (row * 5 + col >= choices.size()) break;
                var variant = choices.get(row * 5 + col);
                var button = new SetupButton(x + col * (cell + 4), y, cell, 38,
                        variant.label(),
                        () -> { draft.petVariant = variant.id(); buildForm(); }, () -> draft.petVariant.equals(variant.id()));
                button.petIcon = com.stardew.craft.client.pet.PetScreen.icon(variant); addContent(button);
            }
            y += 42;
        }
        var none = new SetupButton(x, y, w, Math.max(24, lineHeight() + 10), com.stardew.craft.client.pet.PetScreen.tr("no_pet"),
                () -> { draft.petVariant = ""; buildForm(); }, () -> draft.petVariant.isEmpty());
        none.check = true; none.radio = true; addContent(none); y += none.getHeight() + 6;
        if (!draft.petVariant.isEmpty()) y = field("pet_name", com.stardew.craft.client.pet.PetScreen.tr("name"), x, y, w, 24, draft.petName, value -> draft.petName = value);
        return y;
    }

    private int settings(int x, int y, int w) {
        var selected = draft.layout();
        if (selected != null && (!selected.configurationFields().isEmpty() || !options.isEmpty())) {
            y = heading(tr("options"), x, y, w);
            for (var field : selected.configurationFields()) {
                var values = draft.configurations.get(selected.id());
                y = paragraph(field.label(), x, y, w, FarmSetupArt.INK) + 3;
                var b = new SetupButton(x, y, w, Math.max(22, lineHeight() + 8), fieldValue(field), () -> {
                    String current = values.get(field.id());
                    String next = switch (field.type()) {
                        case BOOLEAN -> Boolean.toString(!Boolean.parseBoolean(current));
                        case INTEGER -> Integer.toString(Integer.parseInt(current) >= field.maximum() ? field.minimum() : Integer.parseInt(current) + 1);
                        case CHOICE -> field.choices().get((field.choices().indexOf(current) + 1) % field.choices().size());
                    };
                    values.put(field.id(), next);
                    buildForm();
                }, () -> field.type() == StardewFarmLayoutConfigField.Type.BOOLEAN && Boolean.parseBoolean(values.get(field.id())));
                b.setTooltip(Tooltip.create(field.description()));
                addContent(b);
                y += b.getHeight() + 10;
            }
            for (var option : options) {
                int h = Math.max(28, font.split(option.label(), w - 34).size() * (lineHeight() + 2) + 8);
                var b = new SetupButton(x, y, w, h, option.label(), () ->
                        draft.options.compute(option.id(), (id, value) -> !Boolean.TRUE.equals(value)),
                        () -> Boolean.TRUE.equals(draft.options.get(option.id())));
                b.check = true;
                b.setTooltip(Tooltip.create(option.tooltip()));
                addContent(b);
                y += h + 5;
            }
        }
        if (pendingJoin) {
            y = paragraph(Component.translatable("gui.stardewcraft.farm_selection.pending_create.message"), x, y + 8, w, FarmSetupArt.BAD) + 5;
            Component label = Component.translatable("gui.stardewcraft.farm_selection.pending_create.confirm");
            int h = font.split(label, w - 34).size() * (lineHeight() + 2) + 8;
            var b = new SetupButton(x, y, w, h, label, () -> draft.forceCancelPending = !draft.forceCancelPending, () -> draft.forceCancelPending);
            b.check = true;
            addContent(b);
            y += h;
        }
        return y;
    }

    private Component fieldValue(StardewFarmLayoutConfigField field) {
        String value = draft.configurations.get(draft.layout().id()).get(field.id());
        return field.type() == StardewFarmLayoutConfigField.Type.BOOLEAN
                ? Component.translatable(Boolean.parseBoolean(value) ? "options.on" : "options.off") : Component.literal(value);
    }

    private int field(String key, Component label, int x, int y, int w, int max, String value, Consumer<String> changed) {
        y = paragraph(label, x, y, w, FarmSetupArt.INK) + 4;
        com.stardew.craft.port.PortEditBox box = new com.stardew.craft.port.PortEditBox(font, x + 6, y + 4, w - 12, lineHeight(), label);
        box.setBordered(false); box.setTextShadow(false); box.setTextColor(FarmSetupArt.INK);
        box.setMaxLength(max); box.setValue(value);
        box.setResponder(s -> { changed.accept(s); error = Component.empty(); });
        addContent(box);
        fields.put(key, box);
        return y + lineHeight() + 14;
    }

    private int heading(Component label, int x, int y, int w) {
        return paragraph(label, x + 4, y + 5, w - 8, FarmSetupArt.GOOD) + 7;
    }

    private int paragraph(Component label, int x, int y, int w, int color) {
        for (var line : font.split(label, w)) {
            text.add(new TextLine(x, y, line, color));
            y += lineHeight() + 2;
        }
        return y;
    }

    private void addContent(AbstractWidget widget) {
        content.put(widget, widget.getY());
        addRenderableWidget(widget);
    }

    private void positionWidgets() { content.forEach((widget, y) -> widget.setY(y - scroll)); }
    private int lineHeight() { return StardewFonts.lineHeight(font); }

    private void scrollTo(int value) {
        scroll = Math.max(0, Math.min(layout.maxScroll(contentHeight), value));
        positionWidgets();
    }

    private void reveal(AbstractWidget widget) {
        if (!content.containsKey(widget)) return;
        if (widget.getY() < layout.bodyTop() + 4) scrollTo(scroll + widget.getY() - layout.bodyTop() - 4);
        if (widget.getY() + widget.getHeight() > layout.bodyBottom() - 4)
            scrollTo(scroll + widget.getY() + widget.getHeight() - layout.bodyBottom() + 4);
    }

    private void submit() {
        if (submitted) return;
        String missing = draft.missingField(profileOnly);
        if (!missing.isEmpty()) {
            error = missing.equals("farm") ? tr("choose") : tr("required", fields.get(missing).getMessage());
            if (fields.containsKey(missing)) {
                EditBox field = fields.get(missing);
                setFocused(field); field.setFocused(true); reveal(field);
            }
            return;
        }
        if (!profileOnly && FarmJoinClientState.hasPendingJoinRequest() && !draft.forceCancelPending) {
            error = Component.translatable("gui.stardewcraft.farm_selection.pending_create.message");
            pendingJoin = true;
            buildForm();
            scrollTo(layout.maxScroll(contentHeight));
            return;
        }
        submitted = true;
        if (profileOnly) {
            PacketDistributor.sendToServer(new PlayerProfileSubmitPayload(draft.preferredName.trim(), draft.favoriteThing.trim(), draft.male));
        } else {
            var payload = draft.payload();
            for (var option : options) StardewFarmSelectionOptionRegistry.dispatch(option,
                    Boolean.TRUE.equals(draft.options.get(option.id())), payload.farmTypeId(), payload.farmName(), payload.forceCancelPending());
            PacketDistributor.sendToServer(payload);
        }
        minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        boolean current = !profileOnly && FarmJoinClientState.hasPendingJoinRequest();
        if (current != pendingJoin) { pendingJoin = current; draft.forceCancelPending = false; buildForm(); }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) return true;
        if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_PAGEUP) {
            scrollTo(scroll + (key == InputConstants.KEY_PAGEDOWN ? 1 : -1) * layout.bodyHeight()); return true;
        }
        boolean result = super.keyPressed(key, scan, modifiers);
        if (getFocused() instanceof AbstractWidget widget) reveal(widget);
        return result;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        if (!insideBody(x, y)) return false;
        scrollTo(scroll - (int) (vertical * 30));
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && layout.maxScroll(contentHeight) > 0 && x >= layout.x() + layout.width() - 17
                && x < layout.x() + layout.width() - 7 && y >= layout.bodyTop() && y < layout.bodyBottom()) {
            int thumb = scrollbarThumb();
            int thumbY = layout.bodyTop() + (layout.bodyHeight() - thumb) * scroll / layout.maxScroll(contentHeight);
            scrollbarGrab = y >= thumbY && y < thumbY + thumb ? y - thumbY : thumb / 2.0;
            draggingScrollbar = true; dragScroll(y); return true;
        }
        boolean body = insideBody(x, y);
        content.keySet().forEach(widget -> widget.visible = body);
        footer.forEach(widget -> widget.visible = !body);
        try { return super.mouseClicked(x, y, button); }
        finally {
            content.keySet().forEach(widget -> widget.visible = true);
            footer.forEach(widget -> widget.visible = true);
        }
    }

    private void dragScroll(double y) {
        scrollTo((int) ((y - layout.bodyTop() - scrollbarGrab)
                / Math.max(1, layout.bodyHeight() - scrollbarThumb()) * layout.maxScroll(contentHeight)));
    }
    private int scrollbarThumb() { return Math.max(12, layout.bodyHeight() * layout.bodyHeight() / Math.max(1, contentHeight)); }
    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScrollbar) { dragScroll(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override
    public boolean mouseReleased(double x, double y, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(x, y, button);
    }
    private boolean insideBody(double x, double y) {
        return x >= layout.contentX() - 4 && x < layout.x() + layout.width() - 18 && y >= layout.bodyTop() && y < layout.bodyBottom();
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float pt) { }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0413028);
        int titleWidth = layout.width() - (layout.columns() ? 118 : 52);
        FarmSetupArt.panel(g, layout, Math.min(titleWidth, font.width(title)));
        g.drawString(font, clip(title, titleWidth), layout.x() + 24, layout.y() + (28 - lineHeight()) / 2, FarmSetupArt.LIGHT, false);
        if (!error.getString().isEmpty()) {
            g.drawString(font, clip(error, titleWidth), layout.x() + 18, layout.y() + 29, FarmSetupArt.LIGHT, false);
            if (my >= layout.y() && my < layout.bodyTop()) g.renderTooltip(font, error, mx, my);
        }
        g.enableScissor(layout.x() + 7, layout.bodyTop(), layout.x() + layout.width() - 16, layout.bodyBottom());
        for (var paper : papers) {
            FarmSetupArt.paper(g, paper.x(), paper.y() - scroll, paper.w(), paper.h());
            if (paper.artY() >= 0) {
                FarmSetupArt.landscape(g, paper.x() + 6, paper.artY() - scroll, paper.w() - 12);
            } else {
                int yy = paper.y() - scroll;
                g.fill(paper.x() + 9, yy + 6, paper.x() + paper.w() - 9, yy + 7, 0xFFE3BF86);
                FarmSetupArt.sprite(g, "sprout", paper.x() + paper.w() - 34, yy + 18, 20, 20);
                FarmSetupArt.sprite(g, "fold", paper.x() + paper.w() - 16, yy, 16, 16);
            }
        }
        for (var line : text) if (line.y() - scroll >= layout.bodyTop() && line.y() - scroll + lineHeight() <= layout.bodyBottom())
            g.drawString(font, line.text(), line.x(), line.y() - scroll, line.color(), false);
        for (var widget : children()) if (widget instanceof AbstractWidget control && content.containsKey(control)
                && control.getY() + control.getHeight() >= layout.bodyTop() && control.getY() < layout.bodyBottom()) {
            if (control instanceof EditBox) {
                FarmSetupArt.field(g, control.getX() - 6, control.getY() - 4, control.getWidth() + 12, control.getHeight() + 8, control.isFocused());
            }
            control.render(g, insideBody(mx, my) ? mx : -1000, insideBody(mx, my) ? my : -1000, pt);
        }
        g.disableScissor();
        if (layout.maxScroll(contentHeight) > 0) {
            int barX = layout.x() + layout.width() - 13;
            int thumb = scrollbarThumb();
            int yy = layout.bodyTop() + (layout.bodyHeight() - thumb) * scroll / layout.maxScroll(contentHeight);
            g.fill(barX, layout.bodyTop(), barX + 3, layout.bodyBottom(), 0xFFD8B17A);
            g.fill(barX - 1, yy, barX + 4, yy + thumb, 0xFFA77543);
            g.fill(barX, yy + 1, barX + 2, yy + thumb - 1, 0xFFE9C383);
        }
        footer.forEach(widget -> widget.render(g, mx, my, pt));
    }

    private Component clip(Component label, int width) {
        if (font.width(label) <= width) return label;
        return Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(0, width - font.width("..."))) + "...");
    }

    private void selectSound() {
        long now = System.currentTimeMillis();
        if (minecraft != null && now - lastSound >= 80) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, .35f));
            lastSound = now;
        }
    }

    private final class SetupButton extends Button {
        private final BooleanSupplier chosen;
        private boolean check, radio;
        private String style = "tab", icon;
        private StardewFarmLayoutPreview farm;
        private ResourceLocation petIcon;
        private SetupButton(int x, int y, int w, int h, Component label, Runnable action, BooleanSupplier chosen) {
            super(x, y, w, h, label, b -> { selectSound(); action.run(); }, DEFAULT_NARRATION);
            this.chosen = chosen;
            setTooltip(Tooltip.create(label));
        }
        @Override public void playDownSound(SoundManager manager) { }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean selected = chosen.getAsBoolean();
            boolean hover = isHoveredOrFocused();
            int ink = style.equals("link") ? FarmSetupArt.LIGHT : active ? FarmSetupArt.INK : FarmSetupArt.MUTED;
            int left = getX() + 8, space = getWidth() - 16, textTop = getY(), textHeight = getHeight();
            if (petIcon != null) {
                FarmSetupArt.box(g, selected ? "packet_selected" : hover ? "button_hover" : "packet", getX(), getY(), getWidth(), getHeight(), 4);
                int size = getWidth() >= 36 ? 32 : 16;
                g.blit(petIcon, getX() + (getWidth() - size) / 2, getY() + (getHeight() - size) / 2, size, size, 0, 0, 16, 16, 16, 16);
                if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF967039);
                return;
            }
            if (farm != null) {
                FarmSetupArt.box(g, selected || (hover && active) ? "packet_selected" : "packet", getX(), getY(), getWidth(), getHeight(), 4);
                g.blit(farm.iconTexture(), getX() + 6, getY() + (getHeight() - 20) / 2, 0, 0, 22, 20, 22, 20);
                left = getX() + 34; space = getWidth() - 54;
                if (selected) {
                    g.fill(getX() + 2, getY() + 4, getX() + 4, getY() + getHeight() - 4, 0xFF647343);
                    FarmSetupArt.sprite(g, "check_on", getX() + getWidth() - 16, getY() + (getHeight() - 12) / 2, 12, 12);
                } else if (!active) FarmSetupArt.sprite(g, "lock", getX() + getWidth() - 16, getY() + (getHeight() - 12) / 2, 12, 12);
            } else if (style.equals("pager")) {
                if (hover && active) FarmSetupArt.box(g, "packet_selected", getX(), getY(), getWidth(), getHeight(), 4);
                FarmSetupArt.sprite(g, active ? icon : icon + "_disabled", getX() + (getWidth() - 12) / 2, getY() + (getHeight() - 12) / 2, 12, 12);
            } else if (style.equals("dice")) {
                if (hover) FarmSetupArt.box(g, "packet_selected", getX(), getY(), getWidth(), getHeight(), 4);
                FarmSetupArt.sprite(g, "dice", getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2, 16, 16);
            } else if (check) {
                if (hover) FarmSetupArt.box(g, "packet", getX(), getY(), getWidth(), getHeight(), 4);
                FarmSetupArt.sprite(g, (radio ? "radio_" : "check_") + (selected ? "on" : "off"), left, getY() + (getHeight() - 12) / 2, 12, 12);
                left += 18; space -= 18;
            } else if (style.equals("link") || style.equals("disclosure")) {
                if (icon != null) {
                    FarmSetupArt.sprite(g, icon, left, getY() + (getHeight() - 16) / 2, 20, 16);
                    left += 26; space -= 26;
                } else {
                    g.drawString(font, selected ? "-" : "+", left, getY() + (getHeight() - lineHeight()) / 2, ink, false);
                    left += 12; space -= 12;
                }
                if (hover) g.fill(left, getY() + getHeight() - 3, getX() + getWidth() - 8, getY() + getHeight() - 2, 0xFFC09963);
            } else {
                FarmSetupArt.box(g, hover ? "button_hover" : style.equals("primary") ? "button" : selected ? "packet_selected" : "tab",
                        getX(), getY(), getWidth(), getHeight(), 4);
            }
            if (style.equals("dice") || style.equals("pager")) {
                if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF967039);
                return;
            }
            var lines = font.split(getMessage(), space);
            int count = Math.min(lines.size(), Math.max(1, (textHeight - 6) / (lineHeight() + 2)));
            int yy = textTop + (textHeight - (count * (lineHeight() + 2) - 2)) / 2;
            for (int i = 0; i < count; i++) {
                var line = lines.get(i);
                if (i == count - 1 && lines.size() > count) {
                    StringBuilder plain = new StringBuilder();
                    line.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                    g.drawString(font, font.plainSubstrByWidth(plain.toString(), Math.max(0, space - font.width("..."))) + "...", left, yy, ink, false);
                } else g.drawString(font, line, style.equals("primary") ? getX() + (getWidth() - font.width(line)) / 2 : left, yy, ink, false);
                yy += lineHeight() + 2;
            }
            if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF967039);
        }
    }

    private record Paper(int x, int y, int w, int h, int artY) { }
    private record TextLine(int x, int y, FormattedCharSequence text, int color) { }
}
