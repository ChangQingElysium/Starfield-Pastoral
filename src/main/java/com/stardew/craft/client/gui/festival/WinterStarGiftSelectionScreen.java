package com.stardew.craft.client.gui.festival;

import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.network.payload.WinterStarGiftSelectionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Direct-click ItemGrabMenu equivalent using the native Minecraft inventory panel. */
@SuppressWarnings("null")
public final class WinterStarGiftSelectionScreen extends Screen {
    private static final ResourceLocation INVENTORY_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 83;
    private static final int INVENTORY_TEXTURE_Y = 83;
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 1;
    private static final int HOTBAR_Y = 59;

    private final String npcId;
    private int leftPos;
    private int topPos;

    public WinterStarGiftSelectionScreen(String npcId, String npcDisplayName) {
        super(Component.translatable("stardewcraft.festival.winter_star.select_gift", npcDisplayName));
        this.npcId = npcId;
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_WIDTH) / 2;
        topPos = (height - PANEL_HEIGHT) / 2 + 8;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately avoid Screen.renderBackground(): it enables the 1.21 blur pass.
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.blit(INVENTORY_TEXTURE, leftPos, topPos,
            0, INVENTORY_TEXTURE_Y, PANEL_WIDTH, PANEL_HEIGHT, 256, 256);
        Component heading = StardewFonts.bannerTitle(title);
        graphics.drawString(font, heading, width / 2 - font.width(heading) / 2, topPos - 18, 0xFFFFFFFF, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                int x = leftPos + INVENTORY_X + col * 18;
                int y = topPos + INVENTORY_Y + row * 18;
                hovered = renderSlot(graphics, slot, x, y, mouseX, mouseY, hovered);
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = leftPos + INVENTORY_X + col * 18;
            int y = topPos + HOTBAR_Y;
            hovered = renderSlot(graphics, col, x, y, mouseX, mouseY, hovered);
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private ItemStack renderSlot(GuiGraphics graphics, int slot, int x, int y,
                                 int mouseX, int mouseY, ItemStack hovered) {
        ItemStack stack = inventoryStack(slot);
        if (stack.isEmpty()) {
            return hovered;
        }
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
        if (!isGiftable(stack)) {
            graphics.fill(x, y, x + 16, y + 16, 0x99000000);
        }
        return inside(mouseX, mouseY, x, y, 16, 16) ? stack : hovered;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                if (submitIfClicked(slot, mouseX, mouseY,
                    leftPos + INVENTORY_X + col * 18,
                    topPos + INVENTORY_Y + row * 18)) {
                    return true;
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            if (submitIfClicked(col, mouseX, mouseY,
                leftPos + INVENTORY_X + col * 18, topPos + HOTBAR_Y)) {
                return true;
            }
        }
        return true;
    }

    private boolean submitIfClicked(int slot, double mouseX, double mouseY, int x, int y) {
        if (!inside(mouseX, mouseY, x, y, 16, 16)) {
            return false;
        }
        ItemStack stack = inventoryStack(slot);
        if (!stack.isEmpty() && isGiftable(stack)) {
            PacketDistributor.sendToServer(new WinterStarGiftSelectionPayload(npcId, slot));
            minecraft.setScreen(null);
        }
        return true;
    }

    private ItemStack inventoryStack(int slot) {
        if (minecraft.player == null || slot < 0 || slot >= minecraft.player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return minecraft.player.getInventory().getItem(slot);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static boolean isGiftable(ItemStack stack) {
        return com.stardew.craft.npc.runtime.NpcInteractionService.canBeGivenAsGift(stack);
    }

    private static boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }
}
