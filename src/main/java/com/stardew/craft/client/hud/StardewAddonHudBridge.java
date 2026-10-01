package com.stardew.craft.client.hud;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.client.StardewHudSnapshot;
import com.stardew.craft.api.v1.client.StardewHudRenderEvent;
import com.stardew.craft.api.v1.internal.client.StardewDailyInfoCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.Optional;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewAddonHudBridge {
    private StardewAddonHudBridge() {}

    public static Optional<StardewHudSnapshot> current() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        return Optional.of(snapshot(StardewTimeHud.isMainHudVisible(), width, height,
                StardewHudLayout.current(width, height)));
    }

    public static StardewHudSnapshot snapshot(boolean visible, int width, int height,
                                              StardewHudLayout.Placement placement) {
        return new StardewHudSnapshot(visible, width, height, placement.scale(),
                new StardewHudSnapshot.Bounds(placement.x(), placement.y(), placement.width(), placement.height()),
                new StardewHudSnapshot.Bounds(placement.x(), placement.y(),
                        StardewHudLayout.TIME_BG_WIDTH * placement.scale(),
                        StardewHudLayout.TIME_BG_HEIGHT * placement.scale()));
    }

    static void renderPost(GuiGraphics graphics, int width, int height, StardewHudLayout.Placement placement) {
        graphics.pose().pushPose();
        try {
            MinecraftForge.EVENT_BUS.post(new StardewHudRenderEvent(graphics, snapshot(true, width, height, placement)));
        } finally {
            graphics.pose().popPose();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        StardewDailyInfoCache.clear();
        com.stardew.craft.api.v1.internal.client.StardewConstructionProgressCache.clear();
    }

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        StardewDailyInfoCache.clear();
        com.stardew.craft.api.v1.internal.client.StardewConstructionProgressCache.clear();
    }
}
