package com.stardew.craft.platform.fabric.client.widget;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.Test;

class SlotHighlightsTest {
    @Test
    void customVirtualOverrideReceivesTheOriginalSlotAndMouseCoordinatesAndCanCallSuperAtItsOwnPose() {
        var slot = new Slot(new SimpleContainer(1), 0, 8, 15); List<String> order = new ArrayList<>();
        SlotHighlightProvider provider = (graphics, received, x, y, partial) -> {
            assertSame(slot, received); assertEquals(101, x); assertEquals(92, y); assertEquals(.35F, partial);
            order.add("push/translate/scale");
            SlotHighlights.renderDefault((screen, g, s) -> { assertSame(slot, s); order.add("seasonal"); return false; }, null, graphics, slot, () -> order.add("native"));
            order.add("pop");
        };
        SlotHighlights.render(provider, null, slot, 101, 92, .35F, () -> fail());
        assertEquals(List.of("push/translate/scale", "seasonal", "native", "pop"), order);
    }
    @Test
    void seasonalCancellationAndNonhighlightableSlotsSuppressOnlyTheDefaultDrawing() {
        var slot = new Slot(new SimpleContainer(1), 0, 8, 15) { @Override public boolean isHighlightable() { return false; } };
        List<String> order = new ArrayList<>();
        SlotHighlights.renderDefault((screen, graphics, value) -> { order.add("seasonal"); return false; }, null, null, slot, () -> fail());
        SlotHighlights.renderDefault((screen, graphics, value) -> { order.add("seasonal cancel"); return true; }, null, null, slot, () -> fail());
        SlotHighlights.render(null, null, slot, 1, 2, 0, () -> order.add("default virtual"));
        assertEquals(List.of("seasonal", "seasonal cancel", "default virtual"), order);
    }
}
