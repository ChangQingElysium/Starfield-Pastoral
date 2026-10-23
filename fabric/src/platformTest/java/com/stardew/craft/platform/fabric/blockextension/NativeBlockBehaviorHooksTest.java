package com.stardew.craft.platform.fabric.blockextension;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

class NativeBlockBehaviorHooksTest {
    private static final String BASE = "com.stardew.craft.platform.mixin.blockextension.";

    @Test void everyConsumerWrapperTargetsTheRealNativeInvocationAndRequiresItsExactCount() throws Exception {
        var targets = new LinkedHashMap<String, String>();
        targets.put("mixin.LivingBlockPropertiesMixin", "net.minecraft.world.entity.LivingEntity");
        targets.put("mixin.FlyingMobFrictionMixin", "net.minecraft.world.entity.FlyingMob");
        targets.put("mixin.ItemEntityFrictionMixin", "net.minecraft.world.entity.item.ItemEntity");
        targets.put("mixin.ExperienceOrbFrictionMixin", "net.minecraft.world.entity.ExperienceOrb");
        targets.put("mixin.BoatFrictionMixin", "net.minecraft.world.entity.vehicle.Boat");
        targets.put("mixin.EntitySoundMixin", "net.minecraft.world.entity.Entity");
        targets.put("mixin.PlayerStepContextMixin", "net.minecraft.world.entity.player.Player");
        targets.put("mixin.HorseSoundMixin", "net.minecraft.world.entity.animal.horse.AbstractHorse");
        targets.put("mixin.SnifferSoundMixin", "net.minecraft.world.entity.animal.sniffer.Sniffer");
        targets.put("mixin.BlockItemSoundMixin", "net.minecraft.world.item.BlockItem");
        targets.put("mixin.ExplosionResistanceMixin", "net.minecraft.world.level.ExplosionDamageCalculator");
        targets.put("client.MiningSoundMixin", "net.minecraft.client.multiplayer.MultiPlayerGameMode");
        targets.put("client.BlockBreakSoundMixin", "net.minecraft.client.renderer.LevelRenderer");
        int checked = 0;
        for (var target : targets.entrySet()) {
            var mixin = Class.forName(BASE + target.getKey(), false, getClass().getClassLoader());
            for (var method : mixin.getDeclaredMethods()) {
                var hook = method.getAnnotation(WrapOperation.class); if (hook == null) continue;
                assertEquals(1, hook.method().length); assertTrue(hook.require() > 0);
                String selector = hook.at()[0].target();
                String expected = selector.substring(1, selector.indexOf(';')) + "." + selector.substring(selector.indexOf(';') + 1);
                long count = calls(nativeMethod(target.getValue(), hook.method()[0])).stream().filter(expected::equals).count();
                assertEquals(hook.require(), count, mixin.getSimpleName() + "." + method.getName() + " -> " + expected);
                checked++;
            }
        }
        assertEquals(20, checked);
    }

    @Test void boatFrictionCapturesTheActualSingleContactStateAndMutablePositionAfterCollisionTesting() throws Exception {
        var method = nativeMethod("net.minecraft.world.entity.vehicle.Boat", "getGroundFriction");
        var invocation = invocation(method, "net/minecraft/world/level/block/Block", "getFriction");
        var locals = localsAt("net/minecraft/world/entity/vehicle/Boat", method, invocation);
        assertEquals(List.of(17), slots(locals, "Lnet/minecraft/world/level/block/state/BlockState;"));
        assertEquals(List.of(12), slots(locals, "Lnet/minecraft/core/BlockPos$MutableBlockPos;"));
        var calls = calls(method);
        assertTrue(calls.indexOf("net/minecraft/world/phys/shapes/Shapes.joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
                < calls.indexOf("net/minecraft/world/level/block/Block.getFriction()F"));
        assertEquals(1, java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).filter(insn -> insn.getOpcode() == Opcodes.FDIV).count());
    }

    @Test void placementCapturesTheUpdatedContextRatherThanTheOriginalMethodArgument() throws Exception {
        var method = nativeMethod("net.minecraft.world.item.BlockItem", "place");
        for (var target : List.of("getSoundType", "getPlaceSound")) {
            var call = invocation(method, target.equals("getSoundType") ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/world/item/BlockItem", target);
            var locals = localsAt("net/minecraft/world/item/BlockItem", method, call);
            assertEquals(List.of(1, 2), slots(locals, "Lnet/minecraft/world/item/context/BlockPlaceContext;"));
        }
        var calls = calls(method);
        assertTrue(calls.indexOf("net/minecraft/world/item/BlockItem.updatePlacementContext(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/item/context/BlockPlaceContext;")
                < calls.indexOf("net/minecraft/world/item/BlockItem.getPlaceSound(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/sounds/SoundEvent;"));
    }

    @Test void primaryPlayerAndSnifferPositionsExistAtTheirSoundCalls() throws Exception {
        var player = nativeMethod("net.minecraft.world.entity.player.Player", "playStepSound");
        var combined = invocation(player, "net/minecraft/world/entity/player/Player", "playCombinationStepSounds");
        assertEquals(List.of(1, 3), slots(localsAt("net/minecraft/world/entity/player/Player", player, combined), "Lnet/minecraft/core/BlockPos;"));
        var sniffer = nativeMethod("net.minecraft.world.entity.animal.sniffer.Sniffer", "emitDiggingParticles");
        var sound = invocation(sniffer, "net/minecraft/world/level/block/state/BlockState", "getSoundType");
        assertEquals(1, slots(localsAt("net/minecraft/world/entity/animal/sniffer/Sniffer", sniffer, sound), "Lnet/minecraft/core/BlockPos;").size());
    }

    @Test void explosionFluidMaximumAndEmptySpaceHandlingRemainNativeOutsideTheBlockQuery() throws Exception {
        var calls = calls(nativeMethod(ExplosionDamageCalculator.class.getName(), "getBlockExplosionResistance"));
        assertTrue(calls.contains("net/minecraft/world/level/block/state/BlockState.isAir()Z"));
        assertTrue(calls.contains("net/minecraft/world/level/material/FluidState.isEmpty()Z"));
        assertTrue(calls.contains("net/minecraft/world/level/material/FluidState.getExplosionResistance()F"));
        assertTrue(calls.contains("java/lang/Math.max(FF)F"));
        assertTrue(calls.contains("java/util/Optional.empty()Ljava/util/Optional;"));
    }

    @Test void climbingUsesTheNativeSpectatorGuardLastPositionAndOpenTrapdoorFacingChecks() throws Exception {
        var method = nativeMethod(LivingEntity.class.getName(), "onClimbable");
        var calls = calls(method);
        assertTrue(calls.indexOf("net/minecraft/world/entity/LivingEntity.isSpectator()Z")
                < calls.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/tags/TagKey;)Z"));
        assertTrue(calls.contains("net/minecraft/world/entity/LivingEntity.trapdoorUsableAsLadder(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"));
        var trapdoor = nativeMethod(LivingEntity.class.getName(), "trapdoorUsableAsLadder");
        assertEquals(3, calls(trapdoor).stream().filter(call -> call.endsWith(".getValue(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;")).count());
        assertTrue(java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(insn ->
                insn instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("lastClimbablePos") && field.getOpcode() == Opcodes.PUTFIELD));
    }

    @Test void mapColorHookUsesTheNativeViewAndPositionInsteadOfOnlyAStaticBlockProperty() throws Exception {
        var method = nativeMethod(BlockBehaviour.BlockStateBase.class.getName(), "getMapColor");
        assertEquals("(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/MapColor;", method.desc);
        var mixin = Class.forName(BASE + "mixin.MapColorMixin");
        var hook = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(m -> m.getAnnotation(com.llamalad7.mixinextras.injector.ModifyReturnValue.class))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertEquals("RETURN", hook.at()[0].value()); assertEquals(1, hook.require());
    }

    @Test void commonContractsAndConsumersCanLinkWithoutMinecraftClientClasses() throws Exception {
        for (var name : List.of("BlockBehaviorProvider", "BlockBehaviors", "BlockMaterialSnapshots", "StepSoundContext", "mixin.MapColorMixin", "mixin.ExplosionResistanceMixin",
                "mixin.LivingBlockPropertiesMixin", "mixin.FlyingMobFrictionMixin", "mixin.ItemEntityFrictionMixin", "mixin.ExperienceOrbFrictionMixin",
                "mixin.BoatFrictionMixin", "mixin.EntitySoundMixin", "mixin.PlayerStepContextMixin", "mixin.HorseSoundMixin", "mixin.SnifferSoundMixin", "mixin.BlockItemSoundMixin")) {
            try (var stream = resource((name.startsWith("mixin.") ? BASE : "com.stardew.craft.platform.fabric.blockextension.") + name)) {
                var bytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/"), name); assertFalse(bytes.contains("net/fabricmc/api/Environment"), name);
            }
        }
    }

    private static MethodNode nativeMethod(String className, String name) throws Exception {
        var node = new ClassNode(); try (var stream = resource(className)) { new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); }
        var methods = node.methods.stream().filter(method -> method.name.equals(name)).toList();
        assertEquals(1, methods.size(), className + "." + name + " must select one native method"); return methods.getFirst();
    }
    private static InputStream resource(String className) { var stream = NativeBlockBehaviorHooksTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class"); assertNotNull(stream, className); return stream; }
    private static List<String> calls(MethodNode method) {
        var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result;
    }
    private static MethodInsnNode invocation(MethodNode method, String owner, String name) {
        var matches = java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(insn -> insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)).toList();
        assertEquals(1, matches.size(), owner + "." + name); return (MethodInsnNode) matches.getFirst();
    }
    private static List<BasicValue> localsAt(String owner, MethodNode method, AbstractInsnNode call) throws Exception {
        var frames = new Analyzer<BasicValue>(new TypedInterpreter()).analyze(owner, method);
        var frame = frames[method.instructions.indexOf(call)]; var result = new ArrayList<BasicValue>();
        for (int i = 0; i < frame.getLocals(); i++) result.add(frame.getLocal(i)); return result;
    }
    private static List<Integer> slots(List<BasicValue> locals, String descriptor) {
        var result = new ArrayList<Integer>(); for (int i = 0; i < locals.size(); i++) if (locals.get(i).getType() != null && locals.get(i).getType().getDescriptor().equals(descriptor)) result.add(i); return result;
    }
    private static final class TypedInterpreter extends BasicInterpreter {
        TypedInterpreter() { super(Opcodes.ASM9); }
        @Override public BasicValue newValue(Type type) { return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) ? new BasicValue(type) : super.newValue(type); }
        @Override public BasicValue newOperation(AbstractInsnNode insn) throws org.objectweb.asm.tree.analysis.AnalyzerException {
            return insn.getOpcode() == Opcodes.NEW ? new BasicValue(Type.getObjectType(((TypeInsnNode) insn).desc)) : super.newOperation(insn);
        }
    }
}
