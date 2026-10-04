package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemStacks;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.*;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.network.payload.RequestAnimalOverviewPayload;
import com.stardew.craft.player.*;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_livestock")
@PrefixGameTestTemplate(false)
public final class LivestockGameTests {
    private LivestockGameTests() {}
    @GameTest(template = "empty")
    public static void threeFedDaysMatureAndLayWithDailyPetting(GameTestHelper h) {
        var care = LivestockCare.purchased();
        for (int day = 1; day <= 3; day++) {
            care = care.pet(false); var result = care.nextDay(day > 1, false, true, false, () -> .5);
            h.assertTrue(result.egg() == (day == 3), "Wrong first egg day " + day);
            care = result.care();
            h.assertTrue(care.age() == day && care.friendship() == 15 * day && care.happiness() == 255 && care.fullness() == 0 && !care.petted(), "Fed-day source vector differs");
        }
        h.assertTrue(!care.baby() && care.quality() == 0, "Wrong adult/normal egg state"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void starvationDoesNotMatureByCalendarAndDeductsInSourceOrder(GameTestHelper h) {
        var before = new LivestockCare(1, 1, 500, 255, 0, 0, 0, false);
        var result = before.nextDay(false, false, true, false, () -> .5);
        h.assertTrue(result.care().age() == 1 && result.care().ownedDays() == 2 && result.care().friendship() == 472
                && result.care().happiness() == 105 && !result.egg(), "Starvation vector mismatch");
        var exactly200 = new LivestockCare(1, 1, 500, 100, 200, 0, 0, true).nextDay(false, false, false, false, () -> .5);
        h.assertTrue(exactly200.care().age() == 2 && exactly200.care().friendship() == 500 && exactly200.care().happiness() == 114, "Fullness 200 boundary treated as hungry"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void lowMoodRetainsPreviousQualityAndLayCounter(GameTestHelper h) {
        double[] draws = {0, 0, 0, .9}; int[] index = {0};
        var result = new LivestockCare(3, 3, 300, 90, 255, 3, 2, true)
                .nextDay(false, false, false, false, () -> draws[index[0]++]);
        h.assertTrue(result.egg() && !result.large() && result.care().quality() == 2 && result.care().daysSinceLay() == 4
                && index[0] == 4, "Low-mood gate or random call order differs from FarmAnimal.cs"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void professionChangesQualityAndPettingCapsOnce(GameTestHelper h) {
        var base = new LivestockCare(3, 3, 600, 225, 255, 0, 0, true);
        h.assertTrue(base.nextDay(false, false, true, false, () -> .4).care().quality() == 1, "Expected silver without profession");
        var professional = base.nextDay(false, false, true, true, () -> .4);
        h.assertTrue(professional.large() && professional.care().quality() == 4, "Expected large iridium egg with Coopmaster");
        var pet = new LivestockCare(3, 3, 990, 200, 0, 0, 0, false).pet(true);
        h.assertTrue(pet.friendship() == 1000 && pet.happiness() == 255 && pet.pet(false).equals(pet), "Repeated pet or caps wrong"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void festivalFoodAppliesToFollowingDay(GameTestHelper h) {
        var hungry = new LivestockCare(3, 3, 300, 255, 0, 0, 0, true);
        var today = hungry.nextDay(false, true, true, false, () -> .5);
        h.assertTrue(!today.egg() && today.care().fullness() == 250, "Festival incorrectly fed prior night");
        var tomorrow = today.care().pet(false).nextDay(false, false, true, false, () -> .5);
        h.assertTrue(tomorrow.egg() && tomorrow.care().age() == 4, "Festival fullness did not carry into next settlement"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void journalReloadAndDuplicateCommitKeepOneEgg(GameTestHelper h) {
        var data = new LivestockWorldData(); var id = UUID.randomUUID(); var home = UUID.randomUUID();
        var animal = new LivestockRecord(id, id, UUID.randomUUID(), home, "Pebble", 2, 8, LivestockCare.purchased());
        data.put(animal); var egg = new LivestockWorldData.Product(UUID.randomUUID(), id, home, true, 4);
        data.prepare(new LivestockWorldData.Batch(home, List.of(animal.withCare(9, animal.care())), List.of(egg), List.of(new BlockPos(1, 2, 3))));
        var restored = LivestockWorldData.load(data.save(new CompoundTag()), h.getLevel().registryAccess());
        h.assertTrue(restored.pending().consumed().equals(List.of(new BlockPos(1, 2, 3))), "Journal lost physical feed receipt");
        restored.finish(); restored.finish();
        h.assertTrue(restored.find(id).settledDay() == 9 && restored.eggs().equals(List.of(egg)), "Replay duplicated settlement");
        h.assertTrue(restored.collect(egg.id()) && !restored.collect(egg.id()), "Egg collected twice"); h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void selfBuiltPurchasePetDailyFeedAndEggCollection(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var clock = StardewTimeManager.get();
        int originalDay = clock.getCurrentDay(), originalTime = clock.getCurrentTime();
        UUID owner = UUID.randomUUID(); var farms = FarmInstanceRegistry.get(server);
        var farm = farms.createFarm(owner, "ChickTest", "Chicks", FarmType.STANDARD);
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "ChickTest"));
        var buildings = BuildingWorldData.get(server); var animals = LivestockWorldData.get(server);
        try {
            clock.setCurrentDay(5); clock.setCurrentTime(1000);
            var home = selfHome(h, farm); var trough = home.manager().offset(2, 0, 0);
            PlayerStardewDataAPI.setMoney(player, 5000); player.getInventory().clearContent();
            var nonce = LivestockShop.openForPlayer(player);
            h.assertTrue(LivestockShop.purchase(player, nonce, home.id(), home.revision(), "Pebble").isEmpty(), "Valid self-built home rejected purchase");
            h.assertTrue(PlayerStardewDataAPI.getMoney(player) == 4200 && animals.occupancy(home.id()) == 1, "Price or occupancy wrong");
            h.assertTrue(LivestockShop.purchase(player, nonce, home.id(), home.revision(), "Again").isEmpty() && PlayerStardewDataAPI.getMoney(player) == 4200, "Purchase replay charged twice");
            var chicken = (LivestockEntity) level.getEntity(nonce);
            h.assertTrue(chicken != null && chicken.isBaby() && !chicken.shouldBeSaved(), "No new-system chick projection");
            int experience = PlayerStardewDataAPI.getData(player).getSkillExperience(SkillType.FARMING);
            withMiningDataLevel(h, () -> {
                chicken.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
                chicken.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            });
            h.assertTrue(PlayerStardewDataAPI.getData(player).getSkillExperience(SkillType.FARMING) == experience + 5, "Repeated pet duplicated experience");
            h.assertTrue(animals.find(nonce).care().petted() && animals.find(nonce).care().friendship() == 15,
                    "Entity interaction marked care without granting exactly one pet friendship reward");
            var restoredCare = LivestockWorldData.load(animals.save(new CompoundTag()), level.registryAccess()).find(nonce).care();
            h.assertTrue(restoredCare.equals(animals.find(nonce).care()), "Pet friendship or daily flag was lost on save/load");
            for (int day = 6; day <= 8; day++) {
                if (day > 6) { fillHay(level, trough); withMiningDataLevel(h, () -> chicken.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND)); }
                clock.setCurrentDay(day); LivestockService.onNewDay(level);
                var settled = animals.find(nonce); int eggs = animals.eggs().size();
                h.assertTrue(settled.care().friendship() == 15 * (day - 5) && !settled.care().petted(),
                        "Fed and petted animal lost friendship or retained yesterday's pet flag on day " + day);
                var row = new CompoundTag(); LivestockUiData.care(row, settled);
                h.assertTrue(row.getInt("Friendship") == settled.care().friendship() && !row.getBoolean("Petted"),
                        "Animal UI snapshot does not match persisted friendship and pet state");
                LivestockService.onNewDay(level);
                h.assertTrue(animals.find(nonce).equals(settled) && animals.eggs().size() == eggs, "Same day settled twice");
                if (day > 6) h.assertTrue(!LivestockService.hasHay(level, trough, false), "Daily meal not consumed");
                fillHay(level, trough); LivestockService.onNewDay(level);
                h.assertTrue(LivestockService.hasHay(level, trough, false), "Repeated settlement consumed newly replenished hay");
            }
            h.assertTrue(animals.find(nonce).care().age() == 3 && animals.eggs().stream().filter(e -> e.animal().equals(nonce)).count() == 1, "Three fed days failed to produce one egg");
            chicken.discard(); LivestockService.project(server); LivestockService.project(server);
            h.assertTrue(level.getEntity(nonce) instanceof LivestockEntity restored && !restored.isBaby(), "Projection failed to restore adult from record");
            var egg = animals.eggs().stream().filter(e -> e.animal().equals(nonce)).findFirst().orElseThrow();
            var floorEgg = (LivestockProductEntity) level.getEntity(egg.id());
            h.assertTrue(floorEgg != null && QualityHelper.getQuality(floorEgg.getItem()) == (egg.quality() == 4 ? 3 : egg.quality()), "Missing/wrong quality floor egg");
            h.assertTrue(!floorEgg.shouldBeSaved() && !net.minecraft.world.entity.item.ItemEntity.class.isInstance(floorEgg), "Floor egg entered the vanilla item lifecycle");
            var hopperPos = floorEgg.blockPosition().below();
            level.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState(), 3);
            var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(hopperPos);
            h.assertTrue(net.minecraft.world.level.block.entity.HopperBlockEntity.getItemsAtAndAbove(level, hopper).isEmpty(), "Hopper can bypass the produce ledger");
            var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Outsider"));
            floorEgg.playerTouch(outsider); h.assertTrue(animals.egg(egg.id()) != null, "Visitor stole egg");
            var expectedProduct=floorEgg.getItem().getItem();
            withMiningDataLevel(h, () -> floorEgg.playerTouch(player)); withMiningDataLevel(h, () -> floorEgg.playerTouch(player));
            h.assertTrue(animals.egg(egg.id()) == null && player.getInventory().countItem(expectedProduct) == 1, "Collection was lost or duplicated");
            var adjacent = trough.east(); fillHay(level, trough); fillHay(level, adjacent);
            animals.prepare(new LivestockWorldData.Batch(home.id(), List.of(animals.find(nonce)), List.of(), List.of(trough)));
            LivestockService.hasHay(level, trough, true); // Interrupted after the physical consumption, before ledger commit.
            LivestockService.recover(server); LivestockService.recover(server);
            h.assertTrue(animals.pending() == null && !LivestockService.hasHay(level, trough, false) && LivestockService.hasHay(level, adjacent, false), "Journal replay stole adjacent food or failed to finish");
            // Missing required facilities block new purchases but retain the animal and building tier.
            level.setBlock(home.manager().offset(0, 0, 2), Blocks.AIR.defaultBlockState(), 3);
            var request = LivestockShop.openForPlayer(player);
            h.assertTrue(!LivestockShop.purchase(player, request, home.id(), home.revision(), "Blocked").isEmpty() && animals.find(nonce) != null, "Invalid home accepted purchase or erased resident");
        } catch (RuntimeException exception) { com.stardew.craft.StardewCraft.LOGGER.error("Livestock integration failure", exception); throw exception; } finally { clock.setCurrentDay(originalDay); clock.setCurrentTime(originalTime); farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void twoMembersCompeteForLastBedAndForgedPurchaseCannotCharge(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var farms = FarmInstanceRegistry.get(server);
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID();
        var farm = farms.createFarm(owner, "Beds", "Beds", FarmType.STANDARD); farms.addMember(owner, member);
        var first = FakePlayerFactory.get(level, new GameProfile(owner, "FirstBuyer"));
        var second = FakePlayerFactory.get(level, new GameProfile(member, "SecondBuyer"));
        try {
            var home = selfHome(h, farm); var data = LivestockWorldData.get(server);
            for (int i = 0; i < 3; i++) data.put(new LivestockRecord(UUID.randomUUID(), owner, farm.getInstanceId(), home.id(), "Existing", data.allocateRandomId(), StardewTimeManager.get().getAbsoluteDay(), LivestockCare.purchased()));
            PlayerStardewDataAPI.setMoney(first, 5000); PlayerStardewDataAPI.setMoney(second, 5000);
            UUID a = LivestockShop.openForPlayer(first), b = LivestockShop.openForPlayer(second);
            h.assertTrue(!LivestockShop.purchase(first, UUID.randomUUID(), home.id(), home.revision(), "Fake").isEmpty() && PlayerStardewDataAPI.getMoney(first) == 5000, "Forged session charged");
            h.assertTrue(LivestockShop.purchase(first, a, home.id(), home.revision(), "Winner").isEmpty(), "First buyer failed");
            h.assertTrue(LivestockShop.purchase(second, b, home.id(), home.revision(), "Too late").equals("full") && data.occupancy(home.id()) == 4 && PlayerStardewDataAPI.getMoney(second) == 5000, "Last-bed race overbooked or charged loser");
        } finally { farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void prefabMoveRetainsAnimalHomeAndPendingEgg(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var farms = FarmInstanceRegistry.get(server);
        UUID owner = UUID.randomUUID(); var farm = farms.createFarm(owner, "MoveChick", "Move", FarmType.STANDARD);
        try {
            var definition = PrefabDefinitions.get(PrefabDefinitions.COOP); var tier = definition.tier(1);
            var anchor = h.absolutePos(new BlockPos(7, 1, 14));
            var home = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), PrefabDefinitions.COOP, BuildingRecord.Mode.PREFAB, level.dimension().location(), anchor,
                    PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, Rotation.NONE), Direction.SOUTH, PrefabDefinitions.transform(definition.reservation(), anchor, Rotation.NONE));
            var buildings = BuildingWorldData.get(server); UUID permit = UUID.randomUUID();
            buildings.recordPurchase(permit, farm.getInstanceId(), true, PrefabDefinitions.COOP); buildings.beginPrefab(home, permit, 1); buildings.markScaffold(home.id());
            buildings.constructionDay(2, true); buildings.constructionDay(3, true); buildings.constructionDay(4, true);
            BuildingPlacementService.finish(level, buildings.find(home.id())); home = buildings.find(home.id());
            h.assertTrue(LivestockHomes.accepts(home) && LivestockHomes.spawn(level, home) != null, "Authored prefab lacks usable animal spawn");
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "MoveChick")); PlayerStardewDataAPI.setMoney(player, 1000);
            UUID nonce = LivestockShop.openForPlayer(player);
            h.assertTrue(LivestockShop.purchase(player, nonce, home.id(), home.revision(), "Moving").isEmpty(), "Prefab purchase failed");
            var data = LivestockWorldData.get(server); var animal = data.find(nonce);
            var egg = new LivestockWorldData.Product(UUID.randomUUID(), nonce, home.id(), true, 4);
            data.prepare(new LivestockWorldData.Batch(home.id(), List.of(animal), List.of(egg), List.of())); data.finish();
            var transfer = BuildingTransfer.move(level, home, home.anchor().east(16));
            h.assertTrue(buildings.beginTransfer(transfer) == BuildingWorldData.Result.SUCCESS, "Move rejected");
            transfer.project(level); buildings.finishTransfer(home.id());
            var oldProjection = level.getEntity(nonce); if (oldProjection != null) oldProjection.discard();
            LivestockService.project(server); var moved = buildings.find(home.id());
            h.assertTrue(data.find(nonce).home().equals(moved.id()) && moved.claim().contains(level.getEntity(nonce).blockPosition()), "Moved animal lost stable home or spawned at old coordinates");
            h.assertTrue(data.egg(egg.id()).home().equals(moved.id()) && moved.claim().contains(level.getEntity(egg.id()).blockPosition()), "Pending egg did not follow moved home");
            var restored = LivestockWorldData.load(data.save(new CompoundTag()), level.registryAccess());
            h.assertTrue(restored.find(nonce).equals(data.find(nonce)) && restored.find(nonce).care().equals(animal.care()) && restored.find(nonce).id().equals(animal.id()) && restored.egg(egg.id()).equals(data.egg(egg.id())), "Reload changed animal/product identity");
        } finally { farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void speciesMaturityIntervalsAndProductsFollowSource(GameTestHelper h) {
        for (var species : LivestockSpecies.values()) {
            if(!species.hasDefaultBehavior()) continue; // Java-only extensions own their production; no guessed vanilla product.
            h.assertTrue(!LivestockProducts.stack(species.normal(), 1, 0).isEmpty(), "Missing product " + species);
            if (!species.deluxe().isEmpty()) h.assertTrue(!LivestockProducts.stack(species.deluxe(), 1, 0).isEmpty(), "Missing deluxe " + species);
            if (species.matureDays() > 0) {
                var before = new LivestockCare(species.matureDays() - 1, 2, 400, 255, 255, 0, 0, true);
                var day = before.nextDay(species, false, false, false, false, 0, () -> .5);
                h.assertTrue(day.matured() && day.egg(), "Maturity must trigger first produce even with long interval: " + species);
            }
            if (species.interval() > 1) {
                var before = new LivestockCare(species.matureDays() + 1, 5, 400, 255, 255, 0, 0, true);
                h.assertTrue(!before.nextDay(species, false, false, false, false, 0, () -> .5).egg(), "Ignored produce interval " + species);
            }
        }
        var sheep = new LivestockCare(5, 5, 900, 255, 255, 0, 0, true);
        h.assertTrue(sheep.nextDay(LivestockSpecies.SHEEP, false, false, false, true, 0, () -> .5).egg(), "Shepherd and 900 friendship must stack speed bonuses");
        h.assertTrue(LivestockSpecies.WHITE_COW.sellPrice(1000) == 1950 && LivestockSpecies.RABBIT.price() == 8000, "Source price mismatch");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void autoPetAndManualPetPreserveSourceFriendship(GameTestHelper h) {
        var base = new LivestockCare(5, 5, 100, 100, 255, 0, 0, false);
        var automatic = base.pet(LivestockSpecies.GOAT, true, true);
        h.assertTrue(automatic.friendship() == 108 && automatic.happiness() == 135 && !automatic.petted() && automatic.autoPetted(), "Automatic pet incorrectly gained profession/manual benefits");
        var manual = automatic.pet(LivestockSpecies.GOAT, false, false);
        h.assertTrue(manual.friendship() == 115 && manual.happiness() == 170 && manual.petted(), "Manual pet after machine failed to complete daily care");
        h.assertTrue(automatic.pet(LivestockSpecies.GOAT, false, true).equals(automatic), "Machine granted care twice");
        h.assertTrue(automatic.nextDay(LivestockSpecies.GOAT, false, false, false, false, 0, () -> .5).care().friendship() == 108, "Automatic care did not prevent neglect penalty");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void sourceQualityMapsToModItemQualityWithoutChangingLedger(GameTestHelper h) {
        h.assertTrue(QualityHelper.getQuality(LivestockProducts.stack("milk", 2, 4)) == QualityHelper.IRIDIUM, "Source iridium 4 leaked into mod's 3-based item models");
        var id = UUID.randomUUID();
        var animal = new LivestockRecord(id, id, id, id, "Goat", 12, 9, LivestockCare.purchased()).species(LivestockSpecies.GOAT).produce("large_goat_milk").cracker(true).reproduction(false);
        h.assertTrue(LivestockRecord.load(animal.save()).equals(animal), "Species/held produce/cracker/reproduction lost on reload"); h.succeed();
    }
    @GameTest(template = "empty")
    public static void animalOverviewReadsCurrentFarmLedgerForOwnerAndMembers(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var farms = FarmInstanceRegistry.get(server);
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), outsider = UUID.randomUUID();
        var farm = farms.createFarm(owner, "OverviewOwner", "Overview", FarmType.STANDARD);
        var otherFarm = farms.createFarm(outsider, "OtherOwner", "Other", FarmType.STANDARD);
        h.assertTrue(farms.addMember(owner, member), "Overview fixture could not add farm member");
        var data = LivestockWorldData.get(server);
        UUID home = UUID.randomUUID();
        try {
            data.put(new LivestockRecord(UUID.randomUUID(), owner, farm.getInstanceId(), home,
                    "Bessie", 700, 1,
                    new LivestockCare(5, 1, 650, 255, 255, 0, 0, true, false))
                    .species(LivestockSpecies.BROWN_COW).cracker(true));
            data.put(new LivestockRecord(UUID.randomUUID(), owner, farm.getInstanceId(), home,
                    "Calf", 702, 1, LivestockCare.purchased())
                    .species(LivestockSpecies.WHITE_COW));
            data.put(new LivestockRecord(UUID.randomUUID(), outsider, otherFarm.getInstanceId(), UUID.randomUUID(),
                    "Hidden", 704, 1, LivestockCare.purchased()));

            ServerPlayer viewer = FakePlayerFactory.get(h.getLevel(), new GameProfile(member, "OverviewMember"));
            var rows = RequestAnimalOverviewPayload.entriesFor(viewer);
            h.assertTrue(rows.size() == 2, "Animal overview omitted farm animals or exposed another farm");
            var brown = rows.stream().filter(row -> row.animalId() == 700).findFirst().orElseThrow();
            h.assertTrue(brown.animalTypeId().equals("brown_cow")
                            && brown.customName().equals("Bessie")
                            && brown.friendship() == 650
                            && brown.petStatus() == 2
                            && brown.receivedAnimalCracker()
                            && brown.textureId().endsWith("animal_page_sprite_brown_cow.png"),
                    "Current brown-cow state was not represented in the overview");
            var white = rows.stream().filter(row -> row.animalId() == 702).findFirst().orElseThrow();
            h.assertTrue(white.animalTypeId().equals("white_cow")
                            && white.textureId().endsWith("animal_page_sprite_cow_baby.png"),
                    "White-cow identity did not map to the existing V-menu sprite");
        } finally {
            farms.deleteFarm(owner);
            farms.deleteFarm(outsider);
        }
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void incubationChecksHomeAndCapacityAndConsumesReceiptOnce(GameTestHelper h) {
        var level = h.getLevel(); var farms = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
        var farm = farms.createFarm(owner, "Hatching", "Hatch", FarmType.STANDARD);
        try {
            var home = selfHome(h, farm); var pos = home.manager().offset(-2, 0, -2);
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "Hatching")); player.setPos(pos.getX(), pos.getY(), pos.getZ());
            level.setBlock(pos, ModBlocks.INCUBATOR.get().defaultBlockState(), 3);
            var incubator = (com.stardew.craft.blockentity.IncubatorBlockEntity)level.getBlockEntity(pos);
            var egg = new ItemStack(ModItems.DUCK_EGG.get(), 2);
            h.assertTrue(!incubator.tryInsert(egg, player) && egg.getCount() == 2, "Tier-one home accepted duck incubator");
            for (int i = 1; i <= 4; i++) level.setBlock(home.manager().offset(i, 0, -2), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
            BuildingWorldData.get(level.getServer()).acceptSelf(home.id(),home.revision(),BuildingResidence.scan(level,home.claim(),home.family()).eligibleTier()); BuildingResidence.refresh(level, BuildingWorldData.get(level.getServer()).find(home.id())); home = BuildingWorldData.get(level.getServer()).find(home.id());
            h.assertTrue(home.tier() == 2 && incubator.tryInsert(egg, player) && egg.getCount() == 1, "Valid tier-two incubation failed");
            h.assertTrue(incubator.getRemainingAbsMinutes() == 9000, "Source incubation duration mismatch");
            incubator.advanceDays(6); h.assertTrue(incubator.isReady(), "Incubator did not complete after source overnight time");
            var state = incubator.saveWithFullMetadata(); var data = LivestockWorldData.get(level.getServer());
            h.assertTrue(incubator.claimReadyAnimal(player, "Puddle") == com.stardew.craft.blockentity.IncubatorBlockEntity.ClaimResult.SUCCESS, "Ready newborn rejected");
            h.assertTrue(data.occupancy(home.id()) == 1 && data.all().stream().anyMatch(a -> a.home().equals(data.find(state.getUUID("NewbornReceipt")).home()) && a.species() == LivestockSpecies.DUCK), "Wrong newborn species");
            incubator.load(state);
            h.assertTrue(incubator.claimReadyAnimal(player, "Duplicate") == com.stardew.craft.blockentity.IncubatorBlockEntity.ClaimResult.SUCCESS && data.occupancy(home.id()) == 1 && !incubator.hasInput(), "Replayed incubation duplicated newborn");
        } finally { farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template = "construction_site")
    public static void farmFeedCapacityIsolationAndBatchReplay(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var owner = UUID.randomUUID(); var farms = FarmInstanceRegistry.get(server);
        var actualFarm = farms.createFarm(owner, "Silo", "Silo", FarmType.STANDARD); var farm = actualFarm.getInstanceId(); var other = UUID.randomUUID();
        try {
        var pos = h.absolutePos(new BlockPos(7, 1, 7)); var bounds = UtilityBuildings.bounds(UtilityBuildings.SILO, pos);
        var record = BuildingRecord.waiting(farm, actualFarm.getSlotIndex(), UtilityBuildings.SILO, BuildingRecord.Mode.SELF_BUILT, level.dimension().location(), pos, pos, Direction.SOUTH, bounds);
        var buildings = BuildingWorldData.get(server);
        level.setBlock(pos, ModBlocks.SILO_MANAGER.get().defaultBlockState(), 3);
        h.assertTrue(buildings.register(record) == BuildingWorldData.Result.SUCCESS, "Silo fixture claim overlaps");
        for(var cell:BlockPos.betweenClosed(pos,pos.offset(1,9,1))) if(!cell.equals(pos)) level.setBlock(cell,Blocks.BRICKS.defaultBlockState(),3);
        h.assertTrue(UtilityBuildings.acceptSilo(level,record),"Complete silo column was not accepted");
        h.assertTrue(FarmFeed.store(server, farm, 300) == 240 && FarmFeed.store(server, other, 1) == 0, "Silo capacity/status mismatch: " + FarmFeed.capacity(server, farm) + "/" + buildings.find(record.id()));
        var data = LivestockWorldData.get(server);
        data.prepare(new LivestockWorldData.Batch(record.id(), List.of(), List.of(), List.of(), List.of(), farm, 228, 14));
        LivestockService.recover(server); LivestockService.recover(server);
        h.assertTrue(FarmFeed.amount(server, farm) == 228 && data.feedDay(record.id()) == 14, "Auto-feed journal replay deducted hay twice");
        buildings.removeSelfBuilt(record.id()); h.assertTrue(FarmFeed.take(server, farm, 240) == 228 && FarmFeed.amount(server, other) == 0, "Silo removal deleted stock or borrowed another farm's hay");
        } finally { farms.deleteFarm(owner); } h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void removedManagerCanRehomeAnimalAndSellOnlyOnce(GameTestHelper h) {
        var level = h.getLevel(); var server = level.getServer(); var farms = FarmInstanceRegistry.get(server); var owner = UUID.randomUUID();
        var farm = farms.createFarm(owner, "Rehome", "Rehome", FarmType.STANDARD);
        try {
            var home = selfHome(h, farm); var oldHome = UUID.randomUUID(); var id = UUID.randomUUID(); var data = LivestockWorldData.get(server);
            var animal = new LivestockRecord(id, owner, farm.getInstanceId(), oldHome, "Lost", data.allocateRandomId(), StardewTimeManager.get().getAbsoluteDay(), LivestockCare.purchased()); data.put(animal);
            var egg = new LivestockWorldData.Product(UUID.randomUUID(), id, oldHome, false, 0); data.product(egg);
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "Rehome")); PlayerStardewDataAPI.setMoney(player, 0);
            var nonce = LivestockManagement.open(player);
            h.assertTrue(LivestockManagement.apply(player, new LivestockManagePayload(nonce, id, home.id(), "move", "")).isEmpty(), "Orphan animal cannot be rehoused");
            h.assertTrue(data.find(id).home().equals(home.id()) && data.egg(egg.id()).home().equals(home.id()), "Orphan recovery lost pending produce");
            nonce = LivestockManagement.open(player); var sell = new LivestockManagePayload(nonce, id, home.id(), "sell", "");
            h.assertTrue(LivestockManagement.apply(player, sell).isEmpty() && PlayerStardewDataAPI.getMoney(player) == 240, "Source sale price wrong");
            h.assertTrue(!LivestockManagement.apply(player, sell).isEmpty() && PlayerStardewDataAPI.getMoney(player) == 240, "Repeated sale paid twice");
        } finally { farms.deleteFarm(owner); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void outdoorScheduleUsesMinuteClockAndWinterRainGates(GameTestHelper h) {
        var clock = StardewTimeManager.get(); int time = clock.getCurrentTime(), season = clock.getCurrentSeason();
        var weather = com.stardew.craft.weather.WeatherManager.getCurrentWeather(h.getLevel()); var home = UUID.randomUUID();
        try {
            clock.setCurrentSeason(0); com.stardew.craft.weather.WeatherManager.setWeather(h.getLevel(), "Sun");
            clock.setCurrentTime(989); h.assertTrue(LivestockOutdoors.mayLeave(h.getLevel(), home), "16:29 should permit leaving");
            clock.setCurrentTime(990); h.assertTrue(!LivestockOutdoors.mayLeave(h.getLevel(), home), "16:30 should stop new outings");
            clock.setCurrentTime(600); clock.setCurrentSeason(3); h.assertTrue(!LivestockOutdoors.mayLeave(h.getLevel(), home), "Winter allowed outings");
            clock.setCurrentSeason(0); com.stardew.craft.weather.WeatherManager.setWeather(h.getLevel(), "Rain"); h.assertTrue(!LivestockOutdoors.mayLeave(h.getLevel(), home), "Rain allowed outings");
        } finally { clock.setCurrentTime(time); clock.setCurrentSeason(season); com.stardew.craft.weather.WeatherManager.setWeather(h.getLevel(), weather); }
        h.succeed();
    }
    @GameTest(template = "construction_site")
    public static void bodyClearanceAndGrassReachabilityUseActualBlocks(GameTestHelper h) {
        var level = h.getLevel(); var start = h.absolutePos(new BlockPos(6, 1, 6)); var grass = start.east(5);
        var farm = new FarmInstance(UUID.randomUUID(), "Bounds", "Bounds", 0, h.absolutePos(BlockPos.ZERO), FarmType.STANDARD);
        var home = BuildingRecord.waiting(farm.getInstanceId(), 0, PrefabDefinitions.COOP, BuildingRecord.Mode.SELF_BUILT, level.dimension().location(), start, start, Direction.SOUTH, new BuildingBounds(start.offset(-2,0,-2),start.offset(2,4,2)));
        for (int x=-2;x<=8;x++) for(int z=-4;z<=4;z++) level.setBlock(start.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(grass,ModBlocks.PASTURE_GRASS.get().defaultBlockState(),3);
        for(var side:Direction.Plane.HORIZONTAL) for(int y=0;y<=3;y++) level.setBlock(grass.relative(side).above(y),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(LivestockOutdoors.reachableGrass(level,farm,home,start)==null,"Offscreen grazing crossed a solid enclosure");
        for(int y=0;y<=3;y++) level.removeBlock(grass.west().above(y),false);
        h.assertTrue(grass.equals(LivestockOutdoors.reachableGrass(level,farm,home,start)),"Open passage did not expose grass");
        level.setBlock(start.above(),Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(LivestockHomes.safe(level,home.claim(),start,LivestockSpecies.WHITE_CHICKEN.dimensions(false)),"Chicken should fit below a one-block ceiling");
        h.assertTrue(!LivestockHomes.safe(level,home.claim(),start,LivestockSpecies.WHITE_COW.dimensions(false)),"Cow was allowed to clip through low ceiling");
        h.assertTrue(!LivestockOutdoors.standable(level,farm,farm.getFarmBoundsMin().west()),"Navigation escaped farm boundary"); h.succeed();
    }
    @GameTest(template = "construction_site")
    public static void blueGrassUsesSpeciesAppetiteAndRetainsGroundMaterial(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(7,1,7)); var id=UUID.randomUUID();
        level.setBlock(pos.below(),ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState(),3);
        level.setBlock(pos,ModBlocks.BLUE_PASTURE_GRASS.get().defaultBlockState(),3);
        var cow=new LivestockRecord(id,id,id,id,"Cow",2,1,new LivestockCare(5,5,100,100,0,0,0,true)).species(LivestockSpecies.WHITE_COW);
        String weather=com.stardew.craft.weather.WeatherManager.getCurrentWeather(level);
        try {
            com.stardew.craft.weather.WeatherManager.setWeather(level,"Sun");
            var fed=LivestockOutdoors.graze(level,cow,pos);
            h.assertTrue(fed.care().fullness()==255 && fed.care().friendship()==116 && level.getBlockState(pos).getValue(com.stardew.craft.block.nature.PastureGrassBlock.CLUMPS)==2,"Blue-grass cow appetite or friendship wrong");
            h.assertTrue(level.getBlockState(pos.below()).is(ModBlocks.DARK_GRASS_BLOCK.get()),"Grazing changed custom ground identity");
            h.assertTrue(LivestockOutdoors.graze(level,fed,pos).equals(fed),"Already full animal ate again");
        } finally {com.stardew.craft.weather.WeatherManager.setWeather(level,weather);}
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void collectorSnapshotReplaysOnceAndLeavesFullBoxProduce(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Collect","Collect",FarmType.STANDARD);
        try {
            var home=selfHome(h,farm);var pos=home.manager().offset(-2,0,-2);
            level.setBlock(pos,ModBlocks.AUTO_GRABBER.get().defaultBlockState(),3);
            var box=(com.stardew.craft.blockentity.AutoGrabberBlockEntity)level.getBlockEntity(pos);
            var data=LivestockWorldData.get(server);var id=UUID.randomUUID();
            var cow=new LivestockRecord(id,owner,farm.getInstanceId(),home.id(),"Milk",2,8,new LivestockCare(5,5,0,255,0,0,4,true)).species(LivestockSpecies.WHITE_COW).produce("large_milk").cracker(true);
            var residents=new ArrayList<>(List.of(cow));var eggs=new ArrayList<LivestockWorldData.Product>();
            eggs.add(new LivestockWorldData.Product(UUID.randomUUID(),id,home.id(),false,0,"duck_egg",2,null));
            var changed=LivestockCollectors.collect(level,home,residents,eggs);
            h.assertTrue(box.isEmpty() && changed.size()==1 && eggs.isEmpty() && com.stardew.craft.port.PortJava.getFirst(residents).produce().isEmpty(),"Collector changed world before journal or failed to collect");
            data.prepare(new LivestockWorldData.Batch(home.id(),residents,eggs,List.of(),List.of(),farm.getInstanceId(),0,8,changed));
            LivestockService.recover(server);LivestockService.recover(server);
            int milk=0,duck=0;for(int slot=0;slot<box.getContainerSize();slot++){var item=box.getItem(slot);if(item.is(ModItems.LARGE_MILK.get())){milk+=item.getCount();h.assertTrue(QualityHelper.getQuality(item)==3,"Collector iridium mapping failed");}if(item.is(ModItems.DUCK_EGG.get()))duck+=item.getCount();}
            h.assertTrue(milk==2 && duck==2,"Collector replay duplicated/lost products");
            for(int slot=0;slot<box.getContainerSize();slot++)box.setItem(slot,new ItemStack(net.minecraft.world.item.Items.STONE,64));
            residents=new ArrayList<>(List.of(cow));eggs=new ArrayList<>(List.of(new LivestockWorldData.Product(UUID.randomUUID(),id,home.id(),false,0)));
            h.assertTrue(LivestockCollectors.collect(level,home,residents,eggs).isEmpty() && eggs.size()==1 && !com.stardew.craft.port.PortJava.getFirst(residents).produce().isEmpty(),"Full collector destroyed produce");
        } finally {farms.deleteFarm(owner);}
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void pendingNewbornReservesBedAndSurvivesNamingReplay(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Birth","Birth",FarmType.STANDARD);
        try {
            var home=selfHome(h,farm);var data=LivestockWorldData.get(server);var id=UUID.randomUUID();
            for(int i=0;i<3;i++)data.put(new LivestockRecord(UUID.randomUUID(),owner,farm.getInstanceId(),home.id(),"Adult",data.allocateRandomId(),1,LivestockCare.purchased()));
            var baby=new LivestockRecord(id,owner,farm.getInstanceId(),home.id(),"",data.allocateRandomId(),1,LivestockCare.purchased());data.put(baby);data.newborn(id,true);
            h.assertTrue(data.occupancy(home.id())==4,"Pending newborn did not reserve a bed");
            var restored=LivestockWorldData.load(data.save(new CompoundTag()),level.registryAccess());
            h.assertTrue(restored.newborn(id) && restored.occupancy(home.id())==4,"Reload lost pending newborn reservation");
            var player=FakePlayerFactory.get(level,new GameProfile(owner,"Birth"));
            h.assertTrue(LivestockBirths.name(player,id,"Juniper").isEmpty() && !data.newborn(id) && data.find(id).name().equals("Juniper"),"Newborn naming failed");
            h.assertTrue(!LivestockBirths.name(player,id,"Again").isEmpty() && data.occupancy(home.id())==4,"Naming replay created another newborn");
        } finally {farms.deleteFarm(owner);}
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void pigDigRequiresOpenFarmGroundAndSpendsOpportunity(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"Truffle","Truffle",FarmType.STANDARD);var clock=StardewTimeManager.get();
        int time=clock.getCurrentTime(),season=clock.getCurrentSeason();String weather=com.stardew.craft.weather.WeatherManager.getCurrentWeather(level);
        try {
            var pos=farm.getFarmBoundsMin().offset(12,2,12);level.getChunk(pos.getX()>>4,pos.getZ()>>4);
            for(var side:Direction.Plane.HORIZONTAL){var tile=pos.relative(side);level.setBlock(tile.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(tile,Blocks.AIR.defaultBlockState(),3);}
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            var anchor=pos.west(5);var home=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),PrefabDefinitions.BARN,BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),anchor,anchor,Direction.SOUTH,new BuildingBounds(anchor,anchor.offset(2,3,2)));
            BuildingWorldData.get(server).register(home);var id=UUID.randomUUID();var data=LivestockWorldData.get(server);
            var pig=new LivestockRecord(id,owner,farm.getInstanceId(),home.id(),"Pig",2,1,new LivestockCare(10,10,0,255,255,0,0,true)).species(LivestockSpecies.PIG).produce("truffle");data.put(pig);
            clock.setCurrentTime(600);clock.setCurrentSeason(3);com.stardew.craft.weather.WeatherManager.setWeather(level,"Sun");
            h.assertTrue(!LivestockTruffles.eligible(level,pig,home,pos),"Pig dug in winter");
            clock.setCurrentSeason(0);com.stardew.craft.weather.WeatherManager.setWeather(level,"Rain");h.assertTrue(!LivestockTruffles.eligible(level,pig,home,pos),"Pig dug in rain");
            com.stardew.craft.weather.WeatherManager.setWeather(level,"Sun");
            level.setBlock(pos,ModBlocks.PASTURE_GRASS.get().defaultBlockState(),3);h.assertTrue(!LivestockTruffles.eligible(level,pig,home,pos),"Pig dug through occupied grass");level.removeBlock(pos,false);
            h.assertTrue(LivestockTruffles.dig(level,pig,home,pos),"Pig failed to dig on bare farm ground");
            h.assertTrue(data.find(id).produce().isEmpty(),"Zero-friendship pig retained repeat opportunity");
            h.assertTrue(!LivestockTruffles.dig(level,data.find(id),home,pos),"Pig repeated a spent daily opportunity");
            var replacements=new java.util.concurrent.atomic.AtomicInteger();
            com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers.register(new net.minecraft.resources.ResourceLocation("addon_contract:truffle_"+id.toString().replace("-","")),100,context->{
                if(context.level()!=level||context.anchor().distSqr(pos)>4)return com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers.Result.PASS;
                replacements.incrementAndGet();return com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers.Result.REPLACE_TRUFFLE;
            });
            int beforeProducts=data.eggs().size();data.put(pig);
            h.assertTrue(LivestockTruffles.dig(level,pig,home,pos)&&replacements.get()==1&&data.eggs().size()==beforeProducts,"New dig path ignored addon replacement");
        } finally {clock.setCurrentTime(time);clock.setCurrentSeason(season);com.stardew.craft.weather.WeatherManager.setWeather(level,weather);farms.deleteFarm(owner);}
        h.succeed();
    }
    @GameTest(template = "construction_site", timeoutTicks = 200)
    public static void automaticFeedingFollowsConsumptionAndAutoPetStartsNextDay(GameTestHelper h) {
        var level=h.getLevel();var server=level.getServer();var farms=FarmInstanceRegistry.get(server);var owner=UUID.randomUUID();
        var farm=farms.createFarm(owner,"AutoFeed","AutoFeed",FarmType.STANDARD);
        try {
            var home=selfHome(h,farm);var pos=home.manager();
            for(int x=-3;x<=2;x++)for(int z:new int[]{-3,3})level.setBlock(pos.offset(x,0,z),ModBlocks.AUTOFEED_TROUGH.get().defaultBlockState(),3);
            level.setBlock(pos.offset(-2,0,-1),ModBlocks.INCUBATOR.get().defaultBlockState(),3);
            level.setBlock(pos.offset(-2,0,1),ModBlocks.AUTO_PETTER.get().defaultBlockState(),3);
            for(int target=2;target<=3;target++){BuildingWorldData.get(server).acceptSelf(home.id(),home.revision(),BuildingResidence.scan(level,home.claim(),home.family()).eligibleTier());home=BuildingWorldData.get(server).find(home.id());} BuildingResidence.refresh(level,home);home=BuildingWorldData.get(server).find(home.id());h.assertTrue(home.tier()==3,"Tier-three feed fixture invalid");
            var data=LivestockWorldData.get(server);data.hay(farm.getInstanceId(),20);var id=UUID.randomUUID();int day=StardewTimeManager.get().getAbsoluteDay();
            data.put(new LivestockRecord(id,owner,farm.getInstanceId(),home.id(),"Feed",data.allocateRandomId(),day-1,new LivestockCare(3,3,100,255,0,0,0,false)));
            fillHay(level,pos.offset(-3,0,-3));LivestockService.onNewDay(level);
            int filled=0;for(int x=-3;x<=2;x++)for(int z:new int[]{-3,3})if(LivestockService.hasHay(level,pos.offset(x,0,z),false))filled++;
            h.assertTrue(filled==12 && data.hay(farm.getInstanceId())==8,"Automatic refill did not follow consumption or deducted wrong farm hay");
            h.assertTrue(data.find(id).care().friendship()==98 && data.find(id).care().autoPetted(),"New-day auto-pet was incorrectly applied before prior-day neglect/production");
            LivestockService.onNewDay(level);h.assertTrue(data.hay(farm.getInstanceId())==8,"Repeated day refilled/deducted twice");
        } finally {farms.deleteFarm(owner);}
        h.succeed();
    }
    /** The stock headless test server lacks the mining dimension read by the existing player HUD sync. */
    @SuppressWarnings("unchecked")
    private static void withMiningDataLevel(GameTestHelper h, Runnable action) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels"); field.setAccessible(true);
            var levels = (Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, net.minecraft.server.level.ServerLevel>) field.get(h.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING; var previous = levels.put(key, h.getLevel());
            try { action.run(); } finally { if (previous == null) levels.remove(key); else levels.put(key, previous); }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private static BuildingRecord selfHome(GameTestHelper h, FarmInstance farm) {
        var level = h.getLevel(); var manager = h.absolutePos(new BlockPos(7, 1, 7));
        var bounds = PrefabDefinitions.get(PrefabDefinitions.COOP).selfBounds(manager);
        for (int x = bounds.min().getX(); x < bounds.maxExclusive().getX(); x++) for (int z = bounds.min().getZ(); z < bounds.maxExclusive().getZ(); z++) {
            level.setBlock(new BlockPos(x, manager.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(manager, ModBlocks.COOP_MANAGER.get().defaultBlockState(), 3);
        for (int i = 1; i <= 4; i++) level.setBlock(manager.offset(i, 0, 0), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
        level.setBlock(manager.offset(0, 0, 2), ModBlocks.HAY_HOPPER.get().defaultBlockState(), 3);
        var home = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), PrefabDefinitions.COOP, BuildingRecord.Mode.SELF_BUILT,
                level.dimension().location(), manager, manager, Direction.SOUTH, bounds);
        var buildings = BuildingWorldData.get(level.getServer()); buildings.register(home); buildings.acceptSelf(home.id(),home.revision(),BuildingResidence.scan(level,home.claim(),home.family()).eligibleTier()); BuildingResidence.refresh(level, buildings.find(home.id()));
        return buildings.find(home.id());
    }
    private static void fillHay(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var trough = level.getBlockEntity(pos); var tag = trough.saveWithFullMetadata();
        tag.put("hay", PortItemStacks.save(new ItemStack(ModItems.HAY.get()), level.registryAccess())); trough.load(tag);
    }
}
