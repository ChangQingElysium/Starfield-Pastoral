package com.stardew.craft.platform.mixin.event.level.growth.mixin;

import com.stardew.craft.platform.mixin.event.level.growth.mixin.BambooGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.BerryGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.BoneMealApplicationMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.ChorusGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.CocoaGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.ColumnGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.FarmlandTrampleMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.FluidPlaceBlockMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.FungusFeatureGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.GrowingHeadGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.MushroomFeatureGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.PistonMovementMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.RandomCropGrowthMixin;
import com.stardew.craft.platform.mixin.event.level.growth.mixin.TreeFeatureGrowthMixin;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

class NativeGrowthTargetsTest {
    private static final List<Class<?>> MIXINS = List.of(RandomCropGrowthMixin.class, BambooGrowthMixin.class,
            CocoaGrowthMixin.class, BerryGrowthMixin.class, GrowingHeadGrowthMixin.class, ColumnGrowthMixin.class,
            ChorusGrowthMixin.class, TreeFeatureGrowthMixin.class, MushroomFeatureGrowthMixin.class,
            FungusFeatureGrowthMixin.class, FarmlandTrampleMixin.class, FluidPlaceBlockMixin.class,
            PistonMovementMixin.class, BoneMealApplicationMixin.class);

    @Test void everyGrowthProducerResolvesItsExactDeclaredNativeInvocationCount() throws Exception {
        int groups = 0;
        for (var mixin : MIXINS) {
            var node = node(mixin);
            var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
            if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
            var annotation = annotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var targets = (List<Type>) annotation.values.get(annotation.values.indexOf("value") + 1);
            for (var target : targets) for (var handler : mixin.getDeclaredMethods()) {
                var wrap = handler.getAnnotation(WrapOperation.class); var inject = handler.getAnnotation(Inject.class);
                var modify = handler.getAnnotation(ModifyExpressionValue.class); var whole = handler.getAnnotation(WrapMethod.class);
                String[] selectors; At[] points; int require;
                if (wrap != null) { selectors = wrap.method(); points = wrap.at(); require = wrap.require(); }
                else if (inject != null) { selectors = inject.method(); points = inject.at(); require = inject.require(); }
                else if (modify != null) { selectors = modify.method(); points = modify.at(); require = modify.require(); }
                else if (whole != null) { for (var selector : whole.method()) assertNotNull(method(target.getClassName(), selector)); groups++; continue; }
                else continue;
                assertTrue(require > 0, handler.toString()); int count = 0;
                for (var selector : selectors) {
                    var method = method(target.getClassName(), selector);
                    for (var at : points) {
                        assertEquals("INVOKE", at.value()); String raw = at.target();
                        String call = raw.substring(1, raw.indexOf(';')) + "." + raw.substring(raw.indexOf(';') + 1);
                        long matches = calls(method).stream().filter(call::equals).count();
                        if (at.ordinal() >= 0) { assertTrue(matches > at.ordinal(), handler + " missing ordinal " + at.ordinal()); count++; }
                        else count += (int) matches;
                    }
                }
                assertEquals(require, count, handler + " -> " + target.getClassName()); groups++;
            }
        }
        assertEquals(23, groups, "a native multi-target producer was omitted");
    }
    @Test void farmlandProducerRetainsNativeRandomEntitySizeAndMobGriefingGuardsAndFallDamage() throws Exception {
        var calls = calls(method("net.minecraft.world.level.block.FarmBlock", "fallOn"));
        int trample = index(calls, ".turnToDirt(");
        assertTrue(index(calls, ".nextFloat(") < trample);
        assertTrue(index(calls, ".getBbWidth(") < trample);
        assertTrue(index(calls, ".getBbHeight(") < trample);
        assertTrue(index(calls, ".getGameRules(") < trample);
        assertTrue(index(calls, ".fallOn(") > trample, "canceling conversion must still reach superclass fall damage");
    }
    @Test void pistonGuardsPrecedeBothPreEventsAndRetractionEventPrecedesEntityFinalization() throws Exception {
        var calls = calls(method("net.minecraft.world.level.block.piston.PistonBaseBlock", "triggerEvent"));
        int extend = index(calls, ".moveBlocks("); int retract = index(calls, ".getBlockEntity(");
        assertTrue(index(calls, ".getNeighborSignal(") < extend);
        assertTrue(extend < retract); assertTrue(retract < index(calls, ".finalTick("));
        assertEquals(1, calls.stream().filter(c -> c.contains(".getNeighborSignal(")).count());
    }
    @Test void treeCancellationPointPrecedesNormalFeatureWorldScanningAndEveryMutation() throws Exception {
        var calls = calls(method("net.minecraft.world.level.block.grower.TreeGrower", "growTree"));
        assertEquals(2, calls.stream().filter(c -> c.contains("java/util/Optional.orElse(")).count());
        assertTrue(index(calls, "java/util/Optional.orElse(") < index(calls, ".isTwoByTwoSapling("));
        assertTrue(index(calls, ".hasFlowers(") < index(calls, ".getConfiguredFeature("));
        assertTrue(index(calls, ".isTwoByTwoSapling(") < index(calls, ".setBlock("));
    }
    @Test void headGrowthShadowFieldsExistWithTheExpectedNativeTypesIncludingInheritedDirection() throws Exception {
        var owner = net.minecraft.world.level.block.GrowingPlantHeadBlock.class;
        assertEquals(double.class, owner.getDeclaredField("growPerTickProbability").getType());
        assertEquals(net.minecraft.core.Direction.class, owner.getSuperclass().getDeclaredField("growthDirection").getType());
        assertTrue(java.lang.reflect.Modifier.isProtected(owner.getSuperclass().getDeclaredField("growthDirection").getModifiers()));
    }
    private static int index(List<String> calls, String needle) { for (int i = 0; i < calls.size(); i++) if (calls.get(i).contains(needle)) return i; fail("missing " + needle); return -1; }
    private static ClassNode node(Class<?> type) throws Exception { return node(type.getName()); }
    private static ClassNode node(String name) throws Exception { var result = new ClassNode(); try (var stream = NativeGrowthTargetsTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) { assertNotNull(stream, name); new ClassReader(stream).accept(result, ClassReader.SKIP_FRAMES); } return result; }
    private static MethodNode method(String owner, String name) throws Exception { var methods = node(owner).methods.stream().filter(m -> m.name.equals(name)).toList(); assertEquals(1, methods.size(), owner + "." + name); return methods.getFirst(); }
    private static List<String> calls(MethodNode method) { var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
}
