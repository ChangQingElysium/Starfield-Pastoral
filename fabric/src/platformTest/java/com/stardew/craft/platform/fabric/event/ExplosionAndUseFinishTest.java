package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.platform.fabric.event.entity.living.ItemUseFinishHooks;
import com.stardew.craft.platform.fabric.event.entity.living.LivingEntityUseItemEvent;
import com.stardew.craft.platform.fabric.event.level.ExplosionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class ExplosionAndUseFinishTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void explosionEventMutatesTheRealBlockAndEntityListsWithoutBeingCancelable() throws Exception {
        var level = EventTestObjects.allocate(ServerLevel.class);
        var explosion = EventTestObjects.allocate(Explosion.class);
        var blocks = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(BlockPos.ZERO, new BlockPos(1, 0, 0)));
        var field = Explosion.class.getDeclaredField("toBlow"); field.setAccessible(true); field.set(explosion, blocks);
        var entity = EventTestObjects.allocate(ServerPlayer.class);
        var entities = new ArrayList<Entity>(List.of(entity));
        var event = new ExplosionEvent.Detonate(level, explosion, entities);
        assertSame(blocks, event.getAffectedBlocks()); assertSame(entities, event.getAffectedEntities());
        event.getAffectedBlocks().remove(BlockPos.ZERO); event.getAffectedEntities().clear();
        assertEquals(List.of(new BlockPos(1, 0, 0)), explosion.getToBlow()); assertTrue(entities.isEmpty());
        assertSame(level, event.getLevel()); assertThrows(IllegalStateException.class, () -> event.setCanceled(true));
    }

    @Test void finishingCopiesOriginalFoodBeforeConsumptionAndLetsListenerReplaceContainerResult() throws Exception {
        var user = EventTestObjects.allocate(OffHandUser.class);
        var held = new ItemStack(Items.MUSHROOM_STEW);
        held.set(DataComponents.CUSTOM_NAME, Component.literal("original meal"));
        var replacement = new ItemStack(Items.GLASS_BOTTLE);
        Consumer<LivingEntityUseItemEvent.Finish> listener = event -> {
            assertEquals(1, event.getItem().getCount()); assertTrue(event.getItem().is(Items.MUSHROOM_STEW));
            assertEquals(Component.literal("original meal"), event.getItem().get(DataComponents.CUSTOM_NAME));
            assertEquals(InteractionHand.OFF_HAND, event.getHand()); assertEquals(0, event.getDuration());
            assertTrue(held.isEmpty()); assertTrue(event.getResultStack().is(Items.BOWL));
            event.setResultStack(replacement);
        };
        FabricEvents.GAME.addListener(LivingEntityUseItemEvent.Finish.class, listener);
        try {
            assertSame(replacement, ItemUseFinishHooks.finish(user, held, 0, () -> {
                held.shrink(1); return new ItemStack(Items.BOWL);
            }));
        } finally { FabricEvents.GAME.unregister(listener); }
    }

    @Test void nativeConsumersHaveSingleCallsAfterCollectionAndBeforeItemHandReplacement() throws Exception {
        var explosion = nativeType("net/minecraft/world/level/Explosion").methods.stream().filter(m -> m.name.equals("explode")).findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(explosion.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i).toList();
        var collect = calls.stream().filter(c -> c.owner.equals("net/minecraft/world/level/Level") && c.name.equals("getEntities")).toList();
        assertEquals(1, collect.size());
        assertEquals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;", collect.getFirst().desc);
        assertTrue(calls.indexOf(collect.getFirst()) < java.util.stream.IntStream.range(0, calls.size()).filter(i -> calls.get(i).name.equals("ignoreExplosion")).findFirst().orElseThrow());
        var finish = nativeType("net/minecraft/world/entity/LivingEntity").methods.stream().filter(m -> m.name.equals("completeUsingItem")).findFirst().orElseThrow();
        var names = java.util.Arrays.stream(finish.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(i -> ((MethodInsnNode) i).name).toList();
        assertEquals(1, names.stream().filter("finishUsingItem"::equals).count());
        assertTrue(names.indexOf("triggerItemUseEffects") < names.indexOf("finishUsingItem"));
        assertTrue(names.indexOf("finishUsingItem") < names.indexOf("setItemInHand"));
        assertTrue(names.indexOf("setItemInHand") < names.indexOf("stopUsingItem"));
    }

    private static final class OffHandUser extends ServerPlayer {
        OffHandUser() { super(null, null, new GameProfile(new UUID(0, 1), "Fixture"), ClientInformation.createDefault()); }
        @Override public InteractionHand getUsedItemHand() { return InteractionHand.OFF_HAND; }
    }
    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = ExplosionAndUseFinishTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
