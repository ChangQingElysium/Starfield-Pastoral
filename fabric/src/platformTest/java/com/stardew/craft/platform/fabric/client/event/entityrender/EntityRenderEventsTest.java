package com.stardew.craft.platform.fabric.client.event.entityrender;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.RenderLivingEvent;
import com.stardew.craft.platform.fabric.client.event.RenderPlayerEvent;
import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityRenderEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test
    void playerAndLivingEventsNestAtTheirNativeBoundariesWithIndependentInheritance() throws Exception {
        var entity = player(); var bus = new EventBus(); var pose = new PoseStack(); List<String> order = new ArrayList<>();
        bus.addListener(RenderPlayerEvent.Pre.class, event -> { assertInstanceOf(PlayerEvent.class, event); assertSame(entity, event.getEntity()); assertSame(pose, event.getPoseStack()); assertEquals(.5f, event.getPartialTick()); assertEquals(123, event.getPackedLight()); order.add("playerPre"); });
        bus.addListener(RenderLivingEvent.Pre.class, event -> { assertSame(Event.class, RenderLivingEvent.class.getSuperclass()); order.add("livingPre"); });
        bus.addListener(RenderLivingEvent.Post.class, event -> order.add("livingPost"));
        bus.addListener(RenderPlayerEvent.Post.class, event -> { assertFalse(event.isCancelable()); order.add("playerPost"); });
        FabricEntityRenderEvents.player(bus, entity, null, .5f, pose, null, 123,
                () -> FabricEntityRenderEvents.<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>living(bus, entity, null, .5f, pose, null, 123, () -> order.add("native")));
        assertEquals(List.of("playerPre", "livingPre", "native", "livingPost", "playerPost"), order);
    }
    @Test
    void canceledPlayerSkipsEverythingWhileCanceledLivingStillReturnsToThePlayerPost() throws Exception {
        var entity = player(); var canceled = new EventBus();
        canceled.addListener(RenderPlayerEvent.Pre.class, event -> event.setCanceled(true));
        canceled.addListener(RenderPlayerEvent.Post.class, event -> fail("player post after player cancellation"));
        FabricEntityRenderEvents.player(canceled, entity, null, 0, new PoseStack(), null, 0, () -> fail("player rendered"));
        var innerCancel = new EventBus(); List<String> order = new ArrayList<>();
        innerCancel.addListener(RenderLivingEvent.Pre.class, event -> { event.setCanceled(true); order.add("livingPre"); });
        innerCancel.addListener(RenderLivingEvent.Post.class, event -> fail("living post after living cancellation"));
        innerCancel.addListener(RenderPlayerEvent.Post.class, event -> order.add("playerPost"));
        FabricEntityRenderEvents.player(innerCancel, entity, null, 0, new PoseStack(), null, 0,
                () -> FabricEntityRenderEvents.<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>living(innerCancel, entity, null, 0, new PoseStack(), null, 0, () -> fail("living rendered")));
        assertEquals(List.of("livingPre", "playerPost"), order);
    }
    private static LocalPlayer player() throws ReflectiveOperationException {
        var unsafe = Class.forName("sun.misc.Unsafe"); var field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (LocalPlayer) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), LocalPlayer.class);
    }
}
