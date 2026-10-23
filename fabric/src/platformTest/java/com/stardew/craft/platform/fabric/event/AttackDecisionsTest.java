package com.stardew.craft.platform.fabric.event;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.entity.player.AttackDecisionHooks;
import com.stardew.craft.platform.fabric.event.entity.player.CriticalHitEvent;
import com.stardew.craft.platform.fabric.event.entity.player.SweepAttackEvent;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class AttackDecisionsTest {
    @Test void criticalCanBeForcedWithItsOwnMultiplierAndIndependentSweepGate() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        Consumer<CriticalHitEvent> listener = event -> {
            assertFalse(event.isVanillaCritical()); assertEquals(1, event.getVanillaMultiplier());
            event.setCriticalHit(true); event.setDamageMultiplier(2.25F); event.setDisableSweep(false);
        };
        FabricEvents.GAME.addListener(CriticalHitEvent.class, listener);
        try {
            var event = AttackDecisionHooks.critical(player, player, false);
            assertTrue(event.isCriticalHit()); assertEquals(2.25F, event.getDamageMultiplier());
            assertFalse(AttackDecisionHooks.blocksSweep(event));
            event.setDisableSweep(true); assertTrue(AttackDecisionHooks.blocksSweep(event));
            event.setCriticalHit(false); assertFalse(AttackDecisionHooks.blocksSweep(event));
            assertThrows(UnsupportedOperationException.class, () -> event.setDamageMultiplier(-1));
        } finally { FabricEvents.GAME.unregister(listener); }
    }

    @Test void defaultCriticalRetainsVanillaStatusAndMultiplier() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        var event = AttackDecisionHooks.critical(player, player, true);
        assertTrue(event.isVanillaCritical()); assertTrue(event.isCriticalHit());
        assertEquals(1.5F, event.getVanillaMultiplier()); assertEquals(1.5F, event.getDamageMultiplier());
        assertTrue(AttackDecisionHooks.blocksSweep(event));
    }

    @Test void canceledSweepRetainsChosenValueAndStopsOrdinaryLaterHandlers() throws Exception {
        var player = EventTestObjects.allocate(ServerPlayer.class);
        Consumer<SweepAttackEvent> force = event -> {
            assertFalse(event.isVanillaSweep()); event.setSweeping(true); event.setCanceled(true);
        };
        Consumer<SweepAttackEvent> later = event -> fail("ordinary canceled-event listener");
        FabricEvents.GAME.addListener(EventPriority.HIGHEST, false, SweepAttackEvent.class, force);
        FabricEvents.GAME.addListener(SweepAttackEvent.class, later);
        try { assertTrue(AttackDecisionHooks.sweep(player, player, false)); }
        finally { FabricEvents.GAME.unregister(force); FabricEvents.GAME.unregister(later); }
    }

    @Test void nativeCriticalLocalsMultiplierAndSweepBoundaryAreUniqueAndPrecedeDamage() throws Exception {
        var type = nativeType("net/minecraft/world/entity/player/Player");
        var method = type.methods.stream().filter(m -> m.name.equals("attack")).findFirst().orElseThrow();
        var instructions = method.instructions.toArray();
        int stores = 0, multiplier = -1, speed = -1, health = -1, movement = -1, hurt = -1;
        var loads = new java.util.ArrayList<Integer>();
        for (int i = 0; i < instructions.length; i++) {
            var instruction = instructions[i];
            if (instruction instanceof VarInsnNode local && local.var == 9) {
                if (local.getOpcode() == Opcodes.ISTORE) stores++;
                if (local.getOpcode() == Opcodes.ILOAD) loads.add(i);
            }
            if (instruction instanceof LdcInsnNode constant && Float.valueOf(1.5F).equals(constant.cst)) {
                assertEquals(-1, multiplier); multiplier = i;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("getSpeed")) { assertEquals(-1, speed); speed = i; }
                if (call.name.equals("getHealth") && health == -1) health = i;
                if (call.owner.equals("net/minecraft/world/entity/Entity") && call.name.equals("getDeltaMovement")) movement = i;
                if (call.owner.equals("net/minecraft/world/entity/Entity") && call.name.equals("hurt")) hurt = i;
            }
        }
        assertEquals(1, stores); assertTrue(loads.size() >= 3);
        assertTrue(loads.get(0) < multiplier && multiplier < loads.get(1));
        assertTrue(loads.get(1) < speed);
        int zero = -1;
        for (int i = speed + 1; i < movement; i++) if (instructions[i].getOpcode() == Opcodes.FCONST_0) {
            assertEquals(-1, zero); zero = i;
        }
        assertTrue(speed < zero && zero < health && health < movement && movement < hurt);
        assertEquals(Opcodes.FSTORE, instructions[zero + 1].getOpcode());
        assertEquals(14, ((VarInsnNode) instructions[zero + 1]).var);
    }

    @Test void vanillaDragonPartsExposeTheirActualParentAndNoOwnedPartSubclassesAreAssumed() throws Exception {
        var part = nativeType("net/minecraft/world/entity/boss/EnderDragonPart");
        assertEquals(1, part.fields.stream().filter(f -> f.name.equals("parentMob")
                && f.desc.equals("Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;") && (f.access & Opcodes.ACC_FINAL) != 0).count());
        var bridge = nativeType("com/stardew/craft/platform/mixin/entity/mixin/DragonPartParentMixin");
        assertTrue(bridge.interfaces.contains("com/stardew/craft/platform/fabric/entity/PartEntity"));
    }

    private static ClassNode nativeType(String name) throws Exception {
        var result = new ClassNode();
        try (var input = AttackDecisionsTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(result, 0);
        }
        return result;
    }
}
