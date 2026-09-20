package com.stardew.craft.gametest;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.greenhouse.GreenhouseBuildings;
import com.stardew.craft.item.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_documents")
@PrefixGameTestTemplate(false)
public final class BuildingDocumentGameTests {
    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void paidDocumentDeliveryIsPlannedBeforeInventoryChanges(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PaperInventory"));
        var inventory = player.getInventory(); inventory.clearContent();
        for (int i = 0; i < inventory.items.size(); i++) inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        var paper = new ItemStack(ModItems.COOP_BLUEPRINT.get());
        h.assertTrue(BuildingPurchasePlan.prepare(inventory, paper, List.of()) == null, "Full inventory accepted a paid blueprint");
        inventory.setItem(7, new ItemStack(ModItems.WOOD_NORMAL.get(), 300));
        var plan = BuildingPurchasePlan.prepare(inventory, paper, List.of(new BuildingPurchasePlan.Material(ModItems.WOOD_NORMAL.get(), 300)));
        h.assertTrue(plan != null && inventory.getItem(7).getCount() == 300, "Preflight spent materials or ignored the freed slot");
        plan.apply(inventory);
        h.assertTrue(inventory.getItem(7).is(ModItems.COOP_BLUEPRINT.get()), "Freed slot did not receive the blueprint");
        h.assertTrue(inventory.countItem(Items.COBBLESTONE) == 35 * 64, "Unrelated inventory changed");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void upgradePermissionsAreFarmFamilyAndTierBoundAfterReload(GameTestHelper h) {
        var data = new BuildingWorldData(); var farm = UUID.randomUUID(); var permit = UUID.randomUUID();
        data.recordUpgradePurchase(permit, farm, PrefabDefinitions.COOP, 2);
        data = BuildingWorldData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(data.permitsUpgrade(permit, farm, PrefabDefinitions.COOP, 2), "Permit lost its target on reload");
        h.assertTrue(!data.permits(permit, farm, PrefabDefinitions.COOP), "Upgrade permit can buy a new building");
        h.assertTrue(!data.permitsUpgrade(permit, farm, PrefabDefinitions.COOP, 3), "Permit skips an upgrade tier");
        h.assertTrue(!data.permitsUpgrade(permit, farm, PrefabDefinitions.BARN, 2), "Coop permit upgrades a barn");
        h.assertTrue(!data.permitsUpgrade(permit, UUID.randomUUID(), PrefabDefinitions.COOP, 2), "Permit escaped its farm");
        var definition = PrefabDefinitions.get(PrefabDefinitions.COOP);
        var record = BuildingRecord.waiting(farm, 0, PrefabDefinitions.COOP, BuildingRecord.Mode.PREFAB,
                h.getLevel().dimension().location(), new BlockPos(0, 64, 0), new BlockPos(5, 65, 6), Direction.SOUTH,
                PrefabDefinitions.transform(definition.reservation(), new BlockPos(0, 64, 0), Rotation.NONE));
        UUID build = UUID.randomUUID(); data.recordPurchase(build, farm, true, PrefabDefinitions.COOP);
        data.beginPrefab(record, build, 10); data.markScaffold(record.id());
        for (int day = 11; day <= 13; day++) data.constructionDay(day, true);
        data.finishPrefab(record.id()); record = data.find(record.id());
        h.assertTrue(data.beginPermittedUpgrade(record.id(), record.revision(), permit, 2, 13) == BuildingWorldData.Result.SUCCESS, "Matching paid upgrade rejected");
        h.assertTrue(data.beginPermittedUpgrade(record.id(), record.revision(), permit, 2, 13) != BuildingWorldData.Result.SUCCESS, "Duplicate upgrade consumed twice");
        h.assertTrue(!data.hasUpgradePermit(farm, PrefabDefinitions.COOP, 2), "Committed permit still spendable");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void fourCornerRotationsRestoreEveryCellAndTierBounds(GameTestHelper h) {
        for (BlockPos original : List.of(BlockPos.ZERO, new BlockPos(2, 5, -3), new BlockPos(-7, 0, 9))) {
            BlockPos result = original;
            for (int i = 0; i < 4; i++) result = PrefabDefinitions.rotateCell(result, Rotation.CLOCKWISE_90);
            h.assertTrue(result.equals(original), "Four rotations drifted the corner grid");
        }
        h.assertTrue(PrefabDefinitions.rotateCell(BlockPos.ZERO, Rotation.CLOCKWISE_90).equals(new BlockPos(-1, 0, 0)), "Rotation used a block center instead of the corner vertex");
        for (var family : List.of(PrefabDefinitions.COOP, PrefabDefinitions.BARN)) {
            var definition = PrefabDefinitions.get(family);
            h.assertTrue(definition.reservation().min().equals(BlockPos.ZERO), "Reservation has no common northwest origin");
            for (var tier : definition.tiers()) for (var rotation : Rotation.values()) {
                var outer = PrefabDefinitions.transform(definition.reservation(), BlockPos.ZERO, rotation);
                var inner = PrefabDefinitions.transform(tier.bounds(), BlockPos.ZERO, rotation);
                h.assertTrue(outer.contains(inner.min()) && outer.contains(inner.maxInclusive()), "A rotated tier escapes its reservation");
                var manager = PrefabDefinitions.world(tier.manager(), tier.anchor(), BlockPos.ZERO, rotation);
                h.assertTrue(inner.contains(manager), "Manager and building disagree on the rotated anchor");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void reservationEdgesReplaceOnlyOverlappingSegments(GameTestHelper h) {
        AABB small = new AABB(0, 0, 0, 4, 4, 4), large = new AABB(0, 0, 0, 8, 8, 8);
        h.assertTrue(BuildingOutline.excluding(small, small).isEmpty(), "Identical boxes draw duplicate edges");
        var edges = BuildingOutline.excluding(small, large);
        double length = edges.stream().mapToDouble(e -> e.from().distanceTo(e.to())).sum();
        h.assertTrue(edges.size() == 9 && length == 36, "Shared corner's three edges were not removed precisely");
        h.assertTrue(BuildingOutline.excluding(small, large.move(20, 0, 0)).size() == 12, "Separate boxes lost edges");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site", timeoutTicks = 40)
    public static void invalidBlueprintPinsWithoutConsumptionThenCanBeUnpinned(GameTestHelper h) {
        var level = h.getLevel(); var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PaperPin"));
        var ground = h.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        player.moveTo(ground.getX() + .5, ground.getY() + 1, ground.getZ() + 2.5, 0, 0);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ground).add(0, .5, 0));
        var stack = new ItemStack(ModItems.COOP_BLUEPRINT.get()); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var item = (BuildingBlueprintItem) stack.getItem();
        item.use(level, player, InteractionHand.MAIN_HAND);
        var expected=BuildingBlueprintItem.targetAnchor(stack,ground,BuildingBlueprintItem.facing(stack));
        h.assertTrue(stack.getCount() == 1 && expected.equals(BuildingBlueprintItem.pinned(stack, level)), "First use consumed the document or refused an invalid site");
        var restored = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) stack.save(level.registryAccess()));
        h.assertTrue(BuildingBlueprintItem.pinned(restored, level).equals(expected), "Pinned preview was lost on item reload");
        h.runAfterDelay(1, () -> {
            item.use(level, player, InteractionHand.MAIN_HAND);
            h.assertTrue(stack.getCount() == 1 && BuildingBlueprintItem.pinned(stack, level) != null, "Invalid confirmation lost the document/preview");
            h.runAfterDelay(1, () -> {
                // Turn away and move out of reach: cancelling must not depend on hitting the box.
                player.moveTo(ground.getX()+50,ground.getY()+2,ground.getZ()+50,0,-85);
                player.setShiftKeyDown(true); item.use(level, player, InteractionHand.MAIN_HAND);
                h.assertTrue(stack.getCount() == 1 && BuildingBlueprintItem.pinned(stack, level) == null, "Shift-use did not return to following mode");
                h.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "empty")
    public static void blueprintRotationPersistsAndDoesNotFollowPlayerFacing(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PaperRotation"));
        player.setYRot(180);
        var stack = new ItemStack(ModItems.BARN_BLUEPRINT.get()); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BuildingBlueprintItem.rotate(player, InteractionHand.MAIN_HAND, false);
        h.assertTrue(BuildingBlueprintItem.facing(stack) == Direction.WEST, "Clockwise rotation is reversed");
        player.setYRot(45);
        var restored = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) stack.save(h.getLevel().registryAccess()));
        h.assertTrue(BuildingBlueprintItem.facing(restored) == Direction.WEST, "Rotation follows camera or disappears on reload");
        BuildingBlueprintItem.rotate(player, InteractionHand.MAIN_HAND, true);
        h.assertTrue(BuildingBlueprintItem.facing(stack) == Direction.SOUTH, "Counterclockwise rotation did not undo the turn");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void documentGripFacesPlayerAndKeepsTheFloorInTheGround(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"FrontGrip"));
        for(var family:java.util.List.of(PrefabDefinitions.COOP,PrefabDefinitions.BARN))for(var view:Direction.Plane.HORIZONTAL) {
            player.setYRot(view.toYRot());var item=new ItemStack(PrefabDefinitions.blueprintItem(family));
            var front=BuildingBlueprintItem.facing(item,player);h.assertTrue(front==view.getOpposite(),"Fresh blueprint shows its back to the player");
            var ground=new BlockPos(30,64,30);var anchor=BuildingBlueprintItem.targetAnchor(item,ground,front);
            h.assertTrue(anchor.getY()==ground.getY(),"Schem floor floats one block above the clicked ground");
            var bounds=PrefabDefinitions.transform(PrefabDefinitions.get(family).reservation(),anchor,PrefabDefinitions.rotation(front));
            h.assertTrue(bounds.contains(ground),"Clicked ground lies outside the preview and cannot select it again");
            var grip=anchor.offset(BuildingBlueprintItem.gripOffset(family,front));
            h.assertTrue(grip.getY()==ground.getY() && grip.distManhattan(ground)<=2,"Cursor detached from front corner");
            var rotated=front.getClockWise();var rotatedAnchor=anchor.offset(BuildingBlueprintItem.gripOffset(family,front)).subtract(BuildingBlueprintItem.gripOffset(family,rotated));
            h.assertTrue(rotatedAnchor.offset(BuildingBlueprintItem.gripOffset(family,rotated)).equals(grip),"Pinned rotation moved its grip point");
        }h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void allBuildingDocumentsParticipateInStardewItemMetadata(GameTestHelper h) {
        for(var holder:java.util.List.of(ModItems.COOP_BLUEPRINT,ModItems.BARN_BLUEPRINT,ModItems.SILO_BLUEPRINT,ModItems.FISH_POND_BLUEPRINT,ModItems.GREENHOUSE_BLUEPRINT,
                ModItems.COOP_UPGRADE_2_PERMIT,ModItems.COOP_UPGRADE_3_PERMIT,ModItems.BARN_UPGRADE_2_PERMIT,ModItems.BARN_UPGRADE_3_PERMIT)) {
            var item=holder.get();h.assertTrue(item instanceof com.stardew.craft.item.IStardewItem,"Document has no Stardew metadata");
            var metadata=(com.stardew.craft.item.IStardewItem)item;
            h.assertTrue(metadata.getItemTypeKey().equals("stardewcraft.type.building") && metadata.getSellPrice(new ItemStack(item))==-1,"Paid document has incorrect category/resale metadata");
        }h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void greenhousePrefabKeepsItsEmbeddedFoundationAndManager(GameTestHelper h) {
        var family=PrefabDefinitions.get(GreenhouseBuildings.FAMILY);var tier=family.tier(1);
        var template=PrefabDefinitions.template(h.getLevel(),tier);
        GreenhouseBuildings.validateAssets(h.getLevel());
        h.assertTrue(family.tiers().size()==1,"Greenhouse unexpectedly gained an upgrade tier");
        h.assertTrue(tier.size().equals(new BlockPos(15,11,13)) && tier.manager().equals(new BlockPos(12,1,11)),
                "Greenhouse template dimensions or manager marker drifted");
        h.assertTrue(template.retainedGround().size()==52 && template.retainedGround().stream().allMatch(pos->pos.getY()==0),
                "Greenhouse no longer retains its embedded Y=0 soil foundation");
        h.assertTrue(template.cells().stream().anyMatch(cell->cell.pos().equals(tier.manager())
                        && cell.state().is(ModBlocks.GREENHOUSE_MANAGER.get())),
                "Greenhouse manager marker was not imported as the manager block");
        var before=new BuildingRecord(UUID.randomUUID(),UUID.randomUUID(),0,GreenhouseBuildings.FAMILY,
                BuildingRecord.Mode.PREFAB,h.getLevel().dimension().location(),BlockPos.ZERO,tier.manager(),Direction.SOUTH,
                family.reservation(),BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,0,"");
        var movedAnchor=new BlockPos(40,0,40);var movedRotation=PrefabDefinitions.rotation(Direction.WEST);
        var after=new BuildingRecord(before.id(),before.farmId(),0,before.family(),before.mode(),before.dimension(),
                movedAnchor,PrefabDefinitions.world(tier.manager(),tier.anchor(),movedAnchor,movedRotation),Direction.WEST,
                PrefabDefinitions.transform(family.reservation(),movedAnchor,movedRotation),
                BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,1,"");
        h.assertTrue(BuildingTransfer.destination(before,after,GreenhouseBuildings.portal(before))
                        .equals(GreenhouseBuildings.portal(after)),
                "A rotated greenhouse move separates its portal from the registered entrance");
        h.assertTrue(GreenhouseBuildings.portal(before).equals(new BlockPos(7,1,12))
                        && GreenhouseBuildings.exit(before).equals(new BlockPos(7,1,12)),
                "Greenhouse entrance trigger is not in front of the authored oak door");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void liftedMoveSnapshotHidesAndExactlyRestoresTheBuilding(GameTestHelper h) {
        var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(4,3,4));
        var claim=new BuildingBounds(anchor.offset(-1,0,-1),anchor.offset(2,3,2));
        var record=new BuildingRecord(UUID.randomUUID(),UUID.randomUUID(),0,UtilityBuildings.SILO,
                BuildingRecord.Mode.SELF_BUILT,level.dimension().location(),anchor,anchor,Direction.SOUTH,
                claim,BuildingRecord.Phase.READY,1,BuildingRecord.Residence.VALID,0,"");
        level.setBlock(anchor,ModBlocks.SILO_MANAGER.get().defaultBlockState(),3);
        var barrel=anchor.east();level.setBlock(barrel,Blocks.BARREL.defaultBlockState(),3);
        ((net.minecraft.world.Container)level.getBlockEntity(barrel)).setItem(0,new ItemStack(Items.DIAMOND,3));
        var snapshot=BuildingTransfer.move(level,record,record.anchor(),record.facing());
        var lift=new BuildingMoveLift(UUID.randomUUID(),UUID.randomUUID(),snapshot,new CompoundTag());
        var restored=BuildingMoveLift.load(lift.save(),level.registryAccess());
        restored.snapshot().lift(level);
        h.assertTrue(level.getBlockState(anchor).isAir() && level.getBlockState(barrel).isAir(),
                "Move preview left the original building visible");
        var originalAir=anchor.above().west();level.setBlock(originalAir,Blocks.STONE.defaultBlockState(),3);
        restored.snapshot().restoreLift(level);
        h.assertTrue(level.getBlockState(anchor).is(ModBlocks.SILO_MANAGER.get())
                        && level.getBlockState(barrel).is(Blocks.BARREL),
                "Cancelling a move did not restore the original blocks");
        h.assertTrue(((net.minecraft.world.Container)level.getBlockEntity(barrel)).getItem(0).is(Items.DIAMOND)
                        && ((net.minecraft.world.Container)level.getBlockEntity(barrel)).getItem(0).getCount()==3,
                "Cancelling a move lost block entity contents");
        h.assertTrue(level.getBlockState(originalAir).isAir(),
                "Cancelling a move did not restore the original air volume");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void cancellingAnUnusableMoveDoesNotTouchTheOriginalBuilding(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CancelMove"));
        var family=PrefabDefinitions.get(PrefabDefinitions.COOP);var anchor=h.absolutePos(BlockPos.ZERO);
        var record=BuildingRecord.waiting(UUID.randomUUID(),0,PrefabDefinitions.COOP,BuildingRecord.Mode.PREFAB,
                h.getLevel().dimension().location(),anchor,anchor.above(),Direction.SOUTH,PrefabDefinitions.transform(family.reservation(),anchor,Rotation.NONE));
        var data=BuildingWorldData.get(h.getLevel().getServer());data.register(record);
        var stack=new ItemStack(ModItems.COOP_BLUEPRINT.get());BuildingBlueprintItem.bindMove(stack,record);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var tag=BuildingBlueprintItem.draft(stack);tag.putLong("DraftAnchor",new BlockPos(500,90,500).asLong());tag.putString("DraftDimension","minecraft:the_nether");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        BuildingDrafts.get(player.server).write(stack);
        BuildingBlueprintItem.cancel(player,InteractionHand.MAIN_HAND,false);
        h.assertTrue(!BuildingBlueprintItem.draft(stack).contains("DraftAnchor") && stack.getCount()==1,"Unreachable cross-dimension pin cannot be reset");
        BuildingBlueprintItem.cancel(player,InteractionHand.MAIN_HAND,true);
        h.assertTrue(stack.isEmpty() && data.find(record.id()).equals(record),"Ending movement changed or removed the original building");
        var paid=new ItemStack(ModItems.COOP_BLUEPRINT.get());BuildingBlueprintItem.bind(paid,UUID.randomUUID());player.setItemInHand(InteractionHand.MAIN_HAND,paid);
        BuildingBlueprintItem.cancel(player,InteractionHand.MAIN_HAND,true);
        h.assertTrue(paid.getCount()==1,"Cancellation consumed a purchased building blueprint");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void movingWithinFarmPreservesPermissionsAndReportsBoundarySeparately(GameTestHelper h) {
        var registry=com.stardew.craft.farm.FarmInstanceRegistry.get(h.getLevel().getServer());var owner=UUID.randomUUID();var member=UUID.randomUUID();
        var farm=registry.createFarm(owner,"MoveOwner","Move permissions",com.stardew.craft.farm.FarmType.STANDARD);farm.addMember(member,4);
        try {
            var min=farm.getFarmBoundsMin();var max=farm.getFarmBoundsMax();var anchor=new BlockPos((min.getX()+max.getX())/2,Math.max(min.getY()+1,64),(min.getZ()+max.getZ())/2);
            var family=PrefabDefinitions.get(PrefabDefinitions.COOP);
            var record=BuildingRecord.waiting(farm.getInstanceId(),farm.getSlotIndex(),PrefabDefinitions.COOP,BuildingRecord.Mode.PREFAB,
                    h.getLevel().dimension().location(),anchor,anchor.above(),Direction.SOUTH,PrefabDefinitions.transform(family.reservation(),anchor,Rotation.NONE));
            for(var facing:Direction.Plane.HORIZONTAL) {
                var target=PrefabDefinitions.transform(family.reservation(),anchor.east(10),PrefabDefinitions.rotation(facing));
                var resolved=BuildingPlacementService.placementFarm(registry,target,record);
                h.assertTrue(resolved==farm,"Rotation changed the farm used to authorize movement");
                h.assertTrue(BuildingPlacementService.sitePermission(resolved,owner,target,record)==null,"Owner cannot place a move in their own farm");
                h.assertTrue(BuildingPlacementService.sitePermission(resolved,member,target,record)==null,"Farm member cannot place a move");
                h.assertTrue("permission".equals(BuildingPlacementService.sitePermission(resolved,UUID.randomUUID(),target,record)),"Outsider gained moving permission");
            }
            var outside=PrefabDefinitions.transform(family.reservation(),max.east(1),Rotation.NONE);
            h.assertTrue("bounds".equals(BuildingPlacementService.sitePermission(BuildingPlacementService.placementFarm(registry,outside,record),owner,outside,record)),"Out-of-bounds move was mislabeled as permission failure");
        }finally{registry.deleteFarm(owner);}h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void duplicateMaterialRowsCannotSpendTheSameStackTwice(GameTestHelper h) {
        var inventory=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MaterialRows")).getInventory();
        inventory.clearContent();inventory.setItem(0,new ItemStack(Items.OAK_LOG,64));
        var materials=List.of(new BuildingPurchasePlan.Material(Items.OAK_LOG,50),new BuildingPurchasePlan.Material(Items.OAK_LOG,50));
        h.assertTrue(!BuildingPurchasePlan.hasMaterials(inventory,materials),"Duplicate rows counted the same logs twice");
        h.assertTrue(BuildingPurchasePlan.prepare(inventory,new ItemStack(Items.PAPER),materials)==null
                && inventory.getItem(0).getCount()==64,"Failed purchase consumed part of the materials");
        inventory.offhand.set(0,new ItemStack(Items.OAK_LOG,36));
        h.assertTrue(BuildingPurchasePlan.hasMaterials(inventory,materials),"Offhand materials disagree with consumption");
        var plan=BuildingPurchasePlan.prepare(inventory,new ItemStack(Items.PAPER),materials);
        h.assertTrue(plan!=null,"Exact aggregated materials refused");plan.apply(inventory);
        h.assertTrue(inventory.countItem(Items.OAK_LOG)==0 && inventory.countItem(Items.PAPER)==1,"Exact material exchange lost or duplicated items");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void largeAddonDeliveriesMergeAndSplitWithoutOversizedStacks(GameTestHelper h) {
        var inventory=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"AddonDelivery")).getInventory();
        inventory.clearContent();inventory.setItem(9,new ItemStack(Items.PAPER,30));
        var plan=BuildingPurchasePlan.prepare(inventory,new ItemStack(Items.PAPER,100),List.of());
        h.assertTrue(plan!=null && inventory.countItem(Items.PAPER)==30,"Delivery simulation mutated inventory");plan.apply(inventory);
        h.assertTrue(inventory.countItem(Items.PAPER)==130 && inventory.getItem(9).getCount()==64,"Delivery did not merge existing paper first");
        for(var stack:inventory.items)h.assertTrue(stack.getCount()<=stack.getMaxStackSize(),"Delivery created an oversized stack");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void partialDeliveryCapacityDoesNotConsumePayment(GameTestHelper h) {
        var inventory=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"FullDelivery")).getInventory();
        inventory.clearContent();for(int i=0;i<inventory.items.size();i++)inventory.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        inventory.setItem(3,new ItemStack(Items.OAK_LOG,4));
        var materials=List.of(new BuildingPurchasePlan.Material(Items.OAK_LOG,4));
        h.assertTrue(BuildingPurchasePlan.hasMaterials(inventory,materials),"Full inventory falsely reports missing materials");
        h.assertTrue(BuildingPurchasePlan.prepare(inventory,new ItemStack(Items.PAPER,65),materials)==null,"One freed slot accepted two stacks");
        h.assertTrue(inventory.getItem(3).is(Items.OAK_LOG) && inventory.getItem(3).getCount()==4,"Rejected delivery spent logs");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void catalogExposesBothPricesBeforeSelectingConstructionRoute(GameTestHelper h) {
        for(var family:List.of(PrefabDefinitions.COOP,PrefabDefinitions.BARN)) {
            var source=com.stardew.craft.building.BuildingBlueprintRegistry.find(family).orElseThrow();
            var row=com.stardew.craft.shop.CarpenterBlueprint.from(source);
            h.assertTrue(row.presentation().getBoolean("ChooseRoute") && row.presentation().getInt("ManagerPrice")==500,"Catalog lacks separate manager price");
            h.assertTrue(row.cost()==source.definition().money() && row.materials().size()==source.definition().materials().size(),"Catalog hides full construction cost");
        }h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void purchaseRepliesPreserveTheirRequestIdentity(GameTestHelper h) {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        var id=UUID.randomUUID();
        try {
            var request=new com.stardew.craft.network.payload.CarpenterPurchasePayload("stardewcraft:robin",2,PrefabDefinitions.COOP.toString(),17,id);
            com.stardew.craft.network.payload.CarpenterPurchasePayload.STREAM_CODEC.encode(buffer,request);
            h.assertTrue(com.stardew.craft.network.payload.CarpenterPurchasePayload.STREAM_CODEC.decode(buffer).equals(request),"Catalog request lost its identity");
            var routes=new com.stardew.craft.network.payload.OpenBuildingRoutesPayload(5000,500,11,8,4000,net.minecraft.network.chat.Component.literal("wood"),17,PrefabDefinitions.COOP,id);
            com.stardew.craft.network.payload.OpenBuildingRoutesPayload.STREAM_CODEC.encode(buffer,routes);
            h.assertTrue(com.stardew.craft.network.payload.OpenBuildingRoutesPayload.STREAM_CODEC.decode(buffer).equals(routes),"Route reply lost its request");
            var result=new com.stardew.craft.network.payload.CarpenterPurchaseResultPayload(false,5000,"",2,id);
            com.stardew.craft.network.payload.CarpenterPurchaseResultPayload.STREAM_CODEC.encode(buffer,result);
            h.assertTrue(com.stardew.craft.network.payload.CarpenterPurchaseResultPayload.STREAM_CODEC.decode(buffer).equals(result),"Purchase reply lost its request");
            var work=new com.stardew.craft.network.payload.BuildingWorkRequestPayload(PrefabDefinitions.COOP,17,UUID.randomUUID(),3,"preview",id);
            com.stardew.craft.network.payload.BuildingWorkRequestPayload.STREAM_CODEC.encode(buffer,work);
            h.assertTrue(com.stardew.craft.network.payload.BuildingWorkRequestPayload.STREAM_CODEC.decode(buffer).equals(work),"Work request lost its identity");
        } finally {buffer.release();}h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void paidBlueprintFollowsItsControllerAcrossDebugFarms(GameTestHelper h) {
        var registry=new com.stardew.craft.farm.FarmInstanceRegistry();
        var actor=UUID.randomUUID();
        var source=registry.createDebugFarm(actor,"Builder","Source",com.stardew.craft.farm.FarmType.STANDARD);
        var target=registry.createDebugFarm(actor,"Builder","Target",com.stardew.craft.farm.FarmType.FOREST);
        var stranger=registry.createFarm(UUID.randomUUID(),"Other","Other",com.stardew.craft.farm.FarmType.STANDARD);
        var data=new BuildingWorldData();var permit=UUID.randomUUID();
        data.recordPurchase(permit,source.getInstanceId(),true,PrefabDefinitions.COOP);
        h.assertTrue(data.permitsPlacement(permit,target.getInstanceId(),PrefabDefinitions.COOP,actor,registry),
                "A controller's blueprint stayed trapped on the farm selected at purchase time");
        h.assertTrue(!data.permitsPlacement(permit,stranger.getInstanceId(),PrefabDefinitions.COOP,actor,registry),
                "A blueprint crossed into an unrelated farm");
        var family=PrefabDefinitions.get(PrefabDefinitions.COOP);var anchor=new BlockPos(0,64,0);
        var record=BuildingRecord.waiting(target.getInstanceId(),target.getSlotIndex(),PrefabDefinitions.COOP,
                BuildingRecord.Mode.PREFAB,h.getLevel().dimension().location(),anchor,
                PrefabDefinitions.world(family.tier(1).manager(),family.tier(1).anchor(),anchor,Rotation.NONE),Direction.SOUTH,
                PrefabDefinitions.transform(family.reservation(),anchor,Rotation.NONE));
        h.assertTrue(data.beginPrefab(record,permit,1,actor,registry)==BuildingWorldData.Result.SUCCESS,
                "Cross-debug-farm placement did not atomically consume the valid permit");
        h.assertTrue(!data.permitsPlacement(permit,target.getInstanceId(),PrefabDefinitions.COOP,actor,registry),
                "Placed blueprint remained reusable");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_buildings",template="empty")
    public static void obstructionFlashPayloadKeepsEveryReportedCell(GameTestHelper h) {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        var expected=List.of(new BlockPos(1,2,3),new BlockPos(-4,5,-6),new BlockPos(7,8,9));
        try {
            var payload=new com.stardew.craft.network.payload.BuildingObstructionFlashPayload(
                    h.getLevel().dimension().location(),expected);
            com.stardew.craft.network.payload.BuildingObstructionFlashPayload.STREAM_CODEC.encode(buffer,payload);
            var restored=com.stardew.craft.network.payload.BuildingObstructionFlashPayload.STREAM_CODEC.decode(buffer);
            h.assertTrue(restored.dimension().equals(payload.dimension()) && restored.positions().equals(expected),
                    "Obstruction flash lost cells in transit");
        } finally {buffer.release();}
        h.succeed();
    }

}
