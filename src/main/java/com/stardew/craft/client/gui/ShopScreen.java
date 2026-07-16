package com.stardew.craft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.network.payload.ShopPurchasePayload;
import com.stardew.craft.network.payload.ShopPurchaseResultPayload;
import com.stardew.craft.network.payload.ShopSellPayload;
import com.stardew.craft.network.payload.ShopPickupPayload;
import com.stardew.craft.network.payload.ShopSellResultPayload;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class ShopScreen extends Screen {

    // SDV layout (screen-pixels = sprite-px × 4)
    private static final int BORDER   = 40;
    private static final int WIN_W    = 1000 + 2 * BORDER;
    private static final int WIN_H    = 600  + 2 * BORDER;
    private static final int PANEL_H  = WIN_H - 256 + 32 + 4;
    private static final int ROW_H    = (WIN_H - 256) / 4 + 4;
    private static final int ROWS     = 4;
    private static final int PORTRAIT_OFFSET = 320;

    // Cursors sprite dimensions (NOT ×4)
    private static final int PORT_W=74;
    private static final int ARR_UP_W=11,ARR_UP_SH=12;
    private static final int SCR_F_W=6,SCR_F_SH=10;
    private static final int CLOSE_W=12,CLOSE_SH=12;

    // Inventory constants (9-wide × 4-row: hotbar at row-3, main at rows 0-2)
    private static final int INV_COLS  = 9;
    private static final int INV_ROWS  = 4;
    private static final int SLOT_SIZE = 64;  // SDV screen px
    private static final int SLOT_GAP  = 4;   // SDV screen px

    // Colors
    private static final int BG_TINT = 0xBF000000;
    private static final long BUY_HOLD_INITIAL_DELAY_MS = 320;
    private static final long BUY_HOLD_REPEAT_MS = 95;
    private static final String FAIR_STAR_TOKEN_SHOP_ID = "Festival_StardewValleyFair_StarTokens";

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------
    private final String              shopId;
    private final List<ShopItemEntry> forSale;
    private int                       playerMoney;
    private final String              ownerNpcId;
    private final String              ownerDialogue;
    /**
     * IStardewItem.getItemTypeKey() values this shop will buy from player.
     * Sent from server via OpenShopScreenPayload (mirrors SDV SalableItemTags).
     */
    private final Set<String>         acceptedSellTypeKeys;

    // Layout (MC gui coords)
    private int panelX, panelY;
    private int panelWGui, mainHGui;
    private int invBoxXGui, invBoxYGui, invBoxWGui, invBoxHGui;
    private int rowHGui, rowWGui;
    private int invGridX, invGridY, slotSzGui;
    private int upArrowX,upArrowY,upArrowW,upArrowH;
    private int dnArrowX,dnArrowY,dnArrowW,dnArrowH;
    private int scrBarX,scrBarY,scrBarW,scrBarH;
    private int scrRunX,scrRunY,scrRunW,scrRunH;
    private int closeX,closeY,closeW,closeH;
    private float closeScale = 1.0f;
    private int currencyX, currencyY;

    // Portrait texture cache (dynamic dimensions, like StardewNpcDialogueScreen)
    private record PortraitInfo(ResourceLocation texture, int sheetW, int sheetH) {}
    private static final Map<ResourceLocation, PortraitInfo> PORTRAIT_CACHE = new ConcurrentHashMap<>();

    public static void clearPortraitCache() {
        PORTRAIT_CACHE.clear();
    }

    private int   currentIndex = 0;
    private int   hoveredRow   = -1;
    private int   hoveredInvSlot = -1;
    private final float[] rowScales = new float[ROWS];

    private boolean scrolling = false;
    private int     scrollDragOffsetY;

    private float guiScale = 1.0f;
    private long  openedAtMs;
    private static final long SAFETY_MS = 250;

    /** Item currently held on cursor (SDV heldItem equivalent). */
    private ItemStack heldItem = ItemStack.EMPTY;
    private boolean   purchasePending = false;
    private boolean   buyHoldActive = false;
    private int       buyHoldItemIndex = -1;
    private long      buyHoldStartedAtMs = 0L;
    private long      buyHoldLastTickMs = 0L;

    // =========================================================================
    public ShopScreen(OpenShopScreenPayload payload) {
        super(Component.empty());
        this.shopId             = payload.shopId();
        this.forSale            = new ArrayList<>(payload.items()); // mutable for stock updates
        this.playerMoney        = payload.playerMoney();
        this.ownerNpcId         = payload.ownerNpcId();
        this.ownerDialogue      = payload.ownerDialogue();
        this.acceptedSellTypeKeys = new HashSet<>(payload.acceptedSellTypeKeys());
        for (int i = 0; i < ROWS; i++) rowScales[i] = 1.0f;
    }

    // =========================================================================
    // Item name / description resolution (client-side from MC registry)
    // =========================================================================

    /**
     * Returns the localised display name of the item.
     * Priority: MC ItemStack.getHoverName() (honours resource-pack / lang overrides).
     * Falls back to ShopItemEntry.displayName() if the MC item is unknown.
     */
    private String resolveItemName(ShopItemEntry entry) {
        String itemId = entry.itemId();
        // Decoration unlock entries: "壁纸 #42" / "Wallpaper #42" etc.
        if (itemId.startsWith("wallpaper:") || itemId.startsWith("flooring:")) {
            boolean isWp = itemId.startsWith("wallpaper:");
            String styleId = itemId.substring(isWp ? "wallpaper:".length() : "flooring:".length());
            String typeKey = isWp ? "stardewcraft.shop.wallpaper_unlock" : "stardewcraft.shop.flooring_unlock";
            return net.minecraft.client.resources.language.I18n.get(typeKey) + " #" + styleId;
        }
        boolean isRecipe = itemId.startsWith("recipe:");
        if (isRecipe) itemId = itemId.substring("recipe:".length());
        ItemStack stack = resolveStack(itemId);
        String base;
        if (!stack.isEmpty()) {
            String name = stack.getHoverName().getString();
            base = isRecipe ? name + " (" + net.minecraft.client.resources.language.I18n.get("stardewcraft.shop.recipe_suffix") + ")" : name;
        } else {
            base = entry.displayName().isEmpty() ? entry.itemId() : entry.displayName();
        }
        // SDV parity (ShopMenu.cs:1897): if (item.Stack > 1) displayName += " x" + item.Stack;
        // Not applied to recipes (recipes always show as stack=1 in SDV).
        int stack1 = entry.purchaseStack();
        if (!isRecipe && stack1 > 1) {
            base = base + " x" + stack1;
        }
        return base;
    }

    /**
     * Returns the item description line.
     * MC items don't all have descriptions; use the server-sent description
     * (which ShopRegistry sets to "") — callers skip empty strings.
     */
    /** Resolves a mod item id to an ItemStack (1 unit), or EMPTY on failure. */
    private static ItemStack resolveStack(String itemId) {
        // Strip recipe: prefix so icon shows the actual dish item
        if (itemId.startsWith("recipe:")) itemId = itemId.substring("recipe:".length());
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            if (mcItem != null && mcItem != Items.AIR) return new ItemStack(mcItem);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    /**
     * Returns true if the item in the given inventory slot can be sold
     * at this shop (mirrors SDV highlightItemToSell).
     */
    private boolean canSellAt(ItemStack stack) {
        if (!heldItem.isEmpty()) {
            return ItemStack.isSameItemSameComponents(heldItem, stack);
        }
        if (stack.isEmpty()) return false;
        if (StardewItemDataApi.getSellPrice(stack) <= 0) return false;
        return acceptedSellTypeKeys.contains(StardewItemDataApi.getTypeKey(stack))
                || matchesShopSpecificSellRule(stack);
    }

    private boolean matchesShopSpecificSellRule(ItemStack stack) {
        if (!"Blacksmith".equals(shopId)) {
            return false;
        }
        return stack.is(ModTags.Items.ORES)
            || stack.is(ModTags.Items.BARS)
            || "stardewcraft:coal".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    // =========================================================================
    // init
    // =========================================================================
    @Override
    protected void init() {
        super.init();
        openedAtMs = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        guiScale = (float) mc.getWindow().getGuiScale();
        float s4 = s4();

        panelWGui = ui(WIN_W);
        mainHGui  = ui(PANEL_H);
        panelX    = width  / 2 - ui(880) / 2;
        panelY    = height / 2 - ui(WIN_H) / 2 - ui(32);
        rowHGui   = ui(ROW_H);
        rowWGui   = panelWGui - ui(32);

        // Inventory box
        slotSzGui = ui(SLOT_SIZE);
        int gapGui = ui(SLOT_GAP);
        int gridW  = INV_COLS * slotSzGui + (INV_COLS - 1) * gapGui;
        int gridH  = INV_ROWS * slotSzGui + (INV_ROWS - 1) * gapGui;
        invBoxWGui = gridW + ui(56);
        invBoxHGui = gridH + ui(44);
        invBoxXGui = panelX + panelWGui - invBoxWGui - ui(8);
        invBoxYGui = panelY + mainHGui + ui(8);
        invGridX   = invBoxXGui + ui(20);
        invGridY   = invBoxYGui + ui(16);

        // Scroll arrows
        upArrowW = (int)(ARR_UP_W * s4); upArrowH = (int)(ARR_UP_SH * s4);
        upArrowX = panelX + panelWGui + ui(16); upArrowY = panelY + ui(16);
        dnArrowW = upArrowW; dnArrowH = upArrowH;
        dnArrowX = upArrowX; dnArrowY = panelY + mainHGui - ui(64);

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

        // SDV parity: money box = overrideX = xPos-36, overrideY = yPos+height-inv.height-12
        // Our equiv: panelY + mainHGui - 12. Anchor directly to the main panel so
        // rounding does not drift when GUI scale is not 4x.
        currencyX = panelX - ui(36);
        currencyY = panelY + mainHGui - ui(12);
    }

    // =========================================================================
    // renderBackground — suppress MC dirt/blur (SDV draws its own fadeToBlackRect)
    // =========================================================================
    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    // =========================================================================
    // render
    // =========================================================================
    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG_TINT); // SDV: fadeToBlackRect × 0.75

        float s4 = s4();

        // 1. Main items box (with shadow)
        CommonGuiTextures.drawTextureBox(g, panelX, panelY, panelWGui, mainHGui, s4, true);

        // 2. Inventory box. Draw after the main panel so its shadow cannot cover inventory.
        CommonGuiTextures.drawTextureBox(g, invBoxXGui, invBoxYGui, invBoxWGui, invBoxHGui, s4, false);

        // 3. Currency
        drawCurrency(g, s4);

        // 4. Player inventory grid
        drawInventory(g, mouseX, mouseY, s4);

        // 5. Item rows
        hoveredRow = -1;
        for (int i = 0; i < ROWS; i++) {
            int itemIdx = currentIndex + i;
            if (itemIdx >= forSale.size()) break;
            drawItemRow(g, mouseX, mouseY, s4, i, itemIdx);
        }

        // 6. Out-of-stock message
        if (forSale.isEmpty()) {
            String msg = "Nothing for sale.";
            GuiText.drawCenteredClamped(g, font, Component.literal(msg),
                panelX + panelWGui / 2, panelY + mainHGui / 2 - font.lineHeight / 2,
                Math.max(1, panelWGui - ui(80)), 0x404040, false);
        }

        // 7. Scroll arrows
        boolean upOn = currentIndex > 0;
        boolean dnOn = currentIndex < Math.max(0, forSale.size() - ROWS);
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

        // 8. Scrollbar
        if (forSale.size() > ROWS) {
            CommonGuiTextures.drawScrollTrackBox(g, scrRunX, scrRunY, scrRunW, scrRunH, s4);
            CommonGuiTextures.drawScrollBarThumb(g, scrBarX, scrBarY, s4);
        }

        // 9. Close button
        boolean closeHov = isIn(mouseX, mouseY, closeX, closeY, closeW, closeH);
        closeScale = closeHov ? Math.min(closeScale+0.04f,1.2f) : Math.max(1.0f,closeScale-0.04f);
        float cs = s4 * closeScale;
        int cdx = closeX + closeW/2 - (int)(CLOSE_W *cs/2);
        int cdy = closeY + closeH/2 - (int)(CLOSE_SH*cs/2);
        CommonGuiTextures.drawCloseButton(g, cdx, cdy, cs);

        // 10. Portrait — SDV ShopMenu.cs L2008-L2024 parity
        // SDV: portrait_draw_position = xPositionOnScreen - 320
        //      Frame = 74×4 = 296 screen px; face at +20,+20; dialogue at Y+312 via drawHoverText
        int portX = panelX - ui(PORTRAIT_OFFSET);
        if (portX > 0 && shouldDrawOwnerPortrait()) {
            // Frame background from cursors (SDV: Utility.drawWithShadow at portrait_draw_position)
            CommonGuiTextures.drawShopPortraitFrame(g, portX, panelY, s4);
            int portSize = (int)(PORT_W * s4);
            // Face on top of frame — uses dynamic texture dimensions
            drawNpcPortraitInShop(g, portX, panelY, portSize);

            // Dialogue text below portrait in styled box
            // SDV: drawHoverText at (recalculated_x, yPositionOnScreen + 312)
            // 312 = 296 (portrait frame) + 16 (gap), all in screen pixels
            if (!ownerDialogue.isEmpty()) {
                String dialogueText = ownerDialogue;
                if (ownerDialogue.startsWith("stardewcraft.")) {
                    dialogueText = Component.translatable(ownerDialogue).getString();
                }
                // SDV pre-wraps text to 304 screen px width
                int wrapWidth = ui(304);
                List<net.minecraft.util.FormattedCharSequence> wrappedLines =
                    font.split(Component.literal(dialogueText), wrapWidth);
                int textH = wrappedLines.size() * font.lineHeight;
                int boxPad = ui(16);  // padding inside the tooltip box
                int boxW = wrapWidth + boxPad * 2;
                int boxH = textH + boxPad * 2;
                // SDV recalculates X: xPositionOnScreen - MeasureString(text).X - 64
                // We center the dialogue box under the portrait frame
                int dlgX = portX + portSize / 2 - boxW / 2;
                int dlgY = panelY + ui(312);  // SDV: yPositionOnScreen + 312

                // Draw SDV-style tooltip box (same as IClickableMenu.drawHoverText default theme)
                CommonGuiTextures.drawMenuTextureBox(g, dlgX, dlgY, boxW, boxH, 1.0f / guiScale, true);

                // Draw text inside box
                int textX = dlgX + boxPad;
                int textY = dlgY + boxPad;
                for (var line : wrappedLines) {
                    g.drawString(font, line, textX, textY, 0x5C2B00, false);
                    textY += font.lineHeight;
                }
            }
        }

        // 11. Held item on cursor — centered on cursor tip (MC convention: -8,-8)
        if (!heldItem.isEmpty()) {
            int itemSize = CommonGuiTextures.itemSize(s4);
            CommonGuiTextures.drawItemWithDecorations(g, font, heldItem, mouseX - itemSize / 2, mouseY - itemSize / 2, s4);
        }

        // 12. Tooltip
        if (hoveredRow >= 0) {
            int idx = currentIndex + hoveredRow;
            if (idx < forSale.size()) drawBuyTooltip(g, mouseX, mouseY, forSale.get(idx));
        } else if (hoveredInvSlot >= 0 && heldItem.isEmpty()) {
            drawInvTooltip(g, mouseX, mouseY, hoveredInvSlot);
        }
    }

    private boolean shouldDrawOwnerPortrait() {
        return !ownerNpcId.isBlank() && !"JojaMart".equals(shopId);
    }

    // =========================================================================
    // Sub-render helpers
    // =========================================================================

    /**
     * Draw real NPC portrait texture inside the portrait frame.
     * Uses dynamic NativeImage dimension loading (same as StardewNpcDialogueScreen)
     * to handle varying texture atlas sizes (128×128, 128×256, 128×320, 128×384).
     */
    private void drawNpcPortraitInShop(GuiGraphics g, int x, int y, int size) {
        String npcId = ownerNpcId.toLowerCase(java.util.Locale.ROOT).trim();
        // SDV: face at (portrait_draw_position + 20, yPositionOnScreen + 20), scale 4f
        // Frame = 74 sprite × s4; face = 64 sprite × s4. Margin = (74-64)/2 × s4 = 5×s4 = ui(20)
        int margin = ui(20);
        int drawX = x + margin;
        int drawY = y + margin;
        int drawSize = size - margin * 2;

        PortraitInfo info = resolvePortraitInfo(npcId);
        g.blit(info.texture(), drawX, drawY, drawSize, drawSize,
            0, 0, 64, 64, info.sheetW(), info.sheetH());
    }

    /** Load portrait texture with actual NativeImage dimensions, cached. */
    private PortraitInfo resolvePortraitInfo(String npcId) {
        ResourceLocation portrait = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/portraits/" + npcId + ".png");
        PortraitInfo cached = PORTRAIT_CACHE.get(portrait);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager().getResource(portrait).isEmpty()) {
            portrait = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "textures/entity/npc/" + npcId + ".png");
        }

        int w = 128, h = 256; // safe defaults
        try {
            var res = mc.getResourceManager().getResource(portrait).orElse(null);
            if (res != null) {
                try (var stream = res.open(); NativeImage img = NativeImage.read(stream)) {
                    w = img.getWidth();
                    h = img.getHeight();
                }
            }
        } catch (IOException ignored) {}

        PortraitInfo info = new PortraitInfo(portrait, Math.max(64, w), Math.max(64, h));
        PORTRAIT_CACHE.put(portrait, info);
        return info;
    }

    // SDV money box sprite: cursors (340,472,65,17) ×4 = background banner
    // SDV digit sprite:    cursors (286, 502-digit*8, 5, 8) ×4, color=Maroon (0x800000)
    // Position: drawMoneyBox called with overrideX = xPos-36, overrideY = yPos+height-inventory.height-12
    // The banner draws at pos + (28,172), digits at pos + (68,196) relative to the
    // "position" field which in ShopMenu equals (overrideX, overrideY-172).
    // So banner absolute Y = overrideY - 172 + 172 = overrideY
    //    digit  absolute Y = overrideY - 172 + 196 = overrideY + 24
    private static final int NUM_DIGITS = 8;

    private void drawCurrency(GuiGraphics g, float s4) {
        // ── Banner ──────────────────────────────────────────────────────────
        // SDV DayTimeMoneyBox: banner draws at position + (28, 0) where both
        // values are in SDV *screen* pixels (already ×4 scale).  The correct
        // MC-GUI conversion is ui(sdvScreenPx) = sdvScreenPx / guiScale, NOT
        // sdvScreenPx * s4 (which would be 4× too large).
        int bannerX = currencyX + ui(28);  // ui(28) == 28/guiScale
        int bannerY = currencyY;
        CommonGuiTextures.drawMoneyBox(g, bannerX, bannerY, s4);

        // ── Digits ──────────────────────────────────────────────────────────
        // SDV MoneyDial: digits at position + (68, 24) (SDV screen px),
        // advance per digit = 6 sprite-px × 4 = 24 SDV screen-px.
        // (int)(6*s4) == ui(24) because 6 sprite-px × s4 = 6×4/guiScale ✓
        int digitStartX  = currencyX + ui(68);  // offset is SDV screen-px → ui()
        int digitY       = currencyY + ui(24);   // same
        int digitSpacing = Math.round(6 * s4);   // 6 sprite-px × scale per digit

        int val = Math.max(0, playerMoney);
        int[] digits = new int[NUM_DIGITS];
        int temp = val;
        for (int i = NUM_DIGITS - 1; i >= 0; i--) { digits[i] = temp % 10; temp /= 10; }

        boolean significant = false;
        for (int i = 0; i < NUM_DIGITS; i++) {
            int d = digits[i];
            if (d > 0 || i == NUM_DIGITS - 1) significant = true;
            if (significant) {
                CommonGuiTextures.drawMoneyDigitTint(g, digitStartX + i * digitSpacing, digitY, d, s4, 0.502f, 0f, 0f, 1f);
            }
        }
    }

    private void drawInventory(GuiGraphics g, int mouseX, int mouseY, float s4) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int sz  = slotSzGui;
        int gap = ui(SLOT_GAP);
        hoveredInvSlot = -1;

        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                // row 3 = hotbar (slots 0-8), rows 0-2 = main inventory (9-35)
                int mcSlot = (row == 3) ? col : (9 + row * 9 + col);
                int sx = invGridX + col * (sz + gap);
                int sy = invGridY + row * (sz + gap);
                boolean hov = isIn(mouseX, mouseY, sx, sy, sz, sz);
                if (hov) hoveredInvSlot = mcSlot;

                ItemStack stack = mc.player.getInventory().getItem(mcSlot);
                boolean sellable = canSellAt(stack);

                CommonGuiTextures.drawMenuTile(g, sx, sy, sz, sz, 10);
                if (hov) {
                    g.fill(sx, sy, sx + sz, sy + sz, 0x35FFFFFF);
                }

                if (!stack.isEmpty()) {
                    int ix = sx + (sz - CommonGuiTextures.itemSize(s4)) / 2;
                    int iy = sy + (sz - CommonGuiTextures.itemSize(s4)) / 2;
                    // Only dim the item icon (not the slot background).
                    if (!sellable) g.setColor(0.62f, 0.62f, 0.62f, 1f);
                    CommonGuiTextures.drawItemWithDecorations(g, font, stack, ix, iy, s4);
                    if (!sellable) g.setColor(1f, 1f, 1f, 1f);
                }
            }
        }
    }

    private void drawItemRow(GuiGraphics g, int mouseX, int mouseY, float s4, int i, int itemIdx) {
        ShopItemEntry item = forSale.get(itemIdx);
        boolean canAfford = item.price() <= 0 || playerMoney >= item.price();
        boolean hasTrade = hasTradeItem(item, 1);
        boolean outOfStock = item.stock() == 0;
        boolean canBuy = canAfford && hasTrade && !outOfStock;

        int rowX = panelX + ui(16);
        int rowY = panelY + ui(16) + i * rowHGui;
        boolean hov = isIn(mouseX, mouseY, rowX, rowY, rowWGui, rowHGui) && !scrolling;
        if (hov) hoveredRow = i;

        // Scale animation
        rowScales[i] = hov && canBuy
            ? Math.min(rowScales[i]+0.03f, 1.1f)
            : Math.max(1.0f, rowScales[i]-0.03f);

        // Row background tint
        if      (hov && canBuy)    g.setColor(0.961f, 0.871f, 0.702f, 1.0f); // Wheat
        else if (!canBuy)          g.setColor(0.6f,   0.6f,   0.6f,   1.0f); // grey
        CommonGuiTextures.drawEntryBox(g, rowX, rowY, rowWGui, rowHGui, s4, false);
        g.setColor(1f,1f,1f,1f);

        // Icon background (SDV: button.X+32-12, button.Y+24-4)
        int icoX = rowX + ui(20);
        int icoY = rowY + ui(20);
        CommonGuiTextures.drawItemSlot18(g, icoX, icoY, s4);

        // Item icon
        int iconX = rowX + ui(24);
        int iconY = rowY + ui(24);
        boolean isRecipeItem = item.itemId().startsWith("recipe:");
        // SDV parity: recipes render at 0.5 transparency and 0.75 scale;
        // non-buyable items render at 0.25 transparency.
        float alpha = canBuy ? (isRecipeItem ? 0.5f : 1.0f) : 0.25f;
        drawItemIconAt(g, item.itemId(), iconX, iconY, s4, alpha, isRecipeItem);

        // Name — resolved from MC registry (localised), not hardcoded
        int nameX = rowX + ui(104);
        int nameMaxWidth = Math.max(ui(120), rowWGui - ui(item.price() > 0 || item.requiresTrade() ? 360 : 180));
        Component name = GuiText.ellipsize(font, Component.literal(resolveItemName(item)), nameMaxWidth);
        // SDV SpriteText ItemRowTextColor = Color.Black (0x000000), we approximate with 0x1a1a1a
        g.drawString(font, name, nameX, rowY + ui(28),
            canBuy ? 0x1a1a1a : 0x888888, false);

        // Price + coin + trade (SDV ShopMenu.cs L1932-1961)
        // SDV approach: draw price first, then shift 'right' leftward, then draw trade items.
        int tradeRight = rowX + rowWGui; // SDV: right = forSaleButton.bounds.Right
        int tradeIconY = rowY + ui(28);  // default (no-price) Y for trade icon
        int tradeTextY = rowY + ui(28);  // default Y for trade count text
        if (item.price() > 0) {
            String prStr = item.price() + " ";
            int prW   = font.width(prStr);
            int prX   = rowX + rowWGui - ui(60) - prW;
            int prY   = rowY + ui(28);
            g.drawString(font, prStr, prX, prY,
                canAfford ? 0x404040 : 0x992222, false);

            float coinA = canBuy ? 1.0f : 0.25f;
            if (isFairStarTokenShop()) {
                if (coinA < 1f) {
                    CommonGuiTextures.drawFairStarTokenTint(g, rowX + rowWGui - ui(52), rowY + ui(36), s4, 1f, 1f, 1f, coinA);
                } else {
                    CommonGuiTextures.drawFairStarToken(g, rowX + rowWGui - ui(52), rowY + ui(36), s4);
                }
            } else if (coinA < 1f) {
                CommonGuiTextures.drawShopCoinTint(g, rowX + rowWGui - ui(52), rowY + ui(36), s4, 1f, 1f, 1f, coinA);
            } else {
                CommonGuiTextures.drawShopCoin(g, rowX + rowWGui - ui(52), rowY + ui(36), s4);
            }

            // SDV: right -= SpriteText.getWidthOfString(price + " ") + 96
            tradeRight -= prW + ui(96);
            tradeIconY = rowY + ui(20);
            tradeTextY = rowY + ui(28);
        }

        // Trade requirement (SDV: stock.TradeItem)
        if (item.requiresTrade()) {
            ItemStack trade = resolveStack(item.tradeItemId());
            int req = Math.max(1, item.tradeItemCount());
            boolean enough = hasTrade;
            String reqText = "x" + req;
            int reqTextW = font.width(reqText);
            // SDV: icon at (right - 88 - textWidth, tradeIconDrawY)
            int tx = tradeRight - ui(88) - reqTextW;
            int ty = tradeIconY;

            if (!trade.isEmpty()) {
                if (!enough) g.setColor(1f, 1f, 1f, 0.25f);
                CommonGuiTextures.drawItem(g, trade, tx, ty, s4);
                if (!enough) g.setColor(1f, 1f, 1f, 1f);
            }

            // SDV: text at (right - textWidth - 16, tradeTextDrawY)
            g.drawString(font, reqText, tradeRight - reqTextW - ui(16), tradeTextY,
                enough ? 0x404040 : 0x992222, false);
        }

        // SDV stock count: drawn as tiny digits on item icon, NOT as freestanding text.
        // SDV explicitly skips stock digits for "ClintUpgrade" shop and recipe items.
        // Stock info is already shown in tooltip.
        if (item.stock() != Integer.MAX_VALUE && item.stock() > 0
                && !"ClintUpgrade".equals(shopId) && !isRecipeItem) {
            // SDV: Utility.drawTinyDigits at icon bottom-right (drawPos + (64-w+3, 47))
            int stockX = iconX + ui(64 - tinyDigitWidthSdv(item.stock(), 3.0f) + 3);
            int stockY = iconY + ui(47);
            drawTinyDigits(g, item.stock(), stockX, stockY, 3.0f, canBuy ? 1.0f : 0.25f);
        }
    }

    private void drawTinyDigits(GuiGraphics g, int value, int x, int y, float sdvScale, float alpha) {
        int currentValue = Math.max(0, value);
        int digitCount = 0;
        int remaining = currentValue;
        do {
            digitCount++;
        } while ((remaining /= 10) >= 1);

        int digitStrip = (int)Math.pow(10.0, digitCount - 1);
        boolean significant = false;
        int xPosition = 0;
        for (int i = 0; i < digitCount; i++) {
            int currentDigit = currentValue / digitStrip % 10;
            if (currentDigit > 0 || i == digitCount - 1) {
                significant = true;
            }
            if (significant) {
                StardewGuiUtil.drawFromCursorsTint(g, x + xPosition, y,
                    368 + currentDigit * 5, 56, 5, 7,
                    sdvScale / guiScale, 1.0f, 1.0f, 1.0f, alpha);
            }
            xPosition += ui((int)(5.0f * sdvScale) - 1);
            digitStrip /= 10;
        }
    }

    private int tinyDigitWidthSdv(int value, float sdvScale) {
        int digitCount = 0;
        int remaining = Math.max(0, value);
        do {
            digitCount++;
        } while ((remaining /= 10) >= 1);
        return (int)(digitCount * 5.0f * sdvScale);
    }

    /** SDV recipe overlay texture (objectSpriteSheet index 451 — the scroll/blueprint icon). */
    private static final ResourceLocation RECIPE_OVERLAY_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/recipe_overlay.png");

    private void drawItemIconAt(GuiGraphics g, String itemId,
                                 int x, int y, float s4, float alpha, boolean isRecipe) {
        // Decoration unlock entries: draw style sample sprite from DecorationStyleRegistry
        if (itemId.startsWith("wallpaper:") || itemId.startsWith("flooring:")) {
            drawDecorationStyleIcon(g, itemId, x, y, alpha);
            return;
        }
        // Strip recipe: prefix so the dish icon is displayed
        if (itemId.startsWith("recipe:")) itemId = itemId.substring("recipe:".length());
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            if (mcItem == null || mcItem == Items.AIR) return;

            // SDV parity: recipes draw the dish icon at 0.75× scale and semi-transparent
            if (isRecipe) {
                float recipeScale = s4 * 0.75f;
                int itemSize = CommonGuiTextures.itemSize(s4);
                int recipeSize = CommonGuiTextures.itemSize(recipeScale);
                int recipeX = x + (itemSize - recipeSize) / 2;
                int recipeY = y + (itemSize - recipeSize) / 2;
                g.setColor(1f, 1f, 1f, alpha);
                CommonGuiTextures.drawItem(g, new ItemStack(mcItem), recipeX, recipeY, recipeScale);
                g.setColor(1f, 1f, 1f, 1f);

                // SDV: draw recipe scroll overlay at location+(16,16) at 3× sprite scale
                // = 48×48 SDV px in a 64×64 slot → 12×12 GUI px in MC's 16×16 item slot
                int overlaySize = Math.round(12.0f * s4);
                int ox = x + itemSize - overlaySize;
                int oy = y + itemSize - overlaySize;
                g.blit(RECIPE_OVERLAY_TEXTURE, ox, oy, overlaySize, overlaySize, 0, 0, 16, 16, 16, 16);
            } else {
                if (alpha < 1f) g.setColor(1f, 1f, 1f, alpha);
                CommonGuiTextures.drawItem(g, new ItemStack(mcItem), x, y, s4);
                if (alpha < 1f) g.setColor(1f, 1f, 1f, 1f);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Renders a wallpaper/flooring style icon in the shop icon slot — 1:1 同
     * {@link com.stardew.craft.client.gui.DecorationSelectionScreen} 的"小图标"画法：
     * 外层从 mouse_cursors2 采样容器框 (39,31,16,16) / (55,31,16,16)，内层从
     * walls_and_floors 采样小预览（壁纸 8×14 / 地板 14×13），居中叠在框内。
     */
    private void drawDecorationStyleIcon(GuiGraphics g, String itemId, int x, int y, float alpha) {
        boolean isWp = itemId.startsWith("wallpaper:");
        String styleId = itemId.substring(isWp ? "wallpaper:".length() : "flooring:".length());
        com.stardew.craft.deco.DecorationType type = isWp
            ? com.stardew.craft.deco.DecorationType.WALLPAPER
            : com.stardew.craft.deco.DecorationType.FLOORING;
        com.stardew.craft.deco.DecorationStyle style =
            com.stardew.craft.deco.DecorationStyleRegistry.getStyle(type, styleId);
        if (style == null) return;

        // 1) 容器框（mouse_cursors2 里 16×16 的小框）
        CommonGuiTextures.drawCategoryFrame(g, x, y, isWp, 1.0f, alpha);

        // 2) 内层预览贴图（壁纸 8×14 / 地板 14×13，居中）
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        int innerW = isWp ? 8 : 14;
        int innerH = isWp ? 14 : 13;
        int innerX = x + (16 - innerW) / 2;
        int innerY = y + 1;
        g.blit(style.texture(), innerX, innerY, innerW, innerH,
            style.sourceX(), style.sourceY(),
            style.sourceWidth(), style.sourceHeight(),
            style.texWidth(), style.texHeight());

        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawBuyTooltip(GuiGraphics g, int mx, int my, ShopItemEntry item) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = resolveStack(item.itemId());

        List<Component> lines = new ArrayList<>();

        // --- MC 原生 tooltip 内容（名称、模组说明、耦魔等）---
        if (!stack.isEmpty()) {
            List<Component> vanillaLines = stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.EMPTY,
                mc.player,
                net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            lines.addAll(vanillaLines);
        } else {
            // 未知物品，至少显示名义
            lines.add(Component.literal(resolveItemName(item)).withStyle(ChatFormatting.BOLD));
        }

        // --- 商店自定义信息（空行分隔）---
        lines.add(Component.empty());
        if (item.price() > 0) {
            boolean ok = playerMoney >= item.price();
            Component currency = Component.translatable(isFairStarTokenShop()
                    ? "stardewcraft.shop.currency.star_tokens"
                    : "stardewcraft.shop.currency.gold");
            lines.add(Component.translatable("stardewcraft.shop.tooltip.buy", item.price(), currency)
                .withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.DARK_RED));
        } else {
            lines.add(Component.translatable("stardewcraft.catalogue.free").withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.literal("[Shift]×5  [Ctrl+Shift]×25").withStyle(ChatFormatting.DARK_GRAY));
        if (item.stock() != Integer.MAX_VALUE)
            lines.add(Component.translatable("stardewcraft.shop.tooltip.stock", item.stock()).withStyle(ChatFormatting.AQUA));
        if (item.requiresTrade()) {
            ItemStack trade = resolveStack(item.tradeItemId());
            Object tradeName = trade.isEmpty() ? item.tradeItemId() : trade.getHoverName();
            int reqCount = Math.max(1, item.tradeItemCount());
            boolean enough = hasTradeItem(item, 1);
            lines.add(Component.translatable("stardewcraft.shop.tooltip.trade_requirement", tradeName, reqCount)
                .withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.DARK_RED));
        }
        if (!stack.isEmpty()) {
            int sell = StardewItemDataApi.getSellPrice(stack);
            if (sell > 0)
                lines.add(Component.translatable("stardewcraft.shop.tooltip.base_sell_price", sell)
                        .withStyle(ChatFormatting.GRAY));
        }
        g.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
    }

    private void drawInvTooltip(GuiGraphics g, int mx, int my, int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (stack.isEmpty()) return;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(
            net.minecraft.world.item.Item.TooltipContext.EMPTY, mc.player,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL));
        boolean sellable = canSellAt(stack);
        if (sellable) {
            // Show the same metadata-driven base sell value used by the server.
            int sellUnit = StardewItemDataApi.getSellPrice(stack);
            // 应用职业加成显示，与服务器端一致
            try {
                java.util.Set<String> profNames = new java.util.HashSet<>(
                    com.stardew.craft.client.ClientPlayerDataCache.getProfessions());
                com.stardew.craft.economy.sell.SellQuote q =
                    com.stardew.craft.economy.sell.ProfessionSellPriceService.quoteItemForProfessionNames(
                        profNames, stack, com.stardew.craft.economy.sell.SellSource.SHOP_COUNTER);
                if (q.sellable() && q.finalUnitPrice() > 0) {
                    sellUnit = q.finalUnitPrice();
                }
            } catch (Throwable ignored) { /* 客户端缓存不可用时退化到基础价 */ }
            if (sellUnit > 0) {
                lines.add(Component.translatable("stardewcraft.shop.tooltip.sell", sellUnit * stack.getCount())
                    .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("stardewcraft.shop.tooltip.sell_controls")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (!sellable && !stack.isEmpty()) {
            lines.add(Component.translatable("stardewcraft.shop.tooltip.not_purchased")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        g.renderTooltip(font, lines, java.util.Optional.empty(), mx, my);
    }

    // =========================================================================
    // Input
    // =========================================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx=(int)mouseX, my=(int)mouseY;

        // Close
        if (isIn(mx,my,closeX,closeY,closeW,closeH)) {
            if (!heldItem.isEmpty()) placeHeldItemInInventory();
            playSound(ModSounds.DWOP.get());
            onClose();
            return true;
        }
        // Up arrow
        if (isIn(mx,my,upArrowX,upArrowY,upArrowW,upArrowH)) {
            if (currentIndex > 0) { currentIndex--; updateScrollBarPosition(); playSound(ModSounds.SHWIP.get()); }
            return true;
        }
        // Down arrow
        if (isIn(mx,my,dnArrowX,dnArrowY,dnArrowW,dnArrowH)) {
            if (currentIndex < Math.max(0,forSale.size()-ROWS)) { currentIndex++; updateScrollBarPosition(); playSound(ModSounds.SHWIP.get()); }
            return true;
        }
        // Scrollbar drag
        if (isIn(mx,my,scrBarX,scrBarY,scrBarW,scrBarH)) {
            scrolling=true; scrollDragOffsetY=my-scrBarY; return true;
        }
        // Inventory slot
        int inv = getInvSlotAt(mx,my);
        if (inv >= 0) { onInventorySlotClicked(inv,button); return true; }
        // Item row
        for (int i=0; i<ROWS; i++) {
            int idx=currentIndex+i;
            if (idx>=forSale.size()) break;
            int rx=panelX+ui(16), ry=panelY+ui(16)+i*rowHGui;
            if (isIn(mx,my,rx,ry,rowWGui,rowHGui)) {
                if (button == 0 || button == 1) {
                    tryPurchase(idx, false);
                    buyHoldActive = true;
                    buyHoldItemIndex = idx;
                    buyHoldStartedAtMs = System.currentTimeMillis();
                    buyHoldLastTickMs = buyHoldStartedAtMs;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override
    public boolean mouseDragged(double mouseX,double mouseY,int button,double dx,double dy){
        if (scrolling && forSale.size()>ROWS) {
            int newY=(int)mouseY-scrollDragOffsetY;
            newY=Math.max(scrRunY,Math.min(scrRunY+scrRunH-scrBarH,newY));
            float pct=(float)(newY-scrRunY)/Math.max(1,scrRunH-scrBarH);
            currentIndex=Math.max(0,Math.min(forSale.size()-ROWS,Math.round(pct*(forSale.size()-ROWS))));
            updateScrollBarPosition();
            return true;
        }
        return super.mouseDragged(mouseX,mouseY,button,dx,dy);
    }

    @Override
    public boolean mouseReleased(double mouseX,double mouseY,int button){
        scrolling=false;
        if (button == 0 || button == 1) {
            buyHoldActive = false;
            buyHoldItemIndex = -1;
        }
        return super.mouseReleased(mouseX,mouseY,button);
    }

    @Override
    public void tick() {
        super.tick();
        if (!buyHoldActive) return;
        if (buyHoldItemIndex < 0 || buyHoldItemIndex >= forSale.size()) return;

        long now = System.currentTimeMillis();
        if (now - buyHoldStartedAtMs < BUY_HOLD_INITIAL_DELAY_MS) return;
        if (now - buyHoldLastTickMs < BUY_HOLD_REPEAT_MS) return;

        buyHoldLastTickMs = now;

        // SDV parity: play the repeat-purchase sound on every hold tick,
        // independently of purchasePending (which only gates the network packet).
        ShopItemEntry holdItem = forSale.get(buyHoldItemIndex);
        boolean canAfford = holdItem.price() <= 0 || playerMoney >= holdItem.price();
        boolean inStock   = holdItem.stock() != 0;
        boolean canFitOnCursor = clampPurchaseQuantityToCursor(holdItem, 1) > 0;
        if (canAfford && inStock && hasTradeItem(holdItem, 1) && canFitOnCursor) {
            playSound(ModSounds.PURCHASE_REPEAT.get());
        } else if (!canFitOnCursor) {
            buyHoldActive = false;
        }

        tryPurchase(buyHoldItemIndex, true);
    }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double hScroll,double vScroll){
        int dir=vScroll>0?-1:1;
        int ni=Math.max(0,Math.min(forSale.size()-ROWS,currentIndex+dir));
        if (ni!=currentIndex) { currentIndex=ni; updateScrollBarPosition(); playSound(ModSounds.SHINY4.get()); }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode,int scan,int mods){
        if (keyCode==InputConstants.KEY_ESCAPE) {
            if (!heldItem.isEmpty()) { placeHeldItemInInventory(); return true; }
            onClose(); return true;
        }
        return super.keyPressed(keyCode,scan,mods);
    }

    // =========================================================================
    // Purchase (SDV: tryToPurchaseItem)
    // =========================================================================
    private boolean hasTradeItem(ShopItemEntry entry, int qty) {
        if (!entry.requiresTrade()) return true;
        ItemStack req = resolveStack(entry.tradeItemId());
        if (req.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int need = Math.max(1, entry.tradeItemCount()) * Math.max(1, qty);
        return mc.player.getInventory().countItem(req.getItem()) >= need;
    }

    private int clampPurchaseQuantityToCursor(ShopItemEntry entry, int requestedQty) {
        ItemStack salable = resolveStack(entry.itemId());
        if (salable.isEmpty()) return requestedQty;

        int deliveredPerPurchase = Math.max(1, entry.purchaseStack());
        int maxStackSize = Math.max(1, salable.getMaxStackSize());
        int availableSpace = maxStackSize;

        if (!heldItem.isEmpty() && ItemStack.isSameItemSameComponents(heldItem, salable)) {
            availableSpace = Math.max(0, maxStackSize - heldItem.getCount());
        }

        return Math.min(requestedQty, availableSpace / deliveredPerPurchase);
    }

    private void tryPurchase(int itemIdx, boolean repeating) {
        if (System.currentTimeMillis()-openedAtMs < SAFETY_MS) return;
        if (purchasePending) return;

        ShopItemEntry item = forSale.get(itemIdx);
        int qty = purchaseQuantityForCurrentModifiers(item);

        // SDV parity: never buy more than this salable's max stack size.
        ItemStack salable = resolveStack(item.itemId());
        if (!salable.isEmpty()) {
            qty = Math.min(qty, Math.max(1, salable.getMaxStackSize()));
        }
        qty = clampPurchaseQuantityToCursor(item, qty);

        if (item.stock()!=Integer.MAX_VALUE) qty=Math.min(qty,item.stock());
        if (qty<=0) {
            if (repeating) {
                buyHoldActive = false;
            } else {
                playSound(ModSounds.CANCEL.get());
            }
            return;
        }

        int cost=item.price()*qty;
        if (cost>playerMoney || !hasTradeItem(item, qty)) {
            if (repeating) {
                buyHoldActive = false;
            } else {
                playSound(ModSounds.CANCEL.get());
            }
            return;
        }

        playerMoney -= cost; // optimistic
        purchasePending = true;
        if (!repeating) {
            playSound(ModSounds.PURCHASE_CLICK.get());
        }
        PacketDistributor.sendToServer(new ShopPurchasePayload(shopId, itemIdx, item.itemId(), qty));
    }

    private int purchaseQuantityForCurrentModifiers(ShopItemEntry item) {
        if (!hasShiftDown()) {
            return 1;
        }
        if (!hasControlDown()) {
            return 5;
        }
        if (isBuyAllModifierDown()) {
            int stock = item.stock() == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, item.stock());
            int affordable = item.price() <= 0 ? Integer.MAX_VALUE : playerMoney / Math.max(1, item.price());
            int tradeAvailable = maxTradePurchases(item);
            return Math.max(1, Math.min(999, Math.min(stock, Math.min(affordable, tradeAvailable))));
        }
        return 25;
    }

    private boolean isBuyAllModifierDown() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_1);
    }

    private int maxTradePurchases(ShopItemEntry item) {
        if (!item.requiresTrade()) {
            return Integer.MAX_VALUE;
        }
        ItemStack req = resolveStack(item.tradeItemId());
        if (req.isEmpty()) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        return mc.player.getInventory().countItem(req.getItem()) / Math.max(1, item.tradeItemCount());
    }

    // =========================================================================
    // Sell (SDV: receiveLeftClick on inventory component)
    // =========================================================================
    private void onInventorySlotClicked(int slot,int button) {
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null) return;
        if (!heldItem.isEmpty()) { placeHeldItemToSlot(slot); return; }
        ItemStack stack=mc.player.getInventory().getItem(slot);
        if (stack.isEmpty()) return;
        // SDV highlightItemToSell: only allow selling what this shop accepts
        if (!canSellAt(stack)) return;
        int qty=(button==1)?1:stack.getCount();
        playSound(ModSounds.PURCHASE_CLICK.get());
        PacketDistributor.sendToServer(new ShopSellPayload(shopId,slot,qty));
    }

    // =========================================================================
    // heldItem placement
    // =========================================================================
    private void placeHeldItemToSlot(int slot) {
        if (heldItem.isEmpty()) return;
        sendPickupPayload(slot); // tell server: place in THIS specific slot
        heldItem = ItemStack.EMPTY;
        playSound(ModSounds.SHINY4.get());
    }

    private void placeHeldItemInInventory() {
        if (!heldItem.isEmpty()) {
            sendPickupPayload(-1); // -1 = auto-place in first available slot
            heldItem = ItemStack.EMPTY;
        }
    }

    /** Send ShopPickupPayload to server. targetSlot >= 0 places in that specific slot; -1 = auto. */
    private void sendPickupPayload(int targetSlot) {
        if (heldItem.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(heldItem.getItem()).toString();
        PacketDistributor.sendToServer(new ShopPickupPayload(id, heldItem.getCount(), targetSlot));
    }

    // =========================================================================
    // Network callbacks
    // =========================================================================
    public void onPurchaseResult(ShopPurchaseResultPayload r) {
        purchasePending=false;
        playerMoney=r.newMoney();
        if (!r.success()) { playSound(ModSounds.CANCEL.get()); return; }
        if (isFairStarTokenShop()
                && r.itemId().isEmpty()
                && r.quantity() == 0
                && r.itemIndex() >= 0
                && r.itemIndex() < forSale.size()
                && "stardewcraft:stardrop".equals(forSale.get(r.itemIndex()).itemId())) {
            playSound(ModSounds.PURCHASE_CLICK.get());
            Minecraft.getInstance().setScreen(null);
            return;
        }
        // Update stock
        int idx=r.itemIndex();
        if (idx>=0&&idx<forSale.size()) {
            ShopItemEntry old=forSale.get(idx);
            if (old.stock()!=Integer.MAX_VALUE) {
                forSale.set(idx,new ShopItemEntry(old.itemId(),old.displayName(),old.description(),
                    old.price(),Math.max(0,old.stock()-r.quantity()),old.tradeItemId(),old.tradeItemCount(),
                    old.seasons(),old.minYear(),old.minMineLevel(),old.mailFlag(),
                    old.dayOfWeek(),old.dayOfMonthParity(),old.purchaseStack()));
            }
        }
        if (!r.itemId().isEmpty() && r.quantity() > 0) {
            // Recipe purchases are immediate unlocks — no physical item to hold.
            // Do NOT remove the entry from forSale to keep indices in sync with server.
            // The stock is already set to 0 above, so the item shows as out-of-stock.
            if (r.itemId().startsWith("recipe:")) {
                // nothing else to do — recipe is unlocked server-side,
                // stock=0 prevents re-purchase, rendering greys it out.
            } else {
                ItemStack bought = resolveStack(r.itemId());
                if (!bought.isEmpty()) {
                    bought.setCount(r.quantity());
                    if (heldItem.isEmpty()) {
                        heldItem = bought;
                    } else if (ItemStack.isSameItemSameComponents(heldItem, bought)) {
                        heldItem.grow(bought.getCount());
                    } else {
                        // Different item on cursor — stash old one first
                        placeHeldItemInInventory();
                        heldItem = bought;
                    }
                }
            }
        }
        try { if (ModSounds.COIN!=null) playSound(ModSounds.COIN.get()); } catch(Exception ignored){}

        // SDV parity: Marlon Recovery — after buying 1 item, close shop (all lost items cleared)
        if ("MarlonRecovery".equals(shopId) && r.success()) {
            this.onClose();
        }
    }

    private boolean isFairStarTokenShop() {
        return FAIR_STAR_TOKEN_SHOP_ID.equals(shopId);
    }

    public void onSellResult(ShopSellResultPayload r) {
        if (!r.success()) return;
        playerMoney=r.newMoney();
        playSound(ModSounds.PURCHASE_CLICK.get());
        // Inventory will sync via vanilla; refresh local view
        Minecraft mc=Minecraft.getInstance();
        if (mc.player!=null) mc.player.getInventory().setChanged();
    }

    // =========================================================================
    // Scrollbar
    // =========================================================================
    private void updateScrollBarPosition() {
        if (forSale.size()<=ROWS) { scrBarY=scrRunY; return; }
        int maxIdx=Math.max(1,forSale.size()-ROWS);
        float pct=(float)currentIndex/maxIdx;
        scrBarY=scrRunY+Math.round(pct*Math.max(0,scrRunH-scrBarH));
        if (currentIndex>=forSale.size()-ROWS) scrBarY=scrRunY+scrRunH-scrBarH;
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private int getInvSlotAt(int mx,int my) {
        int sz=slotSzGui, gap=ui(SLOT_GAP);
        for (int r=0;r<INV_ROWS;r++) for (int c=0;c<INV_COLS;c++) {
            int sx=invGridX+c*(sz+gap), sy=invGridY+r*(sz+gap);
            if (isIn(mx,my,sx,sy,sz,sz)) return (r==3)?c:(9+r*9+c);
        }
        return -1;
    }

    private int ui(int sdvPx) { return Math.round(sdvPx/guiScale); }
    private float s4()        { return 4.0f/guiScale; }
    private static boolean isIn(int mx,int my,int x,int y,int w,int h){
        return mx>=x&&mx<x+w&&my>=y&&my<y+h;
    }
    private void playSound(net.minecraft.sounds.SoundEvent ev){
        if (minecraft!=null&&minecraft.player!=null) minecraft.player.playSound(ev,1f,1f);
    }

    @Override public boolean isPauseScreen(){ return false; }
}
