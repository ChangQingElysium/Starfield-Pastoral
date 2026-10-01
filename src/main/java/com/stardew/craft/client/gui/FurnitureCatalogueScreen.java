package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.payload.FurnitureCataloguePurchasePayload;
import com.stardew.craft.network.payload.FurnitureCatalogueResultPayload;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SDV-style Furniture Catalogue screen with category tabs, search bar, and favourites.
 * All items are free with unlimited stock — no inventory panel needed.
 */
@SuppressWarnings("null")
public class FurnitureCatalogueScreen extends Screen {

    // ── Layout constants (SDV screen-pixels) ────────────────────────────────
    private static final int BORDER = 40;
    private static final int WIN_W  = 900 + 2 * BORDER;
    private static final int WIN_H  = 600 + 2 * BORDER;
    private static final int ROWS   = 5;
    private static final int ROW_H  = 600 / ROWS;

    // Tab dimensions (SDV sprite-px)
    private static final int TAB_W = 48;
    private static final int TAB_H = 48;
    private static final int TAB_GAP = 8;

    // Cursors sprite dimensions
    private static final int ARR_UP_W=11,ARR_UP_SH=12;
    private static final int SCR_F_W=6,SCR_F_SH=10;
    private static final int CLOSE_W=12,CLOSE_SH=12;

    private static final int BG_TINT = 0xBF000000;

    // ── Tab categories ──────────────────────────────────────────────────────
    enum Tab {
        ALL,      // ⭐ All items
        SEATS,    // 🪑 Chairs, sofas, benches, stools
        TABLES,   // 🗄 Tables, desks, counters
        LAMPS,    // 💡 Lamps, lights, sconces, candles
        WALL_DECOR, // 🖼 Wall decorations, paintings, shelves, clocks
        CARPETS,  // 🧶 Carpets, rugs
        WALLPAPER,// Wallpaper blocks
        FLOORING, // Flooring blocks
        FAVOURITES // ★ User favourites
    }

    // ── State ───────────────────────────────────────────────────────────────
    private final List<ShopItemEntry> allItems;
    private List<ShopItemEntry> filteredItems = new ArrayList<>();
    @SuppressWarnings("unused")
    private int playerMoney;

    private Tab activeTab = Tab.ALL;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private int cursorBlink = 0;

    private int currentIndex = 0;
    private int hoveredRow = -1;
    private final float[] rowScales = new float[ROWS];

    private boolean scrolling = false;
    private int scrollDragOffsetY;

    // Layout (MC gui coords)
    private int panelX, panelY, panelWGui, panelHGui;
    private int rowHGui, rowWGui;
    private int upArrowX,upArrowY,upArrowW,upArrowH;
    private int dnArrowX,dnArrowY,dnArrowW,dnArrowH;
    private int scrBarX,scrBarY,scrBarW,scrBarH;
    private int scrRunX,scrRunY,scrRunW,scrRunH;
    private int closeX,closeY,closeW,closeH;
    private float closeScale = 1.0f;
    private int searchBoxX, searchBoxY, searchBoxW, searchBoxH;
    private int tabX, tabY; // top-left of tab column
    private int tabWGui, tabHGui, tabGapGui;

    private float guiScale = 1.0f;
    private long openedAtMs;
    private static final long SAFETY_MS = 250;

    // =========================================================================
    public FurnitureCatalogueScreen(List<ShopItemEntry> items, int playerMoney) {
        super(Component.empty());
        this.allItems = new ArrayList<>(items);
        this.playerMoney = playerMoney;
        for (int i = 0; i < ROWS; i++) rowScales[i] = 1.0f;
        rebuildFilteredList();
    }

    // =========================================================================
    // Filter logic
    // =========================================================================
    private void rebuildFilteredList() {
        filteredItems.clear();
        FurnitureFavourites favs = FurnitureFavourites.getInstance();

        for (ShopItemEntry item : allItems) {
            // Tab filter
            if (activeTab == Tab.FAVOURITES) {
                if (!favs.isFavourite(item.itemId())) continue;
            } else if (activeTab != Tab.ALL) {
                if (!matchesTab(item.itemId(), activeTab)) continue;
            }

            // Search filter
            if (!searchQuery.isEmpty()) {
                String name = resolveItemName(item).toLowerCase(Locale.ROOT);
                String id = item.itemId().toLowerCase(Locale.ROOT);
                String query = searchQuery.toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !id.contains(query)) continue;
            }

            filteredItems.add(item);
        }

        currentIndex = 0;
        updateScrollBarPosition();
    }

    private static boolean matchesTab(String itemId, Tab tab) {
        String id = itemId.toLowerCase(Locale.ROOT);
        // Strip namespace
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);

        return switch (tab) {
            case SEATS -> id.contains("chair") || id.contains("stool") || id.contains("bench")
                        || id.contains("sofa") || id.contains("couch") || id.contains("seat");
            case TABLES -> id.contains("table") || id.contains("desk") || id.contains("counter")
                         || id.contains("end_table") || id.contains("nightstand");
            case LAMPS -> id.contains("light") || id.contains("lamp") || id.contains("sconce")
                        || id.contains("candle") || id.contains("lantern") || id.contains("torch");
            case WALL_DECOR -> id.contains("painting") || id.contains("photo") || id.contains("banner")
                             || id.contains("calendar") || id.contains("sign") || id.contains("clock")
                             || id.contains("shelf") || id.contains("wall_") || id.contains("poster")
                             || id.contains("frame") || id.contains("mirror");
            case CARPETS -> id.contains("carpet") || id.contains("rug");
            case WALLPAPER -> id.contains("wallpaper");
            case FLOORING -> id.contains("flooring");
            default -> true;
        };
    }

    // =========================================================================
    // Item name resolution (same pattern as ShopScreen)
    // =========================================================================
    private String resolveItemName(ShopItemEntry entry) {
        ItemStack stack = resolveStack(entry.itemId());
        if (!stack.isEmpty()) return stack.getHoverName().getString();
        return entry.displayName().isEmpty() ? entry.itemId() : entry.displayName();
    }

    private static ItemStack resolveStack(String itemId) {
        try {
            ResourceLocation rl = new ResourceLocation(itemId);
            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            if (mcItem != null && mcItem != Items.AIR) return new ItemStack(mcItem);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    // =========================================================================
    // init
    // =========================================================================
    @Override
    protected void init() {
        super.init();
        openedAtMs = System.currentTimeMillis();
        guiScale = (float) StardewGuiViewport.REFERENCE_SCALE;
        float s4 = s4();

        panelWGui = ui(WIN_W);
        panelHGui = ui(WIN_H);
        panelX = width / 2 - panelWGui / 2;
        panelY = height / 2 - panelHGui / 2;

        rowHGui = ui(ROW_H);
        rowWGui = panelWGui - ui(32);

        // Tabs — left of panel
        tabWGui = ui(TAB_W);
        tabHGui = ui(TAB_H);
        tabGapGui = ui(TAB_GAP);
        tabX = panelX - tabWGui - ui(4); // gap from panel left edge
        tabY = panelY + ui(20); // near top of panel

        // Search bar — inside panel at top
        searchBoxX = panelX + ui(20);
        searchBoxY = panelY + ui(16);
        searchBoxW = panelWGui - ui(88); // leave space for close button
        searchBoxH = ui(44);

        // Scroll arrows (right of panel)
        upArrowW = (int)(ARR_UP_W * s4); upArrowH = (int)(ARR_UP_SH * s4);
        upArrowX = panelX + panelWGui + ui(16); upArrowY = panelY + ui(56);
        dnArrowW = upArrowW; dnArrowH = upArrowH;
        dnArrowX = upArrowX; dnArrowY = panelY + panelHGui - ui(64);

        // Scrollbar
        scrBarW = (int)(SCR_F_W * s4); scrBarH = (int)(SCR_F_SH * s4);
        scrBarX = upArrowX + ui(12);
        scrRunX = scrBarX; scrRunY = upArrowY + upArrowH + ui(4);
        scrRunW = scrBarW; scrRunH = dnArrowY - scrRunY - ui(4);
        updateScrollBarPosition();

        // Close button
        closeW = (int)(CLOSE_W * s4); closeH = (int)(CLOSE_SH * s4);
        closeX = panelX + panelWGui - closeW - ui(4);
        closeY = panelY - closeH / 2;
    }

    // =========================================================================
    // render
    // =========================================================================
    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty — we draw our own background
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG_TINT);
        float s4 = s4();

        // 1. Main panel box (with shadow)
        CommonGuiTextures.drawTextureBox(g, panelX, panelY, panelWGui, panelHGui, s4, true);

        // 2. Search bar
        drawSearchBar(g, mouseX, mouseY, s4);

        // 3. Category tabs
        drawTabs(g, mouseX, mouseY, s4);

        // 4. Item rows
        hoveredRow = -1;
        int itemAreaY = panelY + ui(68); // below search bar
        for (int i = 0; i < ROWS; i++) {
            int itemIdx = currentIndex + i;
            if (itemIdx >= filteredItems.size()) break;
            drawItemRow(g, mouseX, mouseY, s4, i, itemIdx, itemAreaY);
        }

        // 5. Empty message
        if (filteredItems.isEmpty()) {
            String msg = activeTab == Tab.FAVOURITES
                ? I18n.get("stardewcraft.catalogue.no_favourites")
                : I18n.get("stardewcraft.catalogue.no_results");
            GuiText.drawCenteredClamped(g, font, Component.literal(msg),
                panelX + panelWGui / 2, panelY + panelHGui / 2 - StardewFonts.lineHeight(font) / 2,
                Math.max(1, panelWGui - ui(80)), 0x404040, false);
        }

        // 6. Scroll arrows
        boolean upOn = currentIndex > 0;
        boolean dnOn = currentIndex < Math.max(0, filteredItems.size() - ROWS);
        if (upOn) {
            CommonGuiTextures.drawScrollArrowUp(g, upArrowX, upArrowY, s4);
        } else {
            CommonGuiTextures.drawScrollArrowUpTint(g, upArrowX, upArrowY, s4, 1f, 1f, 1f, 0.4f);
        }
        if (dnOn) {
            CommonGuiTextures.drawScrollArrowDown(g, dnArrowX, dnArrowY, s4);
        } else {
            CommonGuiTextures.drawScrollArrowDownTint(g, dnArrowX, dnArrowY, s4, 1f, 1f, 1f, 0.4f);
        }

        // 7. Scrollbar
        if (filteredItems.size() > ROWS) {
            CommonGuiTextures.drawScrollTrackBox(g, scrRunX, scrRunY, scrRunW, scrRunH, s4);
            CommonGuiTextures.drawScrollBarThumb(g, scrBarX, scrBarY, s4);
        }

        // 8. Close button
        boolean closeHov = isIn(mouseX, mouseY, closeX, closeY, closeW, closeH);
        closeScale = closeHov ? Math.min(closeScale + 0.04f, 1.2f) : Math.max(1.0f, closeScale - 0.04f);
        float cs = s4 * closeScale;
        int cdx = closeX + closeW / 2 - (int)(CLOSE_W * cs / 2);
        int cdy = closeY + closeH / 2 - (int)(CLOSE_SH * cs / 2);
        CommonGuiTextures.drawCloseButton(g, cdx, cdy, cs);

        // 9. Tooltip
        if (hoveredRow >= 0) {
            int idx = currentIndex + hoveredRow;
            if (idx < filteredItems.size()) drawTooltip(g, mouseX, mouseY, filteredItems.get(idx));
        }
    }

    // =========================================================================
    // Search bar
    // =========================================================================
    private void drawSearchBar(GuiGraphics g, int mouseX, int mouseY, float s4) {
        // Background using texture box (SDV-style input field)
        CommonGuiTextures.drawEntryBox(g, searchBoxX, searchBoxY, searchBoxW, searchBoxH, s4, false);

        int textY = searchBoxY + (searchBoxH - StardewFonts.lineHeight(font)) / 2;
        int textX = searchBoxX + ui(16);

        // Draw placeholder or search text
        if (searchQuery.isEmpty() && !searchFocused) {
            Component placeholder = Component.literal(I18n.get("stardewcraft.catalogue.search_placeholder"));
            g.drawString(font, GuiText.ellipsize(font, placeholder, searchBoxW - ui(32)),
                textX, textY, 0x888888, false);
        } else {
            String displayText = searchQuery;
            // Blinking cursor
            if (searchFocused) {
                cursorBlink++;
                if (cursorBlink % 40 < 20) displayText += "_";
            }
            g.drawString(font, GuiText.ellipsize(font, Component.literal(displayText), searchBoxW - ui(32)),
                textX, textY, 0x1a1a1a, false);
        }

        // Focused border highlight (2px golden border)
        if (searchFocused) {
            int bx = searchBoxX, by = searchBoxY, bw = searchBoxW, bh = searchBoxH;
            g.fill(bx, by, bx + bw, by + 2, 0xFF886622);
            g.fill(bx, by + bh - 2, bx + bw, by + bh, 0xFF886622);
            g.fill(bx, by, bx + 2, by + bh, 0xFF886622);
            g.fill(bx + bw - 2, by, bx + bw, by + bh, 0xFF886622);
        }
    }

    // =========================================================================
    // Tabs
    // =========================================================================
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY, float s4) {
        Tab[] tabs = Tab.values();
        FurnitureFavourites favs = FurnitureFavourites.getInstance();

        for (int i = 0; i < tabs.length; i++) {
            int tx = tabX;
            int ty = tabY + i * (tabHGui + tabGapGui);
            boolean active = tabs[i] == activeTab;
            boolean hov = isIn(mouseX, mouseY, tx, ty, tabWGui, tabHGui);

            // Active tab slides right toward the panel; inactive stays put
            int offsetX = active ? ui(8) : 0;
            int drawX = tx + offsetX;

            // Tab background: flat colored rectangle (no heavy 9-slice frame)
            int bgColor;
            if (active) {
                bgColor = 0xFFF0D880; // warm golden
            } else if (hov) {
                bgColor = 0xFFE8D8B0; // light warm
            } else {
                bgColor = 0xFFC8A860; // muted brown
            }
            // Rounded-ish tab with a darker border
            int bd = 1;
            g.fill(drawX + bd, ty, drawX + tabWGui - bd, ty + tabHGui, bgColor);
            g.fill(drawX, ty + bd, drawX + bd, ty + tabHGui - bd, bgColor);
            g.fill(drawX + tabWGui - bd, ty + bd, drawX + tabWGui, ty + tabHGui - bd, bgColor);
            // Thin border
            int borderColor = active ? 0xFFB08830 : 0xFF907030;
            g.fill(drawX + bd, ty, drawX + tabWGui - bd, ty + bd, borderColor);
            g.fill(drawX + bd, ty + tabHGui - bd, drawX + tabWGui - bd, ty + tabHGui, borderColor);
            g.fill(drawX, ty + bd, drawX + bd, ty + tabHGui - bd, borderColor);
            // Don't draw right border on active tab (it blends into panel)
            if (!active) {
                g.fill(drawX + tabWGui - bd, ty + bd, drawX + tabWGui, ty + tabHGui - bd, borderColor);
            }

            // Tab icon from cursors2 — rendered at s4×0.625 (= 10px sprite → ~40px GUI)
            float iconScale = s4 * 0.625f;
            int renderedSize = (int)(16 * iconScale);
            int iconX = drawX + (tabWGui - renderedSize) / 2;
            int iconY = ty + (tabHGui - renderedSize) / 2;

            if (i < tabs.length) {
                float iconAlpha = (tabs[i] == Tab.FAVOURITES && favs.getFavourites().isEmpty()) ? 0.4f : 1.0f;
                CommonGuiTextures.drawCatalogueTabIconTint(g, iconX, iconY, i, iconScale, 1.0f, 1.0f, 1.0f, iconAlpha);
            }

            // Tooltip on hover
            if (hov) {
                String tabName = I18n.get("stardewcraft.catalogue.tab." + tabs[i].name().toLowerCase(Locale.ROOT));
                g.renderTooltip(font, Component.literal(tabName), mouseX, mouseY);
            }
        }
    }

    // =========================================================================
    // Item rows
    // =========================================================================
    private void drawItemRow(GuiGraphics g, int mouseX, int mouseY, float s4,
                              int rowIndex, int itemIdx, int areaY) {
        ShopItemEntry item = filteredItems.get(itemIdx);
        int rowX = panelX + ui(16);
        int rowY = areaY + rowIndex * rowHGui;
        boolean hov = isIn(mouseX, mouseY, rowX, rowY, rowWGui, rowHGui) && !scrolling;
        if (hov) hoveredRow = rowIndex;

        // Scale animation
        rowScales[rowIndex] = hov
            ? Math.min(rowScales[rowIndex] + 0.03f, 1.1f)
            : Math.max(1.0f, rowScales[rowIndex] - 0.03f);

        // Row background
        if (hov) g.setColor(0.961f, 0.871f, 0.702f, 1.0f);
        CommonGuiTextures.drawEntryBox(g, rowX, rowY, rowWGui, rowHGui, s4, false);
        g.setColor(1f, 1f, 1f, 1f);

        // Icon background
        int icoX = rowX + ui(20);
        int icoY = rowY + ui(20);
        CommonGuiTextures.drawItemSlot18(g, icoX, icoY, s4);

        // Item icon
        int iconX = rowX + ui(24);
        int iconY = rowY + ui(24);
        try {
            ResourceLocation rl = new ResourceLocation(item.itemId());
            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            if (mcItem != null && mcItem != Items.AIR) {
                CommonGuiTextures.drawItem(g, new ItemStack(mcItem), iconX, iconY, s4);
            }
        } catch (Exception ignored) {}

        // Name
        int nameX = rowX + ui(104);
        int nameMaxWidth = Math.max(ui(120), rowWGui - ui(300));
        Component name = GuiText.ellipsize(font, Component.literal(resolveItemName(item)), nameMaxWidth);
        g.drawString(font, name, nameX, rowY + ui(24), 0x1a1a1a, false);

        // "Free" label
        String freeLabel = I18n.get("stardewcraft.catalogue.free");
        g.drawString(font, freeLabel, rowX + rowWGui - ui(60) - font.width(freeLabel),
            rowY + ui(24), 0x228B22, false);

        // Favourite star button (right side of row)
        boolean isFav = FurnitureFavourites.getInstance().isFavourite(item.itemId());
        float starScale = s4 * 0.5f; // render star at 2× sprite size
        int starRendered = (int)(16 * starScale);
        int starX = rowX + rowWGui - starRendered - ui(8);
        int starY = rowY + (rowHGui - starRendered) / 2;

        if (isFav) {
            CommonGuiTextures.drawCatalogueStarTint(g, starX, starY, starScale, 1.0f, 0.85f, 0.0f, 1.0f);
        } else {
            CommonGuiTextures.drawCatalogueStarTint(g, starX, starY, starScale, 0.6f, 0.6f, 0.6f, 0.4f);
        }
    }

    // =========================================================================
    // Tooltip
    // =========================================================================
    private void drawTooltip(GuiGraphics g, int mx, int my, ShopItemEntry item) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = resolveStack(item.itemId());

        List<Component> lines = new ArrayList<>();
        if (!stack.isEmpty()) {
            lines.addAll(stack.getTooltipLines(
                Item.TooltipContext.EMPTY, mc.player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL));
        } else {
            lines.add(Component.literal(resolveItemName(item)));
        }

        lines.add(Component.empty());
        lines.add(Component.literal(I18n.get("stardewcraft.catalogue.free"))
            .withStyle(net.minecraft.ChatFormatting.GREEN));
        lines.add(Component.literal(I18n.get("stardewcraft.catalogue.click_hint"))
            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

        boolean isFav = FurnitureFavourites.getInstance().isFavourite(item.itemId());
        String favHint = isFav
            ? I18n.get("stardewcraft.catalogue.unfavourite_hint")
            : I18n.get("stardewcraft.catalogue.favourite_hint");
        lines.add(Component.literal(favHint).withStyle(net.minecraft.ChatFormatting.GOLD));

        g.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
    }

    // =========================================================================
    // Input — Mouse
    // =========================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // Close
        if (isIn(mx, my, closeX, closeY, closeW, closeH)) {
            playSound(ModSounds.DWOP.get());
            onClose();
            return true;
        }

        // Search bar click → toggle focus
        if (isIn(mx, my, searchBoxX, searchBoxY, searchBoxW, searchBoxH)) {
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Tab click
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int ty = tabY + i * (tabHGui + tabGapGui);
            if (isIn(mx, my, tabX, ty, tabWGui, tabHGui)) {
                if (activeTab != tabs[i]) {
                    activeTab = tabs[i];
                    rebuildFilteredList();
                    playSound(ModSounds.SHWIP.get());
                }
                return true;
            }
        }

        // Up arrow
        if (isIn(mx, my, upArrowX, upArrowY, upArrowW, upArrowH)) {
            if (currentIndex > 0) { currentIndex--; updateScrollBarPosition(); playSound(ModSounds.SHWIP.get()); }
            return true;
        }
        // Down arrow
        if (isIn(mx, my, dnArrowX, dnArrowY, dnArrowW, dnArrowH)) {
            if (currentIndex < Math.max(0, filteredItems.size() - ROWS)) {
                currentIndex++; updateScrollBarPosition(); playSound(ModSounds.SHWIP.get());
            }
            return true;
        }
        // Scrollbar drag
        if (isIn(mx, my, scrBarX, scrBarY, scrBarW, scrBarH)) {
            scrolling = true; scrollDragOffsetY = my - scrBarY; return true;
        }

        // Item row click
        int areaY = panelY + ui(68);
        for (int i = 0; i < ROWS; i++) {
            int idx = currentIndex + i;
            if (idx >= filteredItems.size()) break;
            int rx = panelX + ui(16), ry = areaY + i * rowHGui;
            if (isIn(mx, my, rx, ry, rowWGui, rowHGui)) {
                ShopItemEntry item = filteredItems.get(idx);
                // Check if click is on the favourite star
                float starScaleC = s4() * 0.5f;
                int starRenderedC = (int)(16 * starScaleC);
                int starX = rx + rowWGui - starRenderedC - ui(8);
                int starY = ry + (rowHGui - starRenderedC) / 2;
                int starSize = starRenderedC;
                if (isIn(mx, my, starX, starY, starSize, starSize)) {
                    FurnitureFavourites.getInstance().toggle(item.itemId());
                    if (activeTab == Tab.FAVOURITES) rebuildFilteredList();
                    playSound(ModSounds.SHINY4.get());
                } else if (button == 0 || button == 1) {
                    // Purchase (free)
                    tryPurchase(item);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (scrolling && filteredItems.size() > ROWS) {
            int newY = (int) mouseY - scrollDragOffsetY;
            newY = Math.max(scrRunY, Math.min(scrRunY + scrRunH - scrBarH, newY));
            float pct = (float)(newY - scrRunY) / Math.max(1, scrRunH - scrBarH);
            currentIndex = Math.max(0, Math.min(filteredItems.size() - ROWS,
                Math.round(pct * (filteredItems.size() - ROWS))));
            updateScrollBarPosition();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
        int dir = vScroll > 0 ? -1 : 1;
        int ni = Math.max(0, Math.min(filteredItems.size() - ROWS, currentIndex + dir));
        if (ni != currentIndex) {
            currentIndex = ni;
            updateScrollBarPosition();
            playSound(ModSounds.SHINY4.get());
        }
        return true;
    }

    // =========================================================================
    // Input — Keyboard
    // =========================================================================
    @Override
    public boolean keyPressed(int keyCode, int scan, int mods) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (searchFocused) { searchFocused = false; return true; }
            onClose();
            return true;
        }

        if (searchFocused) {
            if (keyCode == InputConstants.KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    rebuildFilteredList();
                }
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN) {
                searchFocused = false;
                return true;
            }
            // Let charTyped handle actual characters
            return true; // consume all keys when search is focused
        }

        return super.keyPressed(keyCode, scan, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (searchFocused) {
            if (ch >= 32) { // printable characters only
                searchQuery += ch;
                rebuildFilteredList();
                return true;
            }
        }
        return super.charTyped(ch, mods);
    }

    // =========================================================================
    // Purchase
    // =========================================================================
    private void tryPurchase(ShopItemEntry item) {
        if (System.currentTimeMillis() - openedAtMs < SAFETY_MS) return;

        boolean shift = hasShiftDown(), ctrl = hasControlDown();
        int qty = (shift && ctrl) ? 25 : shift ? 5 : 1;

        ItemStack salable = resolveStack(item.itemId());
        if (!salable.isEmpty()) {
            qty = Math.min(qty, Math.max(1, salable.getMaxStackSize()));
        }

        playSound(ModSounds.PURCHASE_CLICK.get());
        PacketDistributor.sendToServer(new FurnitureCataloguePurchasePayload(item.itemId(), qty));
    }

    // =========================================================================
    // Network callback
    // =========================================================================
    public void onPurchaseResult(FurnitureCatalogueResultPayload r) {
        playerMoney = r.newMoney();
        if (r.success()) {
            try { if (ModSounds.COIN != null) playSound(ModSounds.COIN.get()); } catch (Exception ignored) {}
        } else {
            playSound(ModSounds.CANCEL.get());
        }
    }

    // =========================================================================
    // Scrollbar
    // =========================================================================
    private void updateScrollBarPosition() {
        if (filteredItems.size() <= ROWS) { scrBarY = scrRunY; return; }
        int maxIdx = Math.max(1, filteredItems.size() - ROWS);
        float pct = (float) currentIndex / maxIdx;
        scrBarY = scrRunY + Math.round(pct * Math.max(0, scrRunH - scrBarH));
        if (currentIndex >= filteredItems.size() - ROWS) scrBarY = scrRunY + scrRunH - scrBarH;
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private int ui(int sdvPx) { return Math.round(sdvPx / guiScale); }
    private float s4() { return 4.0f / guiScale; }
    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    private void playSound(net.minecraft.sounds.SoundEvent ev) {
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(ev, 1f, 1f);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
