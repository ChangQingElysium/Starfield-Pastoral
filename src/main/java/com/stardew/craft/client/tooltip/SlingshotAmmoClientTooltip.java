package com.stardew.craft.client.tooltip;

import com.stardew.craft.tooltip.SlingshotAmmoTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Original MenuTiles slots 43 (empty ammunition) and 10 (occupied). */
public final class SlingshotAmmoClientTooltip implements ClientTooltipComponent {
    private final ItemStack ammo;
    public SlingshotAmmoClientTooltip(SlingshotAmmoTooltip data) { ammo = data.ammunition(); }
    @Override public int getHeight() { return 20; }
    @Override public int getWidth(Font font) { return 18; }
    @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        var texture = new ResourceLocation("stardewcraft",
                "textures/gui/slingshot/" + (ammo.isEmpty() ? "ammo_slot" : "slot") + ".png");
        graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
        if (!ammo.isEmpty()) {
            graphics.renderItem(ammo, x, y);
            graphics.renderItemDecorations(font, ammo, x, y);
        }
    }
}
