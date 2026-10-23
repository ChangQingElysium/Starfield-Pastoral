package com.stardew.craft.platform.fabric.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Player lifecycle payloads with explicitly implemented Fabric producers. */
public abstract class PlayerEvent extends com.stardew.craft.platform.fabric.event.entity.living.LivingEvent {
    private final Player player;

    protected PlayerEvent(Player player) {
        this(player, false);
    }

    protected PlayerEvent(Player player, boolean cancelable) {
        this(player, cancelable, false);
    }

    protected PlayerEvent(Player player, boolean cancelable, boolean allowNullPlayer) {
        super(player, cancelable, allowNullPlayer);
        this.player = player;
    }

    public Player getEntity() {
        return player;
    }

    public static final class BreakSpeed extends PlayerEvent {
        private final net.minecraft.world.level.block.state.BlockState state;
        private final float originalSpeed;
        private final java.util.Optional<net.minecraft.core.BlockPos> position;
        private float newSpeed;
        public BreakSpeed(Player player, net.minecraft.world.level.block.state.BlockState state, float original,
                net.minecraft.core.BlockPos position) {
            super(player, true);
            this.state = java.util.Objects.requireNonNull(state);
            this.originalSpeed = this.newSpeed = original;
            this.position = java.util.Optional.ofNullable(position);
        }
        public net.minecraft.world.level.block.state.BlockState getState() { return state; }
        public float getOriginalSpeed() { return originalSpeed; }
        public float getNewSpeed() { return newSpeed; }
        public void setNewSpeed(float speed) { newSpeed = speed; }
        public java.util.Optional<net.minecraft.core.BlockPos> getPosition() { return position; }
    }

    public static final class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) { super(player); }
    }

    public static final class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) { super(player); }
    }

    public static final class StartTracking extends PlayerEvent {
        private final net.minecraft.world.entity.Entity target;
        public StartTracking(Player player, net.minecraft.world.entity.Entity target) {
            super(player);
            this.target = java.util.Objects.requireNonNull(target, "target");
        }
        public net.minecraft.world.entity.Entity getTarget() { return target; }
    }

    public static final class StopTracking extends PlayerEvent {
        private final net.minecraft.world.entity.Entity target;
        public StopTracking(Player player, net.minecraft.world.entity.Entity target) {
            super(player);
            this.target = java.util.Objects.requireNonNull(target, "target");
        }
        public net.minecraft.world.entity.Entity getTarget() { return target; }
    }

    public static final class PlayerRespawnEvent extends PlayerEvent {
        private final boolean endConquered;

        public PlayerRespawnEvent(Player player, boolean endConquered) {
            super(player);
            this.endConquered = endConquered;
        }

        public boolean isEndConquered() { return endConquered; }
    }

    public static final class Clone extends PlayerEvent {
        private final Player original;
        private final boolean wasDeath;

        public Clone(Player replacement, Player original, boolean wasDeath) {
            super(replacement);
            this.original = original;
            this.wasDeath = wasDeath;
        }

        public Player getOriginal() { return original; }
        public boolean isWasDeath() { return wasDeath; }
    }

    public static final class PlayerChangedDimensionEvent extends PlayerEvent {
        private final ResourceKey<Level> from;
        private final ResourceKey<Level> to;

        public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> from, ResourceKey<Level> to) {
            super(player);
            this.from = from;
            this.to = to;
        }

        public ResourceKey<Level> getFrom() { return from; }
        public ResourceKey<Level> getTo() { return to; }
    }
}
