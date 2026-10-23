package com.stardew.craft.platform.fabric.event.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.Event;
import com.stardew.craft.platform.fabric.event.EventPriority;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDeathEvent;
import com.stardew.craft.platform.fabric.event.entity.living.LivingEvent;
import com.stardew.craft.platform.fabric.event.entity.player.InteractionTestPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityDispatchTest {
    private final List<Object> listeners = new ArrayList<>();

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @AfterEach void unregister() { listeners.forEach(FabricEvents.GAME::unregister); }

    @Test void tickPostsBeforeAndAfterTheActualActionWithTheSameEntity() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        List<String> calls = new ArrayList<>();
        listen(EntityTickEvent.Pre.class, event -> {
            assertSame(entity, event.getEntity());
            calls.add("pre");
        });
        listen(EntityTickEvent.Post.class, event -> {
            assertSame(entity, event.getEntity());
            assertThrows(IllegalStateException.class, () -> event.setCanceled(true));
            calls.add("post");
        });
        assertTrue(EntityDispatch.tick(entity, () -> calls.add("native-tick")));
        assertEquals(List.of("pre", "native-tick", "post"), calls);
    }

    @Test void canceledTickSkipsActionAndPostButStillReachesCanceledListeners() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        AtomicInteger observers = new AtomicInteger();
        listen(EntityTickEvent.Pre.class, event -> event.setCanceled(true));
        listen(EventPriority.LOW, true, EntityTickEvent.Pre.class, event -> {
            assertTrue(event.isCanceled());
            observers.incrementAndGet();
        });
        listen(EntityTickEvent.Post.class, event -> fail("post of canceled tick"));
        assertFalse(EntityDispatch.tick(entity, () -> fail("canceled tick")));
        assertEquals(1, observers.get());
    }

    @Test void anExplicitLaterUncancelAllowsTheTick() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        listen(EntityTickEvent.Pre.class, event -> event.setCanceled(true));
        listen(EventPriority.LOW, true, EntityTickEvent.Pre.class, event -> event.setCanceled(false));
        AtomicInteger actions = new AtomicInteger();
        assertTrue(EntityDispatch.tick(entity, actions::incrementAndGet));
        assertEquals(1, actions.get());
    }

    @Test void aFailedNativeTickDoesNotPublishPost() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        listen(EntityTickEvent.Post.class, event -> fail("post after failed action"));
        var failure = new IllegalStateException("fixture");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> EntityDispatch.tick(entity, () -> { throw failure; })));
    }

    @Test void canceledJoinDoesNotTouchStorageOrProduceALeaveEvent() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        var level = allocate(ServerLevel.class);
        listen(EntityJoinLevelEvent.class, event -> {
            assertSame(entity, event.getEntity());
            assertSame(level, event.getLevel());
            assertTrue(event.loadedFromDisk());
            event.setCanceled(true);
        });
        listen(EntityLeaveLevelEvent.class, event -> fail("a rejected join did not leave tracking"));
        assertFalse(EntityDispatch.join(entity, level, true, () -> fail("canceled insertion")));
    }

    @Test void successfulJoinPassesThroughTheRealStorageResult() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        var level = allocate(ServerLevel.class);
        AtomicInteger events = new AtomicInteger();
        listen(EntityJoinLevelEvent.class, event -> { assertFalse(event.loadedFromDisk()); events.incrementAndGet(); });
        assertTrue(EntityDispatch.join(entity, level, false, () -> true));
        assertFalse(EntityDispatch.join(entity, level, false, () -> false), "native duplicate UUID rejection must propagate");
        assertEquals(2, events.get());
    }

    @Test void cancelingAReplacementKeepsThePreviousClientStorageEntry() throws ReflectiveOperationException {
        var previous = InteractionTestPlayer.create(ItemStack.EMPTY);
        var replacement = InteractionTestPlayer.create(ItemStack.EMPTY);
        List<Object> storage = new ArrayList<>(List.of(previous));
        listen(EntityJoinLevelEvent.class, event -> event.setCanceled(true));
        assertFalse(EntityDispatch.join(replacement, null, false, () -> {
            storage.clear();
            storage.add(replacement);
            return true;
        }));
        assertEquals(List.of(previous), storage);
    }

    @Test void trackingLeaveIsNotCancellableAndDoesNotImplyDeath() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        var level = allocate(ServerLevel.class);
        AtomicInteger leaves = new AtomicInteger();
        listen(EntityLeaveLevelEvent.class, event -> {
            assertSame(entity, event.getEntity());
            assertSame(level, event.getLevel());
            assertThrows(IllegalStateException.class, () -> event.setCanceled(true));
            leaves.incrementAndGet();
        });
        listen(LivingDeathEvent.class, event -> fail("tracking loss is not death"));
        EntityDispatch.leave(entity, level);
        assertEquals(1, leaves.get());
    }

    @Test void deathCancellationPreventsTheNativeOperationAndKeepsTheRealDamageSource() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        var source = allocate(DamageSource.class);
        AtomicInteger nativeDeath = new AtomicInteger();
        listen(LivingDeathEvent.class, event -> {
            assertSame(entity, event.getEntity());
            assertSame(source, event.getSource());
            event.setCanceled(true);
        });
        if (EntityDispatch.allowDeath(entity, source)) nativeDeath.incrementAndGet();
        assertEquals(0, nativeDeath.get());
    }

    @Test void uncanceledDeathAllowsTheOperationAndNullPlayerDeathSourcesArePreserved() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        AtomicInteger nativeDeath = new AtomicInteger();
        listen(LivingDeathEvent.class, event -> assertNull(event.getSource()));
        if (EntityDispatch.allowDeath(entity, null)) nativeDeath.incrementAndGet();
        assertEquals(1, nativeDeath.get());
    }

    @Test void livingAndEntityBaseSubscriptionsReceiveTheTypedDeathPayload() throws ReflectiveOperationException {
        var entity = InteractionTestPlayer.create(ItemStack.EMPTY);
        List<Class<?>> types = new ArrayList<>();
        listen(LivingEvent.class, event -> { assertSame(entity, event.getEntity()); types.add(LivingEvent.class); });
        listen(EntityEvent.class, event -> { assertSame(entity, event.getEntity()); types.add(EntityEvent.class); });
        assertTrue(EntityDispatch.allowDeath(entity, null));
        assertEquals(List.of(LivingEvent.class, EntityEvent.class), types);
    }

    private <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        listen(EventPriority.NORMAL, false, type, listener);
    }

    private <E extends Event> void listen(EventPriority priority, boolean receiveCanceled, Class<E> type, Consumer<E> listener) {
        listeners.add(listener);
        FabricEvents.GAME.addListener(priority, receiveCanceled, type, listener);
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Fixture allocator unavailable");
        Object allocator = field.get(null);
        return type.cast(allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, type));
    }
}
