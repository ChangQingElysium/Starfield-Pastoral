package com.stardew.craft.port;

import com.stardew.craft.port.net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredHolder;
import com.stardew.craft.port.net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.stardew.craft.port.net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * PORT(1.20.1): wires the registry/attachment/capability/data-map/ticket shims into Forge.
 * <p>
 * Installed (once per mod bus) by the first {@code DeferredRegister.register(IEventBus)} call, which every NeoForge
 * mod entry point performs with its mod bus; entry code may also call {@link #install(IEventBus)} directly.
 * NeoForge fires {@code RegisterCapabilitiesEvent}, {@code RegisterTicketControllersEvent} and
 * {@code RegisterDataMapTypesEvent} on the mod bus during loading; here they are posted to the mod bus during
 * common setup, before any world, block entity or chunk exists.
 */
public final class PortBootstrap {
    public static final String NAMESPACE = "stardewcraft";
    private static final Set<IEventBus> INSTALLED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean forgeBusInstalled;

    private PortBootstrap() {}

    public static synchronized void install(IEventBus modBus) {
        if (!INSTALLED.add(modBus)) return;
        PortParticles.register(modBus); // vanilla-API owner: 1.21-only particle types (coloured entity_effect)
        PortAttributes.register(modBus); // 1.21-only entity attributes (step_height, scale)
        PortEnchantments.register(modBus); // 1.21 data-driven enchantments registered in code
        PortLivingAttributeDefaults.register(modBus); // 1.21.1 createLivingAttributes includes ATTACK_KNOCKBACK
        // Run before the mod's own common-setup listeners, mirroring NeoForge firing these events earlier.
        modBus.addListener(EventPriority.HIGHEST, (FMLCommonSetupEvent event) -> {
            modBus.post(new RegisterDataMapTypesEvent());
            modBus.post(new RegisterCapabilitiesEvent());
            RegisterTicketControllersEvent tickets = new RegisterTicketControllersEvent();
            modBus.post(tickets);
            event.enqueueWork(tickets::apply);
            PortAttachments.resolveAll();
        });
        if (!forgeBusInstalled) {
            forgeBusInstalled = true;
            PortNetwork.init();
            MinecraftForge.EVENT_BUS.register(PortAttachments.class);
            MinecraftForge.EVENT_BUS.register(PortCapabilities.class);
            MinecraftForge.EVENT_BUS.register(PortAuxLight.class);
            MinecraftForge.EVENT_BUS.register(PortDataMaps.class);
        }
    }

    public static boolean isPortOnlyRegistry(ResourceKey<? extends Registry<?>> key) {
        return key.location().equals(NeoForgeRegistries.Keys.ATTACHMENT_TYPES.location());
    }

    public static void trackPortOnlyEntry(ResourceKey<? extends Registry<?>> registry, ResourceLocation id, DeferredHolder<?, ?> holder) {
        if (registry.location().equals(NeoForgeRegistries.Keys.ATTACHMENT_TYPES.location())) {
            PortAttachments.track(id, holder);
        } else {
            throw new IllegalStateException("Unknown port-only registry " + registry.location());
        }
    }
}
