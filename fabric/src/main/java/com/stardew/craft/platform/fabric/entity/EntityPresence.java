package com.stardew.craft.platform.fabric.entity;

import net.minecraft.world.entity.Entity;

public final class EntityPresence {
    private EntityPresence() {}
    public interface State {
        boolean stardewcraft$isAddedToLevel();
        void stardewcraft$setAddedToLevel(boolean added);
    }
    public static boolean isAdded(Entity entity) { return entity instanceof State state && state.stardewcraft$isAddedToLevel(); }
    public static void set(Entity entity, boolean added) {
        if (entity instanceof State state) state.stardewcraft$setAddedToLevel(added);
    }
}
