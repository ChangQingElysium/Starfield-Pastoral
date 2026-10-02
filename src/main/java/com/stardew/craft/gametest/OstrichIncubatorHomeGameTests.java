package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.runtime.LivestockSpecies;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.blockentity.TimedProductionBlockEntity;
import com.stardew.craft.building.runtime.BuildingProtection;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingResidence;
import com.stardew.craft.building.runtime.BuildingService;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.OstrichIncubatorBlock;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("stardewcraft_buildings")
@PrefixGameTestTemplate(false)
public final class OstrichIncubatorHomeGameTests {
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private OstrichIncubatorHomeGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_buildings", template = "construction_site")
    public static void ostrichIncubatorUsesTheEffectiveCoopHomeAndRetainsEggOwnershipAndTiming(GameTestHelper helper) {
        var level = helper.getLevel();
        var farms = FarmInstanceRegistry.get(level.getServer());
        UUID owner = UUID.randomUUID();
        var farm = farms.createFarm(owner, "Ostrich owner", "Ostrich home test", FarmType.STANDARD);
        var buildings = BuildingWorldData.get(level.getServer());
        Set<BlockPos> placed = new LinkedHashSet<>();
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "Ostrich owner"));
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Ostrich visitor"));
        player.setGameMode(GameType.SURVIVAL);
        outsider.setGameMode(GameType.SURVIVAL);
        // Keep both actors away from both claims: placement must not fail through entity collision.
        Vec3 actorPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(45, 2, 42)));
        player.setPos(actorPos);
        outsider.setPos(actorPos.add(0, 0, 2));
        try {
            helper.assertTrue(LivestockSpecies.OSTRICH.family().equals(PrefabDefinitions.COOP),
                    "The shipped explicit ostrich Coop override changed");
            BlockPos coopPos = helper.absolutePos(new BlockPos(12, 2, 16));
            BlockPos barnPos = helper.absolutePos(new BlockPos(34, 2, 16));
            var coop = home(helper, farm, PrefabDefinitions.COOP, coopPos, 2, placed);
            var barn = home(helper, farm, PrefabDefinitions.BARN, barnPos, 1, placed);
            helper.assertTrue(!coop.claim().intersects(barn.claim())
                            && BuildingService.canManage(player, coop) && BuildingService.canManage(player, barn)
                            && !BuildingService.canManage(outsider, coop),
                    "Home fixture claims or real farm permissions are invalid");
            var block = (OstrichIncubatorBlock) GingerIslandBlocks.get("ginger_ostrich_incubator_empty");
            helper.assertTrue(block.asItem() instanceof BlockItem, "Ostrich incubator has no real placeable item");
            var item = (BlockItem) block.asItem();
            outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            helper.assertTrue(!item.place(context(outsider, coopPos)).consumesAction()
                            && outsider.getMainHandItem().getCount() == 2
                            && level.isEmptyBlock(coopPos) && level.isEmptyBlock(coopPos.above()),
                    "An unrelated visitor placed an incubator in the owner's valid Coop");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            placed.add(coopPos);
            placed.add(coopPos.above());
            helper.assertTrue(item.place(context(player, coopPos)).consumesAction()
                            && player.getMainHandItem().getCount() == 1,
                    "The owner could not place the ostrich incubator in the effective Coop home");
            helper.assertTrue(level.getBlockState(coopPos).is(block)
                            && level.getBlockState(coopPos).getValue(IncubatorBlock.PART) == IncubatorBlock.Part.MAIN
                            && level.getBlockState(coopPos.above()).is(block)
                            && level.getBlockState(coopPos.above()).getValue(IncubatorBlock.PART) == IncubatorBlock.Part.EXTENSION
                            && !(level.getBlockEntity(coopPos.above()) instanceof IncubatorBlockEntity),
                    "Real item placement did not create two parts sharing one incubation receipt");
            var incubator = (IncubatorBlockEntity) level.getBlockEntity(coopPos);
            helper.assertTrue(incubator != null && incubator.getContainingRuntimeBuilding(level).id().equals(coop.id()),
                    "The placed incubator did not resolve the real Coop ledger entry");
            var visitorEggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 3);
            helper.assertTrue(!incubator.tryInsertWithResult(visitorEggs, outsider).inserted()
                            && visitorEggs.getCount() == 3 && !incubator.hasInput(),
                    "Visitor incubation consumed an egg or bypassed farm management permissions");
            var eggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 2);
            long now = TimedProductionBlockEntity.getCurrentAbsMinute();
            helper.assertTrue(incubator.tryInsertWithResult(eggs, player).inserted() && eggs.getCount() == 1
                            && incubator.getInput().is(ModItems.OSTRICH_EGG.get()) && incubator.getInput().getCount() == 1
                            && incubator.isWorking() && !incubator.isReady(),
                    "Valid Coop incubation did not consume exactly one ostrich egg and preserve its input");
            var saved = incubator.saveWithoutMetadata();
            int sourceMinutes = LivestockSpecies.OSTRICH.definition().incubationTime();
            int minutes = sourceMinutes > 0 ? sourceMinutes : 9000;
            long deadline = OstrichIncubatorBlock.completionMorning(now, minutes);
            helper.assertTrue(saved.hasUUID("NewbornReceipt") && saved.hasUUID("Caretaker")
                            && saved.getUUID("Caretaker").equals(owner)
                            && saved.getBoolean("OnlyCompleteOvernight") && !saved.getBoolean("Ready")
                            && saved.getLong("ReadyAt") == deadline
                            && incubator.getRemainingAbsMinutes() == deadline - now
                            && level.getBlockState(coopPos).getValue(OstrichIncubatorBlock.LOADED)
                            && level.getBlockState(coopPos.above()).getValue(OstrichIncubatorBlock.LOADED),
                    "Incubation lost its persisted caretaker, receipt, overnight deadline or loaded parts");
            var secondEggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 4);
            helper.assertTrue(!incubator.tryInsertWithResult(secondEggs, player).inserted() && secondEggs.getCount() == 4
                            && saved.equals(incubator.saveWithoutMetadata()),
                    "A second insertion replaced the active receipt, caretaker, input or completion time");
            // The family fix must not narrow the ordinary incubator's existing egg rules.
            var ordinary = (IncubatorBlockEntity) level.getBlockEntity(coop.manager().west(4).north());
            var chickenEggs = new ItemStack(ModItems.EGG_WHITE.get(), 2);
            helper.assertTrue(ordinary.tryInsertWithResult(chickenEggs, player).inserted() && chickenEggs.getCount() == 1
                            && ordinary.getInput().is(ModItems.EGG_WHITE.get()),
                    "Ordinary chicken-egg incubation in the valid tier-two Coop regressed");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            helper.assertTrue(!item.place(context(player, barnPos)).consumesAction()
                            && player.getMainHandItem().getCount() == 2
                            && level.isEmptyBlock(barnPos) && level.isEmptyBlock(barnPos.above()),
                    "The effective Coop-only ostrich incubator was placed in the owner's valid Barn");
            put(level, placed, barnPos, block.defaultBlockState());
            placed.add(barnPos.above());
            block.setPlacedBy(level, barnPos, level.getBlockState(barnPos), player, ItemStack.EMPTY);
            var wrongHome = (IncubatorBlockEntity) level.getBlockEntity(barnPos);
            helper.assertTrue(wrongHome != null && wrongHome.getContainingRuntimeBuilding(level).id().equals(barn.id()),
                    "World-set Barn incubator did not resolve the real negative-test home");
            var beforeRejectedInsert = wrongHome.saveWithoutMetadata();
            var rejectedEggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 3);
            helper.assertTrue(!wrongHome.tryInsertWithResult(rejectedEggs, player).inserted()
                            && rejectedEggs.getCount() == 3 && !wrongHome.hasInput()
                            && beforeRejectedInsert.equals(wrongHome.saveWithoutMetadata()),
                    "The block-entity insertion path accepted the wrong home or mutated its empty receipt");
        } finally {
            BuildingProtection.internal(() -> {
                for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            });
            var fixtureBounds = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(48, 24, 48)));
            for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, fixtureBounds)) dropped.discard();
            buildings.removeFarm(farm.getInstanceId());
            farms.deleteFarm(owner);
        }
        // This test only reads the current machine clock; no day/time/profession or hatch state is changed.
        helper.succeed();
    }

    private static BuildingRecord home(GameTestHelper helper, FarmInstance farm, ResourceLocation family,
            BlockPos incubatorPos, int targetTier, Set<BlockPos> placed) {
        var level = helper.getLevel();
        BlockPos manager = incubatorPos.north(4);
        var claim = PrefabDefinitions.get(family).selfBounds(manager);
        for (int x = claim.min().getX(); x < claim.maxExclusive().getX(); x++) {
            for (int z = claim.min().getZ(); z < claim.maxExclusive().getZ(); z++) {
                put(level, placed, new BlockPos(x, manager.getY() - 1, z), Blocks.STONE.defaultBlockState());
            }
        }
        put(level, placed, manager, PrefabDefinitions.managerState(family, Direction.SOUTH));
        for (int offset = 1; offset <= 4; offset++) {
            put(level, placed, manager.east(offset), ModBlocks.FEED_TROUGH.get().defaultBlockState());
            if (targetTier == 2) put(level, placed, manager.west(offset), ModBlocks.FEED_TROUGH.get().defaultBlockState());
        }
        put(level, placed, manager.north(2), ModBlocks.HAY_HOPPER.get().defaultBlockState());
        if (targetTier == 2) {
            BlockPos ordinaryPos = manager.west(4).north();
            var ordinary = (IncubatorBlock) ModBlocks.INCUBATOR.get();
            put(level, placed, ordinaryPos, ordinary.defaultBlockState());
            placed.add(ordinaryPos.above());
            ordinary.setPlacedBy(level, ordinaryPos, level.getBlockState(ordinaryPos), null, ItemStack.EMPTY);
        }
        var record = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), family, BuildingRecord.Mode.SELF_BUILT,
                level.dimension().location(), manager, manager, Direction.SOUTH, claim);
        var buildings = BuildingWorldData.get(level.getServer());
        helper.assertTrue(buildings.register(record) == BuildingWorldData.Result.SUCCESS, "Home fixture registration failed");
        var assessment = BuildingResidence.scan(level, claim, family);
        helper.assertTrue(assessment.loaded() && assessment.troughs() == targetTier * 4 && assessment.hoppers() == 1
                        && assessment.incubators() == (targetTier == 2 ? 1 : 0) && assessment.eligibleTier() == targetTier,
                "Real residence facilities are missing or misplaced: " + assessment);
        for (int tier = 1; tier <= targetTier; tier++) {
            helper.assertTrue(buildings.acceptSelf(record.id(), record.revision(), assessment.eligibleTier()) == BuildingWorldData.Result.SUCCESS,
                    "Real self-built home did not accept tier " + tier);
            record = buildings.find(record.id());
        }
        BuildingResidence.refresh(level, record);
        record = buildings.find(record.id());
        helper.assertTrue(record.phase() == BuildingRecord.Phase.READY && record.tier() == targetTier
                        && record.residence() == BuildingRecord.Residence.VALID,
                "Home fixture is not a real READY valid residence");
        return record;
    }

    private static void put(net.minecraft.server.level.ServerLevel level, Set<BlockPos> placed,
            BlockPos pos, BlockState state) {
        placed.add(pos);
        level.setBlock(pos, state, QUIET_FLAGS);
    }

    private static BlockPlaceContext context(ServerPlayer player, BlockPos pos) {
        return new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false)));
    }
}
