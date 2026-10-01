package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.api.v1.building.StardewBuildingBuilders;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.BuildingManagerModelBlock;
import com.stardew.craft.building.BuildingBlueprintRegistry;
import com.stardew.craft.building.BuildingCatalogService;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class CoopConstructionGameTests {
    private CoopConstructionGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void sixTemplatesResolveRegisteredStatesAndRotateAroundTheSameDoor(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String name : new String[]{"coop", "barn"}) {
            var family = PrefabDefinitions.get(new ResourceLocation("stardewcraft:" + name));
            for (var tier : family.tiers()) {
                var template = PrefabDefinitions.template(level, tier);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var rotation = PrefabDefinitions.rotation(facing);
                    BlockPos anchor = new BlockPos(-23, 61, -31);
                    var claim = PrefabDefinitions.transform(family.reservation(), anchor, rotation);
                    var occupied = new HashSet<BlockPos>();
                    int managers = 0;
                    for (var cell : template.cells()) {
                        BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), anchor, rotation);
                        helper.assertTrue(claim.contains(pos) && occupied.add(pos), "Rotation escaped reservation or duplicated a block");
                        var state = cell.state();
                        for (var property : state.getProperties()) {
                            if (property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty directionProperty
                                    && property.getName().equals("facing")) {
                                helper.assertTrue(state.rotate(rotation).getValue(directionProperty) == rotation.rotate(state.getValue(directionProperty)),
                                        "Directional prefab block does not rotate: " + state);
                            }
                        }
                        if (state.is(name.equals("coop") ? ModBlocks.COOP_MANAGER.get() : ModBlocks.BARN_MANAGER.get())) {
                            managers++;
                            helper.assertTrue(pos.equals(PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation)), "Wrong manager marker");
                            helper.assertTrue(state.rotate(rotation).getValue(BuildingManagerModelBlock.FACING) == facing, "Manager facing did not rotate");
                        }
                        helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90)
                                .rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90).equals(state), "Four rotations changed block state");
                    }
                    helper.assertTrue(managers == 1, "Template needs exactly one manager");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void openSkyQualifiesForBuildingAndUpgradeWithOnlyFacilitiesInsideFixedRange(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos manager = helper.absolutePos(new BlockPos(12, 1, 12));
        var family = PrefabDefinitions.get(PrefabDefinitions.COOP);
        var bounds = family.selfBounds(manager);
        helper.assertTrue(bounds.maxExclusive().getX() - bounds.min().getX() == 11
                && bounds.maxExclusive().getY() - bounds.min().getY() == 8 && family.managerPrice() == 500, "Approved self-build parameters changed");
        for (int x = bounds.min().getX(); x < bounds.maxExclusive().getX(); x++) {
            for (int z = bounds.min().getZ(); z < bounds.maxExclusive().getZ(); z++) level.setBlock(new BlockPos(x, manager.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(level.canSeeSky(manager), "Fixture must be open to the sky");
        for (int i = 0; i < 4; i++) level.setBlock(manager.offset(-4 + i, 0, -3), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
        BlockPos hopper = manager.offset(2, 0, 2);
        level.setBlock(hopper, ModBlocks.HAY_HOPPER.get().defaultBlockState(), 3);
        int eligible = BuildingResidence.scan(level, bounds, PrefabDefinitions.COOP).eligibleTier();
        helper.assertTrue(eligible == 1, "Open-sky residence rejected");
        var data = new BuildingWorldData();
        var home = BuildingRecord.waiting(UUID.randomUUID(), 0, PrefabDefinitions.COOP, BuildingRecord.Mode.SELF_BUILT,
                level.dimension().location(), manager, manager, Direction.SOUTH, bounds);
        data.register(home);
        helper.assertTrue(data.acceptSelf(home.id(), home.revision(), eligible) == BuildingWorldData.Result.SUCCESS, "Open-sky construction failed");
        // Out-of-range facilities must not grant tier two.
        for (int i = 0; i < 4; i++) level.setBlock(manager.offset(6 + i, 0, 0), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
        helper.assertTrue(BuildingResidence.scan(level, bounds, PrefabDefinitions.COOP).eligibleTier() == 1, "Outside facilities counted");
        for (int i = 0; i < 4; i++) level.setBlock(manager.offset(-4 + i, 0, -2), ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
        level.setBlock(manager.offset(3, 0, 2), ModBlocks.INCUBATOR.get().defaultBlockState(), 3);
        eligible = BuildingResidence.scan(level, bounds, PrefabDefinitions.COOP).eligibleTier();
        helper.assertTrue(eligible == 2, "Extra facilities did not qualify tier two");
        home = data.find(home.id());
        helper.assertTrue(data.acceptSelf(home.id(), home.revision(), eligible) == BuildingWorldData.Result.SUCCESS, "Open-sky upgrade failed");
        home = data.find(home.id());
        helper.assertTrue(home.tier() == 2 && home.claim().equals(bounds), "Upgrade changed the fixed range or failed to advance");
        helper.assertTrue(com.stardew.craft.animal.runtime.LivestockHomes.spawn(level, home) != null, "Open-sky residence has no animal arrival point");
        level.removeBlock(hopper, false);
        helper.assertTrue(BuildingResidence.scan(level, bounds, PrefabDefinitions.COOP).eligibleTier() == 0, "Missing required facilities were accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void placementChecksTheWholeThirdTierAirVolumeAndFlatSupport(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(18, 1, 22));
        var family = PrefabDefinitions.get(PrefabDefinitions.COOP);
        var claim = PrefabDefinitions.transform(family.reservation(), anchor, Rotation.NONE);
        for (int x = claim.min().getX(); x < claim.maxExclusive().getX(); x++) for (int z = claim.min().getZ(); z < claim.maxExclusive().getZ(); z++) {
            level.setBlock(new BlockPos(x, anchor.getY(), z), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(BuildingPlacementService.checkSpace(level, claim) == null, "Clear flat site rejected");
        BlockPos futureOnly = claim.maxInclusive();
        helper.assertTrue(!PrefabDefinitions.transform(family.tier(1).bounds(), anchor, Rotation.NONE).contains(futureOnly), "Test obstacle not outside first tier");
        level.setBlock(futureOnly, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(BuildingPlacementService.checkSpace(level, claim).issue().equals("air"), "Tier-three-only obstruction missed");
        level.removeBlock(futureOnly, false);
        level.removeBlock(claim.min(), false);
        helper.assertTrue(BuildingPlacementService.checkSpace(level, claim).issue().equals("ground"), "Uneven ground accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void constructionClockAndConsumedBlueprintSurviveReload(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var record = record(UUID.randomUUID(), 0, BlockPos.ZERO.above(64), new ResourceLocation("minecraft:overworld"));
        UUID permit = UUID.randomUUID();
        data.recordPurchase(permit, record.farmId(), true, PrefabDefinitions.COOP);
        helper.assertTrue(data.beginPrefab(record, permit, 10) == BuildingWorldData.Result.SUCCESS, "Blueprint rejected");
        helper.assertTrue(!data.permits(permit, record.farmId(), PrefabDefinitions.COOP), "Used blueprint remains spendable");
        helper.assertTrue(data.beginPrefab(record(record.farmId(), 0, new BlockPos(40, 64, 40), record.dimension()), permit, 10)
                == BuildingWorldData.Result.INVALID_STATE, "Copied blueprint redeemed twice");
        data.markScaffold(record.id());
        data.constructionDay(11, true);
        data.constructionDay(11, true);
        data.constructionDay(12, false);
        helper.assertTrue(data.order(record.id()).remainingDays() == 2, "Duplicate day or holiday advanced clock");
        var loaded = BuildingWorldData.load(data.save(new CompoundTag()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.hasPurchase(permit) && !loaded.permits(permit, record.farmId(), PrefabDefinitions.COOP), "Reload forgot receipt or permit consumption");
        loaded.constructionDay(13, true);
        loaded.constructionDay(14, true);
        helper.assertTrue(loaded.finishPrefab(record.id()) == BuildingWorldData.Result.SUCCESS, "Three effective work days did not finish");
        helper.assertTrue(loaded.finishPrefab(record.id()) == BuildingWorldData.Result.INVALID_STATE, "Order completed twice");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void scaffoldWorkerAndFinishedTemplateAreOneRecoverableOrder(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = FarmInstanceRegistry.get(level.getServer());
        UUID owner = UUID.randomUUID();
        var farm = registry.createFarm(owner, "Construction", "Construction test", FarmType.STANDARD);
        var data = BuildingWorldData.get(level.getServer());
        var record = record(farm.getInstanceId(), farm.getSlotIndex(), helper.absolutePos(new BlockPos(18, 1, 22)), level.dimension().location());
        try {
            UUID permit = UUID.randomUUID(); data.recordPurchase(permit, farm.getInstanceId(), true, PrefabDefinitions.COOP);
            helper.assertTrue(data.beginPrefab(record, permit, 10) == BuildingWorldData.Result.SUCCESS, "Could not create order");
            record = data.find(record.id());
            BuildingPlacementService.scaffold(level, record);
            BuildingPlacementService.scaffold(level, record);
            helper.assertTrue(level.getEntitiesOfClass(RobinConstructionEntity.class, BuildingPlacementService.aabb(record.claim())).size() == 1,
                    "Repeated projection duplicated Robin");
            helper.assertTrue(level.getBlockState(record.manager()).is(ModBlocks.COOP_MANAGER.get()), "Waiting manager missing");
            helper.assertTrue(!level.setBlock(record.manager(), Blocks.AIR.defaultBlockState(), 3), "Construction manager could be replaced");
            helper.assertTrue(!level.setBlock(record.anchor().above(3), Blocks.STONE.defaultBlockState(), 3), "Construction reservation accepts an obstacle");
            data.constructionDay(11, true); data.constructionDay(12, true);
            BuildingPlacementService.scaffold(level, record);
            data.constructionDay(13, true);
            BuildingPlacementService.finish(level, record);
            helper.assertTrue(data.find(record.id()).phase() == BuildingRecord.Phase.READY && data.order(record.id()) == null, "Completion left an active order");
            var tier = PrefabDefinitions.get(PrefabDefinitions.COOP).tier(1);
            int count = 0;
            for (var cell : PrefabDefinitions.template(level, tier).cells()) {
                BlockPos pos = PrefabDefinitions.world(cell.pos(), tier.anchor(), record.anchor(), Rotation.NONE);
                if (cell.state().isAir()) continue;
                helper.assertTrue(level.getBlockState(pos).getBlock() == cell.state().getBlock(), "Template block missing at " + pos);
                if (cell.state().is(ModBlocks.COOP_MANAGER.get())) count++;
                if (cell.blockEntity() != null) helper.assertTrue(level.getBlockEntity(pos) != null, "Template block entity not restored");
            }
            helper.assertTrue(count == 1, "Completion duplicated manager");
            helper.assertTrue(level.getEntitiesOfClass(RobinConstructionEntity.class, BuildingPlacementService.aabb(record.claim())).isEmpty(), "Worker remained after completion");
            helper.assertTrue(!level.destroyBlock(record.manager(), true), "Finished manager is destructible");
            BlockPos managerPos = record.manager();
            BuildingProtection.internal(() -> level.removeBlock(managerPos, false));
            helper.assertTrue(data.find(record.id()) != null, "Missing prefab projection erased identity");
            BuildingPlacementService.reconcileCompleted(level, record);
            helper.assertTrue(level.getBlockState(managerPos).is(ModBlocks.COOP_MANAGER.get()), "Reload repair did not rebuild missing manager");
            BlockPos furniture = PrefabDefinitions.world(tier.animalSpawn(), tier.anchor(), record.anchor(), Rotation.NONE);
            helper.assertTrue(level.getBlockState(furniture).isAir() && level.setBlock(furniture, Blocks.CHEST.defaultBlockState(), 3),
                    "Finished interior cannot accept player furniture");
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(furniture);
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            BuildingPlacementService.reconcileCompleted(level, record);
            helper.assertTrue(chest.getItem(0).getCount() == 7, "Projection reconciliation reset player inventory");
        } finally { registry.deleteFarm(owner); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void managerPurchaseUsesItsOwnPriceAndReplayDoesNotChargeAgain(GameTestHelper helper) {
        var level = helper.getLevel(); UUID owner = UUID.randomUUID();
        var registry = FarmInstanceRegistry.get(level.getServer());
        registry.createFarm(owner, "Buyer", "Purchase test", FarmType.STANDARD);
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "Buyer"));
        try {
            PlayerStardewDataAPI.setMoney(player, 4500);
            BuildingCatalogService.open(player, StardewBuildingBuilders.ROBIN);
            long revision = BuildingBlueprintRegistry.revision();
            UUID selfRequest = UUID.randomUUID();
            BuildingPurchaseService.purchase(player, revision, true, selfRequest, PrefabDefinitions.COOP);
            helper.assertTrue(PlayerStardewDataAPI.getMoney(player) == 4000 && player.getInventory().countItem(ModItems.COOP_MANAGER.get()) == 1,
                    "Manager used full prefab cost or required materials");
            BuildingPurchaseService.purchase(player, revision, true, selfRequest, PrefabDefinitions.COOP);
            helper.assertTrue(PlayerStardewDataAPI.getMoney(player) == 4000 && player.getInventory().countItem(ModItems.COOP_MANAGER.get()) == 1,
                    "Duplicate purchase charged or granted twice");
            UUID prefabRequest = UUID.randomUUID();
            BuildingPurchaseService.purchase(player, revision, false, prefabRequest, PrefabDefinitions.COOP);
            helper.assertTrue(PlayerStardewDataAPI.getMoney(player) == 4000, "Missing materials partially charged money");
            player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft:wood_normal")), 300));
            player.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft:stone")), 100));
            BuildingPurchaseService.purchase(player, revision, false, prefabRequest, PrefabDefinitions.COOP);
            helper.assertTrue(PlayerStardewDataAPI.getMoney(player) == 0 && player.getInventory().countItem(ModItems.COOP_BLUEPRINT.get()) == 1,
                    "Prefab did not exchange exact cost for one blueprint");
        } finally { registry.deleteFarm(owner); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void previewPacketsPreserveTemplateAppearanceAndLocalizedMaterials(GameTestHelper helper) {
        var template = PrefabDefinitions.previewTag(helper.getLevel(), PrefabDefinitions.COOP, 1);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var packet = new com.stardew.craft.network.payload.BuildingTemplatePreviewPayload(template);
            com.stardew.craft.network.payload.BuildingTemplatePreviewPayload.STREAM_CODEC.encode(buffer, packet);
            helper.assertTrue(buffer.readableBytes() < 1_000_000, "Preview exceeds reasonable packet size");
            var decoded = com.stardew.craft.network.payload.BuildingTemplatePreviewPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.template().equals(template), "Preview lost appearance data");
        } finally { buffer.release(); }
        var registryBuffer = new com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var offer = new com.stardew.craft.network.payload.OpenBuildingRoutesPayload(5000, 500, 11, 8, 4000,
                    net.minecraft.network.chat.Component.translatable("item.stardewcraft.wood_normal"), 17, PrefabDefinitions.COOP);
            com.stardew.craft.network.payload.OpenBuildingRoutesPayload.STREAM_CODEC.encode(registryBuffer, offer);
            helper.assertTrue(com.stardew.craft.network.payload.OpenBuildingRoutesPayload.STREAM_CODEC.decode(registryBuffer).equals(offer),
                    "Build route packet lost price, range or localized material data");
        } finally { registryBuffer.release(); }
        helper.succeed();
    }

    private static BuildingRecord record(UUID farm, int slot, BlockPos anchor, ResourceLocation dimension) {
        var family = PrefabDefinitions.get(PrefabDefinitions.COOP); var tier = family.tier(1);
        return BuildingRecord.waiting(farm, slot, family.id(), BuildingRecord.Mode.PREFAB, dimension, anchor,
                PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, Rotation.NONE), Direction.SOUTH,
                PrefabDefinitions.transform(family.reservation(), anchor, Rotation.NONE));
    }
}
