package com.stardew.craft.api.v1.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.eventbus.api.Event;

/** CLIENT ONLY, MinecraftForge game event bus. Fired after the visible main HUD and its currency attachment.
 * The pose has been restored to Minecraft GUI coordinates. Balance your own pose/scissor changes.
 * Not fired for hidden HUDs or the layout editor. Do not retain GuiGraphics outside this callback.
 */
public final class StardewHudRenderEvent extends Event {
    private final GuiGraphics graphics;
    private final StardewHudSnapshot hud;

    public StardewHudRenderEvent(GuiGraphics graphics, StardewHudSnapshot hud) {
        this.graphics = graphics;
        this.hud = hud;
    }

    public GuiGraphics graphics() { return graphics; }
    public StardewHudSnapshot hud() { return hud; }
}
