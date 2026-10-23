package com.stardew.craft.platform.fabric.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class MobEffectAttributeFunctionsTest {
    @Test void blessingStaysConstantWhileSpeedAndFuryScaleWithAmplifier() {
        var id = ResourceLocation.parse("stardewcraft:effect.speed");
        for (int amplifier : new int[]{0, 1, 4, 255}) {
            var speed = MobEffectAttributeFunctions.create(id, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    level -> .2 * (level + 1), amplifier);
            var blessing = MobEffectAttributeFunctions.create(id, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    level -> .1, amplifier);
            var fury = MobEffectAttributeFunctions.create(id, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                    level -> .1 * (level + 1), amplifier);
            assertEquals(.2 * (amplifier + 1), speed.amount());
            assertEquals(.1, blessing.amount());
            assertEquals(.1 * (amplifier + 1), fury.amount());
            assertEquals(id, blessing.id());
            assertSame(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, blessing.operation());
        }
    }

    @Test void nativeInspectionAndActualAttributeApplicationShareTheSameTemplateCreateMethod() throws Exception {
        var type = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/effect/MobEffect.class")) {
            assertNotNull(input); new ClassReader(input).accept(type, 0);
        }
        var callers = new ArrayList<String>();
        for (var method : type.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/effect/MobEffect$AttributeTemplate")
                    && call.name.equals("create")) {
                assertEquals("(I)Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;", call.desc);
                callers.add(method.name);
            }
        }
        assertEquals(2, callers.size(), "Both native modifier consumers must pass through the injected template");
        var template = new ClassNode();
        try (var input = getClass().getClassLoader().getResourceAsStream("net/minecraft/world/effect/MobEffect$AttributeTemplate.class")) {
            assertNotNull(input); new ClassReader(input).accept(template, 0);
        }
        assertTrue(template.fields.stream().anyMatch(f -> f.name.equals("id") && f.desc.equals("Lnet/minecraft/resources/ResourceLocation;")));
        assertTrue(template.fields.stream().anyMatch(f -> f.name.equals("operation") && f.desc.endsWith("AttributeModifier$Operation;")));
    }
}
