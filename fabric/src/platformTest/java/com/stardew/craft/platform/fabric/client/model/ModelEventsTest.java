package com.stardew.craft.platform.fabric.client.model;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import io.github.fabricators_of_create.porting_lib.models.geometry.IGeometryLoader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Inject;

class ModelEventsTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void standaloneModelsAreDeduplicatedInRegistrationOrderAndReloadsCollectAgain() {
        var bus = new EventBus();
        var bridge = new FabricModelEvents(bus);
        var first = id("first");
        var second = id("second");
        var calls = new ArrayList<String>();
        bus.addListener(ModelEvent.RegisterAdditional.class, event -> {
            calls.add("register");
            event.register(first); event.register(second); event.register(first);
        });
        bridge.registerAdditional(model -> calls.add(model.toString()));
        assertEquals(List.of("register", first.toString(), second.toString()), calls);
        bridge.registerAdditional(model -> calls.add(model.toString()));
        assertEquals(List.of("register", first.toString(), second.toString(),
                "register", first.toString(), second.toString()), calls);
    }

    @Test void invalidAdditionalModelFailsTheReloadInsteadOfSilentlyLoadingAnUnusableKey() {
        var event = new ModelEvent.RegisterAdditional(new LinkedHashSet<>());
        assertFalse(event.isCancelable());
        assertThrows(IllegalArgumentException.class, () -> event.register(ModelResourceLocation.inventory(id("a").id())));
        assertThrows(NullPointerException.class, () -> event.register(null));
        var bus = new EventBus();
        bus.addListener(ModelEvent.RegisterAdditional.class, e -> { throw new IllegalStateException("broken registration"); });
        assertThrows(IllegalStateException.class, () -> new FabricModelEvents(bus).registerAdditional(model -> fail("loaded partial models")));
    }

    @Test void geometryLoadersUsePortingLibsOriginalMapAndRejectExistingIds() {
        var bus = new EventBus();
        var bridge = new FabricModelEvents(bus);
        var ownId = ResourceLocation.fromNamespaceAndPath("fixture", "geometry");
        var foreignId = ResourceLocation.fromNamespaceAndPath("other", "geometry");
        IGeometryLoader<?> loader = loader();
        IGeometryLoader<?> foreign = loader();
        Map<ResourceLocation, IGeometryLoader<?>> actualPlMap = new HashMap<>();
        actualPlMap.put(foreignId, foreign);
        bus.addListener(ModelEvent.RegisterGeometryLoaders.class, event -> event.register(ownId, loader));
        bridge.registerGeometryLoaders(actualPlMap);
        assertSame(loader, actualPlMap.get(ownId));
        assertSame(foreign, actualPlMap.get(foreignId));
        assertThrows(IllegalArgumentException.class, () -> bridge.registerGeometryLoaders(actualPlMap));
        assertSame(loader, actualPlMap.get(ownId));
        assertThrows(NullPointerException.class,
                () -> new ModelEvent.RegisterGeometryLoaders(actualPlMap).register(ResourceLocation.withDefaultNamespace("bad"), null));
    }

    @Test void modifyMutatesTheExactBakedTableSynchronouslyBeforeNativeConsumersReadIt() throws Exception {
        var bus = new EventBus();
        var bridge = new FabricModelEvents(bus);
        var bakery = identityOnly(ModelBakery.class);
        var original = model();
        var wrapped = model();
        Map<ModelResourceLocation, BakedModel> actualBakedTable = new LinkedHashMap<>();
        actualBakedTable.put(id("state"), original);
        var sprite = identityOnly(TextureAtlasSprite.class);
        java.util.function.Function<Material, TextureAtlasSprite> getter = material -> sprite;
        var thread = new AtomicReference<Thread>();
        bus.addListener(ModelEvent.ModifyBakingResult.class, event -> {
            thread.set(Thread.currentThread());
            assertSame(actualBakedTable, event.getModels());
            assertSame(bakery, event.getModelBakery());
            assertSame(getter, event.getTextureGetter());
            assertSame(sprite, event.getTextureGetter().apply(null));
            event.getModels().replace(id("state"), wrapped);
            assertFalse(event.isCancelable());
        });
        bridge.modifyBakingResult(actualBakedTable, getter, bakery);
        assertSame(Thread.currentThread(), thread.get());
        assertSame(wrapped, actualBakedTable.get(id("state")));
    }

    @Test void completionExposesManagerAndAnUnmodifiableViewOfTheAdoptedBakedTable() throws Exception {
        var bus = new EventBus();
        var bridge = new FabricModelEvents(bus);
        var bakery = identityOnly(ModelBakery.class);
        var manager = identityOnly(ModelManager.class);
        Map<ModelResourceLocation, BakedModel> actualBakedTable = new LinkedHashMap<>();
        var baked = model(); actualBakedTable.put(id("a"), baked);
        var result = new AtomicReference<ModelEvent.BakingCompleted>();
        bus.addListener(ModelEvent.BakingCompleted.class, result::set);
        bridge.bakingCompleted(manager, actualBakedTable, bakery);
        var event = result.get();
        assertSame(manager, event.getModelManager());
        assertSame(bakery, event.getModelBakery());
        assertSame(baked, event.getModels().get(id("a")));
        assertThrows(UnsupportedOperationException.class, () -> event.getModels().put(id("b"), baked));
        actualBakedTable.put(id("b"), baked);
        assertSame(baked, event.getModels().get(id("b")), "completion uses a view, not a stale snapshot");
        assertFalse(event.isCancelable());
    }

    @Test void textureLookupUsesTheCurrentReloadAtlasAndFallsBackToThatAtlasMissingSprite() throws Exception {
        var sprite = identityOnly(TextureAtlasSprite.class);
        var missing = identityOnly(TextureAtlasSprite.class);
        var atlasId = ResourceLocation.fromNamespaceAndPath("fixture", "atlas");
        var presentId = ResourceLocation.fromNamespaceAndPath("fixture", "present");
        var stitched = new AtlasSet.StitchResult(null, null) {
            @Override public TextureAtlasSprite getSprite(ResourceLocation id) { return id.equals(presentId) ? sprite : null; }
            @Override public TextureAtlasSprite missing() { return missing; }
        };
        var getter = FabricModelEvents.textureGetter(Map.of(atlasId, stitched));
        assertSame(sprite, getter.apply(new Material(atlasId, presentId)));
        assertSame(missing, getter.apply(new Material(atlasId, ResourceLocation.withDefaultNamespace("absent"))));
        assertThrows(NullPointerException.class, () -> getter.apply(new Material(ResourceLocation.withDefaultNamespace("unstitched"), presentId)));
    }

    @Test void nativeAdditionalHookOccursAfterSpecialModelsAndBeforeAllParentResolution() throws Exception {
        var mixin = Class.forName("com.stardew.craft.platform.mixin.client.model.mixin.AdditionalModelsMixin");
        var injection = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(method -> method.getAnnotation(Inject.class))
                .filter(annotation -> annotation != null).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"<init>"}, injection.method());
        assertEquals(1, injection.require());
        assertEquals("Ljava/util/Map;values()Ljava/util/Collection;", injection.at()[0].target());
        var constructor = instructions(ModelBakery.class, "<init>");
        var values = "java/util/Map.values()Ljava/util/Collection;";
        assertEquals(1, constructor.stream().filter(values::equals).count());
        assertTrue(constructor.lastIndexOf("net/minecraft/client/resources/model/ModelBakery.loadSpecialItemModelAndDependencies(Lnet/minecraft/client/resources/model/ModelResourceLocation;)V") < constructor.indexOf(values));
        assertTrue(constructor.indexOf(values) < constructor.indexOf("java/util/Collection.forEach(Ljava/util/function/Consumer;)V"));
        var dependencyLoader = instructions(ModelBakery.class, "registerModelAndLoadDependencies");
        assertTrue(dependencyLoader.contains("net/minecraft/client/resources/model/UnbakedModel.getDependencies()Ljava/util/Collection;"));
        assertTrue(dependencyLoader.contains("net/minecraft/client/resources/model/ModelBakery.getModel(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;"));
        assertTrue(dependencyLoader.contains("net/minecraft/client/resources/model/ModelBakery.registerModel(Lnet/minecraft/client/resources/model/ModelResourceLocation;Lnet/minecraft/client/resources/model/UnbakedModel;)V"));
    }

    @Test void bakeHooksMatchNativeWorkerAndApplyBoundariesWithoutSkippingCacheOrAtlasUpload() throws Exception {
        var mixin = Class.forName("com.stardew.craft.platform.mixin.client.model.mixin.ModelBakingEventsMixin");
        Map<String, Inject> hooks = new HashMap<>();
        for (var method : mixin.getDeclaredMethods()) {
            var hook = method.getAnnotation(Inject.class);
            if (hook != null) hooks.put(hook.method()[0], hook);
        }
        assertEquals(2, hooks.size());
        assertEquals(1, hooks.get("loadModels").require());
        assertEquals("Lnet/minecraft/client/resources/model/ModelBakery;getBakedTopLevelModels()Ljava/util/Map;", hooks.get("loadModels").at()[0].target());
        var load = instructions(ModelManager.class, "loadModels");
        var bakedTable = "net/minecraft/client/resources/model/ModelBakery.getBakedTopLevelModels()Ljava/util/Map;";
        assertEquals(1, load.stream().filter(bakedTable::equals).count());
        assertTrue(load.indexOf("net/minecraft/client/resources/model/ModelBakery.bakeModels(Lnet/minecraft/client/resources/model/ModelBakery$TextureGetter;)V") < load.indexOf(bakedTable));
        assertTrue(load.indexOf(bakedTable) < load.indexOf("net/minecraft/core/DefaultedRegistry.iterator()Ljava/util/Iterator;"));
        assertEquals(1, hooks.get("apply").require());
        assertEquals("Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", hooks.get("apply").at()[0].target());
        var apply = instructions(ModelManager.class, "apply");
        String boundary = "net/minecraft/util/profiling/ProfilerFiller.popPush(Ljava/lang/String;)V";
        assertEquals(1, apply.stream().filter(boundary::equals).count());
        assertTrue(apply.indexOf("PUT net/minecraft/client/resources/model/ModelManager.bakedRegistry") < apply.indexOf(boundary));
        assertTrue(apply.indexOf("PUT net/minecraft/client/resources/model/ModelManager.modelGroups") < apply.indexOf(boundary));
        assertTrue(apply.indexOf("PUT net/minecraft/client/resources/model/ModelManager.missingModel") < apply.indexOf(boundary));
        assertTrue(apply.indexOf("java/util/Collection.forEach(Ljava/util/function/Consumer;)V") < apply.indexOf(boundary), "native atlas uploads finish first");
        assertTrue(apply.indexOf(boundary) < apply.indexOf("net/minecraft/client/renderer/block/BlockModelShaper.replaceCache(Ljava/util/Map;)V"));
    }

    private static ModelResourceLocation id(String path) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("fixture", path), "standalone");
    }

    private static IGeometryLoader<?> loader() {
        return (IGeometryLoader<?>) Proxy.newProxyInstance(IGeometryLoader.class.getClassLoader(),
                new Class<?>[]{IGeometryLoader.class}, (proxy, method, args) -> { throw new AssertionError("identity-only loader fixture"); });
    }

    private static BakedModel model() {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> { throw new AssertionError("identity-only baked fixture"); });
    }

    /** These identity fixtures never access native constructor state, textures, GL or a game instance. */
    private static <T> T identityOnly(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static List<String> instructions(Class<?> type, String methodName) throws Exception {
        var calls = new ArrayList<String>();
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            calls.add(owner + "." + name + descriptor);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.PUTFIELD) calls.add("PUT " + owner + "." + name);
                        }
                    };
                }
            }, 0);
        }
        return calls;
    }
}
