package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainFarmlandGameTests {
    private TerrainFarmlandGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void nativeCropSupportAndRecessedCollision(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 1, 6));
        var farm = ModBlocks.FARMLAND.get().defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, farm, 3);
        helper.assertTrue(farm.getBlock() instanceof FarmBlock, "Must use native farmland hooks");
        for (int moisture = 0; moisture <= 7; moisture++) {
            var authored = farm.setValue(FarmBlock.MOISTURE, moisture);
            var vanilla = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture);
            helper.assertTrue(authored.getCollisionShape(level, pos).max(Direction.Axis.Y) == 15 / 16d,
                    "Farmland collision must be inset one pixel");
            helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(authored.getCollisionShape(level, pos),
                    vanilla.getCollisionShape(level, pos), net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME), "Collision differs from native farmland");
            helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(authored.getShape(level, pos),
                    vanilla.getShape(level, pos), net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME), "Outline differs from native farmland");
        }
        helper.assertTrue(farm.isFertile(level, pos), "Wet farmland must provide native growth bonus");
        helper.assertTrue(Blocks.WHEAT.defaultBlockState().canSurvive(level, pos.above()), "Vanilla wheat rejected authored farmland");
        helper.assertTrue(Blocks.PUMPKIN_STEM.defaultBlockState().canSurvive(level, pos.above()), "Vanilla stems rejected authored farmland");
        helper.assertTrue(!farm.setValue(FarmBlock.MOISTURE, 0).isFertile(level, pos), "Dry farmland must not get the wet bonus");
        var drops = Block.getDrops(farm, level, pos, null);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModBlocks.DIRT.get().asItem()), "Farmland drop lost its dirt identity");
        var vanillaDrops = Block.getDrops(Blocks.FARMLAND.defaultBlockState(), level, pos, null);
        helper.assertTrue(vanillaDrops.size() == 1 && vanillaDrops.getFirst().is(Blocks.DIRT.asItem()),
                "Vanilla farmland must keep its native dirt drop");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void onlyAuthoredDirtCreatesAuthoredFarmland(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 1, 6));
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "TerrainFarmer"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        for (int variant = 0; variant < 5; variant++) {
            var dirt = ModBlocks.DIRT.get().defaultBlockState().setValue(TerrainVariants.DIRT, variant);
            level.setBlock(pos, dirt, 3);
            var tilled = dirt.getToolModifiedState(context, ToolActions.HOE_TILL, true);
            helper.assertTrue(tilled != null && tilled.is(ModBlocks.FARMLAND.get()), "A dirt variant cannot be tilled");
        }
        for (Block grass : new Block[]{ModBlocks.GRASS_BLOCK.get(), ModBlocks.DARK_GRASS_BLOCK.get()})
            helper.assertTrue(grass.defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true) == null, "Grass allowed tilling");
        var vanilla = Blocks.DIRT.defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true);
        helper.assertTrue(vanilla != null && vanilla.is(Blocks.FARMLAND), "Vanilla dirt must keep its original target");
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(ModBlocks.DIRT.get().defaultBlockState().getToolModifiedState(context, ToolActions.HOE_TILL, true) == null, "Covered dirt allowed tilling");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void hydrationAndReversionKeepTerrainIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(6, 1, 6));
        var farm = ModBlocks.FARMLAND.get().defaultBlockState();
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, farm, 3);
        level.setBlock(pos.west(), Blocks.WATER.defaultBlockState(), 3);
        farm.randomTick(level, pos, level.random);
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.FARMLAND.get()) && level.getBlockState(pos).getValue(FarmBlock.MOISTURE) == 7,
                "Native hydration failed or replaced the farmland");
        FarmBlock.turnToDirt(null, level.getBlockState(pos), level, pos);
        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.DIRT.get()), "Trampling/decay returned vanilla dirt");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void pregenUpgradeProtectsFarmRegions(GameTestHelper helper) throws Exception {
        var installer = com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller.class;
        var protectedRegion = installer.getDeclaredMethod("isProtectedPlayerRegionFile", String.class);
        protectedRegion.setAccessible(true);
        for (String file : new String[]{"r.39.39.mca", "r.40.39.mca", "r.58.45.mca", "r.36.36.mca"})
            helper.assertTrue((boolean) protectedRegion.invoke(null, file), "Map upgrade may replace a player farm/interior: " + file);
        helper.assertTrue(!(boolean) protectedRegion.invoke(null, "r.0.0.mca"), "Public pregen must still update");
        helper.succeed();
    }
}
