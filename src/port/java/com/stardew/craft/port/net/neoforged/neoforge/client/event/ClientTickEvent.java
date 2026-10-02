package com.stardew.craft.port.net.neoforged.neoforge.client.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * PORT(1.20.1): NeoForge 21.1 client tick event. Bridged from Forge {@code TickEvent.ClientTickEvent}
 * ({@code Phase.START} -> {@link Pre}, {@code Phase.END} -> {@link Post}) by
 * {@code com.stardew.craft.port.event.PortClientEventBridges}. Game bus, client only.
 */
public abstract class ClientTickEvent extends Event {
    protected ClientTickEvent() {}

    /** Fired once per client tick, before the client performs work for the current tick. */
    public static class Pre extends ClientTickEvent {
        public Pre() {}
    }

    /** Fired once per client tick, after the client performs work for the current tick. */
    public static class Post extends ClientTickEvent {
        public Post() {}
    }
}
