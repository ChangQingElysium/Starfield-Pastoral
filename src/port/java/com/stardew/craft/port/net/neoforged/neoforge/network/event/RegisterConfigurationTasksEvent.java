package com.stardew.craft.port.net.neoforged.neoforge.network.event;

import com.stardew.craft.port.net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import com.stardew.craft.port.net.minecraft.server.network.ConfigurationTask;
import com.stardew.craft.port.net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge configuration-task event. Forge 1.20.1 has no configuration phase: this event is posted
 * on the StardewCraft mod bus once per incoming modded connection, while Forge gathers the login
 * handshake messages, and every registered task runs as part of that handshake (the player cannot
 * join until each task's client reply was handled). See {@code PortLoginTasks}.
 */
public class RegisterConfigurationTasksEvent extends Event implements IModBusEvent {
    private final ServerConfigurationPacketListener listener;
    private final List<ConfigurationTask> tasks = new ArrayList<>();

    public RegisterConfigurationTasksEvent(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }

    public void register(ConfigurationTask task) {
        if (!(task instanceof ICustomConfigurationTask)) {
            throw new UnsupportedOperationException("PORT(1.20.1): only ICustomConfigurationTask can run in the Forge login "
                    + "handshake; got " + task.type());
        }
        this.tasks.add(task);
    }

    public ServerConfigurationPacketListener getListener() {
        return this.listener;
    }

    public List<ConfigurationTask> getConfigurationTasks() {
        return Collections.unmodifiableList(this.tasks);
    }
}
