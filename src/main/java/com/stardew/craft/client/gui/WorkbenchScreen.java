package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.WorkbenchCraftPayload;
import com.stardew.craft.network.payload.WorkbenchCraftResultPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.workbench.WorkbenchEntry;
import com.stardew.craft.workbench.WorkbenchRecipeManager;
import com.stardew.craft.workbench.WorkbenchType;
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
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SDV-style workbench GUI.
 * Left-side icon tabs, item grid with ICO slot backgrounds, right-side preview.
 * Pure Screen — no Menu/Container.
 *
 * <pre>
 * Layout (SDV screen-pixels, divided by guiScale for MC GUI coords):
 *
 *   [TABS]  ┌──────────────────────────────────────────────┐
 *   48×48   │  Title                    [mat_icon] 42  [X] │  ← 32px title bar
 *   each    │──────────────────────────────────────────────│
 *           │                        │                     │
 *           │   6×4 item grid        │   Preview panel     │
 *           │   cell=72, gap=8       │   ~380px wide       │
 *           │   ICO sprite per cell  │   ICO + 2× item     │
 *           │                        │   name, cost, info  │
 *           │                        │   [Craft] button    │
 *           │   ◀  1/3  ▶            │                     │
 *           └──────────────────────────────────────────────┘
 *
 *   Panel: 1000×660 (inner 920×580, border 40)
 *   Tabs: outside panel left edge, 48×48, 6px gap
 * </pre>
 */
@SuppressWarnings("null")
public class WorkbenchScreen extends Screen {

    // ── SDV screen-pixel constants ──────────────────────────────────────────
    //    All positions/sizes are in these units, converted via ui() at runtime.
    private static final int BORDER  = 40;
    private static final int INNER_W = 920;
    private static final int INNER_H = 580;
    private static final int WIN_W   = INNER_W + 2 * BORDER; // 1000
    private static final int WIN_H   = INNER_H + 2 * BORDER; // 660
    private static final int PAD     = 20;  // content padding inside border

    // Grid
    private static final int COLS     = 6;
    private static final int ROWS     = 4;
    private static final int CELL     = 72;  // matches ICO@s4 exactly: 18*4=72
    private static final int CELL_GAP = 8;
    // grid total: 6*72+5*8=472 wide, 4*72+3*8=312 tall

    // Tabs (left-side, vertical, outside panel)
    private static final int TAB_SIZE = 56;
    private static final int TAB_GAP  = 4;

    // Cursors sprite dimensions (rendered at s4() scale)
    private static final int ICO_W = 18, ICO_H = 18;
    private static final int CLOSE_W = 12, CLOSE_H = 12;
    private static final int ARR_W = 12, ARR_H = 11;

    // Colors (ARGB)
    private static final int C_OVERLAY  = 0xBF000000;
    private static final int C_DARK     = 0x1A1A1A;
    private static final int C_GREY     = 0x808080;
    private static final int C_GREEN    = 0x228B22;
    private static final int C_RED      = 0xCC2222;
    private static final int C_GOLD     = 0xFFB08830;  // active tab / selected border
    private static final int C_TAB_ACT  = 0xFFF0D880;  // active tab fill
    private static final int C_TAB_HOV  = 0xFFE8D8B0;  // hovered tab fill
    private static final int C_TAB_NRM  = 0xFFC8A860;  // normal tab fill
    private static final int C_TAB_BDR  = 0xFF907030;  // tab border
    private static final int C_SEL      = 0xFFDAA520;  // selected cell golden border

    // ── State ───────────────────────────────────────────────────────────────
    private final WorkbenchType wbType;
    private final List<String> categories = new ArrayList<>();
    private int activeTab = 0;

    private List<WorkbenchEntry> allRecipes;
    private List<WorkbenchEntry> filtered = new ArrayList<>();
    private int selIdx = -1;   // index in filtered list
    private int page = 0, maxPage = 0;

    private int matCount, bonusCount;

    // Cached item stacks for tab icons
    private ItemStack[] tabIcons;
    // Cached material item stacks for title rendering
    private ItemStack matStack = ItemStack.EMPTY;
    private ItemStack bonusStack = ItemStack.EMPTY;

    // ── Layout (MC GUI coords, computed in init()) ──────────────────────────
    private float gs;  // guiScale

    // Panel
    private int pnlX, pnlY, pnlW, pnlH;

    // Content origin (inside panel + border + padding)
    private int cntX, cntY;

    // Tabs
    private int tabX, tabFirstY, tabSzGui;

    // Grid
    private int grdX, grdY, cellGui, gapGui;

    // Preview
    private int prvX, prvY, prvW, prvH;

    // Craft button
    private int btnX, btnY, btnW, btnH;

    // Page nav
    private int pgY;              // Y for page text and arrows
    private int arrLX, arrRX;     // arrow X positions
    private int arrW, arrH;       // arrow size
    private int pgCenterX;        // center X for page text

    // Close button
    private int clsX, clsY, clsW, clsH;
    private float clsAnim = 1.0f;

    // Hover
    private int hovCell = -1;

    private long openedAt;
    private static final long SAFETY_MS = 200;

    // =====================================================================
    public WorkbenchScreen(WorkbenchType type) {
        super(Component.translatable("stardewcraft.workbench." + type.getKey() + ".title"));
        this.wbType = type;
    }

    // =====================================================================
    // init
    // =====================================================================
    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        gs = (float) Minecraft.getInstance().getWindow().getGuiScale();

        allRecipes = WorkbenchRecipeManager.getRecipes(wbType);
        buildCategories();
        rebuildFiltered();
        buildTabIcons();
        buildMaterialStacks();

        // ── Panel ──
        pnlW = ui(WIN_W);
        pnlH = ui(WIN_H);
        pnlX = (width - pnlW) / 2;
        pnlY = (height - pnlH) / 2;

        // Content origin
        cntX = pnlX + ui(BORDER + PAD);
        cntY = pnlY + ui(BORDER + PAD);

        // ── Left-side tabs ──
        tabSzGui = ui(TAB_SIZE);
        tabX = pnlX - tabSzGui - ui(4);
        tabFirstY = pnlY + ui(20);

        // ── Grid (below 36px title area) ──
        cellGui = ui(CELL);
        gapGui = ui(CELL_GAP);
        grdX = cntX;
        grdY = cntY + ui(40); // 40px below content top = below title
        // grid total W = COLS*cellGui + (COLS-1)*gapGui
        // grid total H = ROWS*cellGui + (ROWS-1)*gapGui

        int gridTotalW = COLS * cellGui + (COLS - 1) * gapGui;

        // ── Page nav (below grid) ──
        int gridTotalH = ROWS * cellGui + (ROWS - 1) * gapGui;
        float s4 = s4();
        pgY = grdY + gridTotalH + ui(12);
        pgCenterX = grdX + gridTotalW / 2;
        arrW = (int)(ARR_W * s4);
        arrH = (int)(ARR_H * s4);
        // Arrow positions computed dynamically in drawPageNav based on text width

        // ── Preview panel (right of grid + thin divider gap) ──
        int divGap = ui(16); // thin 2px divider + margins
        prvX = grdX + gridTotalW + divGap;
        prvY = grdY;
        prvW = pnlX + pnlW - ui(BORDER + PAD) - prvX;
        int contentBottom = pnlY + pnlH - ui(BORDER + PAD);
        prvH = contentBottom - prvY;

        // ── Craft button (bottom of preview) ──
        btnH = font.lineHeight + ui(16);  // text height + padding
        btnW = font.width(I18n.get("stardewcraft.workbench.craft")) + ui(60); // text + side padding
        btnX = prvX + (prvW - btnW) / 2;
        btnY = prvY + prvH - btnH - ui(12);

        // ── Close button (top-right corner of panel, straddling edge) ──
        clsW = (int)(CLOSE_W * s4);
        clsH = (int)(CLOSE_H * s4);
        clsX = pnlX + pnlW - clsW - ui(6);
        clsY = pnlY - clsH / 2;

        recountMaterials();
    }

    // ── Setup helpers ───────────────────────────────────────────────────────

    private void buildCategories() {
        categories.clear();
        categories.add("all");
        Set<String> seen = new LinkedHashSet<>();
        for (WorkbenchEntry e : allRecipes) seen.add(e.category());
        categories.addAll(seen);
    }

    private void rebuildFiltered() {
        filtered.clear();
        String cat = categories.get(activeTab);
        for (WorkbenchEntry e : allRecipes) {
            if ("all".equals(cat) || e.category().equals(cat)) filtered.add(e);
        }
        page = 0;
        maxPage = Math.max(0, (filtered.size() - 1) / (COLS * ROWS));
        selIdx = filtered.isEmpty() ? -1 : 0;
    }

    private void buildTabIcons() {
        tabIcons = new ItemStack[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            if (i == 0) {
                // "All" tab → workbench item itself
                String id = wbType == WorkbenchType.WOOD
                    ? "stardewcraft:wood_workbench" : "stardewcraft:stone_workbench";
                tabIcons[i] = resolveStack(ResourceLocation.parse(id));
            } else {
                // First item in that category
                String cat = categories.get(i);
                ItemStack found = ItemStack.EMPTY;
                for (WorkbenchEntry e : allRecipes) {
                    if (e.category().equals(cat)) { found = resolveStack(e.itemId()); break; }
                }
                tabIcons[i] = found;
            }
        }
    }

    private void buildMaterialStacks() {
        matStack = resolveStack(ResourceLocation.parse(wbType.getInputItemId()));
        if (wbType.hasBonus()) {
            bonusStack = resolveStack(ResourceLocation.parse(wbType.getBonusItemId()));
        }
    }

    // ── Materials ───────────────────────────────────────────────────────────

    private void recountMaterials() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        matCount = countInv(wbType.getInputItemId());
        bonusCount = wbType.hasBonus() ? countInv(wbType.getBonusItemId()) : 0;
    }

    private int countInv(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == null || item == Items.AIR) return 0;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private int effectiveMat() {
        return matCount + bonusCount * wbType.getBonusMultiplier();
    }

    // =====================================================================
    // render
    // =====================================================================
    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mx, int my, float pt) { }

    @Override
    public void render(@Nonnull GuiGraphics g, int mx, int my, float pt) {
        float s4 = s4();

        // 1) Full-screen dark overlay
        g.fill(0, 0, width, height, C_OVERLAY);

        // 2) Main panel (BDR 9-slice with shadow)
        CommonGuiTextures.drawTextureBox(g, pnlX, pnlY, pnlW, pnlH, s4, true);

        // 3) Title bar (title text + material icons/counts)
        drawTitle(g);

        // 4) Left-side tabs
        drawTabs(g, mx, my);

        // 5) Vertical divider between grid and preview (thin line)
        int divX = grdX + COLS * cellGui + (COLS - 1) * gapGui + ui(7);
        int divTop = grdY;
        int divBot = prvY + prvH;
        g.fill(divX, divTop, divX + 1, divBot, 0xFF8B7355);
        g.fill(divX + 1, divTop, divX + 2, divBot, 0xFFA08050);

        // 6) Item grid (ICO slots + item icons)
        drawGrid(g, mx, my, s4);

        // 7) Page navigation
        drawPageNav(g, mx, my, s4);

        // 8) Preview panel
        drawPreview(g, mx, my, s4);

        // 9) Close button
        drawClose(g, mx, my, s4);

        // 10) Tooltip (last = on top of everything)
        if (hovCell >= 0) {
            int abs = page * COLS * ROWS + hovCell;
            if (abs < filtered.size()) {
                drawTooltip(g, mx, my, filtered.get(abs));
            }
        }
    }

    // ── Title ───────────────────────────────────────────────────────────────
    private void drawTitle(GuiGraphics g) {
        int ty = pnlY + ui(BORDER) + ui(8);

        // Title text (left)
        String title = I18n.get("stardewcraft.workbench." + wbType.getKey() + ".title");
        g.drawString(font, GuiText.ellipsize(font, Component.literal(title), Math.max(1, pnlW - ui(320))),
            cntX, ty, C_DARK, false);

        // Material icons + counts (right-aligned)
        // Layout: [icon] count   [icon] count
        int rx = pnlX + pnlW - ui(BORDER + PAD);  // right edge of content
        int iconOff = (font.lineHeight - 16) / 2; // vertical center icon with text

        if (wbType.hasBonus() && !bonusStack.isEmpty()) {
            // Bonus: count text, then icon
            String bTxt = String.valueOf(bonusCount);
            rx -= font.width(bTxt);
            g.drawString(font, bTxt, rx, ty, C_DARK, false);
            rx -= 17; // 16px icon + 1px gap
            CommonGuiTextures.drawItem(g, bonusStack, rx, ty + iconOff, 1.0f);
            rx -= ui(20); // spacing between bonus and main
        }

        // Main material: count text, then icon
        String mTxt = String.valueOf(matCount);
        rx -= font.width(mTxt);
        g.drawString(font, mTxt, rx, ty, C_DARK, false);
        rx -= 17;
        if (!matStack.isEmpty()) {
            CommonGuiTextures.drawItem(g, matStack, rx, ty + iconOff, 1.0f);
        }
    }

    // ── Tabs (left-side, vertical, item icons) ──────────────────────────────
    private void drawTabs(GuiGraphics g, int mx, int my) {
        int n = categories.size();
        int gapGui = ui(TAB_GAP);
        float scale4 = s4();

        for (int i = 0; i < n; i++) {
            int ty = tabFirstY + i * (tabSzGui + gapGui);
            boolean active = (i == activeTab);
            boolean hov = !active && isIn(mx, my, tabX, ty, tabSzGui, tabSzGui);

            // Active tab slides right (ui(8) px) to merge into panel
            int tx = active ? tabX + ui(8) : tabX;
            int tw = active ? tabSzGui + ui(8) : tabSzGui;

            // Background fill (rounded-corner-ish via multiple fills)
            int bg = active ? C_TAB_ACT : (hov ? C_TAB_HOV : C_TAB_NRM);
            // Main rect
            g.fill(tx + 1, ty + 1, tx + tw - 1, ty + tabSzGui - 1, bg);
            // Extend edges for slight rounding
            g.fill(tx, ty + 2, tx + 1, ty + tabSzGui - 2, bg);
            g.fill(tx + tw - 1, ty + 2, tx + tw, ty + tabSzGui - 2, bg);
            g.fill(tx + 2, ty, tx + tw - 2, ty + 1, bg);
            g.fill(tx + 2, ty + tabSzGui - 1, tx + tw - 2, ty + tabSzGui, bg);

            // Border (skip right edge for active tab — merges into panel)
            int bdr = active ? C_GOLD : C_TAB_BDR;
            // Top
            g.fill(tx + 2, ty, tx + tw - 2, ty + 1, bdr);
            // Bottom
            g.fill(tx + 2, ty + tabSzGui - 1, tx + tw - 2, ty + tabSzGui, bdr);
            // Left
            g.fill(tx, ty + 2, tx + 1, ty + tabSzGui - 2, bdr);
            // Right (only for inactive)
            if (!active) {
                g.fill(tx + tw - 1, ty + 2, tx + tw, ty + tabSzGui - 2, bdr);
            }

            // Item icon (0.75× SDV drawInMenu scale, centered in tab; shifts with active tab)
            ItemStack icon = (i < tabIcons.length) ? tabIcons[i] : ItemStack.EMPTY;
            if (!icon.isEmpty()) {
                float iconScale = 0.75f * scale4;
                int scaledSz = CommonGuiTextures.itemSize(iconScale);
                int iconBaseX = active ? tabX + ui(8) : tabX; // shift right when active
                int iconTw = active ? tabSzGui + ui(8) : tabSzGui;
                int iconX = iconBaseX + (iconTw - scaledSz) / 2;
                int iconY = ty + (tabSzGui - scaledSz) / 2;
                g.pose().pushPose();
                g.pose().translate(0, 0, 100);
                CommonGuiTextures.drawItem(g, icon, iconX, iconY, iconScale);
                g.pose().popPose();
            }

            // Hover tooltip
            if (hov) {
                String label = "all".equals(categories.get(i))
                    ? I18n.get("stardewcraft.workbench.tab.all")
                    : I18n.get("stardewcraft.workbench.cat." + categories.get(i));
                g.renderTooltip(font, Component.literal(label), mx, my);
            }
        }
    }

    // ── Grid ────────────────────────────────────────────────────────────────
    private void drawGrid(GuiGraphics g, int mx, int my, float s4) {
        hovCell = -1;
        int start = page * COLS * ROWS;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                int abs = start + idx;
                int cx = grdX + col * (cellGui + this.gapGui);
                int cy = grdY + row * (cellGui + this.gapGui);

                boolean has = abs < filtered.size();
                boolean hov = has && isIn(mx, my, cx, cy, cellGui, cellGui);
                boolean sel = has && abs == selIdx;

                if (hov) hovCell = idx;

                // Selected: 2px golden border
                if (sel) {
                    g.fill(cx - 2, cy - 2, cx + cellGui + 2, cy,                C_SEL);
                    g.fill(cx - 2, cy + cellGui, cx + cellGui + 2, cy + cellGui + 2, C_SEL);
                    g.fill(cx - 2, cy,     cx,              cy + cellGui,        C_SEL);
                    g.fill(cx + cellGui, cy, cx + cellGui + 2, cy + cellGui,     C_SEL);
                }

                // Slot background: ICO sprite (18×18 @ s4 = exactly cellGui)
                CommonGuiTextures.drawItemSlot18(g, cx, cy, s4);

                // Hover highlight
                if (hov) {
                    g.fill(cx + 2, cy + 2, cx + cellGui - 2, cy + cellGui - 2, 0x30FFFFFF);
                }

                if (!has) continue;

                WorkbenchEntry entry = filtered.get(abs);
                ItemStack stack = resolveStack(entry.itemId());
                boolean canAfford = effectiveMat() >= entry.cost();

                // Item icon (centered in cell)
                if (!stack.isEmpty()) {
                    if (!canAfford) g.setColor(0.55f, 0.55f, 0.55f, 0.6f);
                    int ix = cx + (cellGui - CommonGuiTextures.itemSize(s4)) / 2;
                    int iy = cy + (cellGui - CommonGuiTextures.itemSize(s4)) / 2;
                    CommonGuiTextures.drawItem(g, stack, ix, iy, s4);
                    if (entry.outputCount() > 1) {
                        CommonGuiTextures.drawItemDecorations(g, font, new ItemStack(stack.getItem(), entry.outputCount()), ix, iy, s4);
                    }
                    if (!canAfford) g.setColor(1f, 1f, 1f, 1f);
                }
            }
        }
    }

    // ── Page navigation ─────────────────────────────────────────────────────
    private void drawPageNav(GuiGraphics g, int mx, int my, float s4) {
        if (maxPage <= 0) return;

        String pStr = (page + 1) + " / " + (maxPage + 1);
        int pw = font.width(pStr);
        int textY = pgY + (arrH - font.lineHeight) / 2;
        g.drawString(font, pStr, pgCenterX - pw / 2, textY, C_DARK, false);

        // Arrows placed with gap from text edges
        int gap = ui(10);
        arrLX = pgCenterX - pw / 2 - gap - arrW;
        arrRX = pgCenterX + pw / 2 + gap;

        // Left arrow
        if (page <= 0) g.setColor(1f, 1f, 1f, 0.3f);
        CommonGuiTextures.drawBackArrow(g, arrLX, pgY, s4);
        if (page <= 0) g.setColor(1f, 1f, 1f, 1f);

        // Right arrow
        if (page >= maxPage) g.setColor(1f, 1f, 1f, 0.3f);
        CommonGuiTextures.drawForwardArrow(g, arrRX, pgY, s4);
        if (page >= maxPage) g.setColor(1f, 1f, 1f, 1f);
    }

    // ── Preview panel ───────────────────────────────────────────────────────
    private void drawPreview(GuiGraphics g, int mx, int my, float s4) {
        // Sub-panel background (ROW 9-slice, no shadow)
        CommonGuiTextures.drawEntryBox(g, prvX, prvY, prvW, prvH, s4, false);

        if (selIdx < 0 || selIdx >= filtered.size()) {
            String hint = I18n.get("stardewcraft.workbench.select_hint");
            GuiText.drawCenteredClamped(g, font, Component.literal(hint), prvX + prvW / 2,
                prvY + prvH / 2 - font.lineHeight / 2, prvW - ui(24), C_GREY, false);
            return;
        }

        WorkbenchEntry entry = filtered.get(selIdx);
        ItemStack stack = resolveStack(entry.itemId());
        int cx = prvX + prvW / 2;  // center X
        int pad = ui(16);

        // ── Layout: top section (item + name) above divider, info below divider ──
        // Divider at vertical center of preview panel
        int divY = prvY + (prvH - btnH - ui(12)) / 2;  // center between top and craft button
        StardewGuiUtil.drawHorizontalPartitionSmall(g, prvX + pad, divY, prvW - pad * 2, s4);

        // ── Top section: Large item + bold name, centered above divider ──
        // ICO backdrop + 2× item render
        int icoRW = (int)(ICO_W * s4);
        int icoRH = (int)(ICO_H * s4);
        // Name (bold)
        String name = stack.isEmpty() ? entry.itemId().toString() : stack.getHoverName().getString();
        Component boldName = Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD);

        // Name sits just above divider
        int nameY = divY - ui(4) - font.lineHeight;
        GuiText.drawCenteredClamped(g, font, boldName, cx, nameY, prvW - ui(24), C_DARK, false);

        // ICO + item centered in remaining space above name
        int icoY = prvY + ui(8) + (nameY - ui(4) - prvY - ui(8) - icoRH) / 2;
        int icoX = cx - icoRW / 2;
        CommonGuiTextures.drawItemSlot18(g, icoX, icoY, s4);

        // 2× SDV drawInMenu render centered on the ICO backdrop
        if (!stack.isEmpty()) {
            float itemScale = 2.0f * s4;
            int itemSize = CommonGuiTextures.itemSize(itemScale);
            int itmX = cx - itemSize / 2;
            int itmY = icoY + (icoRH - itemSize) / 2;
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            CommonGuiTextures.drawItem(g, stack, itmX, itmY, itemScale);
            g.pose().popPose();
        }

        // ── Bottom section: info lines centered between divider and craft button ──
        // Count how many info lines we need
        int lineCount = 1; // cost always
        if (entry.outputCount() > 1) lineCount++;
        lineCount++; // max craftable always
        int lineStep = font.lineHeight + ui(6);
        int infoTotalH = lineCount * font.lineHeight + (lineCount - 1) * ui(6);
        int infoStartY = divY + ui(8) + (btnY - divY - ui(8) - infoTotalH) / 2;

        // Cost line: "消耗: [icon] ×N" with material item icon
        int infoY = infoStartY;
        String costLabel = I18n.get("stardewcraft.workbench.cost") + ": ";
        int costLabelW = font.width(costLabel);
        String costAmount = " \u00d7" + entry.cost();
        int costAmountW = font.width(costAmount);
        int costTotalW = costLabelW + 16 + 1 + costAmountW; // label + 16px icon + 1px gap + amount
        int costX = cx - costTotalW / 2;
        g.drawString(font, costLabel, costX, infoY, C_DARK, false);
        if (!matStack.isEmpty()) {
            int iconOff = (font.lineHeight - 16) / 2;
            CommonGuiTextures.drawItem(g, matStack, costX + costLabelW, infoY + iconOff, 1.0f);
        }
        g.drawString(font, costAmount, costX + costLabelW + 17, infoY, C_DARK, false);
        infoY += lineStep;

        // Output count
        if (entry.outputCount() > 1) {
            String outStr = I18n.get("stardewcraft.workbench.output") + ": \u00d7" + entry.outputCount();
            GuiText.drawCenteredClamped(g, font, Component.literal(outStr), cx, infoY,
                prvW - ui(24), C_DARK, false);
            infoY += lineStep;
        }

        // Max craftable
        boolean ok = effectiveMat() >= entry.cost();
        int maxCraft = entry.cost() > 0 ? effectiveMat() / entry.cost() : 0;
        String maxStr = I18n.get("stardewcraft.workbench.max_craftable") + ": " + maxCraft;
        GuiText.drawCenteredClamped(g, font, Component.literal(maxStr), cx, infoY,
            prvW - ui(24), ok ? C_GREEN : C_RED, false);

        // ── Craft button ──
        drawCraftBtn(g, mx, my, ok);
    }

    // ── Craft button ────────────────────────────────────────────────────────
    private void drawCraftBtn(GuiGraphics g, int mx, int my, boolean ok) {
        boolean hov = isIn(mx, my, btnX, btnY, btnW, btnH);

        // Fill-style button (like tabs)
        int bgColor = ok ? (hov ? 0xFFE8D8B0 : C_TAB_NRM) : 0xFFBBAAAA;
        int bdrColor = ok ? C_TAB_BDR : 0xFF887070;

        // Background
        g.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, bgColor);
        // Rounded corners via edge fills
        g.fill(btnX, btnY + 2, btnX + 1, btnY + btnH - 2, bgColor);
        g.fill(btnX + btnW - 1, btnY + 2, btnX + btnW, btnY + btnH - 2, bgColor);
        g.fill(btnX + 2, btnY, btnX + btnW - 2, btnY + 1, bgColor);
        g.fill(btnX + 2, btnY + btnH - 1, btnX + btnW - 2, btnY + btnH, bgColor);

        // Border
        g.fill(btnX + 2, btnY, btnX + btnW - 2, btnY + 1, bdrColor);
        g.fill(btnX + 2, btnY + btnH - 1, btnX + btnW - 2, btnY + btnH, bdrColor);
        g.fill(btnX, btnY + 2, btnX + 1, btnY + btnH - 2, bdrColor);
        g.fill(btnX + btnW - 1, btnY + 2, btnX + btnW, btnY + btnH - 2, bdrColor);

        // Label
        String label = I18n.get("stardewcraft.workbench.craft");
        GuiText.drawCenteredClamped(g, font, Component.literal(label), btnX + btnW / 2,
            btnY + (btnH - font.lineHeight) / 2, btnW - ui(12), ok ? C_DARK : C_GREY, false);

        if (hov && ok) {
            g.renderTooltip(font, Component.literal(I18n.get("stardewcraft.workbench.craft_hint")), mx, my);
        }
    }

    // ── Close button ────────────────────────────────────────────────────────
    private void drawClose(GuiGraphics g, int mx, int my, float s4) {
        boolean hov = isIn(mx, my, clsX, clsY, clsW, clsH);
        clsAnim = hov ? Math.min(clsAnim + 0.04f, 1.2f) : Math.max(1.0f, clsAnim - 0.04f);
        float cs = s4 * clsAnim;
        int rx = clsX + clsW / 2 - (int)(CLOSE_W * cs / 2);
        int ry = clsY + clsH / 2 - (int)(CLOSE_H * cs / 2);
        CommonGuiTextures.drawCloseButton(g, rx, ry, cs);
    }

    // ── Tooltip ─────────────────────────────────────────────────────────────
    private void drawTooltip(GuiGraphics g, int mx, int my, WorkbenchEntry entry) {
        ItemStack stack = resolveStack(entry.itemId());
        List<Component> lines = new ArrayList<>();

        if (!stack.isEmpty()) {
            lines.addAll(stack.getTooltipLines(
                Item.TooltipContext.EMPTY, minecraft.player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL));
        } else {
            lines.add(Component.literal(entry.itemId().toString()));
        }

        lines.add(Component.empty());
        String matName = itemName(wbType.getInputItemId());
        boolean ok = effectiveMat() >= entry.cost();
        lines.add(Component.literal(matName + " \u00d7" + entry.cost())
            .withStyle(ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
        if (entry.outputCount() > 1) {
            lines.add(Component.literal(I18n.get("stardewcraft.workbench.output") + ": \u00d7" + entry.outputCount())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        g.renderTooltip(font, lines, Optional.empty(), mx, my);
    }

    // =====================================================================
    // Mouse input
    // =====================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (System.currentTimeMillis() - openedAt < SAFETY_MS) return true;
        int mx = (int) mouseX, my = (int) mouseY;

        // Close
        if (isIn(mx, my, clsX, clsY, clsW, clsH)) {
            playSound(ModSounds.DWOP.get());
            onClose();
            return true;
        }

        // Tabs
        int tabGapGui = ui(TAB_GAP);
        for (int i = 0; i < categories.size(); i++) {
            int ty = tabFirstY + i * (tabSzGui + tabGapGui);
            if (isIn(mx, my, tabX, ty, tabSzGui + ui(8), tabSzGui)) {
                if (activeTab != i) {
                    activeTab = i;
                    rebuildFiltered();
                    playSound(ModSounds.SHWIP.get());
                }
                return true;
            }
        }

        // Page arrows
        if (maxPage > 0) {
            if (isIn(mx, my, arrLX, pgY, arrW, arrH) && page > 0) {
                page--;
                playSound(ModSounds.SHWIP.get());
                return true;
            }
            if (isIn(mx, my, arrRX, pgY, arrW, arrH) && page < maxPage) {
                page++;
                playSound(ModSounds.SHWIP.get());
                return true;
            }
        }

        // Grid cells
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int cx = grdX + col * (cellGui + gapGui);
                int cy = grdY + row * (cellGui + gapGui);
                if (isIn(mx, my, cx, cy, cellGui, cellGui)) {
                    int abs = page * COLS * ROWS + row * COLS + col;
                    if (abs < filtered.size()) {
                        selIdx = abs;
                        playSound(ModSounds.SHINY4.get());
                    }
                    return true;
                }
            }
        }

        // Craft button
        if (isIn(mx, my, btnX, btnY, btnW, btnH)) {
            doCraft(button);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hScroll, double vScroll) {
        if (maxPage > 0) {
            int np = Math.max(0, Math.min(maxPage, page + (vScroll > 0 ? -1 : 1)));
            if (np != page) { page = np; playSound(ModSounds.SHINY4.get()); }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_E) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    // =====================================================================
    // Crafting
    // =====================================================================
    private void doCraft(int button) {
        if (selIdx < 0 || selIdx >= filtered.size()) return;
        WorkbenchEntry entry = filtered.get(selIdx);
        int eff = effectiveMat();
        if (eff < entry.cost()) { playSound(ModSounds.CANCEL.get()); return; }

        int qty;
        if (hasShiftDown() && hasControlDown()) qty = eff / entry.cost();
        else if (hasShiftDown() || button == 1) qty = 5;
        else qty = 1;
        qty = Math.max(1, Math.min(qty, eff / entry.cost()));

        playSound(ModSounds.PURCHASE_CLICK.get());
        PacketDistributor.sendToServer(
            new WorkbenchCraftPayload(wbType.getId(), entry.itemId().toString(), qty));
    }

    public void onCraftResult(WorkbenchCraftResultPayload result) {
        if (result.success()) {
            matCount = result.remainingMaterial();
            bonusCount = result.remainingBonus();
            try { playSound(ModSounds.COIN.get()); } catch (Exception ignored) {}
        } else {
            playSound(ModSounds.CANCEL.get());
        }
        recountMaterials();
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private int ui(int sdvPx) { return Math.round(sdvPx / gs); }
    private float s4() { return 4.0f / gs; }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playSound(net.minecraft.sounds.SoundEvent ev) {
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(ev, 1f, 1f);
    }

    private static ItemStack resolveStack(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private static String itemName(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return (item != null && item != Items.AIR) ? new ItemStack(item).getHoverName().getString() : itemId;
    }

    /** JEI bridge for this non-container crafting screen. */
    public ClickableItem jeiIngredientAt(double mouseX, double mouseY) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = grdX + col * (cellGui + gapGui);
                int y = grdY + row * (cellGui + gapGui);
                if (mouseX < x || mouseX >= x + cellGui || mouseY < y || mouseY >= y + cellGui) {
                    continue;
                }
                int index = page * COLS * ROWS + row * COLS + col;
                if (index >= filtered.size()) {
                    return null;
                }
                ItemStack stack = resolveStack(filtered.get(index).itemId());
                return stack.isEmpty() ? null : new ClickableItem(stack, x, y, cellGui, cellGui);
            }
        }
        return null;
    }

    public int jeiGuiLeft() { return pnlX; }
    public int jeiGuiTop() { return pnlY; }
    public int jeiGuiWidth() { return pnlW; }
    public int jeiGuiHeight() { return pnlH; }

    public record ClickableItem(ItemStack stack, int x, int y, int width, int height) {
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
