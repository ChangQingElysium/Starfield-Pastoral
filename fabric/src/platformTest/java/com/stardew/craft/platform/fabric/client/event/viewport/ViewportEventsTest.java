package com.stardew.craft.platform.fabric.client.event.viewport;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.shaders.FogShape;
import com.stardew.craft.platform.fabric.client.event.ViewportEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.EventPriority;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.junit.jupiter.api.Test;

class ViewportEventsTest {
    @Test
    void fogPlaneAndShapeChangesRequireCancellation() {
        var bus = new EventBus();
        bus.addListener(ViewportEvent.RenderFog.class, event -> {
            event.setNearPlaneDistance(1); event.setFarPlaneDistance(20); event.setFogShape(FogShape.SPHERE);
        });
        FabricViewportEvents.fog(bus, fog(), (near, far, shape) -> fail("uncanceled fog edits reached shader"));
    }

    @Test
    void canceledFogAppliesAllThreeResolvedShaderValuesAfterScaling() {
        var bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.addListener(ViewportEvent.RenderFog.class, event -> {
            assertEquals(FogRenderer.FogMode.FOG_TERRAIN, event.getMode());
            assertEquals(FogType.NONE, event.getType());
            assertEquals(0.5, event.getPartialTick());
            event.scaleNearPlaneDistance(0.5F); event.scaleFarPlaneDistance(0.25F);
            event.setFogShape(FogShape.SPHERE); event.setCanceled(true); order.add("event");
        });
        FabricViewportEvents.fog(bus, fog(), (near, far, shape) -> {
            assertEquals(4, near); assertEquals(32, far); assertEquals(FogShape.SPHERE, shape); order.add("shader");
        });
        assertEquals(List.of("event", "shader"), order);
    }

    @Test
    void anExplicitLaterUncancelPreservesTheNativeShaderValues() {
        var bus = new EventBus();
        bus.addListener(ViewportEvent.RenderFog.class, event -> { event.setFarPlaneDistance(20); event.setCanceled(true); });
        bus.addListener(EventPriority.LOW, true, ViewportEvent.RenderFog.class, event -> event.setCanceled(false));
        FabricViewportEvents.fog(bus, fog(), (near, far, shape) -> fail("uncanceled fog reached shader"));
        var color = new ViewportEvent.ComputeFogColor(null, null, 0.25F, 0.1F, 0.2F, 0.3F);
        bus.addListener(ViewportEvent.ComputeFogColor.class, event -> {
            event.setRed(0.4F); event.setGreen(0.5F); event.setBlue(0.6F);
        });
        bus.post(color);
        assertEquals(0.4F, color.getRed()); assertEquals(0.5F, color.getGreen()); assertEquals(0.6F, color.getBlue());
        assertFalse(color.isCancelable()); assertThrows(IllegalStateException.class, () -> color.setCanceled(true));
    }

    private static ViewportEvent.RenderFog fog() {
        return new ViewportEvent.RenderFog(null, FogRenderer.FogMode.FOG_TERRAIN, FogType.NONE, null,
                0.5F, 8, 128, FogShape.CYLINDER);
    }
}
