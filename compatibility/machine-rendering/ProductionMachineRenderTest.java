package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.KegBlock;
import com.stardew.craft.block.utility.MapUtilityStaticBlock;
import com.stardew.craft.block.utility.UtilityMachineRenderState;
import com.stardew.craft.blockentity.AnvilBlockEntity;
import com.stardew.craft.blockentity.GardenPotBlockEntity;
import com.stardew.craft.blockentity.KegBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual registered production BEs and Minecraft dispatcher; lighting world/GPU upload are test doubles. */
class ProductionMachineRenderTest {
    @BeforeAll static void registerFixtures() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Unit tests need only these production registrations, not an entire client/mod startup.
        // Use the exact maintained registration suppliers and identities; never replace loaded entries.
        registerIfMissing(BuiltInRegistries.BLOCK, ModBlocks.BLOCKS, ModBlocks.KEG);
        registerIfMissing(BuiltInRegistries.BLOCK, ModBlocks.BLOCKS, ModBlocks.ANVIL_MASTERY);
        registerIfMissing(BuiltInRegistries.BLOCK, ModBlocks.BLOCKS, ModBlocks.GARDEN_POT);
        registerIfMissing(BuiltInRegistries.BLOCK_ENTITY_TYPE, ModBlockEntities.BLOCK_ENTITIES, ModBlockEntities.KEG);
        registerIfMissing(BuiltInRegistries.BLOCK_ENTITY_TYPE, ModBlockEntities.BLOCK_ENTITIES, ModBlockEntities.ANVIL);
        registerIfMissing(BuiltInRegistries.BLOCK_ENTITY_TYPE, ModBlockEntities.BLOCK_ENTITIES, ModBlockEntities.GARDEN_POT);
    }

    @Test void twentyAndOneThousandEmptyKegsSkipActualDispatcherLightAndRenderCallbacks() throws Exception {
        for (int size : new int[]{20, 1000}) {
            CountingLevel world = allocate(CountingLevel.class);
            CountingKegRenderer current = new CountingKegRenderer();
            BlockEntityRenderDispatcher dispatcher = dispatcher(current);
            List<KegBlockEntity> machines = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                KegBlockEntity be = new KegBlockEntity(new BlockPos(i % 32 - 16, 0, i / 32 - 16),
                        ModBlocks.KEG.get().defaultBlockState());
                be.setLevel(world);
                assertSame(ModBlockEntities.KEG.get(), be.getType());
                assertFalse(be.hasInput());
                assertFalse(be.isReady());
                assertEquals(RenderShape.MODEL, be.getBlockState().getRenderShape());
                machines.add(be);
            }
            // Same actual dispatcher with the old default renderer gate demonstrates the removed work.
            field(BlockEntityRenderDispatcher.class, "renderers").set(dispatcher,
                    Map.of(ModBlockEntities.KEG.get(), legacyGate(current)));
            for (var be : machines) dispatcher.render(be, 0, new PoseStack(), type -> null);
            assertEquals(size * 2, world.lightReads);
            assertEquals(size, current.callbacks);
            assertEquals(0, current.bodies);
            assertEquals(0, current.bubbles);
            world.reset();
            current.reset();
            field(BlockEntityRenderDispatcher.class, "renderers").set(dispatcher, Map.of(ModBlockEntities.KEG.get(), current));
            for (var be : machines) dispatcher.render(be, 0, new PoseStack(), type -> null);
            assertEquals(0, world.stateReads);
            assertEquals(0, world.lightReads, "No packedLight lookup may run for an empty static machine");
            assertEquals(0, current.callbacks, "Idle chunk-mesh bodies must not enter the BER callback");
            assertEquals(0, current.bodies);
            assertEquals(0, current.bubbles);
            System.out.printf("EMPTY-KEG %d real BEs: native dispatcher light reads %d -> 0; render callbacks %d -> 0%n",
                    size, size * 2, size);
        }
    }

    @Test void actualSavedWorkingAndReadyKegStatesStillDispatchAndRespectDistance() throws Exception {
        CountingLevel world = allocate(CountingLevel.class);
        CountingKegRenderer renderer = new CountingKegRenderer();
        BlockEntityRenderDispatcher dispatcher = dispatcher(renderer);
        KegBlockEntity be = new KegBlockEntity(BlockPos.ZERO, ModBlocks.KEG.get().defaultBlockState());
        be.setLevel(world);
        load(be, false, true);
        be.setBlockState(be.getBlockState().setValue(KegBlock.WORKING, true));
        assertTrue(be.isWorking());
        assertFalse(be.isReady());
        dispatcher.render(be, 0, new PoseStack(), type -> null);
        assertEquals(1, renderer.callbacks);
        assertEquals(1, renderer.bodies);
        assertEquals(0, renderer.bubbles);
        assertEquals(2, world.lightReads);

        renderer.reset(); world.reset();
        load(be, true, true);
        be.setBlockState(be.getBlockState().setValue(KegBlock.WORKING, false));
        assertFalse(be.isWorking());
        assertTrue(be.isReady());
        assertFalse(be.getProduct().isEmpty());
        assertEquals(RenderShape.MODEL, be.getBlockState().getRenderShape());
        dispatcher.render(be, 0, new PoseStack(), type -> null);
        assertEquals(1, renderer.callbacks);
        assertEquals(0, renderer.bodies, "Ready body stays in the chunk mesh");
        assertEquals(1, renderer.bubbles);
        assertEquals(2, world.lightReads);
        assertFalse(renderer.shouldRender(be, new Vec3(100, 0, 0)));

        renderer.reset(); world.reset();
        load(be, false, false);
        dispatcher.render(be, 0, new PoseStack(), type -> null);
        assertEquals(0, renderer.callbacks, "Collecting the output must restore the idle gate");
        assertEquals(0, world.lightReads);
        // A resource-pack/addon dynamic body retains its renderer even before its BE work flag is synced.
        be.setBlockState(be.getBlockState().setValue(KegBlock.WORKING, true));
        assertTrue(renderer.shouldRender(be, Vec3.ZERO));
        assertFalse(renderer.shouldRender(be, new Vec3(100, 0, 0)));
    }

    @Test void idleAlwaysDynamicBodiesAndUnknownAddonBodiesArePreserved() {
        var anvil = new AnvilBlockEntity(BlockPos.ZERO, ModBlocks.ANVIL_MASTERY.get().defaultBlockState());
        var anvilRenderer = new AnvilBlockEntityRenderer(null);
        assertFalse(anvil.isReady());
        assertEquals(RenderShape.ENTITYBLOCK_ANIMATED, anvil.getBlockState().getRenderShape());
        assertTrue(anvilRenderer.shouldRender(anvil, Vec3.ZERO), "Idle anvil has no chunk-mesh body");
        assertFalse(anvilRenderer.shouldRenderOffScreen(anvil));
        var pot = new GardenPotBlockEntity(BlockPos.ZERO, ModBlocks.GARDEN_POT.get().defaultBlockState());
        var potRenderer = new GardenPotBlockEntityRenderer(null);
        assertTrue(potRenderer.shouldRender(pot, Vec3.ZERO));
        assertFalse(potRenderer.shouldRenderOffScreen(pot));
        assertTrue(contains(potRenderer.getRenderBoundingBox(pot), pot.getRenderBoundingBox()),
                "Frustum bounds must retain every existing potted-crop bound");
        var addon = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        ProductionMachineBlockEntityRenderer<ChestBlockEntity> addonRenderer = new ProductionMachineBlockEntityRenderer<>() {
            @Override public boolean isReadyForBubble(ChestBlockEntity be) { return false; }
            @Override public void render(ChestBlockEntity be, float tick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {}
        };
        assertTrue(addonRenderer.shouldRender(addon, Vec3.ZERO), "Dynamic models do not require a Stardew work/ready contract");
    }

    @Test void machineSpecificBoundsAndBillboardCornersSurviveAllFacingsAndCameraPitch() {
        KegBlockEntityRenderer kegRenderer = new KegBlockEntityRenderer(null);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = ModBlocks.KEG.get().defaultBlockState().setValue(MapUtilityStaticBlock.FACING, facing);
            var keg = new KegBlockEntity(BlockPos.ZERO, state);
            assertEquals(new AABB(BlockPos.ZERO).inflate(2, 2, 2), kegRenderer.getRenderBoundingBox(keg),
                    "Existing specific keg bounds must be preserved");
        }
        var pot = new GardenPotBlockEntity(BlockPos.ZERO, ModBlocks.GARDEN_POT.get().defaultBlockState());
        AABB bounds = new GardenPotBlockEntityRenderer(null).getRenderBoundingBox(pot);
        for (int yaw = 0; yaw < 360; yaw += 15) for (int pitch = -90; pitch <= 90; pitch += 15) {
            var rotation = new Quaternionf().rotationYXZ((float)Math.toRadians(yaw), (float)Math.toRadians(pitch), 0);
            for (float x : new float[]{-20F / 64, 20F / 64}) for (float y : new float[]{0, 24F / 32}) {
                Vector3f corner = rotation.transform(new Vector3f(x, y, .002F));
                assertTrue(bounds.contains(corner.x + .5, corner.y + 1, corner.z + .5),
                        "Bubble corner clipped at yaw=" + yaw + " pitch=" + pitch);
            }
        }
    }

    private static void load(KegBlockEntity be, boolean ready, boolean contents) {
        var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var tag = new CompoundTag();
        if (contents) {
            tag.put("input", new ItemStack(Items.APPLE).save(lookup));
            tag.put("product", new ItemStack(Items.GOLD_INGOT).save(lookup));
        }
        tag.putLong("readyAtAbsMinute", contents ? 100 : -1);
        tag.putBoolean("ready", ready);
        tag.putInt("stardewcraftMachineClock", 1600);
        be.loadCustomOnly(tag, lookup);
    }

    private static BlockEntityRenderDispatcher dispatcher(CountingKegRenderer renderer) throws Exception {
        var dispatcher = new BlockEntityRenderDispatcher(null, null, () -> null, () -> null, () -> null);
        dispatcher.camera = new Camera();
        field(BlockEntityRenderDispatcher.class, "renderers").set(dispatcher, Map.of(ModBlockEntities.KEG.get(), renderer));
        return dispatcher;
    }

    private static BlockEntityRenderer<KegBlockEntity> legacyGate(CountingKegRenderer target) {
        return new BlockEntityRenderer<>() {
            @Override public void render(KegBlockEntity be, float tick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
                target.render(be, tick, pose, buffer, light, overlay);
            }
        };
    }

    private static final class CountingKegRenderer extends KegBlockEntityRenderer {
        int callbacks, bodies, bubbles;
        CountingKegRenderer() { super(null); }
        @Override public void render(KegBlockEntity be, float tick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
            callbacks++;
            assertEquals(15728880, light, "Packed light must still come from the native dispatcher");
            if (UtilityMachineRenderState.rendersDynamicBody(be.getBlockState())) bodies++;
            if (be.isReady() && !be.getProduct().isEmpty()) bubbles++;
        }
        void reset() { callbacks = bodies = bubbles = 0; }
    }

    private static final class CountingLevel extends ClientLevel {
        int stateReads, lightReads;
        // Never called: allocateInstance provides only the two world queries the native light sampler uses.
        private CountingLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { stateReads++; return Blocks.AIR.defaultBlockState(); }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) { lightReads++; return 15; }
        void reset() { stateReads = lightReads = 0; }
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerIfMissing(Registry<T> registry, DeferredRegister<T> registrations,
                                              DeferredHolder<T, ? extends T> entry) throws Exception {
        if (registry.containsKey(entry.getId())) return;
        var suppliers = (Map<DeferredHolder<T, ? extends T>, Supplier<? extends T>>)field(DeferredRegister.class, "entries").get(registrations);
        var mutable = (MappedRegistry<T>)registry;
        mutable.unfreeze();
        try {
            T value = Registry.register(registry, entry.getId(), suppliers.get(entry).get());
            if (value instanceof Block block) block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        } finally { mutable.freeze(); }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
}
