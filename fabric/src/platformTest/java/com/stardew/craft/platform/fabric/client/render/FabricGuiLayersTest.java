package com.stardew.craft.platform.fabric.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.client.event.RenderGuiLayerEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.LayeredDraw;
import org.junit.jupiter.api.Test;

class FabricGuiLayersTest {
    @Test
    void lowerLayersKeepFiveNativeCallbacksAndExpandTheCompositeInActualNeoOrder() {
        var bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            assertSame(DeltaTracker.ONE, event.getPartialTick());
            assertNotNull(event.getLayer());
            order.add("pre:" + event.getName().getPath());
        });
        bus.addListener(RenderGuiLayerEvent.Post.class, event -> {
            assertFalse(event.isCancelable());
            order.add("post:" + event.getName().getPath());
        });
        var layers = layers(bus, order, true, true);
        assertEquals(15, layers.size());
        layers.forEach(layer -> layer.render(null, DeltaTracker.ONE));
        assertEquals(List.of("native:0", "native:1", "pre:hotbar", "component:0", "post:hotbar",
                "component:1", "component:2", "pre:player_health", "component:3", "post:player_health",
                "component:4", "pre:food_level", "component:5", "post:food_level", "component:6",
                "component:7", "component:8", "component:9", "native:3", "native:4", "native:5"), order);
    }

    @Test
    void hiddenAndCreativeLayersStillDispatchEventsWhileEveryNativePredicateIsRetained() {
        var bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.addListener(RenderGuiLayerEvent.Pre.class, event -> order.add("pre:" + event.getName().getPath()));
        bus.addListener(RenderGuiLayerEvent.Post.class, event -> order.add("post:" + event.getName().getPath()));
        layers(bus, order, false, false).forEach(layer -> layer.render(null, DeltaTracker.ONE));
        assertEquals(List.of("pre:hotbar", "post:hotbar", "pre:player_health", "post:player_health", "pre:food_level", "post:food_level"), order);
        order.clear();
        layers(bus, order, true, false).forEach(layer -> layer.render(null, DeltaTracker.ONE));
        assertFalse(order.contains("component:3"));
        assertFalse(order.contains("component:4"));
        assertFalse(order.contains("component:5"));
        assertFalse(order.contains("component:7"));
        assertTrue(order.contains("component:6")); // vehicle does not have the survival-only predicate
        assertTrue(order.contains("post:food_level"));
    }

    @Test
    void canceledRowsReserveHeightBeforeArmorVehicleAirAndNameWithoutFiringTheirPost() {
        var bus = new EventBus();
        var heights = new GuiHudLayout.State();
        List<String> order = new ArrayList<>();
        bus.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) { heights.addLeft(10); event.setCanceled(true); }
            if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)) { heights.addRight(10); event.setCanceled(true); }
        });
        bus.addListener(RenderGuiLayerEvent.Post.class, event -> {
            assertEquals(VanillaGuiLayers.HOTBAR, event.getName());
            order.add("hotbar-post");
        });
        var nativeLayers = callbacks("native", 6, new ArrayList<>());
        var components = new ArrayList<>(callbacks("component", 10, order));
        components.set(3, (graphics, tick) -> fail("canceled health rendered"));
        components.set(4, (graphics, tick) -> { assertEquals(49, heights.left()); heights.addLeft(10); });
        // This layer must still exist while riding, even though native food rendering would skip.
        components.set(5, (graphics, tick) -> fail("canceled/riding food rendered"));
        components.set(6, (graphics, tick) -> {
            assertEquals(49, heights.right());
            assertEquals(3, heights.addVehicleRows(21));
        });
        components.set(7, (graphics, tick) -> { assertEquals(79, heights.right()); heights.addRight(10); });
        components.set(8, (graphics, tick) -> assertEquals(89, heights.selectedItemHeight()));
        FabricGuiLayers.expandLowerLayers(bus, nativeLayers, () -> true, () -> true, components)
                .forEach(layer -> layer.render(null, DeltaTracker.ONE));
        assertEquals(59, heights.left());
        assertEquals(89, heights.right());
        heights.reset();
        assertEquals(39, heights.left());
        assertEquals(39, heights.right());
        assertEquals(59, heights.selectedItemHeight());
    }

    @Test
    void visibilityIsEvaluatedInsideTheNamedLayerAfterPreAndCannotBeBypassedThroughGetLayer() {
        var bus = new EventBus();
        var visible = new AtomicBoolean(false);
        List<String> order = new ArrayList<>();
        bus.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            event.getLayer().render(null, DeltaTracker.ONE);
            assertTrue(order.isEmpty());
        });
        FabricGuiLayers.expandLowerLayers(bus, callbacks("native", 6, order), visible::get, () -> true,
                callbacks("component", 10, order)).forEach(layer -> layer.render(null, DeltaTracker.ONE));
        assertTrue(order.isEmpty());
        assertThrows(IllegalStateException.class, () -> FabricGuiLayers.expandLowerLayers(bus, List.of(), () -> true, () -> true, List.of()));
    }

    private static List<LayeredDraw.Layer> layers(EventBus bus, List<String> order, boolean visible, boolean canHurt) {
        return FabricGuiLayers.expandLowerLayers(bus, callbacks("native", 6, order), () -> visible,
                () -> canHurt, callbacks("component", 10, order));
    }

    private static List<LayeredDraw.Layer> callbacks(String prefix, int count, List<String> order) {
        List<LayeredDraw.Layer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = prefix + ":" + i;
            result.add((graphics, tick) -> order.add(name));
        }
        return result;
    }
}
