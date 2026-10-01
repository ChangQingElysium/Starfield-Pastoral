package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.network.payload.WorkbenchCraftPayload;
import com.stardew.craft.network.payload.WorkbenchCraftResultPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.workbench.WorkbenchEntry;
import com.stardew.craft.workbench.WorkbenchRecipeManager;
import com.stardew.craft.workbench.WorkbenchType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Shared, original pixel workshop UI. No container inventory is moved on the client. */
@SuppressWarnings("null")
public class WorkbenchScreen extends Screen {
    private final WorkbenchType type;
    private List<WorkbenchEntry> recipes = List.of();
    private final List<WorkbenchEntry> filtered = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private WorkbenchLayout layout;
    private int category, categoryOffset, categoryEnd, page, selected, quantity = 1;
    private int normalCount, hardCount, ticks;
    private boolean pending;
    private long openedAt, lastSelectSound;
    private Component status = Component.empty();
    private Component pendingName = Component.empty();
    private boolean statusError;
    private WorkshopButton craft, minus, plus, maximum, previous, next;
    private EditBox quantityInput;

    public WorkbenchScreen(WorkbenchType type) {
        super(Component.translatable("stardewcraft.workbench." + type.getKey() + ".title"));
        this.type = type;
    }

    private static Component tr(String suffix, Object... args) {
        return Component.translatable("stardewcraft.workbench." + suffix, args);
    }

    @Override
    protected void init() {
        font = StardewFonts.small();
        openedAt = System.currentTimeMillis();
        recipes = WorkbenchRecipeManager.getRecipes(type);
        categories.clear();
        categories.add("all");
        var seen = new LinkedHashSet<String>();
        recipes.forEach(e -> seen.add(e.category()));
        categories.addAll(seen);
        category = Math.min(category, categories.size() - 1);
        layout = WorkbenchLayout.fit(width, height, StardewFonts.lineHeight(font));
        filter(false);
        recount();
        buildWidgets();
    }

    private void filter(boolean reset) {
        ResourceLocation old = entry() == null ? null : entry().itemId();
        filtered.clear();
        String cat = categories.get(category);
        for (var recipe : recipes) if (cat.equals("all") || cat.equals(recipe.category())) filtered.add(recipe);
        selected = 0;
        if (!reset && old != null) {
            for (int i = 0; i < filtered.size(); i++) if (filtered.get(i).itemId().equals(old)) selected = i;
        }
        page = selected / layout.capacity();
        if (reset) quantity = 1;
    }

    private Component categoryName(int index) {
        String key = categories.get(index);
        return tr(key.equals("all") ? "tab.all" : "cat." + key);
    }

    private WorkshopButton button(int x, int y, int w, int h, Component text, Runnable action, boolean primary) {
        return addRenderableWidget(new WorkshopButton(x, y, w, h, text, action, primary));
    }

    private void buildWidgets() {
        clearWidgets();
        int x = layout.x(), y = layout.y(), w = layout.width();
        button(x + w - 28, y + 10, 18, 17, Component.literal("×"), this::onClose, true)
                .setTooltip(Tooltip.create(Component.translatable("gui.close")));
        int tabY = y + 34;
        button(x + 10, tabY, 18, 18, Component.literal("‹"), () -> shiftCategories(-1), false).active = categoryOffset > 0;
        int tabX = x + 32;
        categoryEnd = categoryOffset;
        while (categoryEnd < categories.size()) {
            Component label = categoryName(categoryEnd);
            int tabWidth = Math.min(100, Math.max(40, font.width(label) + 16));
            if (tabX + tabWidth > x + w - 32) break;
            final int index = categoryEnd++;
            WorkshopButton b = button(tabX, tabY, tabWidth, 18, label, () -> chooseCategory(index), false);
            b.chosen = index == category;
            b.setTooltip(Tooltip.create(label));
            tabX += tabWidth + 3;
        }
        button(x + w - 28, tabY, 18, 18, Component.literal("›"), () -> shiftCategories(1), false)
                .active = categoryEnd < categories.size();
        int start = page * layout.capacity();
        for (int i = 0; i < layout.capacity() && start + i < filtered.size(); i++) {
            final int index = start + i;
            var recipe = filtered.get(index);
            int cellX = layout.gridX() + (i % layout.columns()) * WorkbenchLayout.PITCH;
            int cellY = layout.gridY() + (i / layout.columns()) * WorkbenchLayout.PITCH;
            WorkshopButton b = button(cellX, cellY, 24, 24, stack(recipe.itemId()).getHoverName(), () -> select(index), false);
            b.recipeIndex = index;
        }
        int pageY = layout.bottom() - 18;
        previous = button(layout.gridX(), pageY, 20, 18, Component.literal("‹"), () -> turnPage(-1), false);
        next = button(layout.gridX() + layout.gridWidth() - 20, pageY, 20, 18, Component.literal("›"), () -> turnPage(1), false);
        int dx = layout.detailX(), dw = layout.detailWidth(), qy = layout.quantityY();
        minus = button(dx + 5, qy, 18, 20, Component.literal("-"), () -> setQuantity(quantity - 1), false);
        minus.setTooltip(Tooltip.create(tr("less")));
        plus = button(dx + dw - 59, qy, 18, 20, Component.literal("+"), () -> setQuantity(quantity + 1), false);
        plus.setTooltip(Tooltip.create(tr("more")));
        maximum = button(dx + dw - 37, qy, 32, 20, tr("maximum"), () -> setQuantity(limit()), false);
        maximum.setTooltip(Tooltip.create(tr("maximum_hint")));
        quantityInput = new EditBox(font, dx + 28, qy + (20 - StardewFonts.lineHeight(font)) / 2,
                dw - 91, StardewFonts.lineHeight(font), tr("quantity"));
        quantityInput.setBordered(false);
        quantityInput.setTextColor(WorkbenchArt.INK);
        quantityInput.setMaxLength(3);
        quantityInput.setFilter(value -> value.matches("[0-9]{0,3}"));
        quantityInput.setValue(Integer.toString(quantity));
        quantityInput.setResponder(value -> {
            quantity = value.isEmpty() ? 0 : Integer.parseInt(value);
            status = Component.empty();
            updateControls();
        });
        addRenderableWidget(quantityInput);
        craft = button(dx + 5, layout.craftY(), dw - 10, 20, tr("craft"), this::doCraft, true);
        updateControls();
    }

    private void chooseCategory(int index) {
        if (category == index || pending) return;
        category = index;
        filter(true);
        status = Component.empty();
        selectSound();
        buildWidgets();
    }

    private void shiftCategories(int direction) {
        if (pending) return;
        int offset = Math.max(0, Math.min(categories.size() - 1, categoryOffset + direction));
        if (categoryOffset == offset) return;
        categoryOffset = offset;
        selectSound();
        buildWidgets();
    }

    private void select(int index) {
        if (pending || selected == index) return;
        selected = index;
        quantity = Math.max(1, Math.min(quantity, limit()));
        quantityInput.setValue(Integer.toString(quantity));
        status = Component.empty();
        selectSound();
        updateControls();
    }

    private void turnPage(int direction) {
        if (pending) return;
        int target = Math.max(0, Math.min(lastPage(), page + direction));
        if (target == page) return;
        page = target;
        selected = page * layout.capacity();
        quantity = 1;
        status = Component.empty();
        selectSound();
        buildWidgets();
    }

    private void setQuantity(int value) {
        if (pending) return;
        int updated = Math.max(1, Math.min(Math.max(1, limit()), value));
        if (updated == quantity) return;
        quantity = updated;
        quantityInput.setValue(Integer.toString(quantity));
        status = Component.empty();
        selectSound();
        updateControls();
    }

    private WorkbenchEntry entry() {
        return selected >= 0 && selected < filtered.size() ? filtered.get(selected) : null;
    }

    private int lastPage() { return Math.max(0, (filtered.size() - 1) / layout.capacity()); }

    private int available(WorkbenchEntry e) {
        if (type == WorkbenchType.TEMPLATE) return e.inputItemId(type).equals("stardewcraft:wood_hard") ? hardCount : normalCount;
        return normalCount + (type.hasBonus() ? hardCount * type.getBonusMultiplier() : 0);
    }

    private int hardUsed(int batches) {
        return type.hasBonus() && entry() != null ? Math.min(hardCount, batches * entry().cost() / type.getBonusMultiplier()) : 0;
    }

    private boolean affordable(WorkbenchEntry e, int batches) {
        if (e == null || batches < 1) return false;
        int cost = e.cost() * batches;
        if (type.hasBonus()) {
            int used = Math.min(hardCount, cost / type.getBonusMultiplier());
            return cost - used * type.getBonusMultiplier() <= normalCount;
        }
        return cost <= available(e);
    }

    private int limit() {
        var e = entry();
        if (e == null) return 0;
        int result = Math.min(999, Math.min(available(e) / e.cost(),
                stack(e.itemId()).getMaxStackSize() * 36 / e.outputCount()));
        while (result > 0 && !affordable(e, result)) result--;
        return result;
    }

    private void recount() {
        if (minecraft == null || minecraft.player == null) return;
        normalCount = count(type.getInputItemId());
        hardCount = type == WorkbenchType.STONE ? 0 : count("stardewcraft:wood_hard");
    }

    private int count(String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        int total = 0;
        for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
            ItemStack s = minecraft.player.getInventory().getItem(i);
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }

    @Override
    public void tick() {
        if (++ticks % 5 == 0) {
            recount();
            updateControls();
        }
    }

    private void updateControls() {
        if (craft == null) return;
        var e = entry();
        int max = limit();
        craft.active = !pending && quantity <= max && affordable(e, quantity);
        craft.setMessage(pending ? tr("working") : tr("craft_amount", e == null ? 0 : quantity * e.outputCount()));
        minus.active = !pending && quantity > 1;
        plus.active = !pending && quantity < max;
        maximum.active = !pending && max > 0 && quantity != max;
        quantityInput.setEditable(!pending);
        previous.active = !pending && page > 0;
        next.active = !pending && page < lastPage();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public void render(@Nonnull GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xA0182225);
        WorkbenchArt.panel(g, layout);
        WorkbenchArt.sprite(g, type.getKey(), layout.x() + 12, layout.y() + 11, 16);
        g.drawString(font, clipped(title, layout.width() - 70), layout.x() + 34,
                layout.y() + 7 + (23 - StardewFonts.lineHeight(font)) / 2, WorkbenchArt.LIGHT, false);
        int dx = layout.detailX(), dy = layout.gridY(), dw = layout.detailWidth();
        WorkbenchArt.box(g, "well", dx, dy, dw, layout.bottom() - dy, 2);
        renderDetails(g);
        String pageText = (page + 1) + " / " + (lastPage() + 1);
        g.drawCenteredString(font, pageText, layout.gridX() + layout.gridWidth() / 2,
                layout.bottom() - 13, WorkbenchArt.INK);
        super.render(g, mx, my, pt);
        for (int i = page * layout.capacity(); i < Math.min(filtered.size(), (page + 1) * layout.capacity()); i++) {
            int local = i - page * layout.capacity();
            int x = layout.gridX() + local % layout.columns() * 26;
            int y = layout.gridY() + local / layout.columns() * 26;
            if (inside(mx, my, x, y, 24, 24)) tooltip(g, filtered.get(i), mx, my);
        }
        if (entry() != null && inside(mx, my, dx, dy, dw, layout.quantityY() - 37 - dy)) tooltip(g, entry(), mx, my);
        if (inside(mx, my, quantityInput.getX(), layout.quantityY(), quantityInput.getWidth(), 20)) {
            g.renderTooltip(font, tr("batches", quantity), mx, my);
        }
        if (entry() != null && inside(mx, my, dx, layout.statusY(), dw, layout.statusHeight())) {
            g.renderTooltip(font, statusMessage(), mx, my);
        }
    }

    private void renderDetails(GuiGraphics g) {
        var e = entry();
        if (e == null) {
            wrapped(g, tr("select_hint"), layout.detailX() + layout.detailWidth() / 2,
                    layout.gridY() + 12, layout.detailWidth() - 14, WorkbenchArt.MUTED, 3);
            return;
        }
        int x = layout.detailX(), w = layout.detailWidth(), cx = x + w / 2;
        int costY = layout.quantityY() - 36;
        int topRoom = costY - layout.gridY() - 6;
        int icon = topRoom >= 66 ? 32 : topRoom >= 42 ? 16 : 0;
        ItemStack result = stack(e.itemId());
        int lineStep = StardewFonts.lineHeight(font) + 2;
        int nameHeight = font.split(result.getHoverName(), w - 14).size() * lineStep - 2;
        if (icon > 0 && nameHeight > costY - (layout.gridY() + icon + 10) - 2) icon = 0;
        if (icon > 0) CommonGuiTextures.drawItem(g, result, cx - icon / 2, layout.gridY() + 5, icon / 16f);
        int nameY = layout.gridY() + (icon > 0 ? icon + 10 : 6);
        wrapped(g, result.getHoverName(), cx, nameY, w - 14, WorkbenchArt.INK,
                Math.max(1, (costY - nameY) / lineStep));
        g.drawString(font, tr("total_cost", quantity), x + 7, costY, WorkbenchArt.MUTED, false);
        int total = e.cost() * quantity, hard = hardUsed(quantity);
        int materialY = layout.quantityY() - 18;
        int materialX = x + 7;
        if (hard > 0) {
            materialX = drawMaterial(g, "stardewcraft:wood_hard", hard, materialX, materialY, true);
            total -= hard * type.getBonusMultiplier();
        }
        if (total > 0) drawMaterial(g, e.inputItemId(type), total, materialX, materialY, affordable(e, quantity));
        WorkbenchArt.box(g, "well", x + 25, layout.quantityY(), w - 88, 20, 2);
        wrapped(g, statusMessage(), cx, layout.statusY(), w - 12,
                (statusError && !status.getString().isEmpty()) || !affordable(e, quantity) || quantity > limit()
                        ? WorkbenchArt.BAD : WorkbenchArt.GOOD, 2);
    }

    private Component statusMessage() {
        if (!status.getString().isEmpty()) return status;
        var e = entry();
        return quantity < 1 ? tr("quantity_invalid") : !affordable(e, quantity) ? tr("need_materials")
                : quantity > limit() ? tr("batch_limit", limit())
                : tr(type.hasBonus() ? "available_equivalent" : "available", available(e));
    }

    // Three ASCII dots also work in the Russian and Portuguese sprite fonts.
    private Component clipped(Component text, int width) {
        if (font.width(text) <= width) return text;
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...")
                .withStyle(text.getStyle());
    }

    private void wrapped(GuiGraphics g, Component text, int cx, int y, int width, int color, int maxLines) {
        var lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                StringBuilder plain = new StringBuilder();
                line.accept((index, style, codePoint) -> { plain.appendCodePoint(codePoint); return true; });
                line = FormattedCharSequence.forward(font.plainSubstrByWidth(plain.toString(),
                        Math.max(0, width - font.width("..."))) + "...", Style.EMPTY);
            }
            g.drawString(font, line, cx - font.width(line) / 2, y, color, false);
            y += StardewFonts.lineHeight(font) + 2;
        }
    }

    private int drawMaterial(GuiGraphics g, String id, int count, int x, int y, boolean enough) {
        CommonGuiTextures.drawItem(g, stack(new ResourceLocation(id)), x, y, 1);
        String text = Integer.toString(count);
        g.drawString(font, text, x + 18, y + 4, enough ? WorkbenchArt.INK : WorkbenchArt.BAD, false);
        return x + 23 + font.width(text);
    }

    private void tooltip(GuiGraphics g, WorkbenchEntry e, int mx, int my) {
        var item = stack(e.itemId());
        List<Component> lines = new ArrayList<>(item.getTooltipLines(minecraft.player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL));
        lines.add(tr("recipe_cost", stack(new ResourceLocation(e.inputItemId(type))).getHoverName(), e.cost(), e.outputCount()));
        lines.add(tr("in_stock", stack(new ResourceLocation(e.inputItemId(type))).getHoverName(),
                e.inputItemId(type).equals("stardewcraft:wood_hard") ? hardCount : normalCount));
        if (type.hasBonus()) lines.add(tr("hardwood_exchange", type.getBonusMultiplier()));
        g.renderTooltip(font, lines, Optional.empty(), mx, my);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (System.currentTimeMillis() - openedAt < 200) return true;
        if (button == 1 && inside(x, y, layout.detailX(), layout.quantityY(), layout.detailWidth(), 20)) {
            setQuantity(quantity + 5);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        double horizontal = 0.0D; // PORT(1.20.1): no horizontal scroll before 1.20.2
        if (vertical == 0) return false;
        if (inside(x, y, layout.gridX(), layout.gridY(), layout.gridWidth(), layout.gridHeight())) {
            turnPage(vertical > 0 ? -1 : 1);
            return true;
        }
        if (inside(x, y, layout.x() + 10, layout.y() + 34, layout.width() - 20, 18)) {
            shiftCategories(vertical > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_E) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void doCraft() {
        recount();
        if (pending || quantity > limit() || !affordable(entry(), quantity)) { updateControls(); return; }
        pending = true;
        pendingName = stack(entry().itemId()).getHoverName();
        status = tr("working");
        statusError = false;
        updateControls();
        PacketDistributor.sendToServer(new WorkbenchCraftPayload(type.getId(), entry().itemId().toString(), quantity));
    }

    public void onCraftResult(WorkbenchCraftResultPayload result) {
        if (!pending) return;
        pending = false;
        statusError = !result.success();
        status = result.success() ? tr("made", result.craftedCount()) : tr("failed");
        if (result.success()) {
            SoundEvent sound = switch (type) {
                case WOOD -> ModSounds.WOOD_WHACK.get();
                case STONE -> ModSounds.STONE_BUTTON.get();
                case TEMPLATE -> ModSounds.CRAFTING.get();
            };
            sound(sound, type == WorkbenchType.STONE ? .42f : .30f);
            minecraft.getNarrator().sayNow(tr("made_item", pendingName, result.craftedCount()));
        } else {
            sound(ModSounds.CANCEL.get(), .25f);
            minecraft.getNarrator().sayNow(status);
        }
        recount();
        updateControls();
    }

    private void selectSound() {
        long now = System.currentTimeMillis();
        if (now - lastSelectSound < 70) return;
        lastSelectSound = now;
        sound(ModSounds.SMALL_SELECT.get(), .35f);
    }

    private void sound(SoundEvent event, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1f, volume));
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && y >= top && x < left + w && y < top + h;
    }

    private static ItemStack stack(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public ClickableItem jeiIngredientAt(double x, double y) {
        if (layout == null) return null;
        for (int i = 0; i < layout.capacity(); i++) {
            int index = page * layout.capacity() + i;
            if (index >= filtered.size()) break;
            int cellX = layout.gridX() + i % layout.columns() * 26;
            int cellY = layout.gridY() + i / layout.columns() * 26;
            if (inside(x, y, cellX, cellY, 24, 24)) return new ClickableItem(stack(filtered.get(index).itemId()), cellX, cellY, 24, 24);
        }
        return null;
    }

    // JEI queries the opening screen before Screen.init has created its layout.
    // Zero bounds let the existing JEI bridge defer until initialization completes.
    public int jeiGuiLeft() { return layout == null ? 0 : layout.x(); }
    public int jeiGuiTop() { return layout == null ? 0 : layout.y(); }
    public int jeiGuiWidth() { return layout == null ? 0 : layout.width(); }
    public int jeiGuiHeight() { return layout == null ? 0 : layout.height(); }
    public record ClickableItem(ItemStack stack, int x, int y, int width, int height) { }
    @Override public boolean isPauseScreen() { return false; }

    private final class WorkshopButton extends Button {
        private final boolean primary;
        private boolean chosen;
        private int recipeIndex = -1;

        private WorkshopButton(int x, int y, int w, int h, Component text, Runnable action, boolean primary) {
            super(x, y, w, h, text, b -> action.run(), DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        public void playDownSound(SoundManager manager) { }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            boolean hover = isHoveredOrFocused();
            if (recipeIndex >= 0) {
                WorkbenchArt.sprite(g, recipeIndex == selected ? "slot_selected" : "slot", getX(), getY(), 24);
                if (hover && recipeIndex != selected) g.fill(getX() + 2, getY() + 2, getX() + 22, getY() + 22, 0x35FFFFFF);
                var e = filtered.get(recipeIndex);
                ItemStack item = stack(e.itemId());
                CommonGuiTextures.drawItem(g, item, getX() + 4, getY() + 3, 1);
                if (e.outputCount() > 1) CommonGuiTextures.drawItemDecorations(g, font,
                        new ItemStack(item.getItem(), e.outputCount()), getX() + 4, getY() + 3, 1);
                if (!affordable(e, 1)) g.fill(getX() + 18, getY() + 19, getX() + 21, getY() + 21, WorkbenchArt.BAD);
            } else {
                String sprite = !active ? "button_disabled" : hover ? "button_hover" : primary || chosen ? "button" : "tab";
                WorkbenchArt.box(g, sprite, getX(), getY(), getWidth(), getHeight(), 3);
                int color = active && (primary || chosen || hover) ? WorkbenchArt.LIGHT : WorkbenchArt.INK;
                String symbol = getMessage().getString();
                if (symbol.equals("‹") || symbol.equals("›") || symbol.equals("×")) {
                    int cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2;
                    for (int i = -2; i <= 2; i++) {
                        int px = symbol.equals("×") ? cx + i
                                : cx + (symbol.equals("‹") ? Math.abs(i) - 1 : 1 - Math.abs(i));
                        g.fill(px, cy + i, px + 1, cy + i + 1, color);
                        if (symbol.equals("×")) g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
                    }
                } else {
                    Component label = clipped(getMessage(), getWidth() - 8);
                    g.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                            getY() + (getHeight() - StardewFonts.lineHeight(font)) / 2, color, false);
                }
            }
            if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFE1BA68);
        }
    }
}
