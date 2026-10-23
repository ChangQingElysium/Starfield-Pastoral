package com.stardew.craft.platform.fabric.event.sleep;

import com.stardew.craft.platform.fabric.event.PlayerEvent;
import net.minecraft.world.entity.player.Player;

public final class PlayerWakeUpEvent extends PlayerEvent {
    private final boolean immediately, update;
    public PlayerWakeUpEvent(Player player, boolean immediately, boolean update) { super(player); this.immediately = immediately; this.update = update; }
    public boolean wakeImmediately() { return immediately; }
    public boolean updateLevel() { return update; }
}
