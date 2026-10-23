package com.stardew.craft.platform.fabric.client.compat;

import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import squeek.appleskin.api.event.HUDOverlayEvent;

/** Loaded only from the client's isModLoaded("appleskin") branch. */
@Environment(EnvType.CLIENT)
public final class FabricAppleSkinEvents {
    private static boolean installed;
    private FabricAppleSkinEvents() {}
    public static synchronized void install(Consumer<HUDOverlayEvent.Saturation> saturation, Consumer<HUDOverlayEvent.Exhaustion> exhaustion,
            Consumer<HUDOverlayEvent.HungerRestored> hunger, Consumer<HUDOverlayEvent.HealthRestored> health) {
        Objects.requireNonNull(saturation); Objects.requireNonNull(exhaustion); Objects.requireNonNull(hunger); Objects.requireNonNull(health);
        if (installed) throw new IllegalStateException("AppleSkin overlay callbacks already installed");
        HUDOverlayEvent.Saturation.EVENT.register(saturation::accept);
        HUDOverlayEvent.Exhaustion.EVENT.register(exhaustion::accept);
        HUDOverlayEvent.HungerRestored.EVENT.register(hunger::accept);
        HUDOverlayEvent.HealthRestored.EVENT.register(health::accept);
        installed = true;
    }
    public static void setCanceled(HUDOverlayEvent event, boolean canceled) { event.isCanceled = canceled; }
}
