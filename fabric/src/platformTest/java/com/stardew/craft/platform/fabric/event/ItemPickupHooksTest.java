package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.player.ItemEntityPickupEvent;
import com.stardew.craft.platform.fabric.event.entity.player.ItemPickupHooks;
import com.stardew.craft.platform.fabric.util.TriState;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ItemPickupHooksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void triStateOnlyChangesDelayAndOwnershipAndKeepsDefaultRules() {
        var owner = UUID.randomUUID();
        var normal = new ItemPickupHooks.Frame(TriState.DEFAULT, ItemStack.EMPTY);
        var allow = new ItemPickupHooks.Frame(TriState.TRUE, ItemStack.EMPTY);
        var deny = new ItemPickupHooks.Frame(TriState.FALSE, ItemStack.EMPTY);
        assertEquals(20, ItemPickupHooks.delay(normal, 20));
        assertSame(owner, ItemPickupHooks.owner(normal, owner));
        assertEquals(0, ItemPickupHooks.delay(allow, 20));
        assertNull(ItemPickupHooks.owner(allow, owner));
        assertNotEquals(0, ItemPickupHooks.delay(deny, 0));
        assertSame(owner, ItemPickupHooks.owner(deny, owner));
    }

    @Test void preMutationsAreIncludedAndPostKeepsOriginalComponentsWhileExposingLiveRemainder() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var item = EventTestObjects.allocate(FixtureItem.class);
        item.held = new ItemStack(Items.STICK, 10);
        item.held.set(DataComponents.CUSTOM_NAME, Component.literal("Owned item"));
        Consumer<ItemEntityPickupEvent.Pre> pre = event -> event.getItemEntity().getItem().shrink(2);
        Consumer<ItemEntityPickupEvent.Post> post = event -> {
            assertEquals(8, event.getOriginalStack().getCount());
            assertEquals(Component.literal("Owned item"), event.getOriginalStack().get(DataComponents.CUSTOM_NAME));
            event.getOriginalStack().setCount(99);
            assertSame(item.held, event.getCurrentStack());
            event.getCurrentStack().shrink(1);
        };
        FabricEvents.GAME.addListener(ItemEntityPickupEvent.Pre.class, pre);
        FabricEvents.GAME.addListener(ItemEntityPickupEvent.Post.class, post);
        try {
            var frame = ItemPickupHooks.before(item, player);
            assertEquals(8, frame.original().getCount());
            // A real Inventory.add can partially consume the same native stack.
            item.held.setCount(3);
            assertEquals(6, ItemPickupHooks.after(item, player, frame, item.held));
            assertEquals(8, frame.original().getCount());
            assertEquals(2, item.held.getCount());
        } finally {
            FabricEvents.GAME.unregister(pre); FabricEvents.GAME.unregister(post);
        }
    }

    @Test void nativePickupKeepsItsServerGuardInventoryCapacityAndSharedCountForPacketsAndStats() throws Exception {
        var type = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/entity/item/ItemEntity.class")) {
            assertNotNull(input); new ClassReader(input).accept(type, 0);
        }
        var method = type.methods.stream().filter(m -> m.name.equals("playerTouch")).findFirst().orElseThrow();
        int delay = -1, client = -1, adds = 0, takes = 0, targets = 0, countStores = 0, countLoads = 0;
        for (int i = 0; i < method.instructions.size(); i++) {
            var instruction = method.instructions.get(i);
            if (instruction instanceof FieldInsnNode field) {
                if (field.name.equals("isClientSide")) client = i;
                if (field.name.equals("pickupDelay")) delay = i;
                if (field.name.equals("target")) targets++;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.endsWith("/Inventory") && call.name.equals("add")) adds++;
                if (call.owner.endsWith("/Player") && call.name.equals("take")) takes++;
            }
            if (instruction instanceof VarInsnNode local && local.var == 4) {
                if (local.getOpcode() == Opcodes.ISTORE) countStores++;
                if (local.getOpcode() == Opcodes.ILOAD) countLoads++;
            }
        }
        assertTrue(client >= 0 && client < delay);
        assertEquals(1, adds); assertEquals(1, takes); assertEquals(2, targets);
        assertEquals(1, countStores);
        assertEquals(3, countLoads, "Take packet, cleared entity count and stats share the corrected count");
    }

    private static final class FixtureItem extends ItemEntity {
        ItemStack held;
        private FixtureItem() { super(EntityType.ITEM, null); }
        @Override public ItemStack getItem() { return held; }
    }
}
