package com.stardew.craft.event;

import com.stardew.craft.world.interaction.MapInteractionEvents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapInteractionPriorityTest {
    @Test
    void debugCapturePrecedesMapInteractionAndMapPrecedesBlockUse()
            throws NoSuchMethodException {
        SubscribeEvent debug = MapInteractionPointWandEvents.class
                .getDeclaredMethod(
                        "onRightClickBlock",
                        net.neoforged.neoforge.event.entity.player
                                .PlayerInteractEvent.RightClickBlock.class)
                .getAnnotation(SubscribeEvent.class);
        SubscribeEvent map = MapInteractionEvents.class
                .getDeclaredMethod(
                        "onRightClickBlock",
                        net.neoforged.neoforge.event.entity.player
                                .PlayerInteractEvent.RightClickBlock.class)
                .getAnnotation(SubscribeEvent.class);

        assertEquals(EventPriority.HIGHEST, debug.priority());
        assertEquals(EventPriority.HIGH, map.priority());
    }
}
