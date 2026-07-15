package com.stardew.craft.client.gui.festival;

import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.overnight.StardewGuiUtil;
import com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload;
import com.stardew.craft.network.payload.WinterStarRecipientThanksClosedPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Forced overflow menu: rearrange/drop inventory, then collect the queued gift. */
@SuppressWarnings("null")
public final class WinterStarOverflowGiftScreen extends Screen {
    private static final int SLOT = 36;
    private static final int GAP = 4;
    private final ItemStack reward;
    private int panelX, panelY, gridX, gridY, claimX, claimY;

    public WinterStarOverflowGiftScreen(String itemId, int count) {
        super(Component.translatable("stardewcraft.festival.winter_star.inventory_full"));
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        this.reward = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    @Override protected void init() {
        int panelW = 9 * SLOT + 8 * GAP + 64;
        int panelH = 4 * SLOT + 3 * GAP + 154;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridX = panelX + 32;
        gridY = panelY + 98;
        claimX = panelX + panelW - 116;
        claimY = panelY + 38;
    }

    private static int slotIndex(int row, int col) { return row == 3 ? col : 9 + row * 9 + col; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        int panelW = 9 * SLOT + 8 * GAP + 64;
        int panelH = 4 * SLOT + 3 * GAP + 154;
        StardewGuiUtil.drawDialogueBoxFrame(g, panelX, panelY, panelW, panelH);
        g.drawString(font, title, panelX + 32, panelY + 28, 0xFF4B2A12, false);
        CommonGuiTextures.drawItemSlot18(g, panelX + 34, panelY + 50, 2.0f);
        if (!reward.isEmpty()) {
            g.renderItem(reward, panelX + 44, panelY + 60);
            g.renderItemDecorations(font, reward, panelX + 44, panelY + 60);
        }
        CommonGuiTextures.drawBillboardAcceptBox(g, claimX, claimY, 84, 34, 1.0f);
        Component collect = Component.translatable("stardewcraft.festival.winter_star.collect");
        g.drawString(font, collect, claimX + 42 - font.width(collect) / 2, claimY + 12, 0xFF4B2A12, false);

        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slot = slotIndex(row, col), x = gridX + col * (SLOT + GAP);
            int y = gridY + row * (SLOT + GAP) + (row == 3 ? 8 : 0);
            ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
            CommonGuiTextures.drawItemSlot18(g, x, y, 2.0f);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 10, y + 10); g.renderItemDecorations(font, stack, x + 10, y + 10);
                if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) hovered = stack;
            }
        }
        if (minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty()) {
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            g.renderItem(carried, mouseX - 8, mouseY - 8); g.renderItemDecorations(font, carried, mouseX - 8, mouseY - 8);
        } else if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, claimX, claimY, 84, 34)) {
            PacketDistributor.sendToServer(new WinterStarRecipientThanksClosedPayload());
            return true;
        }
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++) {
            int slot = slotIndex(row, col), x = gridX + col * (SLOT + GAP);
            int y = gridY + row * (SLOT + GAP) + (row == 3 ? 8 : 0);
            if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
                PacketDistributor.sendToServer(new CraftingMenuInventoryActionPayload(
                    CraftingMenuInventoryActionPayload.ACTION_CLICK_SLOT, slot, button == 1));
                return true;
            }
        }
        if (minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty()) {
            PacketDistributor.sendToServer(new CraftingMenuInventoryActionPayload(
                CraftingMenuInventoryActionPayload.ACTION_DROP_CARRIED, -1, false));
        }
        return true;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    private static boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }
}
