package com.stardew.craft.client.gui;

import com.stardew.craft.client.font.StardewFonts;

import com.stardew.craft.client.gui.common.StardewGuiViewport;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.network.payload.OpenWardrobePayload;
import com.stardew.craft.network.payload.WardrobeActionPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.wardrobe.WardrobeCategory;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public class WardrobeScreen extends Screen {
    private static final int WIN_W = 1080;
    private static final int WIN_H = 680;
    private static final int MAIN_H = WIN_H - 256 + 32 + 4;
    private static final int ROW_H = (WIN_H - 256) / 4 + 4;
    private static final int ROWS = 4;
    private static final int SLOT = 64;
    private static final int GAP = 4;
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 4;
    private static final int TAB_COUNT = 1 + 5;
    private static final int BG_TINT = 0xBF000000;
    private static final long SAFETY_MS = 250;

    private final BlockPos pos;
    private List<ItemStack> items;
    private int currentTab;
    private int currentItemIndex;
    private final long openedAtMs;
    private boolean openSoundPlayed;

    private int panelX;
    private int panelY;
    private int panelW;
    private int mainH;
    private int rowH;
    private int rowW;
    private int invBoxX;
    private int invBoxY;
    private int invBoxW;
    private int invBoxH;
    private int invGridX;
    private int invGridY;
    private int slotSize;
    private int upArrowX;
    private int upArrowY;
    private int upArrowW;
    private int upArrowH;
    private int downArrowX;
    private int downArrowY;
    private int downArrowW;
    private int downArrowH;
    private int scrollBarX;
    private int scrollBarY;
    private int scrollBarW;
    private int scrollBarH;
    private int scrollRunX;
    private int scrollRunY;
    private int scrollRunW;
    private int scrollRunH;
    private int closeX;
    private int closeY;
    private int closeW;
    private int closeH;
    private float closeScale = 1.0f;
    private boolean scrolling;
    private int scrollDragOffsetY;

    public WardrobeScreen(OpenWardrobePayload payload) {
        super(Component.translatable(payload.titleKey()));
        this.pos = payload.pos();
        this.items = copyItems(payload.items());
        this.openedAtMs = System.currentTimeMillis();
    }

    public boolean isFor(BlockPos pos) {
        return this.pos.equals(pos);
    }

    public void updateItems(List<ItemStack> items) {
        this.items = copyItems(items);
        clampScroll();
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();
        if (!openSoundPlayed) {
            playSound(ModSounds.DWOP.get());
            openSoundPlayed = true;
        }
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        clampScroll();
        float s4 = s4();

        graphics.fill(0, 0, width, height, BG_TINT);
        CommonGuiTextures.drawTextureBox(graphics, panelX, panelY, panelW, mainH, s4, true);
        CommonGuiTextures.drawTextureBox(graphics, invBoxX, invBoxY, invBoxW, invBoxH, s4, false);

        drawRows(graphics, mouseX, mouseY, s4);
        drawInventory(graphics, mouseX, mouseY, s4);
        drawTabs(graphics, mouseX, mouseY, s4);
        drawScrollControls(graphics, s4);
        drawCloseButton(graphics, mouseX, mouseY, s4);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private void recalcLayout() {
        float s4 = s4();
        panelW = ui(WIN_W);
        mainH = ui(MAIN_H);
        panelX = width / 2 - panelW / 2;
        panelY = height / 2 - ui(WIN_H) / 2 - ui(32);
        rowH = ui(ROW_H);
        rowW = panelW - ui(32);

        slotSize = ui(SLOT);
        int gap = ui(GAP);
        int gridW = INV_COLS * slotSize + (INV_COLS - 1) * gap;
        int gridH = INV_ROWS * slotSize + (INV_ROWS - 1) * gap;
        invBoxW = gridW + ui(56);
        invBoxH = gridH + ui(44);
        invBoxX = panelX + panelW - invBoxW - ui(8);
        invBoxY = panelY + mainH + ui(8);
        invGridX = invBoxX + ui(20);
        invGridY = invBoxY + ui(16);

        upArrowW = Math.round(11 * s4);
        upArrowH = Math.round(12 * s4);
        upArrowX = panelX + panelW + ui(16);
        upArrowY = panelY + ui(16);
        downArrowW = upArrowW;
        downArrowH = upArrowH;
        downArrowX = upArrowX;
        downArrowY = panelY + mainH - ui(64);

        scrollBarW = Math.round(6 * s4);
        scrollBarH = Math.round(10 * s4);
        scrollRunX = upArrowX + ui(12);
        scrollRunY = upArrowY + upArrowH + ui(4);
        scrollRunW = scrollBarW;
        scrollRunH = downArrowY - scrollRunY - ui(4);
        updateScrollBarPosition();

        closeW = Math.round(12 * s4);
        closeH = Math.round(12 * s4);
        closeX = panelX + panelW - closeW - ui(4);
        closeY = panelY - closeH / 2;
    }

    private void drawRows(GuiGraphics graphics, int mouseX, int mouseY, float s4) {
        List<Integer> visible = visibleIndices();
        if (visible.isEmpty()) {
            GuiText.drawCenteredClamped(graphics, this.font,
                Component.translatable("stardewcraft.wardrobe.empty"),
                panelX + panelW / 2, panelY + mainH / 2 - StardewFonts.lineHeight(font) / 2,
                Math.max(1, panelW - ui(96)), 0x5C2B00, false);
            return;
        }

        for (int row = 0; row < ROWS; row++) {
            int visibleIndex = currentItemIndex + row;
            if (visibleIndex >= visible.size()) {
                break;
            }
            int itemIndex = visible.get(visibleIndex);
            ItemStack stack = items.get(itemIndex);
            int rowX = panelX + ui(16);
            int rowY = panelY + ui(16) + row * rowH;
            boolean hovered = contains(mouseX, mouseY, rowX, rowY, rowW, rowH) && !scrolling;

            if (hovered) {
                graphics.setColor(0.961f, 0.871f, 0.702f, 1.0f);
            }
            CommonGuiTextures.drawEntryBox(graphics, rowX, rowY, rowW, rowH, s4, false);
            if (hovered) {
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            CommonGuiTextures.drawItemSlot18(graphics, rowX + ui(20), rowY + ui(20), s4);
            CommonGuiTextures.drawItemWithDecorations(graphics, this.font, stack, rowX + ui(24), rowY + ui(24), s4);

            Component name = stack.getCount() > 1
                ? Component.literal(stack.getHoverName().getString() + " x" + stack.getCount())
                : stack.getHoverName();
            Component shown = GuiText.ellipsize(this.font, name, Math.max(ui(96), rowW - ui(144)));
            graphics.drawString(this.font, shown, rowX + ui(104), rowY + ui(28), 0x1A1A1A, false);
        }
    }

    private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY, float s4) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int gap = ui(GAP);
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int invSlot = row == 3 ? col : 9 + row * INV_COLS + col;
                int x = invGridX + col * (slotSize + gap);
                int y = invGridY + row * (slotSize + gap);
                ItemStack stack = mc.player.getInventory().getItem(invSlot);
                boolean hovered = contains(mouseX, mouseY, x, y, slotSize, slotSize);

                CommonGuiTextures.drawMenuTile(graphics, x, y, slotSize, slotSize, 10);
                if (hovered) {
                    graphics.fill(x, y, x + slotSize, y + slotSize, 0x35FFFFFF);
                }
                if (!stack.isEmpty()) {
                    if (!WardrobeCategory.isAccepted(stack)) {
                        graphics.setColor(0.62f, 0.62f, 0.62f, 1.0f);
                    }
                    CommonGuiTextures.drawItemWithDecorationsCenteredInBox(graphics, this.font, stack, x, y, slotSize, slotSize, s4);
                    if (!WardrobeCategory.isAccepted(stack)) {
                        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY, float s4) {
        for (int i = 0; i < TAB_COUNT; i++) {
            int x = tabX(i);
            int y = tabY(i);
            boolean hovered = contains(mouseX, mouseY, x, y, ui(64), ui(64));
            float alpha = hovered || i == currentTab ? 1.0f : 0.9f;
            CommonGuiTextures.drawWardrobeTabIconTint(graphics, x, y, i, s4, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private void drawScrollControls(GuiGraphics graphics, float s4) {
        int max = maxScroll();
        if (currentItemIndex > 0) {
            CommonGuiTextures.drawScrollArrowUp(graphics, upArrowX, upArrowY, s4);
        } else {
            CommonGuiTextures.drawScrollArrowUpTint(graphics, upArrowX, upArrowY, s4, 1.0f, 1.0f, 1.0f, 0.4f);
        }
        if (currentItemIndex < max) {
            CommonGuiTextures.drawScrollArrowDown(graphics, downArrowX, downArrowY, s4);
        } else {
            CommonGuiTextures.drawScrollArrowDownTint(graphics, downArrowX, downArrowY, s4, 1.0f, 1.0f, 1.0f, 0.4f);
        }
        if (max > 0) {
            CommonGuiTextures.drawScrollTrackBox(graphics, scrollRunX, scrollRunY, scrollRunW, scrollRunH, s4);
            CommonGuiTextures.drawScrollBarThumb(graphics, scrollBarX, scrollBarY, s4);
        }
    }

    private void drawCloseButton(GuiGraphics graphics, int mouseX, int mouseY, float s4) {
        boolean hovered = contains(mouseX, mouseY, closeX, closeY, closeW, closeH);
        closeScale = hovered ? Math.min(closeScale + 0.04f, 1.2f) : Math.max(1.0f, closeScale - 0.04f);
        float scale = s4 * closeScale;
        int x = closeX + closeW / 2 - Math.round(12 * scale / 2);
        int y = closeY + closeH / 2 - Math.round(12 * scale / 2);
        CommonGuiTextures.drawCloseButton(graphics, x, y, scale);
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int tab = hoveredTab(mouseX, mouseY);
        if (tab >= 0) {
            graphics.renderTooltip(this.font, tabTooltip(tab), mouseX, mouseY);
            return;
        }
        int itemIndex = hoveredWardrobeItem(mouseX, mouseY);
        if (itemIndex >= 0) {
            graphics.renderTooltip(this.font, items.get(itemIndex), mouseX, mouseY);
            return;
        }
        int invSlot = hoveredInventorySlot(mouseX, mouseY);
        if (invSlot >= 0 && Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(invSlot);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (contains(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            onClose();
            return true;
        }
        if (contains(mouseX, mouseY, upArrowX, upArrowY, upArrowW, upArrowH)) {
            if (currentItemIndex > 0) {
                currentItemIndex--;
                updateScrollBarPosition();
                playSound(ModSounds.SHWIP.get());
            }
            return true;
        }
        if (contains(mouseX, mouseY, downArrowX, downArrowY, downArrowW, downArrowH)) {
            if (currentItemIndex < maxScroll()) {
                currentItemIndex++;
                updateScrollBarPosition();
                playSound(ModSounds.SHWIP.get());
            }
            return true;
        }
        if (contains(mouseX, mouseY, scrollBarX, scrollBarY, scrollBarW, scrollBarH)) {
            scrolling = true;
            scrollDragOffsetY = (int) mouseY - scrollBarY;
            return true;
        }
        if (contains(mouseX, mouseY, scrollRunX - ui(16), scrollRunY, ui(40), scrollRunH)) {
            scrolling = true;
            scrollDragOffsetY = scrollBarH / 2;
            scrollToMouse(mouseY);
            return true;
        }
        int tab = hoveredTab(mouseX, mouseY);
        if (tab >= 0) {
            if (currentTab != tab) {
                currentTab = tab;
                currentItemIndex = 0;
                updateScrollBarPosition();
                playSound(ModSounds.SHWIP.get());
            }
            return true;
        }
        if (isOutsideMenu(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (!isPastSafetyTimer()) {
            return true;
        }
        int itemIndex = hoveredWardrobeItem(mouseX, mouseY);
        if (itemIndex >= 0) {
            PacketDistributor.sendToServer(new WardrobeActionPayload(pos, WardrobeActionPayload.ACTION_TAKE_WARDROBE_INDEX, itemIndex));
            if (canPlayerAccept(items.get(itemIndex))) {
                playSound(ModSounds.COIN.get());
            }
            return true;
        }
        int invSlot = hoveredInventorySlot(mouseX, mouseY);
        if (invSlot >= 0 && Minecraft.getInstance().player != null) {
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(invSlot);
            if (WardrobeCategory.isAccepted(stack)) {
                int action = button == 1
                    ? WardrobeActionPayload.ACTION_STORE_ONE_INVENTORY_SLOT
                    : WardrobeActionPayload.ACTION_STORE_INVENTORY_SLOT;
                PacketDistributor.sendToServer(new WardrobeActionPayload(pos, action, invSlot));
                playSound(ModSounds.DWOP.get());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            int next = Math.max(0, Math.min(max, currentItemIndex - (int) Math.signum(scrollY)));
            if (next != currentItemIndex) {
                currentItemIndex = next;
                updateScrollBarPosition();
                playSound(ModSounds.SHINY4.get());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        playSound(ModSounds.DWOP.get());
        super.onClose();
    }

    private int hoveredTab(double mouseX, double mouseY) {
        for (int i = 0; i < TAB_COUNT; i++) {
            if (contains(mouseX, mouseY, tabX(i), tabY(i), ui(64), ui(64))) {
                return i;
            }
        }
        return -1;
    }

    private int hoveredWardrobeItem(double mouseX, double mouseY) {
        List<Integer> visible = visibleIndices();
        for (int row = 0; row < ROWS; row++) {
            int rowX = panelX + ui(16);
            int rowY = panelY + ui(16) + row * rowH;
            if (contains(mouseX, mouseY, rowX, rowY, rowW, rowH)) {
                int visibleIndex = currentItemIndex + row;
                return visibleIndex < visible.size() ? visible.get(visibleIndex) : -1;
            }
        }
        return -1;
    }

    private int hoveredInventorySlot(double mouseX, double mouseY) {
        int gap = ui(GAP);
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int x = invGridX + col * (slotSize + gap);
                int y = invGridY + row * (slotSize + gap);
                if (contains(mouseX, mouseY, x, y, slotSize, slotSize)) {
                    return row == 3 ? col : 9 + row * INV_COLS + col;
                }
            }
        }
        return -1;
    }

    private List<Integer> visibleIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (currentTab == 0 || WardrobeCategory.categoryFor(stack) == categoryForTab(currentTab)) {
                out.add(i);
            }
        }
        out.sort(Comparator
            .comparingInt((Integer i) -> categorySort(items.get(i)))
            .thenComparing(i -> registrySortKey(items.get(i)))
            .thenComparingInt(i -> i));
        return out;
    }

    private WardrobeCategory categoryForTab(int tab) {
        return switch (tab) {
            case 1 -> WardrobeCategory.HATS;
            case 2 -> WardrobeCategory.SHIRTS;
            case 3 -> WardrobeCategory.PANTS;
            case 4 -> WardrobeCategory.SHOES;
            case 5 -> WardrobeCategory.RINGS;
            default -> null;
        };
    }

    private Component tabTooltip(int tab) {
        if (tab == 0) {
            return Component.translatable("stardewcraft.wardrobe.category.all");
        }
        WardrobeCategory category = categoryForTab(tab);
        return Component.translatable(category == null ? "stardewcraft.wardrobe.category.all" : category.translationKey());
    }

    private int categorySort(ItemStack stack) {
        WardrobeCategory category = WardrobeCategory.categoryFor(stack);
        if (category == null) {
            return 99;
        }
        return switch (category) {
            case SHIRTS -> -1000;
            case PANTS -> -999;
            case SHOES -> -97;
            case RINGS -> -96;
            case HATS -> -95;
        };
    }

    private String registrySortKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
    }

    private int maxScroll() {
        return Math.max(0, visibleIndices().size() - ROWS);
    }

    private void clampScroll() {
        currentItemIndex = Math.max(0, Math.min(currentItemIndex, maxScroll()));
        updateScrollBarPosition();
    }

    private void updateScrollBarPosition() {
        scrollBarX = scrollRunX;
        int max = maxScroll();
        if (max <= 0 || scrollRunH <= scrollBarH) {
            scrollBarY = scrollRunY;
            return;
        }
        scrollBarY = scrollRunY + Math.round((scrollRunH - scrollBarH) * (currentItemIndex / (float) max));
    }

    private void scrollToMouse(double mouseY) {
        int max = maxScroll();
        if (max > 0 && scrollRunH > scrollBarH) {
            int previous = currentItemIndex;
            float percentage = ((float) mouseY - scrollDragOffsetY - scrollRunY) / (float) (scrollRunH - scrollBarH);
            currentItemIndex = Math.max(0, Math.min(max, Math.round(max * percentage)));
            updateScrollBarPosition();
            if (currentItemIndex != previous) {
                playSound(ModSounds.SHINY4.get());
            }
        }
    }

    private int tabX(int index) {
        return currentTab == index ? panelX - ui(56) : panelX - ui(64);
    }

    private int tabY(int index) {
        return panelY + ui(16 + index * 64);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isOutsideMenu(double mouseX, double mouseY) {
        return mouseX < panelX - ui(64)
            || mouseY < panelY - ui(64)
            || mouseX > panelX + panelW + ui(128)
            || mouseY > panelY + ui(WIN_H) + ui(64);
    }

    private boolean isPastSafetyTimer() {
        return System.currentTimeMillis() - openedAtMs >= SAFETY_MS;
    }

    private boolean canPlayerAccept(ItemStack stack) {
        if (stack.isEmpty() || Minecraft.getInstance().player == null) {
            return false;
        }
        int remaining = stack.getCount();
        var inventory = Minecraft.getInstance().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack inSlot = inventory.getItem(slot);
            if (inSlot.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), remaining);
            } else if (ItemStack.isSameItemSameComponents(inSlot, stack)) {
                remaining -= Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void playSound(SoundEvent event) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(event, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int ui(int sdvPx) {
        return Math.round(sdvPx / guiScale());
    }

    private float s4() {
        return 4.0f / guiScale();
    }

    private float guiScale() {
        return (float) StardewGuiViewport.REFERENCE_SCALE;
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        return source.stream().map(ItemStack::copy).toList();
    }
}
