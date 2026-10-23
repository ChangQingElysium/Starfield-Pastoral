package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import java.util.Objects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class AttackEntityEvent extends PlayerEvent {
    private final Entity target;
    public AttackEntityEvent(Player player, Entity target) {
        super(player, true);
        this.target = Objects.requireNonNull(target, "target");
    }
    public Entity getTarget() { return target; }
}
