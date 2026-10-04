package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.BlacksmithService;
import com.stardew.craft.shop.GeodeLootService;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_artifact_trove")
@PrefixGameTestTemplate(false)
public final class ArtifactTroveGameTests {
    // SDV 1.6 Objects[275].GeodeDrops[0].RandomItemId, in original order:
    // 100,101,103..106,108..125,166,373,797,Book_Artifact. No 74 (Prismatic Shard).
    private static final List<String> EXPECTED = List.of(
            "chipped_amphora", "arrowhead", "ancient_doll", "elvish_jewelry", "chewing_stick",
            "ornamental_fan", "rare_disc", "ancient_sword", "rusty_spoon", "rusty_spur", "rusty_cog",
            "chicken_statue", "ancient_seed", "prehistoric_tool", "dried_starfish", "anchor", "glass_shards",
            "bone_flute", "prehistoric_handaxe", "dwarvish_helm", "dwarf_gadget", "ancient_drum",
            "golden_mask", "golden_relic", "treasure_chest", "golden_pumpkin", "pearl", "book_artifact");

    @GameTest(templateNamespace = "stardewcraft_artifact_trove", template = "empty", timeoutTicks = 100)
    public static void troveAloneEnablesClintAndChargesOneOpening(GameTestHelper h) throws Exception {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TroveOpening"));
        var menuCheck = BlacksmithService.class.getDeclaredMethod("playerHasGeode", ServerPlayer.class);
        menuCheck.setAccessible(true);
        int beforeMoney = PlayerStardewDataAPI.getMoney(player);
        try {
            player.getInventory().clearContent();
            h.assertTrue(!(boolean) menuCheck.invoke(null, player), "Empty inventory enables processing");
            player.getInventory().setItem(0, new ItemStack(ModItems.GEODE_CRUSHER.get()));
            h.assertTrue(!(boolean) menuCheck.invoke(null, player), "Crusher machine mistaken for a geode by its name");
            player.getInventory().setItem(0, new ItemStack(ModItems.ARTIFACT_TROVE.get(), 2));
            h.assertTrue((boolean) menuCheck.invoke(null, player), "Artifact trove alone does not enable Clint's menu");
            h.assertTrue(GeodeLootService.isClintInput(player.getInventory().getItem(0)), "Inventory picker rejects trove");
            h.assertTrue(!GeodeLootService.isGeodeCrusherInput(player.getInventory().getItem(0)), "Trove incorrectly allowed in geode crusher");
            for (var item : List.of(ModItems.GEODE, ModItems.FROZEN_GEODE, ModItems.MAGMA_GEODE,
                    ModItems.OMNI_GEODE, ModItems.MYSTERY_BOX, ModItems.GOLDEN_MYSTERY_BOX)) {
                h.assertTrue(GeodeLootService.isClintInput(new ItemStack(item.get())), "Existing Clint input rejected");
            }
            h.assertTrue(!GeodeLootService.isClintInput(ItemStack.EMPTY)
                    && !GeodeLootService.isClintInput(new ItemStack(Items.STONE)), "Non-geode accepted");
            PlayerStardewDataAPI.setMoney(player, 24);
            GeodeLootService.handleGeodeCrack(player, 0);
            h.assertTrue(player.getInventory().getItem(0).getCount() == 2
                    && PlayerStardewDataAPI.getMoney(player) == 24, "Unaffordable opening consumed item or money");
            PlayerStardewDataAPI.setMoney(player, 100);
            GeodeLootService.handleGeodeCrack(player, 0);
            h.assertTrue(player.getInventory().getItem(0).getCount() == 1
                    && PlayerStardewDataAPI.getMoney(player) == 75, "Opening must consume one trove and 25g");
            h.assertTrue(player.getInventory().items.stream().filter(s -> !s.isEmpty()).count() == 1, "Treasure granted before animation claim");
            GeodeLootService.handleGeodeClaim(player);
            var loot = player.getInventory().items.stream().filter(s -> !s.isEmpty() && !s.is(ModItems.ARTIFACT_TROVE.get())).toList();
            h.assertTrue(loot.size() == 1 && com.stardew.craft.port.PortJava.getFirst(loot).getCount() == 1
                    && EXPECTED.contains(BuiltInRegistries.ITEM.getKey(com.stardew.craft.port.PortJava.getFirst(loot).getItem()).getPath()), "Opening returned an invalid result");
            GeodeLootService.handleGeodeClaim(player);
            h.assertTrue(player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == 2, "Repeated claim duplicated reward");
        } finally {
            GeodeLootService.onPlayerLogout(player);
            PlayerStardewDataAPI.setMoney(player, beforeMoney);
            player.getInventory().clearContent();
            PlayerDataManager.get().removePlayerData(player.getUUID());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_artifact_trove", template = "empty", timeoutTicks = 100)
    public static void allTwentyEightVanillaResultsHaveOneEqualSlot(GameTestHelper h) throws Exception {
        var roll = GeodeLootService.class.getDeclaredMethod("getTreasureFromGeode", String.class, ServerPlayer.class,
                Random.class, java.util.function.Consumer.class, java.util.Map.class);
        roll.setAccessible(true);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "TrovePool"));
        java.util.function.Consumer<com.stardew.craft.api.v1.action.StardewAction> effects = action -> {};
        for (int index = 0; index < EXPECTED.size(); index++) {
            final int selected = index;
            var random = new Random(0) {
                @Override public int nextInt(int bound) {
                    if (bound == 9) return 0; // Only the two original warm-up passes.
                    if (bound != 28) throw new AssertionError("Trove pool must have exactly 28 equal entries: " + bound);
                    return selected;
                }
            };
            var stack = (ItemStack) roll.invoke(null, "stardewcraft:artifact_trove", player, random, effects, java.util.Map.of());
            h.assertTrue(stack.getCount() == 1 && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
                    .equals("stardewcraft:" + EXPECTED.get(index)), "Wrong vanilla result at slot " + index);
        }
        h.succeed();
    }
}
