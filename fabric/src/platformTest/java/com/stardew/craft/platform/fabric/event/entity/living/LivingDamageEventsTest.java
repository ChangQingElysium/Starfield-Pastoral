package com.stardew.craft.platform.fabric.event.entity.living;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageContainer;
import com.stardew.craft.platform.fabric.event.entity.living.damage.DamageFrames;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LivingDamageEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void nativePhaseProducersShareOneContainerAndPostAnImmutableSnapshotEvenAtZero() throws Exception {
        var bus = new EventBus();
        FabricLivingDamageEvents.install(bus);
        FabricLivingDamageEvents.install(bus);
        assertThrows(IllegalStateException.class, () -> FabricLivingDamageEvents.install(new EventBus()));
        var player = TestPlayer.create();
        List<String> order = new ArrayList<>();
        List<DamageContainer> containers = new ArrayList<>();
        List<LivingDamageEvent.Post> posts = new ArrayList<>();
        bus.addListener(LivingIncomingDamageEvent.class, event -> {
            assertSame(player, event.getEntity());
            order.add("incoming");
            if (event.getOriginalAmount() == 999) { event.setCanceled(true); return; }
            containers.add(event.getContainer());
            event.setAmount(12);
            event.setInvulnerabilityTicks(45);
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction / 2);
        });
        bus.addListener(LivingDamageEvent.Pre.class, event -> {
            order.add("pre");
            assertFalse(event.isCancelable());
            assertSame(containers.getLast(), event.getContainer());
            assertEquals(20, event.getOriginalDamage());
            assertEquals(2, event.getContainer().getReduction(DamageContainer.Reduction.ARMOR));
            assertEquals(10, event.getNewDamage());
            event.setNewDamage(0);
        });
        bus.addListener(LivingDamageEvent.Post.class, event -> {
            order.add("post");
            assertFalse(event.isCancelable());
            assertEquals(0, event.getNewDamage());
            posts.add(event);
        });
        bus.addListener(LivingKnockBackEvent.class, event -> {
            assertEquals(2, event.getOriginalStrength());
            assertEquals(3, event.getOriginalRatioX());
            assertEquals(4, event.getOriginalRatioZ());
            event.setStrength(0.5F); event.setRatioX(-1); event.setRatioZ(-2); event.setCanceled(true);
        });
        player.frames.within(() -> {
            assertFalse(FabricLivingDamageEvents.incoming(player, null, 20));
            var container = player.frames.current().container;
            assertEquals(12, container.getNewDamage());
            container.setReduction(DamageContainer.Reduction.ARMOR, 4);
            assertEquals(0, FabricLivingDamageEvents.pre(player));
            assertEquals(0, FabricLivingDamageEvents.absorption(player, 5));
            assertEquals(0, player.frames.current().absorbed);
            FabricLivingDamageEvents.post(player);
            order.add("outer-death-check");
            container.setNewDamage(100);
            container.setPostAttackInvulnerabilityTicks(1);
            container.setReduction(DamageContainer.Reduction.ARMOR, 5);
            return true;
        });
        assertEquals(List.of("incoming", "pre", "post", "outer-death-check"), order);
        assertEquals(0, posts.getFirst().getNewDamage());
        assertEquals(2, posts.getFirst().getReduction(DamageContainer.Reduction.ARMOR));
        assertEquals(45, posts.getFirst().getPostAttackInvulnerabilityTicks());
        assertNull(player.frames.currentOrNull());
        order.clear();
        player.frames.within(() -> {
            assertTrue(FabricLivingDamageEvents.incoming(player, null, 999));
            return false; // native incoming cancellation returns here, without mitigation or Post
        });
        assertEquals(List.of("incoming"), order);
        assertNull(player.frames.currentOrNull());
        var knockback = FabricLivingDamageEvents.knockback(player, 2, 3, 4);
        assertTrue(knockback.isCanceled());
        assertEquals(0.5F, knockback.getStrength());
        assertEquals(-1, knockback.getRatioX());
        assertEquals(-2, knockback.getRatioZ());
        FabricLivingDamageEvents.post(player); // actuallyHurt's initial invulnerable early return has no Post
        assertEquals(1, posts.size());
    }

    static final class TestPlayer extends ServerPlayer implements DamageFrames.Access {
        DamageFrames frames;
        private TestPlayer() { super(null, null, null, null); }
        @Override public DamageFrames stardewcraft$damageFrames() { return frames; }
        static TestPlayer create() throws Exception {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            var field = type.getDeclaredField("theUnsafe");
            if (!field.trySetAccessible()) throw new IllegalStateException("Payload allocator unavailable");
            Object allocator = field.get(null);
            var player = (TestPlayer) type.getMethod("allocateInstance", Class.class).invoke(allocator, TestPlayer.class);
            player.frames = new DamageFrames();
            return player;
        }
    }
}
