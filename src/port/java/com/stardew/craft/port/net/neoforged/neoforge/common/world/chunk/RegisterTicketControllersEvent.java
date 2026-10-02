package com.stardew.craft.port.net.neoforged.neoforge.common.world.chunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * PORT(1.20.1): posted on the mod bus during common setup by {@code PortBootstrap}; registered controllers are
 * installed as one {@link ForgeChunkManager} validation callback per namespace.
 */
public class RegisterTicketControllersEvent extends Event implements IModBusEvent {
    private final Map<ResourceLocation, TicketController> controllers = new LinkedHashMap<>();

    public RegisterTicketControllersEvent() {}

    public synchronized void register(TicketController controller) {
        if (controllers.putIfAbsent(controller.id(), controller) != null) {
            throw new IllegalArgumentException("Duplicate ticket controller " + controller.id());
        }
    }

    /** Installs the Forge callbacks; call on the main thread (enqueued work). */
    public synchronized void apply() {
        Map<String, List<TicketController>> byNamespace = new LinkedHashMap<>();
        controllers.values().forEach(controller -> byNamespace
                .computeIfAbsent(controller.id().getNamespace(), ignored -> new ArrayList<>()).add(controller));
        byNamespace.forEach((namespace, list) -> ForgeChunkManager.setForcedChunkLoadingCallback(namespace, (level, helper) -> {
            for (TicketController controller : list) {
                if (controller.callback() != null) controller.callback().validateTickets(level, helper);
            }
        }));
    }
}
