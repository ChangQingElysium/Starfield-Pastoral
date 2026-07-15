package com.stardew.craft.client.deco;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.deco.PaintbrushSelectionManager.Mode;
import com.stardew.craft.item.tool.PaintbrushItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Renders a small mode indicator above the hotbar when holding the paintbrush.
 * Shows current mode (Flood Fill / Region Select) and selection status.
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class PaintbrushModeIndicator {

    private static float displayAlpha = 0f;

    private PaintbrushModeIndicator() {}

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.options.hideGui || player.isSpectator()) return;

        boolean holding = player.getMainHandItem().getItem() instanceof PaintbrushItem
                       || player.getOffhandItem().getItem() instanceof PaintbrushItem;

        // Smooth fade in/out
        displayAlpha = Mth.lerp(0.15f, displayAlpha, holding ? 1.0f : 0.0f);
        if (displayAlpha < 0.02f) return;

        PaintbrushSelectionManager mgr = PaintbrushSelectionManager.get();
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        // Build display text
        Component modeText;
        int modeColor;
        if (mgr.getMode() == Mode.FLOOD_FILL) {
            modeText = Component.translatable("stardewcraft.paintbrush.mode.flood_fill");
            modeColor = 0x88DDFF; // cool blue
        } else {
            modeText = Component.translatable("stardewcraft.paintbrush.mode.region_select");
            modeColor = 0xFFD050; // warm gold
        }

        // Selection status for region mode
        Component statusText = null;
        if (mgr.getMode() == Mode.REGION_SELECT) {
            if (!mgr.hasFirstPos()) {
                statusText = Component.translatable("stardewcraft.paintbrush.status.first_corner");
            } else if (!mgr.hasCompleteSelection()) {
                statusText = Component.translatable("stardewcraft.paintbrush.status.second_corner");
            } else {
                statusText = Component.translatable("stardewcraft.paintbrush.status.ready");
            }
        }

        int alpha = (int)(displayAlpha * 255);
        int screenW = g.guiWidth();

        // Position above hotbar
        int baseY = g.guiHeight() - 72;

        // Mode text
        int modeW = font.width(modeText);
        int modeX = (screenW - modeW) / 2;
        int colorWithAlpha = (alpha << 24) | (modeColor & 0xFFFFFF);
        g.drawString(font, modeText, modeX, baseY, colorWithAlpha, false);

        // Status text (smaller, below mode text)
        if (statusText != null) {
            int statusW = font.width(statusText);
            int statusX = (screenW - statusW) / 2;
            int statusAlpha = (int)(displayAlpha * 180);
            g.drawString(font, statusText, statusX, baseY + 11, (statusAlpha << 24) | 0xCCCCCC, false);
        }

        // Hint text
        Component hint = Component.translatable(
                "stardewcraft.paintbrush.hint.switch_mode",
                Component.translatable("stardewcraft.paintbrush.input.shift_scroll"));
        int hintW = font.width(hint);
        int hintX = (screenW - hintW) / 2;
        int hintAlpha = (int)(displayAlpha * 100);
        g.drawString(font, hint, hintX, baseY - 10, (hintAlpha << 24) | 0x999999, false);
    }
}
