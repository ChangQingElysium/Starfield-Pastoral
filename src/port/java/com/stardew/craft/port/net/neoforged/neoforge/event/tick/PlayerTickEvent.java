package com.stardew.craft.port.net.neoforged.neoforge.event.tick;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * PORT(1.20.1): NeoForge 21.1 player tick event (both logical sides), bridged from Forge
 * {@code TickEvent.PlayerTickEvent} ({@code START} -> {@link Pre}, {@code END} -> {@link Post}).
 */
public abstract class PlayerTickEvent extends PlayerEvent {
    protected PlayerTickEvent(Player player) {
        super(player);
    }

    public static class Pre extends PlayerTickEvent {
        public Pre() {
            this(null);
        }

        public Pre(Player player) {
            super(player);
        }
    }

    public static class Post extends PlayerTickEvent {
        public Post() {
            this(null);
        }

        public Post(Player player) {
            super(player);
        }
    }
}
