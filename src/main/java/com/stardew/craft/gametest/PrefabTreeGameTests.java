package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.event.WildTreeShakeEvents;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeChopHandler;
import com.stardew.craft.tree.prefab.PrefabTreeManager;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PrefabTreeGameTests {
    private PrefabTreeGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void placedWoodIsNotAdoptedButRegisteredDisconnectedPartsShareOneTree(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = PrefabTreeRegistry.get(level);
        var def = WildTrees.OAK;
        BlockPos root = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos log = root.above(3).east(2);
        BlockPos leaf = root.above(5).west(2);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            level.setBlock(root, def.modernRoot().get().defaultBlockState(), 2 | 16);
            level.setBlock(log, def.modernLog().get().defaultBlockState(), 2 | 16);
            level.setBlock(leaf, def.modernLeaves().get().defaultBlockState(), 2 | 16);
            helper.assertTrue(!WildTrees.markGeneratedModernTree(level, root, def), "Placed wood was treated as a generated tree");
            helper.assertTrue(WildTrees.findTapperSupportDef(level, log) == null, "Placed wood supports a tapper");
            helper.assertTrue(!WildTreeShakeEvents.canShake(player, root), "Placed root can be shaken");
            var placedBreak = new BlockEvent.BreakEvent(level, root, level.getBlockState(root), player);
            helper.assertTrue(!PrefabTreeChopHandler.onBlockBreak(player, level, root, placedBreak), "Placed wood was automatically adopted");

            registry.register(root, def.id(), 1, Set.of(root, log, leaf));
            helper.assertTrue(WildTrees.markGeneratedModernTree(level, root, def), "Registered tree was not marked");
            helper.assertTrue(root.equals(WildTrees.findTapperTreeRoot(level, log)), "Disconnected log lost its tree root");
            helper.assertTrue(WildTreeShakeEvents.canShake(player, leaf), "Registered leaf cannot shake its tree");
            Set<BlockPos> logs = new HashSet<>();
            WildTrees.forEachLiveGeneratedModernLogInTree(level, root, def, logs::add);
            helper.assertTrue(logs.equals(Set.of(log)), "Tapper lookup omitted disconnected wood");
            registry.markFelled(registry.getByRoot(root));
            helper.assertTrue(!WildTrees.isModernCompleteTree(level, root, def), "Stump is still a complete tree");
            helper.assertTrue(!WildTreeShakeEvents.canShake(player, root), "Stump can still be shaken");
            helper.assertTrue(WildTrees.findTapperSupportDef(level, log) == null, "Detached wood still supports a tapper after felling");
        } finally {
            var tree = registry.getByRoot(root);
            if (tree != null) registry.unregister(tree);
            for (BlockPos pos : Set.of(root, log, leaf)) level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void packagedPrefabTreesSupportTappersAndWholeTreeRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = PrefabTreeRegistry.get(level);
        BlockPos root = helper.absolutePos(new BlockPos(5, 12, 5));
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        for (var def : WildTrees.ALL) {
            Set<BlockPos> members = Set.of();
            try {
                helper.assertTrue(PrefabTreeManager.place(level, root, def, 1, false), "Packaged prefab missing for " + def.id());
                var tree = registry.getByRoot(root);
                members = Set.copyOf(tree.members());
                BlockPos log = members.stream().filter(pos -> def.isModernLog(level.getBlockState(pos))).findFirst().orElseThrow();
                helper.assertTrue(WildTrees.findTapperSupportDef(level, log) == def, "Natural log does not support a tapper: " + def.id());
                var partBreak = new BlockEvent.BreakEvent(level, log, level.getBlockState(log), player);
                PrefabTreeChopHandler.onBlockBreak(player, level, log, partBreak);
                helper.assertTrue(partBreak.isCanceled() && !level.isEmptyBlock(log), "Tree component protection failed");
                player.setGameMode(GameType.CREATIVE);
                var wholeBreak = new BlockEvent.BreakEvent(level, log, level.getBlockState(log), player);
                PrefabTreeChopHandler.onBlockBreak(player, level, log, wholeBreak);
                helper.assertTrue(wholeBreak.isCanceled() && registry.getByRoot(root) == null,
                    "Creative removal left a registered tree");
                helper.assertTrue(members.stream().allMatch(level::isEmptyBlock), "Whole-tree removal left components behind");
                helper.assertTrue(WildTrees.findTapperSupportDef(level, log) == null, "Removed tree still supports a tapper");
            } finally {
                var tree = registry.getByRoot(root);
                if (tree != null) registry.unregister(tree);
                com.stardew.craft.manager.WildTreeSeedManager.get(level).untrackTree(level, root);
                for (BlockPos pos : members) level.removeBlock(pos, false);
                player.setGameMode(GameType.SURVIVAL);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            }
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }
}
