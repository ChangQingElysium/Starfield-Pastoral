package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.living.LivingDropHooks;
import com.stardew.craft.platform.fabric.event.entity.living.LivingDropsEvent;
import com.stardew.craft.platform.fabric.event.level.BlockDropHooks;
import com.stardew.craft.platform.fabric.event.level.BlockDropsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class DropEventHooksTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void livingCaptureIsOwnerSpecificReentrantAndClosedBeforePublishing() throws Exception {
        var first = EventTestObjects.allocate(ServerPlayer.class);
        var second = EventTestObjects.allocate(ServerPlayer.class);
        var third = EventTestObjects.allocate(ServerPlayer.class);
        var a = EventTestObjects.allocate(ItemEntity.class);
        var b = EventTestObjects.allocate(ItemEntity.class);
        var outer = LivingDropHooks.begin(first);
        try (outer) {
            assertTrue(LivingDropHooks.capture(first, a));
            try (var inner = LivingDropHooks.begin(second)) {
                assertTrue(LivingDropHooks.capture(first, b));
                assertTrue(LivingDropHooks.capture(second, a));
                assertFalse(LivingDropHooks.capture(third, b));
                assertEquals(List.of(a), inner.items());
            }
            assertEquals(List.of(a, b), outer.items());
        }
        assertFalse(LivingDropHooks.capture(first, a));
        assertThrows(IllegalStateException.class, outer::close);
    }

    @Test void livingListenersCanReplaceAllDropsAndCancellationSkipsSpawn() throws Exception {
        var owner = EventTestObjects.allocate(ServerPlayer.class);
        var original = EventTestObjects.allocate(ItemEntity.class);
        var replacement = EventTestObjects.allocate(ItemEntity.class);
        var source = new DamageSource(Holder.direct(new DamageType("test", 0.1F)));
        var drops = new ArrayList<>(List.of(original));
        var spawned = new ArrayList<ItemEntity>();
        Consumer<LivingDropsEvent> listener = event -> {
            assertSame(owner, event.getEntity()); assertSame(source, event.getSource());
            assertTrue(event.isRecentlyHit());
            assertFalse(LivingDropHooks.capture(owner, replacement));
            event.getDrops().clear(); event.getDrops().add(replacement);
        };
        FabricEvents.GAME.addListener(LivingDropsEvent.class, listener);
        try { LivingDropHooks.publish(owner, source, drops, true, spawned::add); }
        finally { FabricEvents.GAME.unregister(listener); }
        assertEquals(List.of(replacement), spawned);
        Consumer<LivingDropsEvent> cancel = event -> event.setCanceled(true);
        FabricEvents.GAME.addListener(LivingDropsEvent.class, cancel);
        try { LivingDropHooks.publish(owner, source, drops, false, item -> fail("canceled drop spawned")); }
        finally { FabricEvents.GAME.unregister(cancel); }
    }

    @Test void blockEventUsesModifiedItemsThenAfterBreakThenModifiedExperience() throws Exception {
        var level = EventTestObjects.allocate(ServerLevel.class);
        var old = EventTestObjects.allocate(ItemEntity.class);
        var added = EventTestObjects.allocate(ItemEntity.class);
        var drops = new ArrayList<>(List.of(old));
        var event = new BlockDropsEvent(level, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null, drops, null, ItemStack.EMPTY, 3);
        var calls = new ArrayList<String>();
        Consumer<BlockDropsEvent> listener = value -> {
            assertSame(level, value.getLevel()); assertNull(value.getBlockEntity()); assertNull(value.getBreaker());
            value.getDrops().clear(); value.getDrops().add(added); value.setDroppedExperience(17);
        };
        FabricEvents.GAME.addListener(BlockDropsEvent.class, listener);
        try {
            BlockDropHooks.publish(event, item -> { assertSame(added, item); calls.add("item"); },
                    () -> calls.add("after"), xp -> calls.add("xp=" + xp));
        } finally { FabricEvents.GAME.unregister(listener); }
        assertEquals(List.of("item", "after", "xp=17"), calls);
        assertThrows(IllegalArgumentException.class, () -> event.setDroppedExperience(-1));
    }

    @Test void canceledBlockEventSkipsItemsExperienceAndAfterBreakEvenWithPositiveXp() throws Exception {
        var level = EventTestObjects.allocate(ServerLevel.class);
        var event = new BlockDropsEvent(level, BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null,
                new ArrayList<>(), null, ItemStack.EMPTY, 10);
        Consumer<BlockDropsEvent> cancel = value -> value.setCanceled(true);
        FabricEvents.GAME.addListener(BlockDropsEvent.class, cancel);
        try { BlockDropHooks.publish(event, item -> fail("spawn"), () -> fail("after break"), xp -> fail("experience")); }
        finally { FabricEvents.GAME.unregister(cancel); }
    }

    @Test void nativeDropEntryPointsAndCaptureOperationsAreUniqueAndKeepXpBeforeLivingEvent() throws Exception {
        var living = nativeType("net/minecraft/world/entity/LivingEntity");
        var loot = living.methods.stream().filter(m -> m.name.equals("dropAllDeathLoot")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(loot.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call && call.name.equals("dropExperience")).count());
        assertEquals(1, java.util.Arrays.stream(loot.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.RETURN).count());
        var entity = nativeType("net/minecraft/world/entity/Entity");
        var spawn = entity.methods.stream().filter(m -> m.name.equals("spawnAtLocation")
                && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(spawn.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/level/Level") && call.name.equals("addFreshEntity")).count());
        var player = nativeType("net/minecraft/server/level/ServerPlayer");
        var drop = player.methods.stream().filter(m -> m.name.equals("drop") && m.desc.contains(";ZZ)")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(drop.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/level/Level") && call.name.equals("addFreshEntity")).count());
        var block = nativeType("net/minecraft/world/level/block/Block");
        var resources = block.methods.stream().filter(m -> m.name.equals("dropResources")).toList();
        assertEquals(3, resources.size());
        for (var method : resources) {
            assertTrue((method.access & Opcodes.ACC_STATIC) != 0);
            assertEquals(1, java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                    && call.owner.equals("net/minecraft/world/level/block/state/BlockState") && call.name.equals("spawnAfterBreak")).count(), method.desc);
        }
        var pop = block.methods.stream().filter(m -> m.name.equals("popResource") && m.desc.contains("Ljava/util/function/Supplier;")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(pop.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/level/Level") && call.name.equals("addFreshEntity")).count());
    }

    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = DropEventHooksTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
