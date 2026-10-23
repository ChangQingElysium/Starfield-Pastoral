package com.stardew.craft.platform.fabric.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.client.model.ExtendedBakedModel;
import com.stardew.craft.platform.fabric.particle.BlockParticleSources;
import com.stardew.craft.platform.mixin.client.particle.BlockModelParticleMixin;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import io.github.fabricators_of_create.porting_lib.models.data.ModelProperty;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.minecraft.SharedConstants;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class TerrainParticleSpritesTest {
    private static final ModelProperty<String> MATERIAL = new ModelProperty<>();
    private static TextureAtlasSprite plain, filled;

    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        plain = allocate(TextureAtlasSprite.class); filled = allocate(TextureAtlasSprite.class);
    }

    @Test void knownSourceUsesAttachedDataThenModelEnrichmentBeforeChoosingTheFilledSprite() {
        var source = new BlockPos(18, 63, -9);
        var attached = ModelData.of(MATERIAL, "stored fill");
        var resolved = ModelData.of(MATERIAL, "visible fill");
        var model = new FilledModel(resolved);
        var state = Blocks.STONE.defaultBlockState();
        var view = view(attached, source);

        // This is the old native consumer: it returns the template's fallback material.
        assertSame(plain, model.getParticleIcon());
        assertSame(filled, TerrainParticleSprites.getTexture(model, state, view, source));
        assertSame(attached, model.attached);
        assertSame(resolved, model.selected);
        assertSame(view, model.view);
        assertSame(state, model.state);
        assertEquals(source, model.pos);
        assertEquals(1, model.resolutions);
    }

    @Test void absentAttachmentStillAllowsLocationDependentModelDataAndPlainModelsKeepTheirSprite() {
        var source = new BlockPos(3, 4, 5);
        var model = new FilledModel(ModelData.of(MATERIAL, "visible fill"));
        assertSame(filled, TerrainParticleSprites.getTexture(model, Blocks.STONE.defaultBlockState(), view(null, source), source));
        assertSame(ModelData.EMPTY, model.attached);
        assertSame(plain, TerrainParticleSprites.getTexture(new PlainModel(), Blocks.STONE.defaultBlockState(), view(ModelData.EMPTY, source), source));
    }

    @Test void unknownSourceKeepsTheNativeSpriteAndKnownSourceUpdatesTheSameParticle() throws Exception {
        var particle = allocate(RecordingParticle.class);
        var state = Blocks.STONE.defaultBlockState();
        assertSame(particle, TerrainParticleSprites.update(particle, state, null));
        assertEquals(0, particle.updates);
        var source = new BlockPos(-20, 7, 100);
        assertSame(particle, TerrainParticleSprites.update(particle, state, source));
        assertEquals(1, particle.updates);
        assertSame(state, particle.state);
        assertSame(source, particle.source);
        assertThrows(NullPointerException.class, () -> TerrainParticleSprites.getTexture(new PlainModel(), state, null, null));
    }

    @Test void unknownSourceShaperQueryUsesEmptyDataOnlyForExtendedModelsAndPreservesTheNativeWrapperChain() throws Exception {
        var receiver = new BlockModelParticleMixin() {};
        var method = BlockModelParticleMixin.class.getDeclaredMethod("stardewcraft$emptyModelData", BakedModel.class, Operation.class);
        method.setAccessible(true);
        var calls = new AtomicInteger();
        Operation<TextureAtlasSprite> original = arguments -> { calls.incrementAndGet(); return filled; };
        assertSame(filled, method.invoke(receiver, new PlainModel(), original));
        assertEquals(1, calls.get());
        var model = new FilledModel(ModelData.EMPTY);
        assertSame(plain, method.invoke(receiver, model, original));
        assertSame(ModelData.EMPTY, model.selected);
        assertEquals(1, calls.get());
        assertEquals(0, model.resolutions, "no position must not cause a world/model-data lookup");
    }

    @Test void sourceMetadataIsPerOptionSnapshotsMutablePositionsAndDoesNotChangeTheNativeWireCodec() {
        var first = new SourceOption(); var other = new SourceOption();
        var mutable = new BlockPos.MutableBlockPos(4, 20, -8);
        assertSame(first, BlockParticleSources.withSource(first, mutable));
        mutable.set(100, 200, 300);
        assertEquals(new BlockPos(4, 20, -8), BlockParticleSources.get(first));
        assertNull(BlockParticleSources.get(other));

        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var withoutSource = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var codec = BlockParticleOption.streamCodec(ParticleTypes.BLOCK);
            codec.encode(bytes, first); codec.encode(withoutSource, other);
            assertEquals(bytes.readableBytes(), withoutSource.readableBytes());
            while (bytes.isReadable()) assertEquals(bytes.readByte(), withoutSource.readByte());
        } finally { bytes.release(); withoutSource.release(); }
        BlockParticleSources.withSource(first, null);
        assertNull(BlockParticleSources.get(first));
    }

    @Test void nativeInjectionContractsDistinguishKnownSourceConstructorsFromTheUnknownSourceDelegate() throws Exception {
        String particle = "net/minecraft/client/particle/TerrainParticle";
        String state = "Lnet/minecraft/world/level/block/state/BlockState;";
        String level = "Lnet/minecraft/client/multiplayer/ClientLevel;";
        String known = "(" + level + "DDDDDD" + state + "Lnet/minecraft/core/BlockPos;)V";
        String unknown = "(" + level + "DDDDDD" + state + ")V";
        var engine = read("net/minecraft/client/particle/ParticleEngine");
        assertEquals(1, countCalls(method(engine, "crack"), particle, "<init>", known));
        assertEquals(1, countCalls(method(engine, "method_34020"), particle, "<init>", known));
        var terrain = read(particle);
        assertEquals(1, countCalls(method(terrain, "createTerrainParticle"), particle, "<init>", unknown));
        // A universal ten-argument constructor injection would also change particles with no source.
        assertEquals(1, countCalls(terrain.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals(unknown)).findFirst().orElseThrow(), particle, "<init>", known));
        String option = "net/minecraft/core/particles/BlockParticleOption";
        String optionCtor = "(Lnet/minecraft/core/particles/ParticleType;" + state + ")V";
        assertEquals(1, countCalls(method(read("net/minecraft/world/entity/Entity"), "spawnSprintParticle"), option, "<init>", optionCtor));
        assertEquals(1, countCalls(method(read("net/minecraft/world/entity/LivingEntity"), "checkFallDamage"), option, "<init>", optionCtor));
    }

    @Test void sourceMetadataAndItsTwoNativeProducersHaveNoPhysicalClientReferences() throws Exception {
        for (String name : List.of("fabric/particle/BlockParticleSources", "fabric/particle/BlockParticleSources$Source",
                "mixin/particle/BlockParticleOptionMixin", "mixin/particle/EntitySprintParticleMixin", "mixin/particle/LivingLandingParticleMixin")) {
            try (var input = getClass().getClassLoader().getResourceAsStream("com/stardew/craft/platform/" + name + ".class")) {
                assertNotNull(input);
                assertFalse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"), name);
            }
        }
    }

    private static BlockAndTintGetter view(ModelData data, BlockPos source) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(TerrainParticleSpritesTest.class.getClassLoader(),
                new Class<?>[] { BlockAndTintGetter.class, FabricBlockView.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockEntityRenderData")) { assertEquals(source, args[0]); return data; }
                    throw new AssertionError("Unexpected live world lookup: " + method);
                });
    }

    private static ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (var input = TerrainParticleSpritesTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static long countCalls(MethodNode method, String owner, String name, String desc) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) count++;
        return count;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class PlainModel implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { return List.of(); }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return plain; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
    private static final class FilledModel extends PlainModel implements ExtendedBakedModel {
        final ModelData resolved;
        ModelData attached, selected;
        BlockAndTintGetter view;
        BlockPos pos;
        BlockState state;
        int resolutions;
        FilledModel(ModelData resolved) { this.resolved = resolved; }
        @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData data) {
            this.view = view; this.pos = pos; this.state = state; attached = data; resolutions++; return resolved;
        }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
            selected = data; return "visible fill".equals(data.get(MATERIAL)) ? filled : plain;
        }
    }
    private static final class SourceOption extends BlockParticleOption implements BlockParticleSources.Source {
        BlockPos source;
        SourceOption() { super(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()); }
        @Override public BlockPos stardewcraft$sourcePos() { return source; }
        @Override public void stardewcraft$sourcePos(BlockPos pos) { source = pos; }
    }
    private static final class RecordingParticle extends TerrainParticle implements TerrainParticleSprites.SpriteAccess {
        int updates;
        BlockState state;
        BlockPos source;
        private RecordingParticle() { super(null, 0, 0, 0, 0, 0, 0, null); }
        @Override public void stardewcraft$updateSprite(BlockState state, BlockPos sourcePos) { updates++; this.state = state; source = sourcePos; }
    }
}
