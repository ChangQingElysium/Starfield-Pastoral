package com.stardew.craft.platform.fabric.blockextension.removal;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.SugarCaneBlock;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

class NativeBlockLifecycleHooksTest {
    private static final String BASE = "com.stardew.craft.platform.mixin.blockextension.";
    private static final List<String> MIXINS = List.of("removal.mixin.ServerPlayerRemovalMixin", "removal.client.ClientPlayerRemovalMixin", "rail.mixin.RailStateRulesMixin",
            "plant.mixin.UpwardPlantSupportMixin", "plant.mixin.MushroomSoilMixin", "plant.mixin.CactusSoilMixin", "plant.mixin.SugarCaneSoilMixin", "plant.mixin.DirectionalPlantSoilMixin",
            "plant.mixin.SmallDripleafSoilMixin", "plant.mixin.BambooPlacementSoilMixin", "plant.mixin.TrunkSoilPreservationMixin", "plant.mixin.CropSoilMixin", "plant.mixin.CropGrowthSoilMixin",
            "plant.mixin.PitcherSoilMixin", "plant.mixin.ChorusPlantSoilMixin", "plant.mixin.ChorusFlowerGrowthSoilMixin");

    @Test void everyDeclaredInjectionResolvesAgainstThePinnedRealMinecraftClasses() throws Exception {
        int checked = 0;
        for (var name : MIXINS) {
            var mixin = Class.forName(BASE + name); var node = node(mixin);
            var annotation = java.util.stream.Stream.concat(node.visibleAnnotations == null ? java.util.stream.Stream.empty() : node.visibleAnnotations.stream(),
                    node.invisibleAnnotations == null ? java.util.stream.Stream.empty() : node.invisibleAnnotations.stream()).filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var values = (List<Type>) annotation.values.get(annotation.values.indexOf("value") + 1);
            for (var target : values) {
                var targetClass = Class.forName(target.getClassName(), false, getClass().getClassLoader());
                for (var method : mixin.getDeclaredMethods()) {
                    var wrap = method.getAnnotation(WrapOperation.class); var inject = method.getAnnotation(Inject.class); var modify = method.getAnnotation(ModifyReturnValue.class);
                    String[] selectors; At[] ats; int required;
                    if (wrap != null) { selectors = wrap.method(); ats = wrap.at(); required = wrap.require(); }
                    else if (inject != null) { selectors = inject.method(); ats = inject.at(); required = inject.require(); }
                    else if (modify != null) { selectors = modify.method(); ats = modify.at(); required = modify.require(); }
                    else continue;
                    assertTrue(required > 0); int count = 0;
                    for (var selector : selectors) {
                        var nativeMethod = method(targetClass, selector);
                        for (var at : ats) {
                            if (at.value().equals("HEAD")) count++;
                            else if (at.value().equals("RETURN") || at.value().equals("TAIL")) {
                                int returns = 0; for (var insn : nativeMethod.instructions) if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) returns++;
                                count += at.value().equals("TAIL") ? 1 : returns;
                            } else if (at.value().equals("INVOKE")) {
                                String raw = at.target(); String call = raw.substring(1, raw.indexOf(';')) + "." + raw.substring(raw.indexOf(';') + 1);
                                long matches = calls(nativeMethod).stream().filter(call::equals).count();
                                if (at.ordinal() >= 0) { assertTrue(matches > at.ordinal(), name + " -> " + target + " " + selector + " missing ordinal " + at.ordinal()); count++; }
                                else count += (int) matches;
                            } else fail("unverified injection point " + at.value());
                        }
                    }
                    assertEquals(required, count, name + "." + method.getName() + " -> " + target.getClassName()); checked++;
                }
            }
        }
        assertEquals(34, checked, "not all multi-target consumers were checked");
    }

    @Test void serverRemovalCapturesPlayerWillDestroysReturnedStateRatherThanTheOriginalState() throws Exception {
        var method = method(ServerPlayerGameMode.class, "destroyBlock");
        var target = invocation(method, "net/minecraft/server/level/ServerLevel", "removeBlock", 0);
        var locals = locals(ServerPlayerGameMode.class, method, target);
        assertEquals(List.of(4, 5), slots(locals, "Lnet/minecraft/world/level/block/state/BlockState;"));
        assertEquals(List.of(2), slots(locals, "Lnet/minecraft/world/level/block/entity/BlockEntity;"));
        assertEquals(List.of(3), slots(locals, "Lnet/minecraft/world/level/block/Block;"));
        var calls = calls(method);
        assertTrue(calls.indexOf("net/minecraft/world/level/block/Block.playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;")
                < calls.indexOf("net/minecraft/server/level/ServerLevel.removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"));
        assertTrue(calls.stream().anyMatch(call -> call.contains("blockActionRestricted"))); assertTrue(calls.stream().anyMatch(call -> call.contains("canUseGameMasterBlocks")));
    }

    @Test void railPlacementHasOneFallbackAndOneMutableCandidateAndItsSlopeQueriesAreExactlyTheEightAscents() throws Exception {
        var method = method(RailState.class, "place"); var update = invocation(method, "net/minecraft/world/level/block/RailState", "updateConnections", 0);
        assertEquals(2, slots(locals(RailState.class, method, update), "Lnet/minecraft/world/level/block/state/properties/RailShape;").size());
        String rail = "net/minecraft/world/level/block/BaseRailBlock.isRail(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z";
        assertEquals(4, calls(method).stream().filter(rail::equals).count()); assertEquals(4, calls(method(RailState.class, "connectTo")).stream().filter(rail::equals).count());
        var connect = method(RailState.class, "connectTo"); var property = invocation(connect, "net/minecraft/world/level/block/BaseRailBlock", "getShapeProperty", 0);
        assertEquals(1, slots(locals(RailState.class, connect, property), "Lnet/minecraft/world/level/block/state/properties/RailShape;").size());
    }

    @Test void plantSoilHooksRetainTheCactusMushroomSugarCaneAndChorusPrefixGuards() throws Exception {
        var cactus = calls(method(CactusBlock.class, "canSurvive"));
        assertTrue(cactus.indexOf("net/minecraft/world/level/block/state/BlockState.isSolid()Z") < cactus.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/world/level/block/Block;)Z"));
        assertTrue(cactus.indexOf("net/minecraft/world/level/material/FluidState.is(Lnet/minecraft/tags/TagKey;)Z") < cactus.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/world/level/block/Block;)Z"));
        var mushroom = calls(method(MushroomBlock.class, "canSurvive"));
        assertTrue(mushroom.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/tags/TagKey;)Z") < mushroom.indexOf("net/minecraft/world/level/LevelReader.getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"));
        var cane = calls(method(SugarCaneBlock.class, "canSurvive"));
        assertTrue(cane.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/world/level/block/Block;)Z") < cane.indexOf("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/tags/TagKey;)Z"));
        assertEquals(5, calls(method(ChorusPlantBlock.class, "canSurvive")).stream().filter("net/minecraft/world/level/block/state/BlockState.is(Lnet/minecraft/world/level/block/Block;)Z"::equals).count());
    }

    @Test void cropAndChorusGrowthCapturesMatchTheActualNativeLoopIndices() throws Exception {
        var crop = method(CropBlock.class, "getGrowthSpeed"); var moisture = invocation(crop, "net/minecraft/world/level/block/state/BlockState", "getValue", 0);
        var cropLocals = locals(CropBlock.class, crop, moisture); assertEquals(Type.INT_TYPE, cropLocals.get(5).getType()); assertEquals(Type.INT_TYPE, cropLocals.get(6).getType());
        var chorus = method(ChorusFlowerBlock.class, "randomTick"); var root = invocation(chorus, "net/minecraft/world/level/block/state/BlockState", "is", 3);
        assertEquals(Type.INT_TYPE, locals(ChorusFlowerBlock.class, chorus, root).get(10).getType());
    }

    private static ClassNode node(Class<?> type) throws Exception { var result = new ClassNode(); try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { assertNotNull(stream); new ClassReader(stream).accept(result, ClassReader.SKIP_FRAMES); } return result; }
    private static MethodNode method(Class<?> type, String name) throws Exception { var methods = node(type).methods.stream().filter(method -> method.name.equals(name)).toList(); assertEquals(1, methods.size(), type.getName() + "." + name); return methods.getFirst(); }
    private static List<String> calls(MethodNode method) { var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
    private static MethodInsnNode invocation(MethodNode method, String owner, String name, int ordinal) { var result = new ArrayList<MethodInsnNode>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result.add(call); assertTrue(result.size() > ordinal, owner + "." + name); return result.get(ordinal); }
    private static List<BasicValue> locals(Class<?> owner, MethodNode method, AbstractInsnNode call) throws Exception { var frame = new Analyzer<BasicValue>(new TypedInterpreter()).analyze(Type.getInternalName(owner), method)[method.instructions.indexOf(call)]; var result = new ArrayList<BasicValue>(); for (int i = 0; i < frame.getLocals(); i++) result.add(frame.getLocal(i)); return result; }
    private static List<Integer> slots(List<BasicValue> locals, String desc) { var result = new ArrayList<Integer>(); for (int i = 0; i < locals.size(); i++) if (locals.get(i) != null && locals.get(i).getType() != null && locals.get(i).getType().getDescriptor().equals(desc)) result.add(i); return result; }
    private static final class TypedInterpreter extends BasicInterpreter {
        TypedInterpreter() { super(Opcodes.ASM9); }
        @Override public BasicValue newValue(Type type) { return type != null && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) ? new BasicValue(type) : super.newValue(type); }
        @Override public BasicValue newOperation(AbstractInsnNode insn) throws org.objectweb.asm.tree.analysis.AnalyzerException { return insn.getOpcode() == Opcodes.NEW ? new BasicValue(Type.getObjectType(((TypeInsnNode) insn).desc)) : super.newOperation(insn); }
        @Override public BasicValue merge(BasicValue left, BasicValue right) {
            if (left.getType() != null && left.getType().getInternalName().equals("null")) return right;
            if (right.getType() != null && right.getType().getInternalName().equals("null")) return left;
            return super.merge(left, right);
        }
    }
}
