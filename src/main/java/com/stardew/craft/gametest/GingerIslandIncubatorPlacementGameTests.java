package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.runtime.FarmFeed;
import com.stardew.craft.animal.runtime.LivestockSpecies;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.building.runtime.BuildingProtection;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingResidence;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.IslandContext;
import com.stardew.craft.gingerisland.OstrichIncubatorBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.interior.InteriorRegionRegistry;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Workshop placement and production authorization are separate boundaries. */
@GameTestHolder("stardewcraft_ginger_placement")
@PrefixGameTestTemplate(false)
public final class GingerIslandIncubatorPlacementGameTests {
    private static final String NS = "stardewcraft_ginger_placement";
    private static final int QUIET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private GingerIslandIncubatorPlacementGameTests() {}

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void forgedHandProcessStatesNormalizeWhileWorldIncubationStatesRemainLegal(GameTestHelper h) {
        var level = h.getLevel();
        var block = block();
        var player = player(h, GameType.CREATIVE);
        BlockPos pos = h.absolutePos(new BlockPos(20, 4, 20));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
        player.setPos(Vec3.atBottomCenterOf(pos.south(3)));
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                ItemStack hand = new ItemStack(block, 2);
                PortItemData.set(hand, DataComponents.BLOCK_STATE, new BlockItemStateProperties(
                        Map.of("working", "true", "loaded", "true", "part", "extension")));
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.useOn(context(player, pos)).consumesAction() && hand.getCount() == 2,
                        "Forged process stack cannot be placed as a fresh creative machine: " + facing);
                assertParts(h, pos, facing);
                var be = (IncubatorBlockEntity) level.getBlockEntity(pos);
                h.assertTrue(!be.hasInput() && !be.isWorking() && !be.isReady(),
                        "Forged hand flags created a production receipt");
                for (BlockPos cell : new BlockPos[]{pos, pos.above()}) {
                    BlockState placed = level.getBlockState(cell);
                    h.assertTrue(!placed.getValue(IncubatorBlock.WORKING) && !placed.getValue(OstrichIncubatorBlock.LOADED),
                            "Vanilla component reapplication left forged process flags: " + cell);
                }
                assertPlainPickAndDrop(h, pos);
                for (BlockPos cell : new BlockPos[]{pos, pos.above()}) {
                    BlockState placed = level.getBlockState(cell);
                    h.assertTrue(level.setBlock(cell, placed.setValue(IncubatorBlock.WORKING, true)
                                    .setValue(OstrichIncubatorBlock.LOADED, true), QUIET_FLAGS),
                            "World process transition failed");
                }
                assertParts(h, pos, facing);
                for (BlockPos cell : new BlockPos[]{pos, pos.above()}) {
                    BlockState progressed = level.getBlockState(cell);
                    h.assertTrue(progressed.getValue(IncubatorBlock.WORKING) && progressed.getValue(OstrichIncubatorBlock.LOADED),
                            "Placement normalization rolled back a legal world process state");
                }
                h.assertTrue(level.getBlockEntity(pos) == be, "World state change replaced the production owner");
                assertPlainPickAndDrop(h, pos);
                level.removeBlock(pos, false);
                h.assertTrue(level.isEmptyBlock(pos.above()) && level.getBlockEntity(pos) == null,
                        "Normalized machine removal left its owner or upper part");
            }
        } finally {
            level.removeBlock(pos, false);
            level.removeBlock(pos.above(), false);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void creativeWorkshopHandPlacementDoesNotUnlockIncubationOrNewborns(GameTestHelper h) {
        var level = h.getLevel();
        var block = block();
        var player = player(h, GameType.CREATIVE);
        BlockPos pos = h.absolutePos(new BlockPos(20, 4, 20));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
        player.setPos(Vec3.atBottomCenterOf(pos.south(3)));
        try {
            h.assertTrue(FarmFeed.home(level, pos) == null, "Workshop unexpectedly has a livestock home");
            player.setGameMode(GameType.SURVIVAL);
            reject(h, player, pos, "Survival placement without a livestock home");
            player.setGameMode(GameType.CREATIVE);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                ItemStack hand = new ItemStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.useOn(context(player, pos)).consumesAction(),
                        "Creative workshop hand placement failed: " + facing);
                assertParts(h, pos, facing);
                h.assertTrue(hand.getCount() == 2, "Creative placement consumed the incubator");
                var be = (IncubatorBlockEntity) level.getBlockEntity(pos);
                var empty = be.saveWithoutMetadata();
                ItemStack eggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 3);
                h.assertTrue(!be.tryInsertWithResult(eggs, player).inserted() && eggs.getCount() == 3
                                && !be.hasInput() && empty.equals(be.saveWithoutMetadata()),
                        "Creative placement bypassed the independent egg/home boundary");
                CompoundTag ready = new CompoundTag();
                ready.put("Input", com.stardew.craft.port.PortItemStacks.save(new ItemStack(ModItems.OSTRICH_EGG.get()), level.registryAccess()));
                ready.putBoolean("Ready", true);
                ready.putLong("ReadyAt", 0);
                ready.putUUID("NewbornReceipt", UUID.randomUUID());
                ready.putUUID("Caretaker", player.getUUID());
                ready.putBoolean("OnlyCompleteOvernight", true);
                be.load(ready);
                var beforeClaim = be.saveWithoutMetadata();
                h.assertTrue(be.claimReadyAnimal(player, "Workshop ostrich") == IncubatorBlockEntity.ClaimResult.INVALID_BUILDING
                                && be.isReady() && beforeClaim.equals(be.saveWithoutMetadata()),
                        "Creative workshop placement granted a newborn or erased its rejected receipt");
                be.load(new CompoundTag());
                level.removeBlock(pos, false);
                h.assertTrue(level.isEmptyBlock(pos.above()) && level.getBlockEntity(pos) == null,
                        "Workshop removal left the upper part or production owner");
            }
        } finally {
            level.removeBlock(pos, false);
            level.removeBlock(pos.above(), false);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void creativePlacementRejectsUpperSolidsFluidsAndEntities(GameTestHelper h) {
        var level = h.getLevel();
        var player = player(h, GameType.CREATIVE);
        BlockPos pos = h.absolutePos(new BlockPos(20, 4, 20));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
        player.setPos(Vec3.atBottomCenterOf(pos.south(3)));
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                for (Block obstacle : new Block[]{Blocks.STONE, Blocks.WATER, Blocks.LAVA}) {
                    BlockState before = obstacle.defaultBlockState();
                    level.setBlock(pos.above(), before, QUIET_FLAGS);
                    reject(h, player, pos, "Upper obstruction " + obstacle + " / " + facing);
                    h.assertTrue(level.getBlockState(pos.above()).equals(before), "Refusal replaced its upper obstruction");
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
                }
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), QUIET_FLAGS);
                reject(h, player, pos, "Main water cell / " + facing);
                h.assertTrue(level.getBlockState(pos).is(Blocks.WATER), "Refusal drained main water");
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
                ArmorStand blocker = new ArmorStand(level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
                h.assertTrue(level.addFreshEntity(blocker), "Upper entity blocker was not added");
                try {
                    reject(h, player, pos, "Upper entity / " + facing);
                    h.assertTrue(level.isEmptyBlock(pos.above()), "Upper entity refusal installed a partial machine");
                } finally {
                    blocker.discard();
                }
            }
        } finally {
            level.removeBlock(pos, false);
            level.removeBlock(pos.above(), false);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void creativePlacementStillChecksBothIslandOwnershipCells(GameTestHelper h) {
        var level = h.getLevel();
        var player = player(h, GameType.CREATIVE);
        BlockPos pos = h.absolutePos(new BlockPos(20, 4, 20));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), QUIET_FLAGS);
        player.setPos(Vec3.atBottomCenterOf(pos.south(3)));
        String previous = InteriorRegionRegistry.getCachedJson();
        try {
            for (BlockPos protectedCell : new BlockPos[]{pos, pos.above()}) {
                InteriorRegionRegistry.applyFromJson(protectedLocation(h, previous, protectedCell));
                h.assertTrue(!IslandContext.canModifyAt(player, protectedCell), "Malformed island binding did not fail closed");
                reject(h, player, pos, "Creative island permission at " + protectedCell);
                h.assertTrue(level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()),
                        "Denied island ownership left a machine part");
            }
        } finally {
            InteriorRegionRegistry.applyFromJson(previous);
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "effective_home", timeoutTicks = 100)
    public static void survivalOwnerHandPlacesInTheEffectiveHomeAndVisitorCannot(GameTestHelper h) {
        var level = h.getLevel();
        var farms = FarmInstanceRegistry.get(level.getServer());
        UUID owner = UUID.randomUUID();
        FarmInstance farm = farms.createFarm(owner, "Incubator owner", "Incubator placement", FarmType.STANDARD);
        var buildings = BuildingWorldData.get(level.getServer());
        Set<BlockPos> placed = new LinkedHashSet<>();
        var player = FakePlayerFactory.get(level, new GameProfile(owner, "Incubator owner"));
        var outsider = player(h, GameType.SURVIVAL);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = h.absolutePos(new BlockPos(20, 2, 20));
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(45, 2, 42))));
        outsider.setPos(player.position().add(0, 0, 2));
        try {
            var home = home(h, farm, pos, placed);
            h.assertTrue(FarmFeed.home(level, pos).id().equals(home.id()), "Effective livestock home is missing");
            reject(h, outsider, pos, "Unrelated survival visitor in an owner's home");
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setYRot(facing.toYRot());
                ItemStack hand = new ItemStack(block(), 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                placed.add(pos);
                placed.add(pos.above());
                h.assertTrue(hand.useOn(context(player, pos)).consumesAction() && hand.getCount() == 1,
                        "Survival owner cannot hand place in the effective home: " + facing);
                assertParts(h, pos, facing);
                var be = (IncubatorBlockEntity) level.getBlockEntity(pos);
                h.assertTrue(be.getContainingRuntimeBuilding(level).id().equals(home.id()), "Machine selected another home");
                ItemStack eggs = new ItemStack(ModItems.OSTRICH_EGG.get(), 2);
                h.assertTrue(!be.tryInsertWithResult(eggs, outsider).inserted() && eggs.getCount() == 2,
                        "Workshop placement change granted visitor production permissions");
                h.assertTrue(be.tryInsertWithResult(eggs, player).inserted() && eggs.getCount() == 1,
                        "Valid survival home production regressed");
                be.load(new CompoundTag());
                level.removeBlock(pos, false);
                h.assertTrue(level.isEmptyBlock(pos.above()), "Home removal left the upper part");
            }
        } finally {
            BuildingProtection.internal(() -> {
                for (BlockPos cell : placed) level.setBlock(cell, Blocks.AIR.defaultBlockState(), QUIET_FLAGS);
            });
            buildings.removeFarm(farm.getInstanceId());
            farms.deleteFarm(owner);
            player.getInventory().clearContent();
            outsider.getInventory().clearContent();
        }
        h.succeed();
    }

    private static OstrichIncubatorBlock block() {
        return (OstrichIncubatorBlock) GingerIslandBlocks.get("ginger_ostrich_incubator_empty");
    }

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "Incubator test"));
        player.setGameMode(mode);
        return player;
    }

    private static UseOnContext context(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false));
    }

    private static void reject(GameTestHelper h, ServerPlayer player, BlockPos pos, String reason) {
        ItemStack hand = new ItemStack(block(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        h.assertTrue(!hand.useOn(context(player, pos)).consumesAction() && hand.getCount() == 2,
                reason + " was allowed or consumed its item");
        h.assertTrue(!h.getLevel().getBlockState(pos).is(block()) && h.getLevel().getBlockEntity(pos) == null,
                reason + " left the production owner");
    }

    private static void assertParts(GameTestHelper h, BlockPos pos, Direction facing) {
        var level = h.getLevel();
        for (int offset = 0; offset <= 1; offset++) {
            BlockState state = level.getBlockState(pos.above(offset));
            h.assertTrue(state.is(block()) && state.getValue(IncubatorBlock.FACING) == facing
                            && state.getValue(IncubatorBlock.PART) == (offset == 0
                                ? IncubatorBlock.Part.MAIN : IncubatorBlock.Part.EXTENSION),
                    "Hand placement left the wrong part or facing");
            h.assertTrue((level.getBlockEntity(pos.above(offset)) instanceof IncubatorBlockEntity) == (offset == 0),
                    "The machine must have one production owner");
        }
    }

    private static void assertPlainPickAndDrop(GameTestHelper h, BlockPos pos) {
        var level = h.getLevel();
        var block = block();
        ItemStack plain = new ItemStack(block);
        for (BlockPos cell : new BlockPos[]{pos, pos.above()}) {
            BlockState state = level.getBlockState(cell);
            h.assertTrue(ItemStack.isSameItemSameTags(plain, block.getCloneItemStack(level, cell, state)),
                    "Picking a machine part exposed a held process state");
            var drops = Block.getDrops(state, level, cell, level.getBlockEntity(cell));
            h.assertTrue(cell.equals(pos) ? drops.size() == 1
                            && ItemStack.isSameItemSameTags(plain, com.stardew.craft.port.PortJava.getFirst(drops)) : drops.isEmpty(),
                    "Machine drops duplicated or exposed a held process state");
        }
    }

    private static String protectedLocation(GameTestHelper h, String previous, BlockPos pos) {
        JsonObject root = JsonParser.parseString(previous).getAsJsonObject();
        JsonObject entry = new JsonObject(), properties = new JsonObject();
        entry.addProperty("dimension", h.getLevel().dimension().location().toString());
        entry.addProperty("ledger_id", "IslandSouth");
        entry.addProperty("priority", 1000000);
        JsonArray min = new JsonArray(), max = new JsonArray();
        for (int value : new int[]{pos.getX(), pos.getY(), pos.getZ()}) min.add(value);
        for (int value : new int[]{pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1}) max.add(value);
        entry.add("min", min);
        entry.add("max", max);
        properties.addProperty(IslandContext.FARM_INSTANCE.toString(), "malformed-farm-binding");
        entry.add("properties", properties);
        root.add("stardewcraft:ginger_incubator_test_region", entry);
        return root.toString();
    }

    private static BuildingRecord home(GameTestHelper h, FarmInstance farm, BlockPos pos, Set<BlockPos> placed) {
        var level = h.getLevel();
        var family = LivestockSpecies.OSTRICH.family();
        int tier = family.equals(PrefabDefinitions.COOP) ? 2 : 1;
        BlockPos manager = pos.north(4);
        var claim = PrefabDefinitions.get(family).selfBounds(manager);
        for (int x = claim.min().getX(); x < claim.maxExclusive().getX(); x++) {
            for (int z = claim.min().getZ(); z < claim.maxExclusive().getZ(); z++) {
                put(h, placed, new BlockPos(x, manager.getY() - 1, z), Blocks.STONE.defaultBlockState());
            }
        }
        put(h, placed, manager, PrefabDefinitions.managerState(family, Direction.SOUTH));
        for (int offset = 1; offset <= 4; offset++) {
            put(h, placed, manager.east(offset), ModBlocks.FEED_TROUGH.get().defaultBlockState());
            if (tier == 2) put(h, placed, manager.west(offset), ModBlocks.FEED_TROUGH.get().defaultBlockState());
        }
        put(h, placed, manager.north(2), ModBlocks.HAY_HOPPER.get().defaultBlockState());
        if (tier == 2) {
            BlockPos ordinaryPos = manager.west(4).north();
            var ordinary = (IncubatorBlock) ModBlocks.INCUBATOR.get();
            put(h, placed, ordinaryPos, ordinary.defaultBlockState());
            placed.add(ordinaryPos.above());
            ordinary.setPlacedBy(level, ordinaryPos, level.getBlockState(ordinaryPos), null, ItemStack.EMPTY);
        }
        var record = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), family, BuildingRecord.Mode.SELF_BUILT,
                level.dimension().location(), manager, manager, Direction.SOUTH, claim);
        var buildings = BuildingWorldData.get(level.getServer());
        h.assertTrue(buildings.register(record) == BuildingWorldData.Result.SUCCESS, "Home registration failed");
        var assessment = BuildingResidence.scan(level, claim, family);
        h.assertTrue(assessment.loaded() && assessment.eligibleTier() == tier, "Effective home facilities are invalid");
        for (int current = 1; current <= tier; current++) {
            h.assertTrue(buildings.acceptSelf(record.id(), record.revision(), assessment.eligibleTier()) == BuildingWorldData.Result.SUCCESS,
                    "Home acceptance failed");
            record = buildings.find(record.id());
        }
        BuildingResidence.refresh(level, record);
        record = buildings.find(record.id());
        h.assertTrue(record.phase() == BuildingRecord.Phase.READY && record.tier() == tier
                        && record.residence() == BuildingRecord.Residence.VALID,
                "Effective home is not READY and valid");
        return record;
    }

    private static void put(GameTestHelper h, Set<BlockPos> placed, BlockPos pos, BlockState state) {
        h.assertTrue(com.stardew.craft.port.PortGameTests.getBounds(h).contains(Vec3.atCenterOf(pos)), "Home fixture escaped its structure bounds");
        placed.add(pos);
        h.getLevel().setBlock(pos, state, QUIET_FLAGS);
    }
}
