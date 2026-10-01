package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class BuildingLifecycleGameTests {
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void barnSelfBuildUsesConfirmedRangeAndNoIncubator(GameTestHelper h) {
        var family = PrefabDefinitions.get(PrefabDefinitions.BARN);
        h.assertTrue(family.selfRadius() == 6 && family.selfHeight() == 8 && family.managerPrice() == 500, "Confirmed barn rules changed");
        BlockPos manager = h.absolutePos(new BlockPos(18, 1, 18)); var bounds = family.selfBounds(manager); var level = h.getLevel();
        for (int x = bounds.min().getX(); x < bounds.maxExclusive().getX(); x++) for (int z = bounds.min().getZ(); z < bounds.maxExclusive().getZ(); z++)
            level.setBlock(new BlockPos(x, manager.getY() + 5, z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        for (int i = 0; i < 8; i++) level.setBlock(bounds.min().offset(i, 0, 1), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
        level.setBlock(manager, ModBlocks.HAY_HOPPER.get().defaultBlockState(), 3);
        h.assertTrue(BuildingResidence.scan(level, bounds, PrefabDefinitions.BARN).eligibleTier() == 2, "Barn demands a coop incubator");
        h.assertTrue(BuildingResidence.scan(level, bounds, PrefabDefinitions.COOP).eligibleTier() == 1, "Coop incorrectly inherited barn facilities");
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void blueprintsAreFamilyBoundAndUpgradeOrdersReload(GameTestHelper h) {
        var data = new BuildingWorldData(); var id = UUID.randomUUID(); var farm = UUID.randomUUID();
        var record = record(farm, 0, PrefabDefinitions.BARN, new BlockPos(0, 64, 0), h.getLevel().dimension().location());
        data.recordPurchase(id, farm, true, PrefabDefinitions.COOP);
        h.assertTrue(data.beginPrefab(record, id, 10) == BuildingWorldData.Result.INVALID_STATE, "Coop permit created a barn");
        id = UUID.randomUUID(); data.recordPurchase(id, farm, true, PrefabDefinitions.BARN);
        data.beginPrefab(record, id, 10); data.markScaffold(record.id());
        for (int day = 11; day <= 13; day++) data.constructionDay(day, true);
        data.finishPrefab(record.id()); var ready = data.find(record.id());
        h.assertTrue(data.beginUpgrade(record.id(), ready.revision(), 13) == BuildingWorldData.Result.SUCCESS, "Upgrade did not start");
        h.assertTrue(data.beginUpgrade(record.id(), ready.revision(), 13) != BuildingWorldData.Result.SUCCESS, "Duplicate upgrade accepted");
        data.constructionDay(14, false); data.constructionDay(15, true);
        var loaded = BuildingWorldData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(loaded.find(record.id()).tier() == 1 && loaded.find(record.id()).phase() == BuildingRecord.Phase.UPGRADING && loaded.order(record.id()).remainingDays() == 1, "Upgrade lost old tier or progress on reload");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void robinAllowsOnlyOneOrderPerFarm(GameTestHelper h) {
        var data = new BuildingWorldData();
        UUID farm = UUID.randomUUID();
        var first = record(farm, 0, PrefabDefinitions.COOP, new BlockPos(0, 64, 0), h.getLevel().dimension().location());
        var second = record(farm, 0, PrefabDefinitions.BARN, new BlockPos(32, 64, 0), h.getLevel().dimension().location());
        UUID firstPermit = UUID.randomUUID();
        UUID secondPermit = UUID.randomUUID();
        data.recordPurchase(firstPermit, farm, true, first.family());
        data.recordPurchase(secondPermit, farm, true, second.family());
        h.assertTrue(data.beginPrefab(first, firstPermit, 10) == BuildingWorldData.Result.SUCCESS,
                "First Robin order was rejected");
        h.assertTrue(data.hasActiveConstruction(farm), "Started order was not marked active");
        h.assertTrue(data.beginPrefab(second, secondPermit, 10) == BuildingWorldData.Result.INVALID_STATE,
                "Robin accepted two simultaneous orders on one farm");

        UUID otherFarm = UUID.randomUUID();
        var other = record(otherFarm, 0, PrefabDefinitions.BARN, new BlockPos(32, 64, 32), h.getLevel().dimension().location());
        UUID otherPermit = UUID.randomUUID();
        data.recordPurchase(otherPermit, otherFarm, true, other.family());
        h.assertTrue(data.beginPrefab(other, otherPermit, 10) == BuildingWorldData.Result.SUCCESS,
                "Robin lock leaked between farms");
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void bothFamiliesUpgradeTwiceAndMoveTheirInventories(GameTestHelper h) {
        for (var family : java.util.List.of(PrefabDefinitions.COOP, PrefabDefinitions.BARN)) {
            var level = h.getLevel(); var farms = FarmInstanceRegistry.get(level.getServer()); var owner = UUID.randomUUID();
            var farm = farms.createFarm(owner, "Lifecycle", "Lifecycle test", FarmType.STANDARD);
            var data = BuildingWorldData.get(level.getServer());
            var player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(owner, "Lifecycle"));
            com.stardew.craft.building.BuildingCatalogService.open(player, com.stardew.craft.api.v1.building.StardewBuildingBuilders.ROBIN);
            long catalog = com.stardew.craft.building.BuildingBlueprintRegistry.revision();
            com.stardew.craft.player.PlayerStardewDataAPI.setMoney(player, 100000);
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(com.stardew.craft.item.ModItems.WOOD_NORMAL.get(), 999));
            player.getInventory().add(new ItemStack(com.stardew.craft.item.ModItems.WOOD_NORMAL.get(), 999));
            player.getInventory().add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft:stone")), 999));
            var record = record(farm.getInstanceId(), farm.getSlotIndex(), family, h.absolutePos(new BlockPos(3, 0, 20)), level.dimension().location());
            try {
                // Include the old and future site ground; the two reservations deliberately overlap.
                for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(47, 0, 47)))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                UUID permit = UUID.randomUUID(); data.recordPurchase(permit, farm.getInstanceId(), true, family); data.beginPrefab(record, permit, 10);
                record = data.find(record.id()); BuildingPlacementService.scaffold(level, record); data.markScaffold(record.id());
                var worker=com.stardew.craft.port.PortJava.getFirst(level.getEntitiesOfClass(RobinConstructionEntity.class,BuildingPlacementService.aabb(record.claim())));
                h.assertTrue(worker.getY()==record.anchor().getY()+1 && worker.getLightProbePosition(1).y>record.anchor().getY()+2.5,"Worker or head light probe is buried in the floor");
                for (int day = 11; day <= 13; day++) data.constructionDay(day, true);
                BuildingPlacementService.finish(level, record); record = data.find(record.id());
                int groundY=record.anchor().getY();
                var floorCell=BuildingTransfer.nativeCells(level,record,1).values().stream().filter(cell->cell.pos().getY()==groundY).findFirst().orElseThrow();
                h.assertTrue(level.getBlockState(floorCell.pos()).getBlock()==floorCell.state().getBlock(),"The schem floor did not replace the original ground layer");
                var protectedBreak=new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,floorCell.pos(),level.getBlockState(floorCell.pos()),player);
                com.stardew.craft.event.FarmAreaProtectionEvents.onBlockBreak(protectedBreak);
                h.assertTrue(protectedBreak.isCanceled(),"Native components bypass the shared public-area break handler");
                h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, BuildingPlacementService.aabb(record.claim())).isEmpty(), "Initial construction dropped items");
                for (int target = 2; target <= 3; target++) {
                    var trough = BuildingTransfer.nativeCells(level, record, record.tier()).values().stream()
                            .filter(cell -> cell.state().is(ModBlocks.FEED_TROUGH.get())).findFirst().orElseThrow();
                    var troughEntity = level.getBlockEntity(trough.pos()); var food = troughEntity.saveWithFullMetadata();
                    food.put("hay", PortItemStacks.save(new ItemStack(com.stardew.craft.item.ModItems.HAY.get(), 1), level.registryAccess()));
                    if (target == 2) troughEntity.load(food);
                    // The high animation must have an actual wall-hung notice location in each old template.
                    h.assertTrue(BuildingLifecycleService.noticePosition(level, record) != null, "No upgrade wall for " + family + " tier " + record.tier());
                    var oldCells = BuildingTransfer.nativeCells(level, record, record.tier());
                    var newCells = BuildingTransfer.nativeCells(level, record, target);
                    BlockPos conflict = newCells.keySet().stream().filter(pos -> !oldCells.containsKey(pos) && level.getBlockState(pos).isAir()).findFirst().orElseThrow();
                    level.setBlock(conflict, Blocks.CHEST.defaultBlockState(), 18);
                    ((ChestBlockEntity) level.getBlockEntity(conflict)).setItem(0, new ItemStack(Items.DIAMOND, 3));
                    var offer = com.stardew.craft.building.BuildingBlueprintRegistry.find(family.withPath(family.getPath() + "_upgrade_" + target)).orElseThrow();
                    var permitItem = (BuildingUpgradePermitItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.get(offer.definition().resultItem());
                    int beforePurchase = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
                    BuildingPurchaseService.purchaseUpgrade(player, offer, permitItem);
                    var upgradeStack = player.getInventory().items.stream().filter(item -> item.is(permitItem)).findFirst().orElseThrow();
                    h.assertTrue(data.order(record.id()) == null, "Buying a permit started work remotely");
                    h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == beforePurchase - PrefabDefinitions.get(family).tier(target).upgrade().money(), "Wrong source-backed permit price");
                    int beforeConflict = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
                    BuildingUpgradeService.use(player, record.manager(), upgradeStack, permitItem);
                    h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == beforeConflict && data.order(record.id()) == null, "Blocked upgrade charged or started");
                    h.assertTrue(((ChestBlockEntity) level.getBlockEntity(conflict)).getItem(0).getCount() == 3, "Preflight consumed a conflicting inventory");
                    level.removeBlockEntity(conflict); level.setBlock(conflict, Blocks.AIR.defaultBlockState(), 18);
                    int paid = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
                    ItemStack duplicate = upgradeStack.copy();
                    h.assertTrue(BuildingUpgradeService.use(player, record.manager(), upgradeStack, permitItem), "Valid permit did not start the upgrade");
                    h.assertTrue(upgradeStack.isEmpty(), "Successful upgrade did not consume its physical permit");
                    h.assertTrue(!BuildingUpgradeService.use(player, record.manager(), duplicate, permitItem), "Copied permit started a second upgrade");
                    h.assertTrue(com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player) == paid, "Permit use charged twice");
                    record = data.find(record.id()); BuildingLifecycleService.upgradeScaffold(level, record);
                    BlockPos work = BuildingLifecycleService.indoorWorkPosition(level, record);
                    h.assertTrue(work != null && level.getBlockState(work).isAir() && level.getBlockState(work.above()).isAir(), "Upgrade worker has no clear indoor work point");
                    h.assertTrue(BuildingProtection.protects(level, work), "Upgrade accepted new blocks in its active work space");
                    h.assertTrue(BuildingProtection.deniesReplacement(level, work, Blocks.STONE.defaultBlockState()), "Upgrade work space allowed a new obstacle after permit use");
                    int today = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
                    data.constructionDay(today + 1, true); data.constructionDay(today + 2, true);
                    BuildingLifecycleService.finishUpgrade(level, record); record = data.find(record.id());
                    int hay = 0;
                    for (var cell : BuildingTransfer.nativeCells(level, record, target).values()) {
                        if (!cell.state().is(ModBlocks.FEED_TROUGH.get()) && !cell.state().is(ModBlocks.AUTOFEED_TROUGH.get())) continue;
                        var hayTag = level.getBlockEntity(cell.pos()).saveWithFullMetadata().getCompound("hay");
                        hay += PortItemStacks.parse(level.registryAccess(), hayTag).orElse(ItemStack.EMPTY).getCount();
                    }
                    h.assertTrue(hay == 1, "Upgrade lost feeding inventory, including ordinary to automatic conversion");
                    h.assertTrue(record.tier() == target && record.phase() == BuildingRecord.Phase.READY, "Upgrade did not reach target");
                    for (var cell : BuildingTransfer.nativeCells(level, record, target).values()) h.assertTrue(level.getBlockState(cell.pos()).getBlock() == cell.state().getBlock(), "Missing upgraded block at " + cell.pos());
                }
                var tier = PrefabDefinitions.get(family).tier(3);
                BlockPos chestPos = PrefabDefinitions.world(tier.animalSpawn(), tier.anchor(), record.anchor(), Rotation.NONE);
                h.assertTrue(level.getBlockState(chestPos).isAir(), "Animal spawn is not clear");
                level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3); ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.DIAMOND, 7));
                BlockPos destination = record.anchor().east(16);
                var transfer = BuildingTransfer.move(level, record, destination);
                h.assertTrue(BuildingPlacementService.checkSpace(level, transfer.after().claim(), record.claim()) == null, "Overlapping translation rejected");
                h.assertTrue(data.beginTransfer(transfer) == BuildingWorldData.Result.SUCCESS, "Transfer rejected");
                var restored = BuildingWorldData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess()).transfer(record.id());
                h.assertTrue(restored != null && restored.after().id().equals(record.id()), "Pending transfer lost identity");
                // Replaying an interrupted projection must not duplicate inventory or leave the old manager.
                restored.project(level); restored.project(level); data.finishTransfer(record.id());
                var moved = data.find(record.id());
                h.assertTrue(moved.anchor().equals(destination) && moved.tier() == 3 && moved.farmId().equals(record.farmId()), "Movement changed identity/tier/farm");
                h.assertTrue(((ChestBlockEntity) level.getBlockEntity(chestPos.east(16))).getItem(0).getCount() == 7, "Move lost chest contents");
                h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, BuildingPlacementService.aabb(moved.claim())).isEmpty(), "Transfer dropped items: " + level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, BuildingPlacementService.aabb(moved.claim())).stream().map(e -> e.getItem().toString()).toList());
                h.assertTrue(!level.destroyBlock(moved.manager(), true), "Moved manager lost protection");
                h.assertTrue(!level.getBlockState(record.manager()).is(PrefabDefinitions.managerBlock(family)), "Old manager remains");
            } finally {
                farms.deleteFarm(owner);
                BuildingProtection.transfer(() -> { for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(47, 23, 47)))) { level.removeBlockEntity(pos); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18); } });
            }
        }
        h.succeed();
    }
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void dateJumpsCountWorkingDaysWithoutReplayingPastDays(GameTestHelper h) {
        var order = new ConstructionOrder(3, 10, true);
        order = order.through(14, day -> day != 11 && day != 13);
        h.assertTrue(order.remainingDays() == 1 && order.lastProcessedDay() == 14, "Forward jump skipped working days or counted holidays");
        h.assertTrue(order.through(12, day -> true).equals(order), "Backward jump rewound order");
        order = order.through(15, day -> true);
        h.assertTrue(order.remainingDays() == 0, "Third working day did not finish");
        var loaded = ConstructionOrder.load(order.save());
        h.assertTrue(loaded.through(15, day -> true).equals(loaded), "Reload replayed same-day completion");
        h.succeed();
    }

    private static BuildingRecord record(UUID farm, int slot, ResourceLocation family, BlockPos anchor, ResourceLocation dimension) {
        var definition = PrefabDefinitions.get(family); var tier = definition.tier(1);
        return BuildingRecord.waiting(farm, slot, family, BuildingRecord.Mode.PREFAB, dimension, anchor,
                PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, Rotation.NONE), Direction.SOUTH,
                PrefabDefinitions.transform(definition.reservation(), anchor, Rotation.NONE));
    }
}
