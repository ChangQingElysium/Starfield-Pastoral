package com.stardew.craft.port.net.neoforged.neoforge.event.tick;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 server tick event, bridged from Forge {@code TickEvent.ServerTickEvent}
 * ({@code START} -> {@link Pre}, {@code END} -> {@link Post}).
 */
public abstract class ServerTickEvent extends Event {
    private final BooleanSupplier hasTime;
    private final MinecraftServer server;

    protected ServerTickEvent(BooleanSupplier hasTime, MinecraftServer server) {
        this.hasTime = hasTime;
        this.server = server;
    }

    /** {@return true if the server has enough time to perform any additional tasks during this tick} */
    public boolean hasTime() {
        return this.hasTime.getAsBoolean();
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public static class Pre extends ServerTickEvent {
        public Pre() {
            this(() -> false, null);
        }

        public Pre(BooleanSupplier hasTime, MinecraftServer server) {
            super(hasTime, server);
        }
    }

    public static class Post extends ServerTickEvent {
        public Post() {
            this(() -> false, null);
        }

        public Post(BooleanSupplier hasTime, MinecraftServer server) {
            super(hasTime, server);
        }
    }
}
