package com.stardew.craft.platform.fabric.client.event.tooltip;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.RenderHandEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PresentationEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void selectedHandsAreIndependentlyCancelableWithActualInterpolatedParameters() {
        var bus = new EventBus();
        List<String> order = new ArrayList<>();
        var pose = new PoseStack();
        bus.addListener(RenderHandEvent.class, event -> {
            assertSame(pose, event.getPoseStack()); assertEquals(123, event.getPackedLight());
            assertEquals(0.3F, event.getPartialTick()); assertEquals(12F, event.getInterpolatedPitch());
            assertEquals(0.2F, event.getSwingProgress()); assertEquals(0.7F, event.getEquipProgress());
            order.add("event:" + event.getHand());
            if (event.getHand() == InteractionHand.MAIN_HAND) event.setCanceled(true);
        });
        for (var hand : InteractionHand.values()) FabricPresentationEvents.hand(bus,
                new RenderHandEvent(hand, pose, null, 123, 0.3F, 12, 0.2F, 0.7F, ItemStack.EMPTY),
                () -> order.add("render:" + hand));
        assertEquals(List.of("event:MAIN_HAND", "event:OFF_HAND", "render:OFF_HAND"), order);
    }
}
