package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.SupplyCrateBlock;
import com.stardew.craft.data.VanillaObjectCatalog;
import com.stardew.craft.farm.SupplyCrateRewards;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.MixedFlowerSeedsItem;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder("stardewcraft_supply_crate")
@PrefixGameTestTemplate(false)
public final class SupplyCrateGameTests {
    // Independent transcription of Object.performToolAction: alternatives use |, simultaneous items use +.
    private static final String[][] SOURCE = {
            {"770:3:5", "371:5:7", "535:2:4", "241:1:2", "395:1:2", "286:3:5", "286:3:5"},
            {"770:3:5", "371:5:7", "749:2:4", "253:1:2", "237:1:2", "246:4:7", "247:2:4", "245:4:7", "287:3:5", "MixedFlowerSeeds:4:5"},
            {"770:3:5", "920:5:7", "749:2:4", "253:2:3", "904|905:1:2", "246:4:7+247:2:4+245:4:7", "275:2:2", "288:3:5", "MixedFlowerSeeds:5:5"}
    };

    private static final class BoundaryRandom extends LegacyRandomSource {
        private final int branch;
        private final boolean upper;
        private int calls;
        BoundaryRandom(int branch, boolean upper) { super(0); this.branch = branch; this.upper = upper; }
        @Override public int nextInt(int bound) { return calls++ == 0 ? branch : upper ? bound - 1 : 0; }
    }

    @GameTest(templateNamespace = "stardewcraft_supply_crate", template = "ring_utilities")
    public static void everySourceOutcomeResolvesWithExactCountsAndWeights(GameTestHelper h) {
        for (int tier = 0; tier < 3; tier++) {
            String[] actual = SupplyCrateRewards.table(tier).stream().map(outcome -> outcome.stream()
                    .map(r -> String.join("|", r.alternatives()) + ":" + r.min() + ":" + r.max())
                    .collect(Collectors.joining("+"))).toArray(String[]::new);
            h.assertTrue(Arrays.equals(SOURCE[tier], actual), "Source pool/weights differ at tier " + tier);
            for (int branch = 0; branch < SOURCE[tier].length; branch++) for (boolean upper : new boolean[]{false, true}) {
                var drops = SupplyCrateRewards.roll(tier, new BoundaryRandom(branch, upper));
                var expected = SOURCE[tier][branch].split("\\+");
                h.assertTrue(drops.size() == expected.length, "Combined reward did not issue every item");
                for (int i = 0; i < expected.length; i++) {
                    var spec = expected[i].split(":");
                    var alternatives = spec[0].split("\\|");
                    String key = alternatives[upper ? alternatives.length - 1 : 0];
                    var reference = VanillaObjectCatalog.stackFor(VanillaObjectCatalog.entryByKey(key));
                    h.assertTrue(!reference.isEmpty() && BuiltInRegistries.ITEM.getKey(reference.getItem()).getNamespace().equals("stardewcraft"),
                            "Missing mod reward for SDV " + key);
                    h.assertTrue(drops.get(i).is(reference.getItem()) && drops.get(i).getCount() == Integer.parseInt(spec[upper ? 2 : 1]),
                            "Wrong reward/count for SDV " + key);
                }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_supply_crate", template = "ring_utilities")
    public static void dateTiersChangeAtFirstAutumnAndSecondSpring(GameTestHelper h) {
        for (int year = 1; year <= 3; year++) for (int season = 0; season < 4; season++) {
            int expected = year > 1 ? 2 : season < 2 ? 0 : 1;
            h.assertTrue(SupplyCrateRewards.tierForDate(year, season) == expected, "Wrong date tier: " + year + "/" + season);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_supply_crate", template = "ring_utilities")
    public static void flowerSeedRewardsPlantSourceFlowersOnlyOnModSoil(GameTestHelper h) {
        var pools = List.of(List.of(ModItems.TULIP_SEEDS.get(), ModItems.BLUE_JAZZ_SEEDS.get()),
                List.of(ModItems.SUMMER_SPANGLE_SEEDS.get(), ModItems.POPPY_SEEDS.get(), ModItems.SUNFLOWER_SEEDS.get()),
                List.of(ModItems.SUNFLOWER_SEEDS.get(), ModItems.FAIRY_ROSE_SEEDS.get()));
        for (int season = 0; season < 3; season++) {
            var pool = pools.get(season);
            for (int choice = 0; choice < pool.size(); choice++)
                h.assertTrue(MixedFlowerSeedsItem.pickSeed(season, new BoundaryRandom(choice, false)) == pool.get(choice), "Incorrect source flower pool");
            h.assertTrue(MixedFlowerSeedsItem.pickSeed(3, new BoundaryRandom(season, false)) == pool.getFirst()
                    && MixedFlowerSeedsItem.pickSeed(3, new BoundaryRandom(season, true)) == pool.getLast(), "Winter did not choose a season first");
        }
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Flower reward"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MIXED_FLOWER_SEEDS.get(), 3));
        var context = new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
        var time = StardewTimeManager.get();
        int previousSeason = time.getCurrentSeason();
        try {
            time.setCurrentSeason(0);
            level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
            ModItems.MIXED_FLOWER_SEEDS.get().useOn(context);
            h.assertTrue(level.getBlockState(pos.above()).isAir() && context.getItemInHand().getCount() == 3, "Flower seeds accepted vanilla farmland");
            level.setBlock(pos, ModBlocks.FARMLAND.get().defaultBlockState(), 3);
            ModItems.MIXED_FLOWER_SEEDS.get().useOn(context);
            var crop = level.getBlockState(pos.above());
            h.assertTrue((crop.is(ModBlocks.TULIP_CROP.get()) || crop.is(ModBlocks.BLUE_JAZZ_CROP.get()))
                    && context.getItemInHand().getCount() == 2, "Flower reward could not plant on mod farmland");
        } finally { time.setCurrentSeason(previousSeason); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_supply_crate", template = "ring_utilities")
    public static void variantsRequireOpenSourceWaterAndRemainPickable(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var block = ModBlocks.SUPPLY_CRATE.get();
        h.assertTrue(ModItems.SUPPLY_CRATE.get() instanceof PlaceOnWaterBlockItem, "Item does not target water surfaces");
        h.assertTrue(StardewItemCatalog.tabForItem(ModItems.SUPPLY_CRATE.get()) == StardewCatalogTab.NATURE, "Missing Nature tab entry");
        var stacks = StardewItemDisplayStacks.stacksForItem(ModItems.SUPPLY_CRATE.get());
        h.assertTrue(stacks.size() == 3, "Missing creative variants");
        for (int variant = 0; variant < 3; variant++) {
            var state = block.defaultBlockState().setValue(SupplyCrateBlock.VARIANT, variant);
            h.assertTrue(PortItemData.get(stacks.get(variant), DataComponents.BLOCK_STATE).apply(block.defaultBlockState()).equals(state), "Variant lost on placement");
            var box = state.getShape(level, pos).bounds();
            h.assertTrue(box.minX >= 0 && box.minZ >= 0 && box.maxX <= 1 && box.maxZ <= 1, "Crate hitbox exceeds cell");
            for (var support : List.of(Blocks.DIRT.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                    Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1))) {
                level.setBlock(pos.below(), support, 2);
                h.assertTrue(!state.canSurvive(level, pos), "Crate accepted solid/flowing/lava support");
            }
            level.setBlock(pos.below(), Blocks.WATER.defaultBlockState(), 2);
            h.assertTrue(state.canSurvive(level, pos), "Source water rejected");
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            h.assertTrue(!state.canSurvive(level, pos), "Submerged placement accepted");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos, state, 3);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(level.getBlockState(pos).isAir(), "Crate remained floating in air");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(), "Removing water generated rewards");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_supply_crate", template = "ring_utilities")
    public static void survivalBreakRewardsOnceCreativeAndInvalidToolsDoNot(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 3, 8));
        var area = new AABB(pos).inflate(2);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Supply crate"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
        var time = StardewTimeManager.get();
        var expected = SupplyCrateRewards.roll(SupplyCrateRewards.tierForDate(time.getCurrentYear(), time.getCurrentSeason()),
                RandomSource.create(level.getSeed() + pos.getX() * 777L + pos.getZ() * 7L));
        for (int variant = 0; variant < 3; variant++) {
            player.setGameMode(GameType.SURVIVAL);
            level.setBlock(pos.below(), Blocks.WATER.defaultBlockState(), 3);
            var state = ModBlocks.SUPPLY_CRATE.get().defaultBlockState().setValue(SupplyCrateBlock.VARIANT, variant);
            level.setBlock(pos, state, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.assertTrue(state.getDestroyProgress(player, level, pos) == 0 && !player.gameMode.destroyBlock(pos), "Bare hand bypassed tool rule");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_SHOVEL));
            h.assertTrue(!player.gameMode.destroyBlock(pos), "Shovel opened crate");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
            h.assertTrue(player.gameMode.destroyBlock(pos), "Axe failed to break crate");
            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
            h.assertTrue(drops.size() == expected.size(), "Wrong reward stack count");
            for (var reward : expected) {
                int count = drops.stream().filter(e -> e.getItem().is(reward.getItem())).mapToInt(e -> e.getItem().getCount()).sum();
                h.assertTrue(count == reward.getCount(), "Rewards changed with skin or were duplicated");
            }
            player.gameMode.destroyBlock(pos);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).size() == drops.size()
                    && level.getFluidState(pos.below()).is(FluidTags.WATER), "Repeat reward or water destruction");
            drops.forEach(ItemEntity::discard);
            level.setBlock(pos, state, 3);
            player.setGameMode(GameType.CREATIVE);
            h.assertTrue(player.gameMode.destroyBlock(pos) && level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Creative rewarded crate");
        }
        h.assertTrue(SupplyCrateBlock.swingsToBreak(new ItemStack(ModItems.AXE.get())) == 3
                && SupplyCrateBlock.swingsToBreak(new ItemStack(ModItems.COPPER_AXE.get())) == 2
                && SupplyCrateBlock.swingsToBreak(new ItemStack(ModItems.IRIDIUM_AXE.get())) == 1, "Tool upgrades do not match 3-HP source crate");
        h.succeed();
    }
}
