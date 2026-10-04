package com.stardew.craft.client.gui;

import com.stardew.craft.api.v1.production.StardewCookingIngredient;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.CookingIngredientAvailabilityCache;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.TrashCanWidget;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.item.cooking.CookingDishItem;
import com.stardew.craft.menu.CookingPotMenu;
import com.stardew.craft.network.payload.CookingPotCookSubmitPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A stable recipe selection, explicit cooking action and native inventory rendering. */
@SuppressWarnings("null")
public class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
    private enum View { CATALOGUE, RECIPE, INVENTORY }
    private record Recipe(ResourceLocation id, ItemStack output) { }
    private record Detail(Component text, ItemStack icon, int color, Component count) { }
    private final List<Recipe> recipes = new ArrayList<>();
    private final List<Recipe> filtered = new ArrayList<>();
    private final List<Detail> details = new ArrayList<>();
    private final List<Detail> ingredients = new ArrayList<>();
    private CookingLayout.Grid grid;
    private int gridTop;
    private CookingLayout.Page page;
    private Recipe selected;
    private View view = View.CATALOGUE, beforeInventory = View.CATALOGUE;
    private EditBox search;
    private String query = "";
    private boolean readyOnly, opened, filterDirty;
    private int line, listScroll, detailScroll, detailHeight, detailBottom, amount = 1, cooldown, ticks;
    private int quantityY, actionY, actionHeight, toolbarY, toolbarHeight, listBottom;
    private Button cook;
    private final List<Button> quantities = new ArrayList<>();
    private ItemStack hoverItem = ItemStack.EMPTY;
    private Component hoverText;
    private int mx, my, dragArea;
    private final TrashCanWidget.Controller trashCan = new TrashCanWidget.Controller();

    public CookingPotScreen(CookingPotMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    private Component tr(String key, Object... args) { return Component.translatable("stardewcraft.cooking.ui." + key, args); }
    private Component name(Recipe recipe) { return unlocked(recipe) ? recipe.output.getHoverName() : Component.literal("???"); }
    private boolean unlocked(Recipe recipe) { return recipe != null && ClientPlayerDataCache.hasRecipe(VanillaCookingRecipeData.storageId(recipe.id)); }
    private List<StardewCookingIngredient> requirements(Recipe recipe) { return VanillaCookingRecipeData.getRequirements(recipe.id); }
    private int countMatching(StardewCookingIngredient ingredient) {
        int count = CookingIngredientAvailabilityCache.getFridgeTokenCount(ingredient.matcherKey());
        // Match the server's consumable inventory, which excludes armour and off-hand slots.
        for (ItemStack stack : minecraft.player.getInventory().items)
            if (!stack.isEmpty() && VanillaCookingRecipeData.matches(stack, ingredient)) count += stack.getCount();
        return count;
    }
    private int available(Recipe recipe) {
        if (!unlocked(recipe)) return 0;
        List<StardewCookingIngredient> reqs = requirements(recipe);
        if (reqs.isEmpty()) return 1;
        int max = Integer.MAX_VALUE;
        for (var req : reqs) if (req.count() > 0) max = Math.min(max, countMatching(req) / req.count());
        return max == Integer.MAX_VALUE ? 0 : max;
    }
    private int requested() { return amount == -1 ? Math.max(1, available(selected)) : amount; }
    private boolean catalogueVisible() { return view != View.INVENTORY && (page.wide() || view == View.CATALOGUE); }
    private boolean recipeVisible() { return view != View.INVENTORY && (page.wide() || view == View.RECIPE); }
    private boolean inventoryVisible() { return page.wide() || view == View.INVENTORY; }

    @Override protected void init() {
        super.init(); font = StardewFonts.small(); line = StardewFonts.lineHeight(font);
        recipes.clear();
        for (ResourceLocation id : VanillaCookingRecipeData.getRecipeIds()) {
            ItemStack stack = VanillaCookingRecipeData.getOutputStack(id, 1);
            if (!stack.isEmpty()) recipes.add(new Recipe(id, stack));
        }
        recipes.sort(Comparator.<Recipe>comparingInt(r -> available(r) > 0 ? 0 : unlocked(r) ? 1 : 2).thenComparing(r -> r.id.toString()));
        if (selected != null) selected = recipes.stream().filter(r -> r.id.equals(selected.id)).findFirst().orElse(null);
        filter();
        if (selected == null && !filtered.isEmpty()) selected = filtered.getFirst();
        buildControls();
        if (!opened) {
            opened = true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BOOK_READ.get(), 1f, .25f));
        }
    }
    private void filter() {
        recipes.sort(Comparator.<Recipe>comparingInt(r -> available(r) > 0 ? 0 : unlocked(r) ? 1 : 2).thenComparing(r -> r.id.toString()));
        filtered.clear();
        String term = query.strip().toLowerCase(Locale.ROOT);
        for (Recipe recipe : recipes)
            if ((!readyOnly || available(recipe) > 0) && (term.isEmpty() || name(recipe).getString().toLowerCase(Locale.ROOT).contains(term))) filtered.add(recipe);
    }
    private int buttonHeight(Component label, int w) { return Math.max(24, font.split(label, Math.max(1, w - 12)).size() * (line + 2) + 8); }
    private void buildControls() {
        boolean editing = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        setFocused(null); clearWidgets(); quantities.clear(); cook = null; search = null;
        int cw = Math.min(456, width - 12) - 32;
        int navH = Math.max(24, line + 12);
        boolean wide = cw >= 382 && Math.min(364, height - 12) >= 340 + 8 * Math.max(0, navH - 24);
        if (wide && view == View.INVENTORY) view = beforeInventory;
        toolbarHeight = view == View.CATALOGUE || wide ? navH + 10 : 0;
        page = CookingLayout.fit(width, height, navH + 14, toolbarHeight, navH);
        imageWidth = page.width(); imageHeight = page.height(); leftPos = page.x(); topPos = page.y();
        toolbarY = page.bodyTop();
        gridTop = page.bodyTop() + toolbarHeight;
        int cx = page.contentX();
        Component back = Component.translatable("gui.back");
        button(cx, topPos + 15, 24, navH, back, "back", false, () -> {
            if (view == View.INVENTORY) { view = beforeInventory; buildControls(); }
            else if (!page.wide() && view == View.RECIPE) { view = View.CATALOGUE; buildControls(); }
            else onClose();
        });
        button(leftPos + imageWidth - 36, topPos + 15, 24, navH, Component.translatable("gui.done"), "close", false, this::onClose);
        if (catalogueVisible()) {
            int gx = page.listX(), sw = page.listWidth() - 32;
            search = new EditBox(font, gx + 16, toolbarY + (navH - line) / 2, sw - 20, line + 4, tr("search"));
            search.setBordered(false); search.setTextColor(CookingArt.INK); search.setTextColorUneditable(CookingArt.MUTED);
            search.setMaxLength(80); search.setValue(query); search.setHint(tr("search").copy().withStyle(st -> st.withColor(CookingArt.MUTED)));
            search.setResponder(value -> { query = value; listScroll = 0; filterDirty = true; });
            addRenderableWidget(search);
            button(gx + sw + 4, toolbarY, 24, navH, tr("ready_only"), "check", readyOnly, () -> {
                readyOnly = !readyOnly; listScroll = 0; filter(); buildControls();
            });
            listBottom = page.wide() ? page.bodyBottom() : page.footerY() - 8;
            grid = CookingLayout.grid(gx, page.listWidth(), listBottom - gridTop);
            int totalRows = (filtered.size() + grid.columns() - 1) / grid.columns();
            listScroll = CookingLayout.clampScroll(listScroll, totalRows, grid.rows());
            int start = listScroll * grid.columns();
            for (int i = start; i < Math.min(filtered.size(), start + grid.rows() * grid.columns()); i++) {
                int index = i - start;
                addRenderableWidget(new RecipeButton(grid.startX() + index % grid.columns() * grid.cell(),
                        gridTop + index / grid.columns() * grid.cell(), grid.cell() - 2, grid.cell() - 2, filtered.get(i)));
            }
        }
        if (recipeVisible()) {
            actionHeight = buttonHeight(tr("cook"), page.detailWidth());
            actionY = page.wide() ? page.bodyBottom() - actionHeight : page.footerY() + Math.max(24, line + 12) - actionHeight;
            int qh = Math.max(24, buttonHeight(tr("all"), (page.detailWidth() - 8) / 3));
            quantityY = actionY - qh - 5; detailBottom = quantityY - 8;
            int qw = (page.detailWidth() - 8) / 3;
            int[] values = {1, 5, -1};
            for (int i = 0; i < 3; i++) {
                int value = values[i]; Component label = value == -1 ? tr("all") : Component.literal(Integer.toString(value));
                Button q = button(page.detailX() + i * (qw + 4), quantityY, i == 2 ? page.detailWidth() - 2 * (qw + 4) : qw, qh,
                        label, null, amount == value, () -> { amount = value; detailScroll = 0; buildControls(); });
                q.setTooltip(net.minecraft.client.gui.components.Tooltip.create(value == -1 ? tr("all_hint") : tr("quantity", value)));
                quantities.add(q);
            }
            cook = button(page.detailX(), actionY, page.detailWidth(), actionHeight, tr("cook"), null, true, this::submit);
            rebuildDetails(); updateAction();
        }
        if (!page.wide()) {
            if (view == View.CATALOGUE) button(cx, page.footerY(), cw, navH, Component.translatable("container.inventory"), null, false, this::showInventory);
            else if (view == View.RECIPE) {
                // Inventory access stays separate from the cooking action, even in a narrow viewport.
                button(leftPos + imageWidth - 66, topPos + 15, 24, navH, Component.translatable("container.inventory"), "inventory", false, this::showInventory);
            }
        }
        positionSlots();
        if (editing && search != null) { setInitialFocus(search); search.moveCursorTo(Math.min(cursor, query.length()), false); }
    }
    private void showInventory() { beforeInventory = view; view = View.INVENTORY; buildControls(); }
    private void positionSlots() {
        int x = (imageWidth - 162) / 2;
        int y = page.wide() ? page.inventoryY() - topPos + 4 : Math.max(page.bodyTop() - topPos + 24, (imageHeight - 76) / 2);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            slot.x = inventoryVisible() ? x + (i < 27 ? i % 9 : i - 27) * 18 : -10000;
            slot.y = inventoryVisible() ? y + (i < 27 ? i / 9 * 18 : 58) : -10000;
        }
    }
    private void updateAction() {
        if (cook == null) return;
        cook.active = selected != null && unlocked(selected) && available(selected) >= requested() && cooldown == 0;
        for (Button q : quantities) q.active = unlocked(selected);
    }
    private void submit() {
        updateAction();
        if (cook == null || !cook.active) return;
        PacketDistributor.sendToServer(new CookingPotCookSubmitPayload(selected.id.toString(), amount));
        cooldown = 8; updateAction();
    }
    private void addDetail(Component text, int color) { details.add(new Detail(text, ItemStack.EMPTY, color, null)); }
    private void rebuildDetails() {
        details.clear(); ingredients.clear();
        if (selected == null) { addDetail(tr("select"), CookingArt.MUTED); return; }
        if (!unlocked(selected)) {
            addDetail(tr("learn"), CookingArt.MUTED);
            return;
        }
        for (var req : requirements(selected)) {
            ItemStack icon = VanillaCookingRecipeData.getDisplayStack(req);
            int count = countMatching(req); long needed = (long) req.count() * requested();
            ingredients.add(new Detail(VanillaCookingRecipeData.describe(req), icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon,
                    count >= needed ? CookingArt.GREEN : CookingArt.RED, Component.literal(count + " / " + needed)));
        }
        addDetail(tr("source"), CookingArt.MUTED);
        if (available(selected) < requested()) addDetail(tr("missing"), CookingArt.RED);
        String desc = selected.output.getDescriptionId() + ".desc";
        if (I18n.exists(desc)) addDetail(Component.translatable(desc), CookingArt.MUTED);
        if (selected.output.getItem() instanceof CookingDishItem dish) {
            addDetail(Component.translatable("stardewcraft.cooking.energy", dish.getEnergy(selected.output)).copy().append("  ").append(Component.translatable("stardewcraft.cooking.health", dish.getHealth(selected.output))), CookingArt.GREEN);
            for (var buff : dish.getBuffs()) {
                String suffix = switch (buff.type()) {
                    case MAX_ENERGY -> "max_energy"; case FISHING -> "fishing_level"; case FARMING -> "farming_level";
                    case FORAGING -> "foraging_level"; case MINING -> "mining_level"; case MAGNETIC_RADIUS -> "magnetic_radius";
                    default -> buff.type().name().toLowerCase(Locale.ROOT);
                };
                int seconds = buff.durationTicks() / 20;
                addDetail(Component.translatable("stardewcraft.tooltip.buff." + suffix, com.stardew.craft.item.cooking.CookingDishItem.displayAmountFor(buff)).copy().append("  " + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60)), CookingArt.GREEN);
            }
        }
    }
    @Override protected void containerTick() {
        super.containerTick(); if (cooldown > 0) cooldown--;
        if (filterDirty) { filterDirty = false; filter(); buildControls(); }
        if (++ticks % 5 == 0) {
            List<Recipe> old = List.copyOf(filtered); filter();
            if (!old.equals(filtered)) buildControls();
            else if (recipeVisible()) rebuildDetails();
        }
        updateAction();
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) { }
    @Override protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        CookingArt.page(g, page);
        Component heading = view == View.INVENTORY ? Component.translatable("container.inventory") : Component.translatable("stardewcraft.cooking.title");
        int titleW = imageWidth - (!page.wide() && view == View.RECIPE ? 132 : 102);
        clippedText(g, heading, page.contentX() + 34, topPos + 15 + (Math.max(24, line + 12) - line) / 2, titleW, CookingArt.INK);
        if (catalogueVisible()) {
            int gx = page.listX(), sw = page.listWidth() - 32;
            CookingArt.sprite(g, "search", gx + 2, toolbarY + (Math.max(24, line + 12) - 8) / 2, 8, 8);
            CookingArt.rule(g, gx, toolbarY + Math.max(24, line + 12) - 1, sw);
            if (filtered.isEmpty()) paragraph(g, tr("empty"), gx + 8, gridTop + 12, page.listWidth() - 24, CookingArt.MUTED);
            int totalRows = (filtered.size() + grid.columns() - 1) / grid.columns();
            scrollbar(g, gx + page.listWidth() - 4, gridTop, listBottom - gridTop, listScroll,
                    Math.max(0, totalRows - grid.rows()), grid.rows(), totalRows);
        }
        if (recipeVisible()) drawDetails(g);
        if (inventoryVisible()) {
            for (Slot slot : menu.slots) CookingArt.box(g, "slot", leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }
    private void drawDetails(GuiGraphics g) {
        int w = page.detailWidth() - 10;
        int heroH = selected == null ? 0 : 60 + font.split(name(selected), w).size() * (line + 3);
        int headingH = ingredients.isEmpty() ? 0 : font.split(tr("ingredients", requested()), w).size() * (line + 3) + 6;
        int countWidth = ingredients.stream().mapToInt(d -> font.width(d.count)).max().orElse(0);
        int columns = CookingLayout.ingredientColumns(w, countWidth), cellW = w / columns;
        int countLines = ingredients.stream().mapToInt(d -> font.split(d.count, cellW - 4).size()).max().orElse(1);
        int cellH = 24 + countLines * (line + 2), ingredientH = ((ingredients.size() + columns - 1) / columns) * cellH;
        detailHeight = heroH + headingH + ingredientH;
        for (Detail d : details) detailHeight += font.split(d.text, w).size() * (line + 3) + 10;
        int viewHeight = Math.max(1, detailBottom - page.bodyTop());
        detailScroll = Math.max(0, Math.min(detailScroll, detailHeight - viewHeight));
        g.enableScissor(page.detailX(), page.bodyTop(), page.detailX() + page.detailWidth(), detailBottom);
        int y = page.bodyTop() - detailScroll;
        if (selected != null) {
            int plateX = page.detailX() + (w - 48) / 2;
            CookingArt.icon(g, "plate", plateX, y, 3);
            CookingArt.dish(g, selected.output, plateX + 8, y + 4, 2, !unlocked(selected) ? 0f : available(selected) > 0 ? 1f : .66f);
            if (inside(mx, my, plateX, Math.max(y, page.bodyTop()), 48, Math.min(y + 48, detailBottom) - Math.max(y, page.bodyTop())) && unlocked(selected)) hoverItem = selected.output;
            int ty = y + 54;
            for (var part : font.split(name(selected), w)) {
                g.drawString(font, part, page.detailX() + (w - font.width(part)) / 2, ty, CookingArt.INK, false); ty += line + 3;
            }
            y += heroH;
        }
        if (!ingredients.isEmpty()) {
            paragraph(g, tr("ingredients", requested()), page.detailX(), y, w, CookingArt.INK); y += headingH;
            for (int i = 0; i < ingredients.size(); i++) {
                Detail d = ingredients.get(i); int cellX = page.detailX() + i % columns * cellW, cellY = y + i / columns * cellH;
                g.renderItem(d.icon, cellX + (cellW - 16) / 2, cellY + 2);
                int ty = cellY + 21;
                for (var part : font.split(d.count, cellW - 4)) { g.drawString(font, part, cellX + (cellW - font.width(part)) / 2, ty, d.color, false); ty += line + 2; }
                if (inside(mx, my, cellX, Math.max(cellY, page.bodyTop()), cellW,
                        Math.min(cellY + cellH, detailBottom) - Math.max(cellY, page.bodyTop())))
                    hoverText = d.text.copy().append("  ").append(d.count).append("\n").append(tr("source"));
            }
            y += ingredientH;
        }
        for (Detail d : details) y = paragraph(g, d.text, page.detailX(), y, w, d.color) + 10;
        g.disableScissor();
        scrollbar(g, page.detailX() + page.detailWidth() - 4, page.bodyTop(), viewHeight, detailScroll, Math.max(0, detailHeight - viewHeight), viewHeight, detailHeight);
    }
    private int paragraph(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence part : font.split(text, Math.max(1, w))) { g.drawString(font, part, x, y, color, false); y += line + 3; }
        return y;
    }
    private void clippedText(GuiGraphics g, Component text, int x, int y, int w, int color) {
        String s = text.getString();
        if (font.width(s) > w) {
            s = font.plainSubstrByWidth(s, Math.max(0, w - font.width("..."))) + "...";
            if (inside(mx, my, x, y, w, line + 3)) hoverText = text;
        }
        g.drawString(font, s, x, y, color, false);
    }
    private void scrollbar(GuiGraphics g, int x, int y, int h, int offset, int max, int visible, int total) {
        if (max <= 0 || h <= 0) return;
        int thumb = Math.min(h, Math.max(12, h * visible / Math.max(1, total)));
        int ty = y + (h - thumb) * offset / max;
        g.fill(x, y, x + 3, y + h, 0xFFDBCEB5); g.fill(x - 1, ty, x + 4, ty + thumb, 0xFFA45D43);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        mx = mouseX; my = mouseY; hoverItem = ItemStack.EMPTY; hoverText = null;
        renderTransparentBackground(g);
        super.render(g, mouseX, mouseY, tick);
        trashCan.render(g, trashX(), trashY(), mouseX, mouseY);
        if (trashCan.renderTooltip(g, font, menu, trashX(), trashY(), mouseX, mouseY)) return;
        if (!hoverItem.isEmpty()) g.renderTooltip(minecraft.font, hoverItem, mouseX, mouseY);
        else if (hoverText != null) g.renderTooltip(font, font.split(hoverText, Math.min(240, width - 24)), mouseX, mouseY);
        else renderTooltip(g, mouseX, mouseY);
    }
    private boolean inside(double x, double y, int rx, int ry, int w, int h) { return x >= rx && x < rx + w && y >= ry && y < ry + h; }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
        if (catalogueVisible() && inside(x, y, page.listX(), gridTop, page.listWidth(), listBottom - gridTop)) {
            listScroll += vertical < 0 ? 1 : -1; buildControls(); return true;
        }
        if (recipeVisible() && inside(x, y, page.detailX(), page.bodyTop(), page.detailWidth(), detailBottom - page.bodyTop())) {
            detailScroll = Math.max(0, Math.min(detailScroll + (vertical < 0 ? 24 : -24), detailHeight - (detailBottom - page.bodyTop()))); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (trashCan.click(menu, trashX(), trashY(), x, y, button)) return true;
        if (button == 0) {
            if (catalogueVisible() && inside(x, y, page.listX() + page.listWidth() - 6, gridTop, 9, listBottom - gridTop)) dragArea = 1;
            else if (recipeVisible() && inside(x, y, page.detailX() + page.detailWidth() - 6, page.bodyTop(), 9, detailBottom - page.bodyTop())) dragArea = 2;
            if (dragArea != 0) { dragScroll(y); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    private void dragScroll(double y) {
        int top = dragArea == 1 ? gridTop : page.bodyTop();
        int h = (dragArea == 1 ? listBottom : detailBottom) - top;
        int visible = dragArea == 1 ? grid.rows() : h;
        int total = dragArea == 1 ? (filtered.size() + grid.columns() - 1) / grid.columns() : detailHeight;
        int max = Math.max(0, total - visible), thumb = Math.min(h, Math.max(12, h * visible / Math.max(1, total)));
        int value = (int) Math.round(Math.max(0, Math.min(1, (y - top - thumb / 2.0) / Math.max(1, h - thumb))) * max);
        if (dragArea == 1) { listScroll = value; buildControls(); } else detailScroll = value;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragArea != 0 && button == 0) { dragScroll(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragArea != 0) { dragArea = 0; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((search == null || !search.isFocused()) && trashCan.deleteKey(menu, key)) return true;
        if (key == 256 && !page.wide() && view != View.CATALOGUE) {
            view = view == View.INVENTORY ? beforeInventory : View.CATALOGUE; buildControls(); return true;
        }
        if (search != null && search.isFocused() && key != 256 && key != 258) return search.keyPressed(key, scan, mods);
        if (key == 266 || key == 267) {
            if (catalogueVisible()) { listScroll += key == 267 ? 3 : -3; buildControls(); }
            else if (recipeVisible()) detailScroll = Math.max(0, Math.min(detailScroll + (key == 267 ? 60 : -60), detailHeight - (detailBottom - page.bodyTop())));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    private int trashX() { return trashCan.xBeside(leftPos, imageWidth, width); }
    private int trashY() { return topPos + imageHeight - 34; }
    public List<Rect2i> jeiGuiExtraAreas() { return List.of(new Rect2i(trashX(), trashY(), 18, 34)); }
    private Button button(int x, int y, int w, int h, Component label, String icon, boolean primary, Runnable action) {
        Button button = new Button(x, y, w, h, label, b -> action.run(), supplier -> supplier.get()) {
            @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
                CookingArt.box(g, !active ? "disabled" : primary ? isHoveredOrFocused() ? "button_hover" : "button" : isHoveredOrFocused() ? "secondary_hover" : "secondary", getX(), getY(), getWidth(), getHeight());
                if (icon != null) {
                    if (icon.equals("inventory")) g.renderItem(new ItemStack(Items.CHEST), getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
                    else CookingArt.sprite(g, primary && icon.equals("check") ? "check_light" : icon, getX() + (getWidth() - 8) / 2, getY() + (getHeight() - 8) / 2, 8, 8);
                    if (isHoveredOrFocused()) hoverText = getMessage();
                } else {
                    var parts = font.split(getMessage(), getWidth() - 12); int yy = getY() + (getHeight() - parts.size() * (line + 2) + 2) / 2;
                    for (var part : parts) { g.drawString(font, part, getX() + (getWidth() - font.width(part)) / 2, yy, !active ? CookingArt.MUTED : primary ? 0xFFFFF3D7 : CookingArt.INK, false); yy += line + 2; }
                }
                if (isFocused()) { g.renderOutline(getX() + 3, getY() + 3, getWidth() - 6, getHeight() - 6, primary ? 0xFFE9CBA0 : CookingArt.RED); }
            }
            @Override public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) { sounds.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, .25f)); }
        };
        return addRenderableWidget(button);
    }
    private final class RecipeButton extends Button {
        private final Recipe recipe;
        RecipeButton(int x, int y, int w, int h, Recipe recipe) {
            super(x, y, w, h, name(recipe), b -> {
                selected = recipe; detailScroll = 0;
                if (!page.wide()) view = View.RECIPE;
                buildControls();
            }, supplier -> supplier.get()); this.recipe = recipe;
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
            boolean chosen = selected != null && selected.id.equals(recipe.id), known = unlocked(recipe);
            if (chosen || isHoveredOrFocused()) {
                g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFFEAD3A4);
                int c = isFocused() ? CookingArt.RED : 0xFFA5774E;
                for (int xx : new int[]{getX(), getX() + getWidth() - 5}) for (int yy : new int[]{getY(), getY() + getHeight() - 1}) g.fill(xx, yy, xx + 5, yy + 1, c);
                for (int xx : new int[]{getX(), getX() + getWidth() - 1}) for (int yy : new int[]{getY(), getY() + getHeight() - 5}) g.fill(xx, yy, xx + 1, yy + 5, c);
            }
            CookingArt.dish(g, recipe.output, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2, 1,
                    !known ? 0f : available(recipe) > 0 ? 1f : .66f);
            if (isHoveredOrFocused()) { if (known) hoverItem = recipe.output; else hoverText = Component.literal("???"); }
        }
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) { sounds.play(SimpleSoundInstance.forUI(ModSounds.SMALL_SELECT.get(), 1f, .25f)); }
    }
}
