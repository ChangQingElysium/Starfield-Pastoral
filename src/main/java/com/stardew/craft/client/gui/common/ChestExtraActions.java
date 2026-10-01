package com.stardew.craft.client.gui.common;

import com.stardew.craft.inventory.ChestMenuActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.ArrayList;
import java.util.List;

/** Additional SDV container actions share the same rail, cursor and input coordinates. */
@OnlyIn(Dist.CLIENT)
public final class ChestExtraActions {
    private final AbstractContainerMenu menu;
    private final TrashCanWidget.Controller trashCan = new TrashCanWidget.Controller();
    private int x, fillY;
    private boolean enabled, allowNote;
    public ChestExtraActions(AbstractContainerMenu menu) { this.menu = menu; }
    public void layout(int x, int organizeY, boolean enabled, boolean allowNote) {
        this.x = x; this.fillY = organizeY + 24; this.enabled = enabled; this.allowNote = allowNote;
    }
    private boolean noteVisible() {
        if (!allowNote) return false;
        var data = com.stardew.craft.communitycenter.network.BundleClientData.INSTANCE;
        if (!data.canReadJunimoText() || com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag("JojaMember")) return false;
        for (int area = 0; area < 6; area++) if (!data.isAreaComplete(area)) return true;
        return false;
    }
    private int trashY() { return fillY + (noteVisible() ? 48 : 24); }
    private boolean hit(double mx, double my, int y, int height) { return mx >= x && mx < x + 18 && my >= y && my < y + height; }
    public void render(GuiGraphics g, int mx, int my) {
        if (!enabled) return;
        icon(g, "chest_fill_stacks", x + 1, fillY + 1, 16, 16);
        if (hit(mx, my, fillY, 18)) g.fill(x - 1, fillY - 1, x + 19, fillY + 19, 0x30FFFFFF);
        if (noteVisible()) icon(g, "junimo_note_icon", x + 1, fillY + 25, 15, 14);
        trashCan.render(g, x, trashY(), mx, my);
    }
    public void tooltip(GuiGraphics g, Font font, int mx, int my) {
        if (!enabled) return;
        String key = hit(mx, my, fillY, 18) ? "stardewcraft.chest.fill_stacks"
                : noteVisible() && hit(mx, my, fillY + 24, 18) ? "stardewcraft.bundle.viewer" : null;
        if (key != null) {
            g.renderTooltip(font, Component.translatable(key), mx, my);
        } else {
            trashCan.renderTooltip(g, font, menu, x, trashY(), mx, my);
        }
    }
    public boolean click(double mx, double my, int button) {
        if (!enabled || button != 0) return false;
        var client = Minecraft.getInstance();
        if (hit(mx, my, fillY, 18)) {
            if (menu.getCarried().isEmpty() && client.gameMode != null) {
                client.gameMode.handleInventoryButtonClick(menu.containerId, ChestMenuActions.FILL_STACKS);
                client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.stardew.craft.sound.ModSounds.SHIP.get(), 1F));
            }
            return true;
        }
        if (noteVisible() && hit(mx, my, fillY + 24, 18)) {
            if (menu.getCarried().isEmpty()) com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.stardew.craft.communitycenter.network.OpenBundleViewerPayload());
            return true;
        }
        return trashCan.click(menu, x, trashY(), mx, my, button);
    }
    public boolean keyPressed(int keyCode) {
        return enabled && trashCan.deleteKey(menu, keyCode);
    }
    public List<Rect2i> bounds() {
        if (!enabled) return List.of();
        var result = new ArrayList<Rect2i>();
        result.add(new Rect2i(x - 1, fillY - 1, 20, 20));
        if (noteVisible()) result.add(new Rect2i(x, fillY + 24, 18, 18));
        result.add(new Rect2i(x - 4, trashY() - 8, 27, 44));
        return result;
    }
    private static void icon(GuiGraphics g, String name, int x, int y, int width, int height) {
        g.blit(new ResourceLocation("stardewcraft", "textures/gui/" + name + ".png"), x, y, 0, 0, width, height, width, height);
    }
}
