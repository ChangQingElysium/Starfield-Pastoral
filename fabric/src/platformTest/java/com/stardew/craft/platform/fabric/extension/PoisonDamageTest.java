package com.stardew.craft.platform.fabric.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class PoisonDamageTest {
    @Test void poisonUsesStableSavedIdAndFallsBackForVanillaConnections() {
        var registry = new MappedRegistry<DamageType>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        var magic = Registry.registerForHolder(registry, net.minecraft.world.damagesource.DamageTypes.MAGIC, new DamageType("magic", 0));
        var vanilla = new DamageSource(magic);
        var registries = new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
        assertSame(vanilla, PoisonDamage.source(registries, () -> vanilla));
        var poison = Registry.registerForHolder(registry, PoisonDamage.TYPE, new DamageType("magic", 0));
        var result = PoisonDamage.source(registries, () -> fail("registered poison must not use magic fallback"));
        assertSame(poison, result.typeHolder());
        assertEquals("neoforge:poison", PoisonDamage.TYPE.location().toString());
        assertEquals("neoforge:is_poison", Tags.DamageTypes.IS_POISON.location().toString());
    }

    @Test void nativePoisonKeepsTheNonlethalHealthGuardAndOnlyReplacesItsOneMagicSource() throws Exception {
        var type = new ClassNode();
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/effect/PoisonMobEffect.class")) {
            assertNotNull(bytes); new ClassReader(bytes).accept(type, 0);
        }
        var method = type.methods.stream().filter(m -> m.name.equals("applyEffectTick")).findFirst().orElseThrow();
        var calls = java.util.Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i).toList();
        assertEquals(1, calls.stream().filter(c -> c.name.equals("magic")).count());
        int health = -1, magic = -1, hurt = -1;
        for (int i = 0; i < calls.size(); i++) {
            switch (calls.get(i).name) { case "getHealth" -> health = i; case "magic" -> magic = i; case "hurt" -> hurt = i; }
        }
        assertTrue(health >= 0 && health < magic && magic < hurt);
    }
}
