package com.stardew.craft.platform.fabric.fluid;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FishPondWaterFluidTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sourceAndFlowingStayOneDistinctPondFamily() throws ReflectiveOperationException {
        var pond = fixture();
        assertSame(pond.source, pond.source.getSource());
        assertSame(pond.flowing, pond.source.getFlowing());
        assertSame(pond.source, pond.flowing.getSource());
        assertTrue(pond.source.isSame(pond.flowing));
        assertTrue(pond.flowing.isSame(pond.source));
        assertFalse(pond.source.isSame(Fluids.WATER));
        assertFalse(pond.source.isSame(Fluids.FLOWING_WATER));
        assertFalse(pond.source.isSame(Fluids.LAVA));
    }

    @Test
    void nativeStateDefinitionsKeepSourceAndFlowAmountsAndFallingFlags() throws ReflectiveOperationException {
        var pond = fixture();
        FluidState source = pond.source.getSource(false);
        assertSame(pond.source, source.getType());
        assertTrue(source.isSource());
        assertEquals(8, source.getAmount());
        assertFalse(source.hasProperty(FlowingFluid.LEVEL));
        assertFalse(source.getValue(FlowingFluid.FALLING));
        for (int amount = 1; amount <= 8; amount++) {
            FluidState flowing = pond.flowing.getFlowing(amount, true);
            assertSame(pond.flowing, flowing.getType());
            assertFalse(flowing.isSource());
            assertEquals(amount, flowing.getAmount());
            assertTrue(flowing.getValue(FlowingFluid.FALLING));
        }
        assertEquals(7, pond.flowing.defaultFluidState().getAmount());
    }

    @Test
    void nativeHeightAndLiquidBlockLevelsMatchUpstreamFlowingWater() throws ReflectiveOperationException {
        var pond = fixture();
        assertEquals(8.0F / 9.0F, pond.source.defaultFluidState().getOwnHeight());
        assertEquals(0, pond.source.getSource(false).createLegacyBlock().getValue(LiquidBlock.LEVEL));
        assertEquals(1, pond.flowing.getFlowing(7, false).createLegacyBlock().getValue(LiquidBlock.LEVEL));
        assertEquals(4, pond.flowing.getFlowing(4, false).createLegacyBlock().getValue(LiquidBlock.LEVEL));
        assertEquals(12, pond.flowing.getFlowing(4, true).createLegacyBlock().getValue(LiquidBlock.LEVEL));
    }

    @Test
    void bothNativeStatesParticipateInTheWaterTag() throws ReflectiveOperationException {
        var pond = fixture();
        assertTrue(pond.source.defaultFluidState().is(FluidTags.WATER));
        assertTrue(pond.flowing.getFlowing(3, false).is(FluidTags.WATER));
        assertFalse(pond.source.defaultFluidState().is(FluidTags.LAVA));
    }

    @Test
    void sourceConversionAndPhysicalRatesKeepTheExplicitPondPolicy() throws ReflectiveOperationException {
        var pond = fixture();
        assertTrue(pond.source.canConvertToSource(null));
        assertTrue(pond.flowing.canConvertToSource(null));
        assertEquals(4, pond.source.getSlopeFindDistance(null));
        assertEquals(1, pond.source.getDropOff(null));
        assertEquals(5, pond.source.getTickDelay(null));
        assertEquals(100.0F, pond.source.defaultFluidState().getExplosionResistance());
    }

    @Test
    void onlyADifferentFluidFlowingDownCanReplacePondWater() throws ReflectiveOperationException {
        var pond = fixture();
        FluidState state = pond.source.defaultFluidState();
        assertTrue(state.canBeReplacedWith(null, BlockPos.ZERO, Fluids.WATER, Direction.DOWN));
        assertTrue(state.canBeReplacedWith(null, BlockPos.ZERO, Fluids.LAVA, Direction.DOWN));
        assertFalse(state.canBeReplacedWith(null, BlockPos.ZERO, pond.flowing, Direction.DOWN));
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN) {
                assertFalse(state.canBeReplacedWith(null, BlockPos.ZERO, Fluids.WATER, direction));
            }
        }
    }

    @Test
    void pondWaterDoesNotMintAnOrdinaryBucket() throws ReflectiveOperationException {
        var pond = fixture();
        assertSame(Items.AIR, pond.source.getBucket());
        assertSame(Items.AIR, pond.flowing.getBucket());
        assertSame(SoundEvents.BUCKET_FILL, pond.source.getPickupSound().orElseThrow());
    }

    @Test
    void dripstoneInterpretationDoesNotAliasTheActualFluidOrChangeOtherFluids() throws ReflectiveOperationException {
        var pond = fixture();
        assertTrue(PondWaterDrips.canFillCauldron(pond.source));
        assertTrue(PondWaterDrips.canFillCauldron(pond.flowing));
        assertSame(Fluids.WATER, PondWaterDrips.cauldronFluid(pond.source));
        assertSame(Fluids.WATER, PondWaterDrips.cauldronFluid(pond.flowing));
        assertSame(pond.source, pond.source.defaultFluidState().getType());
        for (Fluid fluid : List.of(Fluids.EMPTY, Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA)) {
            assertSame(fluid, PondWaterDrips.cauldronFluid(fluid));
            assertFalse(PondWaterDrips.canFillCauldron(fluid));
        }
    }

    @Test
    void tintKeepsTheDefaultAndSpeciesRgbWithoutUsingAlphaAsAnExtraTint() {
        assertEquals(0x78C8FF, PondWaterTint.resolve(null));
        assertEquals(0xAABBCC, PondWaterTint.resolve(0x12AABBCC));
        assertEquals(0x000000, PondWaterTint.resolve(0xFF000000));
        var color = PondWaterTint.fogColor(PondWaterTint.resolve(0xFFA04020));
        assertEquals(160.0F / 255.0F, color.x());
        assertEquals(64.0F / 255.0F, color.y());
        assertEquals(32.0F / 255.0F, color.z());
    }

    /**
     * Vanilla bootstrap freezes intrusive fluid registration in this headless JVM.
     * Allocate only fixture identities, then build real native state definitions
     * and bind a private registry's holders/tags. No global registry is unfrozen
     * or mutated. Liquid block mapping uses the bootstrapped native water block.
     */
    private static Pond fixture() throws ReflectiveOperationException {
        FishPondWaterFluid.Source source = allocate(FishPondWaterFluid.Source.class);
        FishPondWaterFluid.Flowing flowing = allocate(FishPondWaterFluid.Flowing.class);
        Supplier<FlowingFluid> sourceSupplier = () -> source;
        Supplier<FlowingFluid> flowingSupplier = () -> flowing;
        Supplier<LiquidBlock> blockSupplier = () -> (LiquidBlock) Blocks.WATER;
        for (FishPondWaterFluid fluid : List.of(source, flowing)) {
            set(FishPondWaterFluid.class, fluid, "source", sourceSupplier);
            set(FishPondWaterFluid.class, fluid, "flowing", flowingSupplier);
            set(FishPondWaterFluid.class, fluid, "block", blockSupplier);
            var builder = new StateDefinition.Builder<Fluid, FluidState>(fluid);
            var define = Fluid.class.getDeclaredMethod("createFluidStateDefinition", StateDefinition.Builder.class);
            define.setAccessible(true);
            define.invoke(fluid, builder);
            StateDefinition<Fluid, FluidState> definition = builder.create(Fluid::defaultFluidState, FluidState::new);
            set(Fluid.class, fluid, "stateDefinition", definition);
            FluidState initial = definition.any();
            if (fluid == flowing) initial = initial.setValue(FlowingFluid.LEVEL, 7);
            set(Fluid.class, fluid, "defaultFluidState", initial);
            set(FlowingFluid.class, fluid, "shapes", new HashMap<>());
        }
        var registry = new MappedRegistry<Fluid>(Registries.FLUID, Lifecycle.stable(), false);
        ResourceLocation sourceId = ResourceLocation.parse("pond_fixture:source");
        ResourceLocation flowingId = ResourceLocation.parse("pond_fixture:flowing");
        Registry.register(registry, sourceId, source);
        Registry.register(registry, flowingId, flowing);
        registry.freeze();
        var sourceHolder = registry.getHolder(sourceId).orElseThrow();
        var flowingHolder = registry.getHolder(flowingId).orElseThrow();
        set(Fluid.class, source, "builtInRegistryHolder", sourceHolder);
        set(Fluid.class, flowing, "builtInRegistryHolder", flowingHolder);
        registry.bindTags(Map.of(FluidTags.WATER, List.of(sourceHolder, flowingHolder)));
        return new Pond(source, flowing);
    }

    private static void set(Class<?> declaringType, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = declaringType.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> allocatorType = Class.forName("sun.misc.Unsafe");
        var field = allocatorType.getDeclaredField("theUnsafe");
        if (!field.trySetAccessible()) throw new IllegalStateException("Test fixture allocator is unavailable");
        Object allocator = field.get(null);
        return type.cast(allocatorType.getMethod("allocateInstance", Class.class).invoke(allocator, type));
    }

    private record Pond(FishPondWaterFluid.Source source, FishPondWaterFluid.Flowing flowing) {}
}
