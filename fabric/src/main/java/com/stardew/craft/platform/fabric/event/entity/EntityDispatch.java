package com.stardew.craft.platform.fabric.event.entity;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDeathEvent;
import java.util.function.BooleanSupplier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class EntityDispatch {
    private EntityDispatch() {}

    /** A canceled pre event skips the action and its post; it does not skip the world's passenger traversal. */
    public static boolean tick(Entity entity, Runnable action) {
        if (FabricEvents.GAME.post(new EntityTickEvent.Pre(entity)).isCanceled()) return false;
        action.run();
        FabricEvents.GAME.post(new EntityTickEvent.Post(entity));
        return true;
    }

    public static boolean join(Entity entity, Level level, boolean loadedFromDisk, BooleanSupplier insert) {
        if (FabricEvents.GAME.post(new EntityJoinLevelEvent(entity, level, loadedFromDisk)).isCanceled()) return false;
        boolean inserted = insert.getAsBoolean();
        if (inserted) com.stardew.craft.platform.fabric.entity.EntityPresence.set(entity, true);
        return inserted;
    }

    public static void leave(Entity entity, Level level) {
        com.stardew.craft.platform.fabric.entity.EntityPresence.set(entity, false);
        FabricEvents.GAME.post(new EntityLeaveLevelEvent(entity, level));
    }

    public static boolean allowDeath(LivingEntity entity, DamageSource source) {
        return !FabricEvents.GAME.post(new LivingDeathEvent(entity, source)).isCanceled();
    }
}
