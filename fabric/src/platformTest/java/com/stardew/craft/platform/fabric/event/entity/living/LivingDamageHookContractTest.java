package com.stardew.craft.platform.fabric.event.entity.living;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Checks every targeted 1.21.1 native stage without running a world or a damage simulation. */
class LivingDamageHookContractTest {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";

    @Test
    void incomingIsAfterTheFourNativeGuardsAndBeforeBlockingAndBothCooldownBranches() throws IOException {
        var hurt = method(LIVING, "hurt");
        int incoming = call(hurt, LIVING, "isSleeping").getFirst();
        assertEquals(4, opcodes(hurt, 0, incoming, Opcodes.IRETURN));
        assertTrue(incoming < call(hurt, LIVING, "hurtCurrentlyUsedShield").getFirst());
        int adjustments = call(hurt, "net/minecraft/world/entity/WalkAnimationState", "setSpeed").getFirst();
        assertTrue(adjustments > call(hurt, LIVING, "hurtHelmet").getFirst());
        var actual = call(hurt, LIVING, "actuallyHurt");
        assertEquals(2, actual.size());
        assertTrue(adjustments < actual.getFirst());
        assertEquals(1, java.util.stream.StreamSupport.stream(hurt.instructions.spliterator(), false)
                .filter(instruction -> instruction instanceof IntInsnNode constant && constant.operand == 20).count());
        int entityLookup = call(hurt, "net/minecraft/world/damagesource/DamageSource", "getEntity").getFirst();
        assertTrue(entityLookup > actual.getLast());
        assertTrue(entityLookup < call(hurt, LIVING, "die").getFirst());
        assertEquals(1, call(method(PLAYER, "hurt"), LIVING, "hurt").size());
    }

    @Test
    void livingAndPlayerActuallyHurtHaveTheSameMitigationBoundaryButDifferentAbsorptionCalls() throws IOException {
        for (String owner : List.of(LIVING, PLAYER)) {
            var actual = method(owner, "actuallyHurt");
            int armor = call(actual, owner, "getDamageAfterArmorAbsorb").getFirst();
            int magic = call(actual, owner, "getDamageAfterMagicAbsorb").getFirst();
            int absorption = call(actual, "java/lang/Math", "max").getFirst();
            int consume = call(actual, owner, "setAbsorptionAmount").getFirst();
            int health = call(actual, owner, "setHealth").getFirst();
            assertTrue(armor < magic && magic < absorption && absorption < consume && consume < health);
            assertEquals(1, call(actual, "java/lang/Math", "max").size());
            assertEquals(3, opcodes(actual, 0, actual.instructions.size(), Opcodes.RETURN));
            assertEquals(1, java.util.stream.StreamSupport.stream(actual.instructions.spliterator(), false)
                    .filter(instruction -> instruction instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.FSTORE && variable.var == 4).count());
            assertTrue(actual.localVariables.stream().anyMatch(local -> local.index == 4 && local.desc.equals("F")));
            var calls = call(actual, owner, "setAbsorptionAmount");
            if (owner.equals(LIVING)) {
                assertEquals(2, calls.size());
                assertTrue(calls.getLast() > health); // Neo removes precisely this second consumption.
            } else assertEquals(1, calls.size());
        }
    }

    @Test
    void magicAndKnockbackHooksRetainTheirSeparateResistanceEnchantmentsAndNativeResistancePhases() throws IOException {
        var magic = method(LIVING, "getDamageAfterMagicAbsorb");
        assertEquals(1, call(magic, "java/lang/Math", "max").size());
        assertEquals(1, call(magic, "net/minecraft/world/damagesource/CombatRules", "getDamageAfterMagicAbsorb").size());
        assertTrue(call(magic, "java/lang/Math", "max").getFirst()
                < call(magic, "net/minecraft/world/damagesource/CombatRules", "getDamageAfterMagicAbsorb").getFirst());
        var knockback = method(LIVING, "knockback");
        int resistance = call(knockback, LIVING, "getAttributeValue").getFirst();
        int motion = call(knockback, LIVING, "setDeltaMovement").getFirst();
        assertTrue(resistance < motion);
        assertTrue(java.util.stream.StreamSupport.stream(knockback.instructions.spliterator(), false)
                .anyMatch(instruction -> instruction instanceof FieldInsnNode field && field.name.equals("KNOCKBACK_RESISTANCE")));
    }

    private static int opcodes(MethodNode method, int from, int to, int opcode) {
        int count = 0;
        for (int i = from; i < to; i++) if (method.instructions.get(i).getOpcode() == opcode) count++;
        return count;
    }

    private static List<Integer> call(MethodNode method, String owner, String name) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result.add(i);
        }
        assertFalse(result.isEmpty(), owner + "." + name);
        return result;
    }

    private static MethodNode method(String owner, String name) throws IOException {
        try (var bytes = LivingDamageHookContractTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
        }
    }
}
