package com.stardew.craft.platform.fabric.client.event.registration;

import com.stardew.craft.platform.fabric.client.event.ClientChatReceivedEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class FabricSystemChatEvents {
    private static IEventBus installedBus;
    private FabricSystemChatEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus"); if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("System chat bus already installed"); installedBus = bus;
    }
    @Nullable public static Component receive(Component message, boolean overlay) {
        if (installedBus == null) throw new IllegalStateException("System chat bus is not installed"); return receive(installedBus, message, overlay);
    }
    @Nullable public static Component receive(IEventBus bus, Component message, boolean overlay) {
        var event = bus.post(new ClientChatReceivedEvent.System(message, overlay)); return event.isCanceled() ? null : event.getMessage();
    }
}
