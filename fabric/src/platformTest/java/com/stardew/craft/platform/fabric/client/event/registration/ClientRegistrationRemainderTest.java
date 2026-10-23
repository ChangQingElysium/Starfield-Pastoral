package com.stardew.craft.platform.fabric.client.event.registration;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.stardew.craft.platform.fabric.client.event.ClientChatReceivedEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterDimensionSpecialEffectsEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterRenderBuffersEvent;
import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClientRegistrationRemainderTest {
    @Test
    void dimensionRegistrationsReplaceTheActualLookupEntryWithTheLastEffect() {
        var id = ResourceLocation.withDefaultNamespace("overworld");
        var map = new HashMap<ResourceLocation, DimensionSpecialEffects>();
        var first = new DimensionSpecialEffects.OverworldEffects(); var replacement = new DimensionSpecialEffects.EndEffects();
        var event = new RegisterDimensionSpecialEffectsEvent(map);
        event.register(id, first); event.register(id, replacement);
        assertSame(replacement, map.get(id)); assertFalse(event.isCancelable());
    }
    @Test
    void bufferRegistrationKeepsInsertionOrderAndRejectsDuplicatesWithoutReplacingTheOwnedBuffer() {
        var lines = RenderType.lines(); var strip = RenderType.lineStrip();
        var buffers = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        try (var first = new ByteBufferBuilder(64); var second = new ByteBufferBuilder(32)) {
            var event = new RegisterRenderBuffersEvent(buffers); event.registerRenderBuffer(lines, first); event.registerRenderBuffer(strip, second);
            assertEquals(List.of(lines, strip), List.copyOf(buffers.sequencedKeySet()));
            assertThrows(IllegalStateException.class, () -> event.registerRenderBuffer(lines, second)); assertSame(first, buffers.get(lines));
            assertFalse(event.isCancelable());
        }
    }
    @Test
    void systemChatSupportsBothOverlayTargetsMutableMessagesParentSubscribersAndCancellation() {
        var original = Component.translatable("stardewcraft.test"); var changed = Component.literal("changed");
        for (boolean overlay : new boolean[] { false, true }) {
            var bus = new EventBus();
            bus.addListener(ClientChatReceivedEvent.class, event -> {
                assertTrue(event.isSystem()); assertNull(event.getBoundChatType()); assertSame(original, event.getMessage()); event.setMessage(changed);
            });
            bus.addListener(ClientChatReceivedEvent.System.class, event -> { assertEquals(overlay, event.isOverlay()); assertSame(changed, event.getMessage()); });
            assertSame(changed, FabricSystemChatEvents.receive(bus, original, overlay));
            var canceled = new EventBus(); canceled.addListener(ClientChatReceivedEvent.System.class, event -> event.setCanceled(true));
            assertNull(FabricSystemChatEvents.receive(canceled, original, overlay));
        }
    }
}
