package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.ForageBlock;
import com.stardew.craft.manager.ForageSpawnService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

@GameTestHolder("stardewcraft_forage_regressions")
@PrefixGameTestTemplate(false)
public final class ForageSpawnRegressionGameTests {
    @GameTest(templateNamespace = "stardewcraft_forage_regressions", template = "empty", skyAccess = true)
    public static void forestCanopiesAllowForageButSolidRoofsDoNot(GameTestHelper h) throws Exception {
        prepareForestFloor(h);
        Object zone = localSecretWoods(h);
        Object rect = accessor(zone, "rects", List.class).getFirst();
        Object surface = accessor(zone, "surface", Object.class);
        Method site = method("forageSite", ServerLevel.class, zone.getClass(), rect.getClass(),
                int.class, int.class, surface.getClass());
        Method replaceable = method("isReplaceablePlant", BlockState.class);
        var ground = h.absolutePos(new BlockPos(3, 2, 3));
        // SKY light propagates asynchronously after the fixture leaves are placed.
        h.startSequence().thenWaitUntil(() -> h.assertTrue(!h.getLevel().canSeeSky(ground.above()),
                "Fixture canopy did not block direct sky visibility")).thenExecute(() -> {
            h.assertTrue(ground.above().equals(invoke(site, h.getLevel(), zone, rect,
                    ground.getX(), ground.getZ(), surface)), "Forest leaves prevented a natural forage site");

            var roof = ground.above(4);
            h.getLevel().setBlock(roof, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            h.assertTrue(invoke(site, h.getLevel(), zone, rect, ground.getX(), ground.getZ(), surface) == null,
                    "A solid roof allowed forage to spawn inside the room below");
            h.getLevel().setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().setBlock(ground.above(), Blocks.WATER.defaultBlockState(), 3);
            h.assertTrue(h.getLevel().getFluidState(ground.above()).isSource(), "Fixture source water was not placed");
            h.assertTrue(!(boolean) invoke(replaceable, h.getLevel().getBlockState(ground.above())),
                    "Source water was classified as a replaceable plant");
            h.assertTrue(invoke(site, h.getLevel(), zone, rect, ground.getX(), ground.getZ(), surface) == null,
                    "Forage replaced water beneath the canopy");
            h.getLevel().setBlock(ground.above(), Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 3);
            h.assertTrue(!h.getLevel().getFluidState(ground.above()).isEmpty()
                    && !h.getLevel().getFluidState(ground.above()).isSource(), "Fixture flowing water was not placed");
            h.assertTrue(!(boolean) invoke(replaceable, h.getLevel().getBlockState(ground.above())),
                    "Flowing water was classified as a replaceable plant");
            h.assertTrue(invoke(site, h.getLevel(), zone, rect, ground.getX(), ground.getZ(), surface) == null,
                    "Forage replaced flowing water beneath the canopy");
            var waterlogged = Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
            h.getLevel().setBlock(ground.above(), waterlogged, 3);
            h.assertTrue(h.getLevel().getBlockState(ground.above()).equals(waterlogged)
                    && !h.getLevel().getFluidState(ground.above()).isEmpty(), "Fixture waterlogged plant was not placed");
            h.assertTrue(!(boolean) invoke(replaceable, waterlogged), "A waterlogged plant was classified as replaceable");
            h.assertTrue(invoke(site, h.getLevel(), zone, rect, ground.getX(), ground.getZ(), surface) == null,
                    "Forage replaced a waterlogged plant beneath the canopy");
            h.getLevel().setBlock(ground.above(), Blocks.LAVA.defaultBlockState(), 3);
            h.assertTrue(!(boolean) invoke(replaceable, h.getLevel().getBlockState(ground.above())),
                    "Lava was classified as a replaceable plant");
            h.assertTrue(invoke(site, h.getLevel(), zone, rect, ground.getX(), ground.getZ(), surface) == null,
                    "Forage replaced lava beneath the canopy");
        }).thenSucceed();
    }

    @GameTest(templateNamespace = "stardewcraft_forage_regressions", template = "empty", skyAccess = true)
    public static void unloadedWoodsReceiveOneSummerSpawnOnArrivalAndKeepTheirDailyCap(GameTestHelper h) throws Exception {
        prepareForestFloor(h);
        Object zone = localSecretWoods(h);
        var data = new ForageSpawnService.ForageInitData();
        data.markInitialized(); // A migrated world has already initialized other public locations.
        Object unloaded = copyArea(zone, new BlockPos(1_000_000, 2, 1_000_000));
        h.assertTrue(spawn(h, unloaded, data, 41) == 0 && !data.wasSpawned("stardewcraft:secret_woods", 41),
                "Unloaded woods consumed today's spawn before a player arrived");
        h.assertTrue(spawn(h, zone, data, 41) > 0, "Arriving in summer woods did not produce forage");
        h.assertTrue(count(h, ModBlocks.FORAGE_FIDDLEHEAD_FERN.get()) > 0, "Summer woods produced no fiddlehead fern");
        h.assertTrue(countAll(h) <= 4, "A first arrival replayed several missed days of forage");

        clearForage(h);
        h.assertTrue(spawn(h, zone, data, 41) == 0 && countAll(h) == 0,
                "Harvesting and reentering woods repeated the same day's spawn");
        CompoundTag saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
        Constructor<ForageSpawnService.ForageInitData> load = ForageSpawnService.ForageInitData.class
                .getDeclaredConstructor(CompoundTag.class);
        load.setAccessible(true);
        var restored = load.newInstance(saved);
        h.assertTrue(restored.isInitialized() && spawn(h, zone, restored, 41) == 0,
                "Saving and reopening repeated the same day's spawn");
        h.assertTrue(spawn(h, zone, restored, 42) > 0, "The next day failed to refresh forage");
        for (int day = 43; day < 50; day++) spawn(h, zone, restored, day);
        h.assertTrue(countAll(h) == 6, "Secret woods did not retain its original six-forage cap");
        h.succeed();
    }

    private static void prepareForestFloor(GameTestHelper h) {
        for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++) {
            h.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
            h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            h.setBlock(new BlockPos(x, 8, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        }
        h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(3, 2, 3));
        h.assertBlockPresent(Blocks.OAK_LEAVES, new BlockPos(3, 8, 3));
        h.assertTrue(h.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 3, 3) == 3,
                "Fixture heightmap did not find the forest floor below the leaves");
    }

    private static Object localSecretWoods(GameTestHelper h) throws Exception {
        List<?> zones = (List<?>) method("runtimeZones", ServerLevel.class).invoke(null, h.getLevel());
        for (Object zone : zones) {
            if (accessor(zone, "name", String.class).equals("stardewcraft:secret_woods"))
                return copyArea(zone, h.absolutePos(new BlockPos(1, 2, 1)));
        }
        throw new IllegalStateException("The bundled secret_woods forage data was not loaded");
    }

    private static Object copyArea(Object original, BlockPos min) throws Exception {
        Class<?> rectType = Class.forName(ForageSpawnService.class.getName() + "$ZoneRect");
        var rectConstructor = rectType.getDeclaredConstructors()[0];
        rectConstructor.setAccessible(true);
        Object rect = rectConstructor.newInstance(min.getX(), min.getY(), min.getZ(), min.getX() + 9,
                min.getY() + 8, min.getZ() + 9, 1);
        var constructor = original.getClass().getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(accessor(original, "name", String.class), List.of(rect),
                accessor(original, "entries", List.class), accessor(original, "minDailySpawn", Integer.class),
                accessor(original, "maxDailySpawn", Integer.class), accessor(original, "maxSpawnedAtOnce", Integer.class),
                accessor(original, "surface", Object.class), null);
    }

    private static int spawn(GameTestHelper h, Object zone, ForageSpawnService.ForageInitData data, int day) throws Exception {
        return (int) method("spawnZoneOnce", ServerLevel.class, zone.getClass(), int.class, int.class,
                ForageSpawnService.ForageInitData.class, RandomSource.class)
                .invoke(null, h.getLevel(), zone, 1, day, data, RandomSource.create(100L + day));
    }

    private static int countAll(GameTestHelper h) {
        return count(h, ModBlocks.FORAGE_FIDDLEHEAD_FERN.get()) + count(h, ModBlocks.FORAGE_RED_MUSHROOM.get());
    }

    private static int count(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++)
            if (h.getBlockState(new BlockPos(x, 3, z)).is(block)) count++;
        return count;
    }

    private static void clearForage(GameTestHelper h) {
        for (int x = 1; x <= 10; x++) for (int z = 1; z <= 10; z++) {
            var pos = new BlockPos(x, 3, z);
            if (h.getBlockState(pos).getBlock() instanceof ForageBlock) h.setBlock(pos, Blocks.AIR);
        }
    }

    private static <T> T accessor(Object target, String name, Class<T> type) throws Exception {
        Method accessor = target.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return type.cast(accessor.invoke(target));
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = ForageSpawnService.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not invoke the runtime forage check", ex);
        }
    }
}
