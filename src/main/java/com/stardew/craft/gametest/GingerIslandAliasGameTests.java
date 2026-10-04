package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandLegacyAliasBlock;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_ginger_aliases")
@PrefixGameTestTemplate(false)
public final class GingerIslandAliasGameTests {
    private GingerIslandAliasGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void aliasesHideOnlyCatalogEntriesAndKeepBothBlockItems(GameTestHelper h) {
        var visible = StardewItemCatalog.visibleItems();
        int aliases = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!asset.legacyAlias()) continue;
            aliases++;
            var legacy = (GingerIslandLegacyAliasBlock) GingerIslandBlocks.get(asset.id());
            var owner = (GingerIslandStateDecorBlock) GingerIslandBlocks.get(asset.catalog_owner());
            h.assertTrue(legacy.asItem() != owner.asItem()
                            && ((BlockItem) legacy.asItem()).getBlock() == legacy
                            && ((BlockItem) owner.asItem()).getBlock() == owner,
                    "A retained alias overwrote the canonical Block.asItem mapping: " + asset.id());
            h.assertTrue(!visible.contains(legacy.asItem()) && visible.contains(owner.asItem())
                            && StardewItemDisplayStacks.stacksForItem(legacy.asItem()).isEmpty(),
                    "The old alias remains a second building/JEI entry: " + asset.id());
            var stack = legacy.canonicalStack();
            h.assertTrue(stack.is(owner.asItem()), "Alias pick/drop uses another owner: " + asset.id());
            if (owner.hasBuildingVariants()) {
                h.assertTrue(owner.itemState(stack).properties().equals(asset.canonical_state()),
                        "An appearance alias loses its exact authored shape: " + asset.id());
            } else {
                h.assertTrue(!stack.has(DataComponents.BLOCK_STATE),
                        "A repaired alias minted a completed process item: " + asset.id());
            }
        }
        h.assertTrue(aliases == 3, "Reviewed retained alias coverage changed: " + aliases);
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_collision", template = "empty", timeoutTicks = 100)
    public static void oldAliasItemsPlaceOriginalPivotsAndBreakOnceToCanonical(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(6, 2, 6));
        AABB area = new AABB(bottom).inflate(5);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Ginger legacy builder"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(bottom.north(5)));
        player.setYRot(180);
        for (BlockPos relative : BlockPos.betweenClosed(2, 1, 2, 12, 8, 12)) {
            level.setBlock(h.absolutePos(relative), relative.getY() == 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
        try {
            for (var asset : GingerIslandAssets.blocks()) {
                if (!asset.legacyAlias()) continue;
                var legacy = (GingerIslandLegacyAliasBlock) GingerIslandBlocks.get(asset.id());
                ItemStack expected = legacy.canonicalStack();
                int runs = asset.id().equals("ginger_resort_leaf_wrapped_column") ? 2 : 1;
                for (int run = 0; run < runs; run++) {
                    level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
                    ItemStack old = new ItemStack(legacy, 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, old);
                    h.assertTrue(old.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                                    new BlockHitResult(Vec3.atCenterOf(bottom.below()).add(0, .5, 0),
                                            Direction.UP, bottom.below(), false))).consumesAction()
                                    && old.getCount() == 1,
                            "The legacy item can no longer be hand placed: " + asset.id());
                    BlockPos main = bottom.above(legacy.placementAnchorYOffset());
                    var state = level.getBlockState(main);
                    h.assertTrue(state.is(legacy) && state.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN,
                            "Legacy placement moved its original pivot or changed its world ID: " + asset.id());
                    h.assertTrue(ItemStack.isSameItemSameComponents(expected,
                                    legacy.getCloneItemStack(level, main, state)),
                            "Picking the old MAIN loses the canonical appearance");
                    var drops = Block.getDrops(state, level, main, null);
                    h.assertTrue(drops.size() == 1 && ItemStack.isSameItemSameComponents(expected, drops.getFirst()),
                            "Legacy MAIN drops a duplicate owner or loses its appearance");
                    BlockPos target = main;
                    if (run == 1) {
                        target = legacy.placementPositions(main, state.getValue(MapDecorStaticBlock.FACING)).stream()
                                .filter(pos -> !pos.equals(main)).findFirst().orElseThrow();
                        var extension = level.getBlockState(target);
                        h.assertTrue(ItemStack.isSameItemSameComponents(expected,
                                        legacy.getCloneItemStack(level, target, extension))
                                        && Block.getDrops(extension, level, target, null).isEmpty(),
                                "The old extension has a separate owner or a second loot item");
                    }
                    level.destroyBlock(target, true);
                    var spawned = level.getEntitiesOfClass(ItemEntity.class, area);
                    h.assertTrue(spawned.size() == 1 && spawned.getFirst().getItem().getCount() == 1
                                    && ItemStack.isSameItemSameComponents(expected, spawned.getFirst().getItem()),
                            "Breaking an alias MAIN/EXT did not produce exactly one canonical item: " + asset.id());
                    h.assertTrue(!level.getBlockState(main).is(legacy), "The old MAIN survived extension removal");
                }
            }
        } finally {
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
            player.getInventory().clearContent();
        }
        h.succeed();
    }
}
