package com.stardew.craft.platform.fabric.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mirrors FML 4.0.42 / NeoForge 21.1's actual IModBusEvent declarations. */
class ModBusEventHierarchyTest {
    private static final String BASE = "com.stardew.craft.platform.fabric.";

    @Test
    void registrationAndSetupEventsUseTheModMarkerIncludingInheritedNestedTypes() throws Exception {
        for (String name : List.of(
                "capability.RegisterCapabilitiesEvent",
                "client.event.EntityRenderersEvent", "client.event.EntityRenderersEvent$RegisterLayerDefinitions",
                "client.event.EntityRenderersEvent$RegisterRenderers", "client.event.EntityRenderersEvent$AddLayers",
                "client.event.RegisterColorHandlersEvent", "client.event.RegisterColorHandlersEvent$Block",
                "client.event.RegisterColorHandlersEvent$Item", "client.event.RegisterClientReloadListenersEvent",
                "client.event.RegisterClientTooltipComponentFactoriesEvent", "client.event.RegisterDimensionSpecialEffectsEvent",
                "client.event.RegisterItemDecorationsEvent", "client.event.RegisterKeyMappingsEvent",
                "client.event.RegisterMenuScreensEvent", "client.event.RegisterParticleProvidersEvent",
                "client.event.RegisterRenderBuffersEvent", "client.event.TextureAtlasStitchedEvent",
                "client.extension.RegisterClientExtensionsEvent", "chunk.RegisterTicketControllersEvent",
                "lifecycle.EntityAttributeCreationEvent", "lifecycle.SetupEvent", "lifecycle.FMLCommonSetupEvent",
                "lifecycle.FMLClientSetupEvent", "network.RegisterPayloadHandlersEvent",
                "network.RegisterConfigurationTasksEvent", "datamap.RegisterDataMapTypesEvent",
                "registry.RegistryRegistrationEvent")) {
            assertTrue(ModBusEvent.class.isAssignableFrom(type(name)), name);
        }
    }

    @Test
    void onlyModelEventConcreteRegistrationAndBakingTypesCarryTheMarker() throws Exception {
        assertFalse(ModBusEvent.class.isAssignableFrom(type("client.model.ModelEvent")));
        for (String nested : List.of("ModifyBakingResult", "BakingCompleted", "RegisterAdditional", "RegisterGeometryLoaders")) {
            assertTrue(ModBusEvent.class.isAssignableFrom(type("client.model.ModelEvent$" + nested)), nested);
        }
        assertFalse(ModBusEvent.class.isAssignableFrom(type("client.event.RenderLevelStageEvent")));
        assertFalse(ModBusEvent.class.isAssignableFrom(type("event.Event")));
        assertFalse(ModBusEvent.class.isAssignableFrom(type("event.LevelEvent")));
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(BASE + name, false, ModBusEventHierarchyTest.class.getClassLoader());
    }
}
