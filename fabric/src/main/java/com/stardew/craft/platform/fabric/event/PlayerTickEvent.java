package com.stardew.craft.platform.fabric.event;

import net.minecraft.world.entity.player.Player;

/** Published around Player.tick, including the logical client when its mixin is active. */
public abstract class PlayerTickEvent extends PlayerEvent {
    protected PlayerTickEvent(Player player) { super(player); }

    public static final class Pre extends PlayerTickEvent {
        public Pre(Player player) { super(player); }
    }

    public static final class Post extends PlayerTickEvent {
        public Post(Player player) { super(player); }
    }
}
