package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

public final class LivingDropHooks {
    private static final ThreadLocal<Deque<Capture>> CAPTURES = ThreadLocal.withInitial(ArrayDeque::new);
    private LivingDropHooks() {}

    public static Capture begin(Entity owner) {
        var capture = new Capture(owner);
        CAPTURES.get().push(capture);
        return capture;
    }

    public static boolean capture(Entity owner, ItemEntity item) {
        for (Capture capture : CAPTURES.get()) {
            if (capture.owner == owner) { capture.items.add(item); return true; }
        }
        if (CAPTURES.get().isEmpty()) CAPTURES.remove();
        return false;
    }

    public static void publish(LivingEntity owner, DamageSource source, List<ItemEntity> drops, boolean recentlyHit,
            Consumer<ItemEntity> spawn) {
        // Experience is already awarded by the original body before this event.
        if (!FabricEvents.GAME.post(new LivingDropsEvent(owner, source, drops, recentlyHit)).isCanceled()) drops.forEach(spawn);
    }

    public static final class Capture implements AutoCloseable {
        private final Entity owner;
        private final List<ItemEntity> items = new ArrayList<>();
        private boolean closed;
        private Capture(Entity owner) { this.owner = java.util.Objects.requireNonNull(owner); }
        public List<ItemEntity> items() { return items; }
        @Override public void close() {
            if (closed || CAPTURES.get().peek() != this) throw new IllegalStateException("Living drops capture closed out of order");
            CAPTURES.get().pop(); closed = true;
            if (CAPTURES.get().isEmpty()) CAPTURES.remove();
        }
    }
}
