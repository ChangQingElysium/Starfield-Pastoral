package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.building.runtime.BuildingBounds;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingService;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmPermissionManager;
import com.stardew.craft.farm.FarmType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.stardew.craft.building.runtime.BuildingRecord.Action.*;
import static com.stardew.craft.building.runtime.BuildingWorldData.Result.*;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class BuildingFoundationGameTests {
    private static final ResourceLocation DIMENSION = ModDimensions.STARDEW_VALLEY.location();
    private static final ResourceLocation COOP = new ResourceLocation("stardewcraft:coop");

    private BuildingFoundationGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void competingReservationsAcrossFarmsHaveOneWinner(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var first = waiting(BuildingRecord.Mode.PREFAB, -12, DIMENSION);
        var second = waiting(BuildingRecord.Mode.SELF_BUILT, -12, DIMENSION);
        helper.assertTrue(data.register(first) == SUCCESS, "First request rejected");
        helper.assertTrue(data.register(second) == OVERLAP, "Competing farm acquired the same space");
        helper.assertTrue(data.register(first) == DUPLICATE_ID, "Duplicate request inserted twice");
        helper.assertTrue(data.all().size() == 1 && data.find(second.id()) == null, "Rejected request left a record");
        helper.assertTrue(data.occupying(DIMENSION, first.manager()).equals(first.id()), "Claim not owned by winner");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void adjacentFacesAndDifferentDimensionsStayIndependent(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var first = waiting(BuildingRecord.Mode.PREFAB, -12, DIMENSION);
        helper.assertTrue(data.register(first) == SUCCESS, "Initial claim failed");
        helper.assertTrue(data.register(waiting(BuildingRecord.Mode.PREFAB, -2, DIMENSION)) == SUCCESS,
                "Touching faces incorrectly overlap");
        helper.assertTrue(data.register(waiting(BuildingRecord.Mode.PREFAB, -3, DIMENSION)) == OVERLAP,
                "One-block overlap was missed");
        helper.assertTrue(data.register(waiting(BuildingRecord.Mode.PREFAB, -12,
                new ResourceLocation("minecraft:overworld"))) == SUCCESS, "Different dimensions collide");
        helper.assertTrue(!first.claim().contains(first.claim().maxExclusive()), "Exclusive maximum included");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void reloadRebuildsReservationFromBuildingRecords(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var record = waiting(BuildingRecord.Mode.PREFAB, 10, DIMENSION);
        data.register(record);
        data.advance(record.id(), 0, START_CONSTRUCTION);
        var loaded = BuildingWorldData.load(data.save(new CompoundTag()),
                helper.getLevel().registryAccess());
        helper.assertTrue(loaded.find(record.id()).equals(data.find(record.id())), "Reload changed identity or phase");
        helper.assertTrue(loaded.occupying(DIMENSION, record.manager()).equals(record.id()), "Reload lost the claim");
        helper.assertTrue(loaded.register(waiting(BuildingRecord.Mode.SELF_BUILT, 10, DIMENSION)) == OVERLAP,
                "Reload allowed a second claim");
        helper.assertTrue(loaded.advance(record.id(), 0, FINISH_CONSTRUCTION) == STALE_REVISION,
                "Reload lost stale-request protection");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void selfBuiltAssessmentRequiresExplicitAcceptanceAndNeverErasesTier(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var record = waiting(BuildingRecord.Mode.SELF_BUILT, 0, DIMENSION);
        data.register(record);
        helper.assertTrue(data.advance(record.id(), 0, START_CONSTRUCTION) == INVALID_STATE, "Self-built used Robin");
        helper.assertTrue(data.assessResidence(record.id(), 0, 3) == SUCCESS, "Assessment failed");
        helper.assertTrue(data.find(record.id()).phase() == BuildingRecord.Phase.WAITING && data.find(record.id()).tier() == 1, "Scanning auto-built the home");
        for(int tier=1;tier<=3;tier++) {
            var candidate=data.find(record.id());
            helper.assertTrue(data.acceptSelf(record.id(),candidate.revision(),3)==SUCCESS,"Explicit acceptance failed");
            helper.assertTrue(data.find(record.id()).tier()==tier,"Acceptance skipped a tier");
        }
        var ready = data.find(record.id());
        helper.assertTrue(ready.tier() == 3 && ready.phase() == BuildingRecord.Phase.READY
                && ready.residence() == BuildingRecord.Residence.VALID, "Wrong qualified residence state");
        data.assessResidence(record.id(), ready.revision(), 0);
        var missingFacilities = data.find(record.id());
        helper.assertTrue(missingFacilities.tier() == 3 && missingFacilities.phase() == BuildingRecord.Phase.READY
                && missingFacilities.residence() == BuildingRecord.Residence.INVALID, "Facility loss erased building progress");
        helper.assertTrue(missingFacilities.claim().equals(record.claim()) && missingFacilities.id().equals(record.id()),
                "Upgrade changed fixed range or identity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void prefabUpgradesRequireAnOrderAndKeepOldTierUntilCompletion(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var record = waiting(BuildingRecord.Mode.PREFAB, 0, DIMENSION);
        data.register(record);
        helper.assertTrue(data.advance(record.id(), 0, FINISH_CONSTRUCTION) == INVALID_STATE, "Skipped construction");
        helper.assertTrue(data.assessResidence(record.id(), 0, 3) == INVALID_STATE, "Unbuilt prefab became usable");
        helper.assertTrue(data.advance(record.id(), 0, START_CONSTRUCTION) == SUCCESS, "Could not start");
        helper.assertTrue(data.advance(record.id(), 1, FINISH_CONSTRUCTION) == SUCCESS, "Could not finish");
        data.assessResidence(record.id(), 2, 3);
        helper.assertTrue(data.find(record.id()).tier() == 1, "Player facilities upgraded prefab");
        helper.assertTrue(data.advance(record.id(), 3, START_UPGRADE) == SUCCESS, "Could not start upgrade");
        helper.assertTrue(data.find(record.id()).tier() == 1
                && data.find(record.id()).residence() == BuildingRecord.Residence.VALID, "Upgrade removed old housing");
        helper.assertTrue(data.advance(record.id(), 4, START_UPGRADE) == INVALID_STATE, "Started concurrent upgrade");
        helper.assertTrue(data.advance(record.id(), 4, FINISH_UPGRADE) == SUCCESS, "Could not complete upgrade");
        helper.assertTrue(data.advance(record.id(), 4, FINISH_UPGRADE) == STALE_REVISION, "Completed same order twice");
        helper.assertTrue(data.find(record.id()).tier() == 2, "Duplicate completion advanced tier twice");
        data.advance(record.id(), 5, START_UPGRADE);
        data.advance(record.id(), 6, FINISH_UPGRADE);
        helper.assertTrue(data.advance(record.id(), 7, START_UPGRADE) == INVALID_STATE, "Upgraded beyond tier three");
        helper.assertTrue(data.find(record.id()).claim().equals(record.claim()), "Prefab lost full-tier reservation");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void malformedSavedClaimsAreRejectedAndFarmRemovalFreesSpace(GameTestHelper helper) {
        var data = new BuildingWorldData();
        var record = waiting(BuildingRecord.Mode.PREFAB, 0, DIMENSION);
        data.register(record);
        var tag = data.save(new CompoundTag());
        ListTag list = tag.getList("Buildings", 10);
        list.add(waiting(BuildingRecord.Mode.PREFAB, 1, DIMENSION).save());
        boolean rejected = false;
        try {
            BuildingWorldData.load(tag, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Overlapping persisted claims accepted");
        data.removeFarm(record.farmId());
        helper.assertTrue(data.all().isEmpty() && data.occupying(DIMENSION, record.manager()) == null,
                "Deleting farm left an occupied claim");
        helper.assertTrue(data.register(waiting(BuildingRecord.Mode.PREFAB, 0, DIMENSION)) == SUCCESS,
                "Deleted farm space cannot be reused");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void farmTransferPreservesIdentityAndRecycledSlotGetsNewIdentity(GameTestHelper helper) {
        var registry = new FarmInstanceRegistry();
        UUID firstOwner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        var farm = registry.createFarm(firstOwner, "Builder", "Foundation test", FarmType.STANDARD);
        var saved = FarmInstance.load(farm.save());
        helper.assertTrue(saved.getInstanceId().equals(farm.getInstanceId()), "Farm reload changed identity");
        CompoundTag withoutId = farm.save();
        withoutId.remove("InstanceId");
        helper.assertTrue(FarmInstance.load(withoutId).getInstanceId().equals(FarmInstance.load(withoutId).getInstanceId()),
                "Initial farm identity changed before its first save");
        helper.assertTrue(registry.transferFarm(firstOwner, nextOwner, "NewBuilder"), "Transfer failed");
        helper.assertTrue(registry.getFarm(nextOwner).getInstanceId().equals(farm.getInstanceId()),
                "Transfer changed farm identity");
        registry.deleteFarm(nextOwner);
        var replacement = registry.createFarm(firstOwner, "Builder", "New farm", FarmType.STANDARD);
        helper.assertTrue(replacement.getSlotIndex() == farm.getSlotIndex(), "Test did not reuse a slot");
        helper.assertTrue(!replacement.getInstanceId().equals(farm.getInstanceId()), "New farm inherited deleted identity");
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void serverBoundaryUsesCurrentFarmPermissionsAndRejectsOtherWorlds(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        var registry = FarmInstanceRegistry.get(server);
        var farm = registry.createFarm(owner, "Owner", "Permission test", FarmType.STANDARD);
        UUID currentOwner = owner;
        try {
            registry.addMember(owner, member);
            var anchor = farm.getFarmBoundsMin().offset(4, 1, 4);
            var bounds = new BuildingBounds(anchor, anchor.offset(10, 8, 10));
            var denied = BuildingService.register(server, DIMENSION, visitor, COOP, BuildingRecord.Mode.PREFAB,
                    anchor, anchor.above(), Direction.SOUTH, bounds);
            helper.assertTrue(denied.failure() == BuildingService.Failure.FORBIDDEN, "Visitor can claim a farm");
            var success = BuildingService.register(server, DIMENSION, owner, COOP, BuildingRecord.Mode.PREFAB,
                    anchor, anchor.above(), Direction.SOUTH, bounds);
            helper.assertTrue(success.failure() == BuildingService.Failure.NONE, "Owner cannot claim own farm");
            var record = BuildingWorldData.get(server).find(success.buildingId());
            helper.assertTrue(BuildingService.canManage(server, member, record), "Farm member denied");
            FarmPermissionManager.get().setPermission(owner, visitor, FarmPermissionManager.PERM_FULL);
            helper.assertTrue(BuildingService.canManage(server, visitor, record), "Explicit build permission ignored");
            FarmPermissionManager.get().setPermission(owner, visitor, FarmPermissionManager.PERM_VISIT);
            helper.assertTrue(!BuildingService.canManage(server, visitor, record), "Revoked build permission still works");
            var competing = BuildingService.register(server, DIMENSION, member, COOP, BuildingRecord.Mode.SELF_BUILT,
                    anchor, anchor.above(), Direction.SOUTH, bounds);
            helper.assertTrue(competing.failure() == BuildingService.Failure.OVERLAP, "Second player acquired same region");
            var wrongWorldPlayer = FakePlayerFactory.get(server.overworld(), new GameProfile(owner, "Owner"));
            helper.assertTrue(BuildingService.register(wrongWorldPlayer, COOP, BuildingRecord.Mode.PREFAB,
                    anchor, anchor.above(), Direction.SOUTH, bounds).failure() == BuildingService.Failure.WRONG_DIMENSION,
                    "Farm coordinates accepted in another dimension");
            var outside = new BuildingBounds(anchor, farm.getFarmBoundsMax().offset(2, 1, 1));
            helper.assertTrue(BuildingService.register(server, DIMENSION, owner, COOP, BuildingRecord.Mode.PREFAB,
                    anchor, anchor.above(), Direction.SOUTH, outside).failure() == BuildingService.Failure.OUTSIDE_FARM,
                    "Reservation escaped farm bounds");
            helper.assertTrue(registry.transferFarm(owner, visitor, "Visitor"), "Transfer failed");
            currentOwner = visitor;
            helper.assertTrue(BuildingService.canManage(server, visitor, record), "New owner cannot manage inherited building");
            // Existing farm policy may retain the previous owner as an ordinary member.
            if (registry.getFarm(visitor).isFarmer(owner)) {
                helper.assertTrue(BuildingService.canManage(server, owner, record), "Retained farm member denied");
                registry.removeMember(visitor, owner);
            }
            helper.assertTrue(!BuildingService.canManage(server, owner, record), "Removed old owner retained owner privileges");
            helper.assertTrue(BuildingWorldData.get(server).find(record.id()) != null, "Transfer removed building");
            registry.deleteFarm(visitor);
            helper.assertTrue(BuildingWorldData.get(server).find(record.id()) == null, "Farm deletion did not remove building");
            var replacement = registry.createFarm(visitor, "Visitor", "Replacement", FarmType.STANDARD);
            // The shared suite may already have other freed slots ahead of this one in the FIFO.
            for (int attempt = 0; replacement.getSlotIndex() != farm.getSlotIndex()
                    && attempt < registry.getNextSlotIndex(); attempt++) {
                registry.deleteFarm(visitor);
                replacement = registry.createFarm(visitor, "Visitor", "Replacement", FarmType.STANDARD);
            }
            helper.assertTrue(replacement.getSlotIndex() == farm.getSlotIndex(), "Test did not reuse farm slot");
            helper.assertTrue(!BuildingService.canManage(server, visitor, record), "New farm can manage previous farm's building");
        } finally {
            registry.deleteFarm(currentOwner);
        }
        helper.succeed();
    }

    private static BuildingRecord waiting(BuildingRecord.Mode mode, int x, ResourceLocation dimension) {
        BlockPos anchor = new BlockPos(x, 64, -9);
        return BuildingRecord.waiting(UUID.randomUUID(), 0, COOP, mode, dimension, anchor,
                anchor.offset(2, 1, 2), Direction.SOUTH, new BuildingBounds(anchor, anchor.offset(10, 8, 10)));
    }
}
