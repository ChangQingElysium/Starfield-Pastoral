package com.stardew.craft.platform.fabric.event.entity.living.spawn;

import com.stardew.craft.platform.fabric.entity.EntityPresence;
import net.minecraft.world.entity.Mob;

public final class SpawnCancellation {
    private SpawnCancellation() {}
    public interface State {
        boolean stardewcraft$spawnCancelled();
        void stardewcraft$spawnCancelled(boolean value);
    }
    private static State state(Mob mob) {
        if (!(mob instanceof State state)) throw new UnsupportedOperationException("Mob spawn cancellation requires its native state Mixin");
        return state;
    }
    public static boolean isCancelled(Mob mob) { return state(mob).stardewcraft$spawnCancelled(); }
    public static void set(Mob mob, boolean cancel) {
        if (EntityPresence.isAdded(mob)) throw new UnsupportedOperationException("Late Mob spawn cancellation is not permitted after world insertion");
        state(mob).stardewcraft$spawnCancelled(cancel);
    }
}
