package com.stardew.craft.platform.fabric.item.repair;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class GrindstoneRepairContractTest {
    @Test
    void repairChecksUseOnlyTheMergeGateAndDamageWriteWithoutChangingSingleItemDisenchantment() throws Exception {
        var node = new ClassNode();
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/inventory/GrindstoneMenu.class")) {
            assertNotNull(bytes); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        }
        var merge = node.methods.stream().filter(method -> method.name.equals("mergeItems")).findFirst().orElseThrow();
        List<Integer> damageability = new ArrayList<>(); int copy = -1; int damage = -1;
        for (int i = 0; i < merge.instructions.size(); i++) if (merge.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/item/ItemStack")) {
            if (call.name.equals("isDamageableItem")) damageability.add(i);
            if (call.name.equals("copyWithCount")) copy = i;
            if (call.name.equals("setDamageValue")) { assertEquals(-1, damage); damage = i; }
        }
        assertEquals(2, damageability.size()); assertTrue(damageability.getFirst() < copy); assertTrue(copy < damageability.getLast()); assertTrue(damageability.getLast() < damage);
        assertTrue(node.methods.stream().filter(method -> method.name.equals("computeResult")).findFirst().orElseThrow().instructions.size() > 0);
    }
}
