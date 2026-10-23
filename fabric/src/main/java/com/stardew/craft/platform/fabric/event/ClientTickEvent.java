package com.stardew.craft.platform.fabric.event;

/** Client tick markers carry no client object, so their types also link on a server. */
public abstract class ClientTickEvent extends Event {
    public static final class Pre extends ClientTickEvent {
        public Pre() {}
    }

    public static final class Post extends ClientTickEvent {
        public Post() {}
    }
}
