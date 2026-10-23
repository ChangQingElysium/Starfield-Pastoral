package com.stardew.craft.platform.fabric.blockextension.plant;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.util.TriState;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlantHooksTest {
    private static final BlockPos POS = new BlockPos(5, 70, -8);
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test void planterLikeProvidersSupportEveryPlantUpwardWithoutForcingSideOrDownwardPlanting() throws Exception {
        var soil = state(PlanterSoil.class, Blocks.DIRT.defaultBlockState()); var view = view(soil);
        for (var plant : new Block[]{Blocks.OAK_SAPLING, Blocks.RED_MUSHROOM, Blocks.DEAD_BUSH, Blocks.FERN, Blocks.WHEAT, Blocks.NETHER_WART, Blocks.CACTUS, Blocks.BAMBOO}) {
            assertSame(TriState.TRUE, PlantHooks.upwardDecision(plant.defaultBlockState(), view, POS));
            for (var face : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
                assertSame(TriState.DEFAULT, PlantHooks.canSustainPlant(soil, view, POS.below(), face, plant.defaultBlockState()));
        }
    }
    @Test void normalSoilsUseTheNativeSurvivalDecisionAndAnExplicitVetoIsPreserved() throws Exception {
        assertSame(TriState.DEFAULT, PlantHooks.canSustainPlant(Blocks.STONE.defaultBlockState(), null, POS, Direction.UP, Blocks.WHEAT.defaultBlockState()));
        var soil = state(RejectingSoil.class, Blocks.DIRT.defaultBlockState());
        assertSame(TriState.FALSE, PlantHooks.upwardDecision(Blocks.WHEAT.defaultBlockState(), view(soil), POS));
    }
    @Test void nativeAndCustomFarmlandUseMoistureWithoutChangingTheBlockIdentity() throws Exception {
        var wet = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7); var dry = wet.setValue(FarmBlock.MOISTURE, 0);
        assertTrue(PlantHooks.isFertile(wet, null, POS)); assertFalse(PlantHooks.isFertile(dry, null, POS));
        var customWet = state(AuthoredFarmland.class, wet); var customDry = state(AuthoredFarmland.class, dry);
        assertTrue(PlantHooks.isFertile(customWet, null, POS)); assertFalse(PlantHooks.isFertile(customDry, null, POS));
        assertInstanceOf(AuthoredFarmland.class, customWet.getBlock()); assertFalse(PlantHooks.isFertile(Blocks.GRASS_BLOCK.defaultBlockState(), null, POS));
    }
    @Test void aHandledTreeSoilHookKeepsTheExactSupportingBlockAndNeverInvokesTheDirtSetter() throws Exception {
        var grass = state(PermanentGrass.class, Blocks.GRASS_BLOCK.defaultBlockState()); var level = view(grass);
        assertTrue(PlantHooks.handlesTreeSoil((net.minecraft.world.level.LevelSimulatedReader) level, (pos, value) -> fail("permanent grass was replaced"), RandomSource.create(1), POS, null));
        assertInstanceOf(PermanentGrass.class, grass.getBlock());
        assertFalse(PlantHooks.handlesTreeSoil((net.minecraft.world.level.LevelSimulatedReader) view(Blocks.GRASS_BLOCK.defaultBlockState()), (pos, value) -> fail("fallback invoked setter itself"), RandomSource.create(1), POS, null));
    }
    private static LevelReader view(BlockState soil) { return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class, net.minecraft.world.level.LevelSimulatedReader.class}, (proxy, method, args) -> { if (method.getName().equals("getBlockState")) return soil; throw new AssertionError("unexpected world access " + method); }); }
    private static BlockState state(Class<? extends Block> ownerClass, BlockState original) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); var unsafe = (sun.misc.Unsafe) field.get(null); var result = (BlockState) unsafe.allocateInstance(BlockState.class);
        for (Class<?> type = BlockState.class; type != Object.class; type = type.getSuperclass()) for (var member : type.getDeclaredFields()) if (!Modifier.isStatic(member.getModifiers())) { member.setAccessible(true); member.set(result, member.get(original)); }
        var owner = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("owner"); owner.setAccessible(true); owner.set(result, unsafe.allocateInstance(ownerClass)); return result;
    }
    private static final class PlanterSoil extends Block implements PlantSoilProvider {
        PlanterSoil() { super(Properties.of()); }
        @Override public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockState plant) { return face == Direction.UP ? TriState.TRUE : TriState.DEFAULT; }
    }
    private static final class RejectingSoil extends Block implements PlantSoilProvider {
        RejectingSoil() { super(Properties.of()); }
        @Override public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockState plant) { return TriState.FALSE; }
    }
    private static final class AuthoredFarmland extends FarmBlock { AuthoredFarmland() { super(Properties.of()); } }
    private static final class PermanentGrass extends Block implements TreeGrowProvider {
        PermanentGrass() { super(Properties.of()); }
        @Override public boolean onTreeGrow(BlockState state, LevelReader level, BiConsumer<BlockPos, BlockState> place, RandomSource random, BlockPos pos, TreeConfiguration config) { return true; }
    }
}
