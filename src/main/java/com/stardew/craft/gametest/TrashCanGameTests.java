package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.inventory.InventoryTrashPolicy;
import com.stardew.craft.inventory.TrashCanService;
import com.stardew.craft.inventory.TrashCanTier;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.money.SharedMoneyService;
import com.stardew.craft.money.SharedMoneyData;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.shop.BlacksmithService;
import com.stardew.craft.shop.ShopCostService;
import com.stardew.craft.shop.ShopItemEntry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.Set;

@GameTestHolder("stardewcraft_trash_can")
@PrefixGameTestTemplate(false)
public final class TrashCanGameTests {
    private TrashCanGameTests() {
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void allRegisteredItemsExposeStardewMetadata(GameTestHelper helper) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            if (StardewItemDataApi.resolve(new ItemStack(item)).isEmpty()) {
                missing.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        helper.assertTrue(missing.isEmpty(), "registered items without Stardew metadata: " + missing);
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void tiersAndSaveDataMatchOriginal(GameTestHelper helper) {
        int[] prices = {0, 1_000, 2_500, 5_000, 12_500};
        int[] percents = {0, 15, 30, 45, 60};
        String[] bars = {"", "stardewcraft:copper_bar", "stardewcraft:iron_bar",
                "stardewcraft:gold_bar", "stardewcraft:iridium_bar"};
        for (int level = 0; level <= 4; level++) {
            TrashCanTier tier = TrashCanTier.forLevel(level);
            helper.assertValueEqual(tier.level(), level, "wrong trash can level");
            helper.assertValueEqual(tier.price(), prices[level], "wrong trash can price");
            helper.assertValueEqual(tier.reclaimPercent(), percents[level], "wrong reclaim percent");
            if (level > 0) {
                helper.assertValueEqual(tier.barCount(), 5, "trash can upgrade did not cost five bars");
                helper.assertValueEqual(tier.barItemId(), bars[level], "trash can used the wrong upgrade bar");
            }
            if (level < 4) {
                helper.assertValueEqual(tier.next().orElseThrow().level(), level + 1,
                        "trash can did not expose only the next tier");
            } else {
                helper.assertTrue(tier.next().isEmpty(), "iridium trash can still exposed an upgrade");
            }
        }
        helper.assertValueEqual(TrashCanTier.forLevel(-20), TrashCanTier.BASIC, "negative level was not clamped");
        helper.assertValueEqual(TrashCanTier.forLevel(20), TrashCanTier.IRIDIUM, "high level was not clamped");

        PlayerStardewData original = new PlayerStardewData(UUID.randomUUID());
        original.setTrashCanLevel(3);
        original.setToolBeingUpgraded(TrashCanTier.IRIDIUM.upgradeItemId());
        original.setDaysLeftForToolUpgrade(1);
        CompoundTag saved = original.toNBT(helper.getLevel().registryAccess());
        PlayerStardewData loaded = PlayerStardewData.fromNBT(saved, UUID.randomUUID(), helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.getTrashCanLevel(), 3, "trash can level did not survive NBT");
        helper.assertValueEqual(loaded.getToolBeingUpgraded(), TrashCanTier.IRIDIUM.upgradeItemId(),
                "trash can upgrade order did not survive NBT");
        helper.assertValueEqual(loaded.getDaysLeftForToolUpgrade(), 1,
                "trash can upgrade days did not survive NBT");
        helper.assertValueEqual(PlayerStardewData.fromNBT(new CompoundTag(), UUID.randomUUID()).getTrashCanLevel(),
                0, "legacy save did not default to the basic trash can");
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("TrashCanLevel", 99);
        helper.assertValueEqual(PlayerStardewData.fromNBT(invalid, UUID.randomUUID()).getTrashCanLevel(),
                4, "invalid saved level was not clamped");

        PlayerStardewData queued = new PlayerStardewData(UUID.randomUUID());
        queued.setToolBeingUpgraded(TrashCanTier.GOLD.upgradeItemId());
        queued.setDaysLeftForToolUpgrade(0);
        helper.assertTrue(!BlacksmithService.completeTrashCanUpgradeState(queued, TrashCanTier.GOLD),
                "out-of-order trash can order skipped levels");
        queued.setTrashCanLevel(3);
        queued.setToolBeingUpgraded(TrashCanTier.COPPER.upgradeItemId());
        helper.assertTrue(BlacksmithService.completeTrashCanUpgradeState(queued, TrashCanTier.COPPER),
                "stale lower-tier order was not recoverable");
        helper.assertValueEqual(queued.getTrashCanLevel(), 3, "stale order lowered the trash can tier");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void rulesAndRefundFormulaStaySeparated(GameTestHelper helper) {
        helper.assertValueEqual(StardewItemDataApi.getSellPrice(
                        new ItemStack(ModItems.VANILLA_CATEGORY_ITEMS.get("salmonberry").get())),
                5, "salmonberry price differs from the original object data");
        for (var totem : java.util.List.of(
                ModItems.WARP_TOTEM_FARM.get(),
                ModItems.WARP_TOTEM_MOUNTAIN.get(),
                ModItems.WARP_TOTEM_BEACH.get(),
                ModItems.WARP_TOTEM_DESERT.get(),
                ModItems.RAIN_TOTEM.get(),
                ModItems.TREASURE_TOTEM.get())) {
            helper.assertValueEqual(StardewItemDataApi.getSellPrice(new ItemStack(totem)),
                    20, "totem price differs from the original object data");
        }

        ItemStack parsnips = new ItemStack(ModItems.PARSNIP.get(), 10);
        int unitPrice = StardewItemDataApi.getSellPrice(parsnips);
        helper.assertTrue(InventoryTrashPolicy.canTrash(parsnips), "ordinary object could not be trashed");
        helper.assertValueEqual(TrashCanService.calculateRefund(unitPrice, 10, 15),
                unitPrice * 10 * 15 / 100, "stack refund formula changed");
        helper.assertValueEqual(TrashCanService.calculateRefund(1, 1, 15), 0,
                "refund did not floor after multiplying the whole stack");

        helper.assertTrue(!InventoryTrashPolicy.canTrash(new ItemStack(ModItems.AXE.get())),
                "ordinary farming tool was trashable");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.FIBERGLASS_ROD.get())),
                "fishing rod was not trashable");
        helper.assertTrue(!TrashCanService.isReclaimEligible(new ItemStack(ModItems.FIBERGLASS_ROD.get())),
                "fishing rod incorrectly reclaimed money");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(Items.DIAMOND)),
                "unrecognized Minecraft item was not trashable");
        helper.assertTrue(!TrashCanService.isReclaimEligible(new ItemStack(Items.DIAMOND)),
                "unrecognized Minecraft item incorrectly reclaimed money");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.FURNACE.get()))
                        && !TrashCanService.isReclaimEligible(new ItemStack(ModItems.FURNACE.get())),
                "craftable machine did not remain trashable with zero reclaim");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.COOKING_POT.get()))
                        && !TrashCanService.isReclaimEligible(new ItemStack(ModItems.COOKING_POT.get())),
                "furniture did not remain trashable with zero reclaim");
        helper.assertTrue(TrashCanService.isReclaimEligible(new ItemStack(ModItems.RUSTY_SWORD.get())),
                "melee weapon was not reclaimable");
        helper.assertTrue(TrashCanService.isReclaimEligible(new ItemStack(ModItems.SMALL_GLOW_RING.get())),
                "ring was not reclaimable");
        helper.assertTrue(TrashCanService.isReclaimEligible(new ItemStack(ModItems.RUBBER_BOOTS.get())),
                "boots were not reclaimable");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.COPPER_PAN.get()))
                        && !TrashCanService.isReclaimEligible(new ItemStack(ModItems.COPPER_PAN.get())),
                "pan trash/reclaim rules changed");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.SLINGSHOT.get()))
                        && !TrashCanService.isReclaimEligible(new ItemStack(ModItems.SLINGSHOT.get())),
                "slingshot trash/reclaim rules changed");
        helper.assertTrue(InventoryTrashPolicy.canTrash(new ItemStack(ModItems.FROG_EGG.get()))
                        && !TrashCanService.isReclaimEligible(new ItemStack(ModItems.FROG_EGG.get())),
                "trinket incorrectly reclaimed money");
        helper.assertTrue(!InventoryTrashPolicy.canTrash(new ItemStack(ModItems.JUNIMO_NOTE.get())),
                "protected story item was trashable");
        helper.assertTrue(!InventoryTrashPolicy.canTrash(new ItemStack(ModItems.SCYTHE.get())),
                "scythe was trashable");

        var pricingPlayer = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TrashPricing"));
        PlayerStardewData pricingData = PlayerDataManager.getPlayerData(pricingPlayer);
        pricingData.addProfession(ProfessionType.TILLER);
        ItemStack goldParsnip = new ItemStack(ModItems.PARSNIP.get());
        QualityHelper.setQuality(goldParsnip, QualityHelper.GOLD);
        int qualityAdjustedPrice = StardewItemDataApi.getSellPrice(goldParsnip);
        int expectedTillerPrice = (int) Math.floor(qualityAdjustedPrice * 1.10);
        helper.assertValueEqual(ProfessionSellPriceService.quoteItem(
                        pricingPlayer, goldParsnip, SellSource.TRASH_CAN).finalUnitPrice(),
                expectedTillerPrice, "trash quote omitted quality or profession sell-price modifiers");

        ItemStack artifact = new ItemStack(ModItems.ANCIENT_SWORD.get());
        int artifactPrice = StardewItemDataApi.getSellPrice(artifact);
        pricingData.setStat("Book_Artifact", 1);
        helper.assertValueEqual(ProfessionSellPriceService.quoteItem(
                        pricingPlayer, artifact, SellSource.TRASH_CAN).finalUnitPrice(),
                artifactPrice * 3, "trash quote omitted the artifact price book modifier");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void serverTransactionPaysOnlyOnce(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TrashTransaction"));
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setTrashCanLevel(2);
        SharedMoneyService.setMoney(player, 200);
        ItemStack stack = new ItemStack(ModItems.PARSNIP.get(), 10);
        player.inventoryMenu.setCarried(stack.copy());
        int expected = TrashCanService.quote(player, stack).refund();

        TrashCanService.TrashResult first = TrashCanService.trashCarried(player, player.inventoryMenu);
        TrashCanService.TrashResult repeated = TrashCanService.trashCarried(player, player.inventoryMenu);
        helper.assertTrue(first.success() && !repeated.success(), "duplicate trash request was accepted");
        helper.assertTrue(player.inventoryMenu.getCarried().isEmpty(), "trashed stack remained on cursor");
        helper.assertValueEqual(first.refund(), expected, "server used the wrong reclaim amount");
        helper.assertValueEqual(SharedMoneyService.getMoney(player), 200 + expected,
                "shared money service did not receive exactly one refund");

        player.getInventory().setItem(0, stack.copy());
        int expectedSourceCount = player.getInventory().getItem(0).getCount();
        TrashCanService.TrashResult slotFirst = TrashCanService.trashInventorySlot(
                player, 0, true, stack.getItem(), expectedSourceCount);
        TrashCanService.TrashResult slotRepeated = TrashCanService.trashInventorySlot(
                player, 0, true, stack.getItem(), expectedSourceCount);
        helper.assertTrue(slotFirst.success() && !slotRepeated.success(),
                "duplicate slot trash request was accepted after the source count changed");
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), expectedSourceCount - 1,
                "duplicate slot request removed more than one item");

        SharedMoneyService.setMoney(player, Integer.MAX_VALUE - 1);
        player.inventoryMenu.setCarried(stack.copy());
        TrashCanService.TrashResult capped = TrashCanService.trashCarried(player, player.inventoryMenu);
        helper.assertValueEqual(capped.refund(), 1, "trash refund exceeded the wallet integer range");
        helper.assertValueEqual(SharedMoneyService.getMoney(player), Integer.MAX_VALUE,
                "trash refund overflowed the wallet");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void clintUpgradeUsesSpecialCostAndNeedsNoFreeSlot(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TrashUpgrade"));
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setTrashCanLevel(0);
        SharedMoneyService.setMoney(player, 1_000);
        player.getInventory().setItem(0, new ItemStack(ModItems.COPPER_BAR.get(), 5));

        TrashCanTier tier = TrashCanTier.COPPER;
        ShopItemEntry entry = new ShopItemEntry(tier.upgradeItemId(), "", "", tier.price(), 1,
                tier.barItemId(), tier.barCount(), Set.of(), 1, 0, null, -1, 0, 1);
        data.setToolBeingUpgraded("stardewcraft:copper_axe");
        BlacksmithService.handleToolUpgradePurchase(player, entry);
        helper.assertValueEqual(data.getToolBeingUpgraded(), "stardewcraft:copper_axe",
                "trash can replaced an occupied ordinary-tool upgrade slot");
        data.setToolBeingUpgraded("");
        var cost = ShopCostService.legacyCost(entry, 1, StardewCurrencies.MONEY).orElseThrow();
        helper.assertTrue(StardewCosts.pay(player, cost).success(), "Clint trash can cost could not be paid");
        BlacksmithService.handleToolUpgradePurchase(player, entry);
        helper.assertValueEqual(data.getToolBeingUpgraded(), TrashCanTier.COPPER.upgradeItemId(),
                "Clint did not start the copper trash can upgrade");
        helper.assertValueEqual(data.getDaysLeftForToolUpgrade(), 2, "trash can did not use the two-day queue");
        helper.assertValueEqual(data.getTrashCanLevel(), 0, "trash can upgraded before collection");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.COPPER_BAR.get()), 0,
                "Clint did not consume exactly five copper bars");
        helper.assertValueEqual(SharedMoneyService.getMoney(player), 0, "Clint used the wrong copper trash can price");
        helper.assertTrue(!BlacksmithService.completeTrashCanUpgradeState(data, TrashCanTier.COPPER),
                "trash can was collectable before the two-day queue completed");

        BlacksmithService.onNewDay(player);
        helper.assertTrue(!BlacksmithService.completeTrashCanUpgradeState(data, TrashCanTier.COPPER),
                "trash can was collectable after only one day");
        BlacksmithService.onNewDay(player);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertTrue(BlacksmithService.completeTrashCanUpgradeState(data, TrashCanTier.COPPER),
                "full inventory blocked trash can collection");
        helper.assertValueEqual(data.getTrashCanLevel(), 1, "collection did not set the target level");
        helper.assertTrue(data.getToolBeingUpgraded().isEmpty(), "collection did not clear the upgrade slot");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.COPPER_TRASH_CAN_UPGRADE.get()), 0,
                "internal upgrade display item entered the inventory");
        helper.assertTrue(!BlacksmithService.completeTrashCanUpgradeState(data, TrashCanTier.COPPER),
                "completed trash can order could be collected twice");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void sharedWalletDoesNotShareTrashCanLevel(GameTestHelper helper) {
        var first = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TrashSharedFirst"));
        var second = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "TrashSharedSecond"));
        PlayerStardewData firstData = PlayerDataManager.getPlayerData(first);
        PlayerStardewData secondData = PlayerDataManager.getPlayerData(second);
        firstData.setTrashCanLevel(1);
        secondData.setTrashCanLevel(4);
        SharedMoneyService.setMoney(first, 500);
        SharedMoneyService.setMoney(second, 0);
        SharedMoneyData.get().debugMergeWithMember(first, second.getUUID(), 0);

        ItemStack stack = new ItemStack(ModItems.PARSNIP.get(), 10);
        first.inventoryMenu.setCarried(stack.copy());
        int expectedRefund = TrashCanService.quote(first, stack).refund();
        helper.assertValueEqual(expectedRefund,
                TrashCanService.calculateRefund(StardewItemDataApi.getSellPrice(stack), 10, 15),
                "first player did not use their personal trash can tier");
        TrashCanService.trashCarried(first, first.inventoryMenu);

        helper.assertValueEqual(SharedMoneyService.getMoney(first), 500 + expectedRefund,
                "shared wallet did not receive the trash refund");
        helper.assertValueEqual(SharedMoneyService.getMoney(second), 500 + expectedRefund,
                "shared wallet member did not observe the trash refund");
        helper.assertValueEqual(firstData.getTrashCanLevel(), 1, "first player's trash tier changed during payout");
        helper.assertValueEqual(secondData.getTrashCanLevel(), 4, "trash tier leaked between shared-wallet players");
        helper.succeed();
    }
}
