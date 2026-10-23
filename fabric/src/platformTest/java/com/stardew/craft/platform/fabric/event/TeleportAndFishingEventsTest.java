package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import com.stardew.craft.platform.fabric.event.entity.ProjectileImpactEvent;
import com.stardew.craft.platform.fabric.event.entity.ProjectileImpacts;
import com.stardew.craft.platform.fabric.event.entity.player.ItemFishedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TeleportAndFishingEventsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void eachTeleportTypeSupportsCancellationAndMutableDestinationWithoutLosingSourceIdentity() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var pearl = EventTestObjects.allocate(ThrownEnderpearl.class);
        var hit = net.minecraft.world.phys.BlockHitResult.miss(Vec3.ZERO, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.ZERO);
        var pearlEvent = new EntityTeleportEvent.EnderPearl(player, 1, 2, 3, pearl, 5, hit);
        for (var event : List.of(new EntityTeleportEvent.TeleportCommand(player, 1, 2, 3),
                new EntityTeleportEvent.SpreadPlayersCommand(player, 1, 2, 3), new EntityTeleportEvent.EnderEntity(player, 1, 2, 3),
                new EntityTeleportEvent.ChorusFruit(player, 1, 2, 3), pearlEvent)) {
            assertSame(player, event.getEntity()); assertEquals(new Vec3(1, 2, 3), event.getTarget());
            event.setTargetX(4); event.setTargetY(5); event.setTargetZ(6); event.setCanceled(true);
            assertEquals(new Vec3(4, 5, 6), event.getTarget()); assertTrue(event.isCanceled());
        }
        assertSame(player, pearlEvent.getPlayer()); assertSame(pearl, pearlEvent.getPearlEntity()); assertSame(hit, pearlEvent.getHitResult());
        pearlEvent.setAttackDamage(0.5F); assertEquals(0.5F, pearlEvent.getAttackDamage());
    }

    @Test void fishingRetainsTheOriginalShallowCopyContractAndIndependentRodDamage() throws Exception {
        var hook = EventTestObjects.allocate(FixtureHook.class);
        hook.owner = EventTestObjects.allocate(ServerPlayer.class);
        var original = new ItemStack(Items.COD, 2);
        var drops = new ArrayList<>(List.of(original));
        var event = new ItemFishedEvent(drops, 1, hook);
        assertSame(hook.owner, event.getEntity()); assertSame(hook, event.getHookEntity());
        assertNotSame(drops, event.getDrops()); assertSame(original, event.getDrops().getFirst());
        event.getDrops().clear(); assertEquals(1, drops.size());
        event.damageRodBy(4); event.setCanceled(true); assertEquals(4, event.getRodDamage());
        assertThrows(IllegalArgumentException.class, () -> event.damageRodBy(-1));
    }

    @Test void explicitProjectileImpactReturnsCancellationAndKeepsTheNativeHitObject() throws Exception {
        var pearl = EventTestObjects.allocate(ThrownEnderpearl.class);
        var hit = net.minecraft.world.phys.BlockHitResult.miss(Vec3.ZERO, net.minecraft.core.Direction.UP, net.minecraft.core.BlockPos.ZERO);
        Consumer<ProjectileImpactEvent> listener = event -> {
            assertSame(pearl, event.getProjectile()); assertSame(hit, event.getRayTraceResult()); event.setCanceled(true);
        };
        FabricEvents.GAME.addListener(ProjectileImpactEvent.class, listener);
        try { assertTrue(ProjectileImpacts.onProjectileImpact(pearl, hit)); }
        finally { FabricEvents.GAME.unregister(listener); }
        assertFalse(ProjectileImpacts.onProjectileImpact(pearl, hit));
    }

    @Test void nativeCommandsAndEnderMobsHaveTheExactTargetsAndMutableLocals() throws Exception {
        var command = method("net/minecraft/server/commands/TeleportCommand", "performTeleport");
        assertEquals(3, command.localVariables.stream().filter(l -> l.desc.equals("D")).count());
        assertEquals(1, calls(command, "net/minecraft/world/entity/Entity", "teleportTo").size());
        var spread = method("net/minecraft/server/commands/SpreadPlayersCommand", "setPlayerPositions");
        assertEquals(1, calls(spread, "net/minecraft/world/entity/Entity", "teleportTo").size());
        var ender = method("net/minecraft/world/entity/monster/EnderMan", "teleport", "(DDD)Z");
        int position = calls(ender, "net/minecraft/world/entity/monster/EnderMan", "position").getFirst();
        assertTrue(position < calls(ender, "net/minecraft/world/entity/monster/EnderMan", "randomTeleport").getFirst());
        var shulker = method("net/minecraft/world/entity/monster/Shulker", "teleportSomewhere");
        assertEquals(2, shulker.localVariables.stream().filter(l -> l.desc.equals("Lnet/minecraft/core/BlockPos;")).count());
        assertEquals(1, calls(shulker, "net/minecraft/world/entity/monster/Shulker", "findAttachableSurface").size());
    }

    @Test void nativeChorusAndPearlPointsPreserveCancellationSideEffectBoundaries() throws Exception {
        var chorus = method("net/minecraft/world/item/ChorusFruitItem", "finishUsingItem");
        assertEquals(2, chorus.localVariables.stream().filter(l -> l.desc.equals("Lnet/minecraft/world/item/ItemStack;")).count());
        assertEquals(3, chorus.localVariables.stream().filter(l -> l.desc.equals("D")).count());
        assertEquals(1, calls(chorus, "net/minecraft/world/entity/LivingEntity", "randomTeleport").size());
        var pearl = method("net/minecraft/world/entity/projectile/ThrownEnderpearl", "onHit");
        int accepting = calls(pearl, "net/minecraft/server/network/ServerGamePacketListenerImpl", "isAcceptingMessages").getFirst();
        int position = calls(pearl, "net/minecraft/world/entity/projectile/ThrownEnderpearl", "position").getFirst();
        int damage = calls(pearl, "net/minecraft/world/entity/Entity", "hurt").getFirst();
        assertTrue(accepting < position && position < damage);
        assertEquals(1, calls(pearl, "net/minecraft/world/entity/Entity", "hurt").size());
        assertEquals(4, calls(pearl, "net/minecraft/world/entity/projectile/ThrownEnderpearl", "position").size());
    }

    @Test void nativeFishingLootIsMaterializedBeforeItsSecondCriterionAndBeforeItemAndXpSpawns() throws Exception {
        var retrieve = method("net/minecraft/world/entity/projectile/FishingHook", "retrieve");
        var criteria = new ArrayList<Integer>();
        for (int i = 0; i < retrieve.instructions.size(); i++) if (retrieve.instructions.get(i) instanceof org.objectweb.asm.tree.FieldInsnNode field
                && field.owner.equals("net/minecraft/advancements/CriteriaTriggers") && field.name.equals("FISHING_ROD_HOOKED")) criteria.add(i);
        assertEquals(2, criteria.size());
        int loot = calls(retrieve, "net/minecraft/world/level/storage/loot/LootTable", "getRandomItems").getFirst();
        assertTrue(criteria.getFirst() < loot && loot < criteria.get(1));
        assertTrue(criteria.get(1) < calls(retrieve, "net/minecraft/world/level/Level", "addFreshEntity").getFirst());
        assertEquals(1, retrieve.localVariables.stream().filter(l -> l.desc.equals("Ljava/util/List;")).count());
    }

    private static final class FixtureHook extends FishingHook {
        Player owner;
        FixtureHook() { super(EntityType.FISHING_BOBBER, null); }
        @Override public Player getPlayerOwner() { return owner; }
    }
    private static MethodNode method(String owner, String name) throws Exception { return method(owner, name, null); }
    private static MethodNode method(String owner, String name, String descriptor) throws Exception {
        var type = new ClassNode();
        try (var input = TeleportAndFishingEventsTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(type, 0);
        }
        var methods = type.methods.stream().filter(m -> m.name.equals(name) && (descriptor == null || m.desc.equals(descriptor))).toList();
        assertEquals(1, methods.size()); return methods.getFirst();
    }
    private static List<Integer> calls(MethodNode method, String owner, String name) {
        return java.util.stream.IntStream.range(0, method.instructions.size()).filter(i -> method.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)).boxed().toList();
    }
}
