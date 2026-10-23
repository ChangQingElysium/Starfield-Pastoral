package com.stardew.craft.platform.fabric.client.render;

import com.stardew.craft.platform.fabric.client.event.RenderGuiLayerEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public final class FabricGuiLayers {
    private static IEventBus installedBus;
    private FabricGuiLayers() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("GUI layer events already have an installed bus");
        installedBus = bus;
    }

    public static List<LayeredDraw.Layer> expandLowerLayers(List<LayeredDraw.Layer> nativeLayers,
            BooleanSupplier visible, BooleanSupplier canHurtPlayer, List<LayeredDraw.Layer> components) {
        if (installedBus == null) throw new IllegalStateException("GUI layer event bus is not installed");
        return expandLowerLayers(installedBus, nativeLayers, visible, canHurtPlayer, components);
    }

    static List<LayeredDraw.Layer> expandLowerLayers(IEventBus bus, List<LayeredDraw.Layer> nativeLayers,
            BooleanSupplier visible, BooleanSupplier canHurtPlayer, List<LayeredDraw.Layer> components) {
        if (nativeLayers.size() != 6 || components.size() != 10) throw new IllegalStateException("Unexpected 1.21.1 lower GUI layer layout");
        List<LayeredDraw.Layer> result = new ArrayList<>(15);
        result.add(guarded(nativeLayers.get(0), visible)); // camera overlays: original callback
        result.add(guarded(nativeLayers.get(1), visible)); // crosshair: original callback
        for (int i = 0; i < components.size(); i++) {
            final int slot = i;
            LayeredDraw.Layer body = guarded(components.get(i), () -> visible.getAsBoolean()
                    && ((slot != 3 && slot != 4 && slot != 5 && slot != 7) || canHurtPlayer.getAsBoolean()));
            ResourceLocation name = switch (i) {
                case 0 -> VanillaGuiLayers.HOTBAR;
                case 3 -> VanillaGuiLayers.PLAYER_HEALTH;
                case 5 -> VanillaGuiLayers.FOOD_LEVEL;
                default -> null;
            };
            result.add(name == null ? body : named(bus, name, body));
        }
        for (int i = 3; i < nativeLayers.size(); i++) result.add(guarded(nativeLayers.get(i), visible));
        return List.copyOf(result);
    }

    private static LayeredDraw.Layer guarded(LayeredDraw.Layer body, BooleanSupplier predicate) {
        return (graphics, partialTick) -> { if (predicate.getAsBoolean()) body.render(graphics, partialTick); };
    }

    private static LayeredDraw.Layer named(IEventBus bus, ResourceLocation name, LayeredDraw.Layer body) {
        return (graphics, partialTick) -> {
            if (bus.post(new RenderGuiLayerEvent.Pre(graphics, partialTick, name, body)).isCanceled()) return;
            body.render(graphics, partialTick);
            bus.post(new RenderGuiLayerEvent.Post(graphics, partialTick, name, body));
        };
    }
}
