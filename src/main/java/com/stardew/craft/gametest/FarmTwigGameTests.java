package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.FarmTwigBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ForagingProfessionRules;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_farm_twig")
@PrefixGameTestTemplate(false)
public final class FarmTwigGameTests {
    // GameTest worlds omit the mining dimension used by the shared player-data sync.
    @SuppressWarnings("unchecked")
    private static void withMiningDataLevel(GameTestHelper h, Runnable action) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            field.setAccessible(true);
            var levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>) field.get(h.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
            var previous = levels.put(key, h.getLevel());
            try { action.run(); }
            finally { if (previous == null) levels.remove(key); else levels.put(key, previous); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    @GameTest(templateNamespace = "stardewcraft_farm_twig", template = "ring_utilities")
    public static void gathererMatchesSourceGroundForageChance(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Gatherer rules"));
        var data = PlayerDataManager.getPlayerData(player);
        h.assertTrue(ForagingProfessionRules.applyGatherer(player, 3, 0.0D) == 3,
                "A player without Gatherer received its bonus");
        data.addProfession(ProfessionType.GATHERER);
        h.assertTrue(ForagingProfessionRules.applyGatherer(player, 1, 0.199999D) == 2,
                "Gatherer did not double a single ground forage");
        h.assertTrue(ForagingProfessionRules.applyGatherer(player, 3, 0.20D) == 3,
                "Gatherer accepted a roll outside its 20% chance");
        player.discard();
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_farm_twig", template = "ring_utilities")
    public static void axeClearingSettlesOnceAndRejectsInvalidBreaks(GameTestHelper h) {
        withMiningDataLevel(h, () -> {
            var level = h.getLevel();
            var pos = h.absolutePos(new BlockPos(8, 2, 8));
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Twig clearing"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
            var data = PlayerDataManager.getPlayerData(player);
            data.setSkillExperience(SkillType.FORAGING, 0);
            for (int variant = 0; variant < 2; variant++) {
                var state = ModBlocks.FARM_TWIG.get().defaultBlockState().setValue(FarmTwigBlock.VARIANT, variant);
                level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(pos, state, 3);
                data.setEnergy(100);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
                h.assertTrue(state.getDestroyProgress(player, level, pos) == 0 && !player.gameMode.destroyBlock(pos), "Wrong tool cleared twig");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
                data.setEnergy(0);
                h.assertTrue(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos).equals(state), "Exhausted break removed twig");
                data.setEnergy(100);
                var wornAxe = new ItemStack(Items.WOODEN_AXE);
                wornAxe.setDamageValue(wornAxe.getMaxDamage() - 1);
                player.setItemInHand(InteractionHand.MAIN_HAND, wornAxe);
                int xp = data.getSkillExperience(SkillType.FORAGING);
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
                h.assertTrue(player.gameMode.destroyBlock(pos) && level.isEmptyBlock(pos), "Axe failed to clear twig");
                h.assertTrue(data.getEnergy() == 98 && data.getSkillExperience(SkillType.FORAGING) == xp + 1, "Incorrect energy or foraging experience");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModItems.WOOD_NORMAL.get())
                        && drops.getFirst().getItem().getCount() == 1, "Twig must yield exactly one wood");
                player.gameMode.destroyBlock(pos);
                h.assertTrue(data.getEnergy() == 98 && data.getSkillExperience(SkillType.FORAGING) == xp + 1, "Repeated destruction settled again");
                h.assertTrue(level.getBlockState(pos.below()).is(Blocks.DIRT), "Clearing changed soil");
                drops.forEach(ItemEntity::discard);
                var efficient = new ItemStack(ModItems.AXE.get());
                com.stardew.craft.port.PortItemStacks.enchant(efficient, level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(StardewEnchantments.EFFICIENT), 1);
                player.setItemInHand(InteractionHand.MAIN_HAND, efficient);
                data.setEnergy(0);
                level.setBlock(pos, state, 3);
                h.assertTrue(player.gameMode.destroyBlock(pos) && data.getEnergy() == 0, "Efficient axe charged energy");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
                player.setGameMode(GameType.CREATIVE);
                xp = data.getSkillExperience(SkillType.FORAGING);
                level.setBlock(pos, state, 3);
                h.assertTrue(player.gameMode.destroyBlock(pos) && data.getEnergy() == 0
                        && data.getSkillExperience(SkillType.FORAGING) == xp
                        && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(), "Creative removal settled rewards");
                player.setGameMode(GameType.SURVIVAL);
            }
            h.succeed();
        });
    }

    @GameTest(templateNamespace = "stardewcraft_farm_twig", template = "ring_utilities")
    public static void variantsKeepSingleInBlockBoundsAndAxeTiming(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Twig shape"));
        for (var state : ModBlocks.FARM_TWIG.get().getStateDefinition().getPossibleStates()) {
            var shape = state.getShape(level, pos);
            var bounds = shape.bounds();
            h.assertTrue(shape.toAabbs().size() == 1 && bounds.minX >= 0 && bounds.minY >= 0 && bounds.minZ >= 0
                    && bounds.maxX <= 1 && bounds.maxY < 0.25 && bounds.maxZ <= 1, "Twig AABB exceeds block or is fragmented");
            h.assertTrue(shape.equals(state.getCollisionShape(level, pos)), "Outline and collision differ");
            for (var tool : new ItemStack[]{new ItemStack(Items.WOODEN_AXE), new ItemStack(ModItems.IRIDIUM_AXE.get())}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                h.assertTrue(Math.abs(state.getDestroyProgress(player, level, pos) - 1.0F / 12) < 0.0001, "Twig no longer needs one swing at every tier");
            }
            h.assertTrue(state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(FarmTwigBlock.VARIANT)
                    .equals(state.getValue(FarmTwigBlock.VARIANT)), "Rotation changed variant");
        }
        h.assertTrue(com.stardew.craft.item.catalog.StardewItemCatalog.tabForItem(ModItems.FARM_TWIG.get())
                == com.stardew.craft.item.catalog.StardewCatalogTab.NATURE, "Twig missing from Nature tab");
        h.succeed();
    }
}
