package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.platform.fabric.event.entity.player.AttackEntityEvent;
import com.stardew.craft.platform.fabric.extension.CommonHooks;
import com.stardew.craft.platform.fabric.extension.ItemAttackProvider;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerAttackHooksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void canceledAttackStopsBeforeReadingTheHandOrCallingItsItem() throws Exception {
        var player = EventTestObjects.allocate(FixturePlayer.class);
        var target = EventTestObjects.allocate(ServerPlayer.class);
        Consumer<AttackEntityEvent> cancel = event -> {
            assertSame(player, event.getEntity()); assertSame(target, event.getTarget());
            event.setCanceled(true);
        };
        FabricEvents.GAME.addListener(AttackEntityEvent.class, cancel);
        try { assertFalse(CommonHooks.onPlayerAttackTarget(player, target)); }
        finally { FabricEvents.GAME.unregister(cancel); }
        assertEquals(0, player.reads);
    }

    @Test void exactStackComponentsAndItemConsumptionDecideTheMeleeGate() throws Exception {
        var player = EventTestObjects.allocate(FixturePlayer.class);
        var item = EventTestObjects.allocate(FixtureItem.class);
        var target = EventTestObjects.allocate(ServerPlayer.class);
        player.held = new ItemStack(item);
        assertTrue(CommonHooks.onPlayerAttackTarget(player, target));
        assertSame(player.held, item.observed);
        player.held.set(DataComponents.CUSTOM_NAME, Component.literal("consume melee"));
        assertFalse(CommonHooks.onPlayerAttackTarget(player, target));
        assertEquals(2, item.calls);
        player.held = ItemStack.EMPTY;
        assertTrue(CommonHooks.onPlayerAttackTarget(player, target));
        assertEquals(2, item.calls);
    }

    @Test void eventChangesToTheHeldItemAreObservedAfterTheEvent() throws Exception {
        var player = EventTestObjects.allocate(FixturePlayer.class);
        var item = EventTestObjects.allocate(FixtureItem.class);
        var target = EventTestObjects.allocate(ServerPlayer.class);
        var consumed = new ItemStack(item);
        consumed.set(DataComponents.CUSTOM_NAME, Component.literal("consume melee"));
        player.held = new ItemStack(Items.STICK);
        Consumer<AttackEntityEvent> change = event -> player.held = consumed;
        FabricEvents.GAME.addListener(AttackEntityEvent.class, change);
        try { assertFalse(CommonHooks.onPlayerAttackTarget(player, target)); }
        finally { FabricEvents.GAME.unregister(change); }
        assertSame(consumed, item.observed);
    }

    private static final class FixturePlayer extends ServerPlayer {
        ItemStack held;
        int reads;
        private FixturePlayer() { super(null, null, new GameProfile(new UUID(0, 1), "Fixture"), ClientInformation.createDefault()); }
        @Override public ItemStack getMainHandItem() { reads++; return held; }
    }
    private static final class FixtureItem extends Item implements ItemAttackProvider {
        ItemStack observed;
        int calls;
        private FixtureItem() { super(new Properties()); }
        @Override public DataComponentMap components() { return Items.STICK.components(); }
        @Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
            observed = stack; calls++; return stack.has(DataComponents.CUSTOM_NAME);
        }
    }
}
