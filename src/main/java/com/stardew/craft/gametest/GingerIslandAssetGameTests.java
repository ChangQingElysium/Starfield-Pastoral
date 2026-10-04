package com.stardew.craft.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.TapperBlock;
import com.stardew.craft.blockentity.TapperBlockEntity;
import com.stardew.craft.gingerisland.CooledLavaBlock;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.gingerisland.HeavyTapperBlock;
import com.stardew.craft.gingerisland.IslandContext;
import com.stardew.craft.gingerisland.IslandFlameBlock;
import com.stardew.craft.gingerisland.OstrichIncubatorBlock;
import com.stardew.craft.gingerisland.TropicalBedBlock;
import com.stardew.craft.gingerisland.VolcanoCooling;
import com.stardew.craft.gingerisland.VolcanoFloorSwitchBlock;
import com.stardew.craft.gingerisland.WalnutDebris;
import com.stardew.craft.interior.InteriorRegionRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.menu.MiniForgeMenu;
import com.stardew.craft.mining.GoldenWalnutData;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("stardewcraft_ginger_assets")
@PrefixGameTestTemplate(false)
public final class GingerIslandAssetGameTests {
    // Even the small behavioral tests write at relative (5..9, 3, 5..9).
    // Keep them inside a real 20x10x20 fixture instead of the legacy 1x1x1 file.
    private static final String NS = "stardewcraft_ginger_collision";

    @GameTest(templateNamespace = NS, template = "empty")
    public static void objectStatesShareOneRegistrationAndPreservePicking(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        var pedestal = (GingerIslandStateDecorBlock) GingerIslandBlocks.get("ginger_gem_pedestal");
        var ruby = pedestal.defaultBlockState().setValue(GingerIslandStateDecorBlock.GEM,
                GingerIslandStateDecorBlock.Gem.RUBY);
        level.setBlock(pos, ruby, 3);
        h.assertTrue(pedestal.modelForState(ruby).endsWith("/gem_pedestal_ruby"), "Gem state selects the wrong model");
        var picked = pedestal.getCloneItemStack(level, pos, ruby);
        h.assertTrue(ItemStack.isSameItemSameComponents(picked, new ItemStack(pedestal))
                        && !picked.has(DataComponents.BLOCK_STATE),
                "Picking a pedestal leaked its offering as an item state or created a second item");
        var ids = GingerIslandAssets.blocks().stream().map(GingerIslandAssets.BlockAsset::id).toList();
        h.assertTrue(ids.stream().filter(id -> id.startsWith("ginger_gem_pedestal")).count() == 1,
                "Gem variations are still registered as independent blocks");
        h.assertTrue(!ids.contains("ginger_basic_window_night") && !ids.contains("ginger_crystal_cave_statue_active")
                && !ids.contains("ginger_construction_chip_3d"), "State/effect pieces still pollute the item catalog");
        for (var asset : GingerIslandAssets.blocks()) {
            if (!asset.kind().equals("state_decor")) continue;
            var block = (GingerIslandStateDecorBlock) GingerIslandBlocks.get(asset.id());
            for (var choice : asset.state_models().entrySet()) {
                var state = new net.minecraft.world.item.component.BlockItemStateProperties(
                        java.util.Map.of(asset.state_property(), choice.getKey())).apply(block.defaultBlockState());
                h.assertTrue(block.modelForState(state).equals(choice.getValue()), "Unresolved state: " + asset.id());
                h.assertTrue(!state.getShape(level, pos).isEmpty(), "State has no collision: " + asset.id());
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void volcanoSwitchLatchesOnlyForGroundedPlayers(GameTestHelper h) {
        var level = h.getLevel();
        var button = (VolcanoFloorSwitchBlock) GingerIslandBlocks.get("ginger_volcano_floor_switch");
        var pos = h.absolutePos(new BlockPos(6, 3, 6));
        var other = pos.east(3);
        level.setBlock(pos, button.defaultBlockState(), 3);
        level.setBlock(other, button.defaultBlockState(), 3);
        var drop = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + .5, pos.getY() + .25,
                pos.getZ() + .5, new ItemStack(net.minecraft.world.item.Items.STONE));
        button.stepOn(level, pos, level.getBlockState(pos), drop);
        h.assertTrue(!level.getBlockState(pos).getValue(VolcanoFloorSwitchBlock.PRESSED), "Item pressed a player-only switch");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Volcano switch test"));
        player.setPos(Vec3.atBottomCenterOf(pos).add(0, .25, 0));
        player.setOnGround(false);
        button.stepOn(level, pos, level.getBlockState(pos), player);
        h.assertTrue(!level.getBlockState(pos).getValue(VolcanoFloorSwitchBlock.PRESSED), "Flying player pressed a switch");
        player.setOnGround(true);
        button.stepOn(level, pos, level.getBlockState(pos), player);
        h.assertTrue(level.getBlockState(pos).getValue(VolcanoFloorSwitchBlock.PRESSED), "Grounded player could not press switch");
        h.assertTrue(!level.getBlockState(other).getValue(VolcanoFloorSwitchBlock.PRESSED), "Unrelated switch changed");
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        h.assertTrue(shape.toAabbs().size() == 1 && shape.max(Direction.Axis.Y) == 2.0 / 16,
                "Pressed switch must use one low AABB");
        player.setPos(Vec3.atBottomCenterOf(other));
        h.runAfterDelay(30, () -> {
            h.assertTrue(level.getBlockState(pos).getValue(VolcanoFloorSwitchBlock.PRESSED), "Switch released after player left");
            h.assertTrue(!level.getBlockState(other).getValue(VolcanoFloorSwitchBlock.PRESSED), "Other island position changed");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void registeredResourcesAndFunctionalTypes(GameTestHelper h) {
        var level = h.getLevel();
        for (var asset : GingerIslandAssets.blocks()) {
            var block = GingerIslandBlocks.get(asset.id());
            h.assertTrue(block.asItem() != net.minecraft.world.item.Items.AIR, "Missing block item: " + asset.id());
            String path = "/assets/stardewcraft/models/" + asset.model().split(":", 2)[1] + ".json";
            h.assertTrue(GingerIslandAssets.class.getResource(path) != null, "Missing packaged model: " + asset.id());
            if (!asset.kind().equals("cube")) {
                h.assertTrue(!block.defaultBlockState().getShape(level, h.absolutePos(new BlockPos(4, 4, 4))).isEmpty(),
                        "Missing authored collision: " + asset.id());
            }
        }
        h.assertTrue(GingerIslandBlocks.get("ginger_tropical_bed") instanceof TropicalBedBlock, "Bed is inert decoration");
        h.assertTrue(GingerIslandBlocks.get("ginger_heavy_tapper") instanceof HeavyTapperBlock, "Tapper is inert decoration");
        h.assertTrue(GingerIslandBlocks.get("ginger_tropical_tv") instanceof com.stardew.craft.block.tv.TVBlock, "TV is inert decoration");
        h.assertTrue(GingerIslandBlocks.get("ginger_ostrich_incubator_empty") instanceof OstrichIncubatorBlock, "Incubator is inert decoration");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_placement", template = "large_empty")
    public static void cabinPassageAllFacingsAndNoReservedInterior(GameTestHelper h) {
        prepareAssemblySpace(h);
        var level = h.getLevel();
        var cabin = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_captain_cabin_shell");
        var main = h.absolutePos(new BlockPos(40, 4, 40)).above(cabin.placementAnchorYOffset());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFootprintInside(h, cabin, main, facing);
            var state = cabin.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
            level.setBlock(main, state, 2 | 16);
            h.assertTrue(cabin.placeExtensions(level, main, state), "Cabin placement failed: " + facing);
            for (int y = 1; y < 4; y++) for (int x = 1; x < 5; x++) for (int z = 1; z < 4; z++) {
                var cell = main.offset(rotate(new BlockPos(x, y, z), facing));
                h.assertTrue(level.isEmptyBlock(cell), "Interior cell was reserved: " + facing + " " + cell);
            }
            for (int y = 1; y <= 2; y++) {
                var entry = main.offset(rotate(new BlockPos(0, y, 2), facing));
                h.assertTrue(level.isEmptyBlock(entry), "Cabin entrance blocked: " + facing);
            }
            var feet = Vec3.atBottomCenterOf(main.offset(rotate(new BlockPos(0, 1, 2), facing))).add(0, .001, 0);
            var body = new net.minecraft.world.phys.AABB(feet.x - .3, feet.y, feet.z - .3,
                    feet.x + .3, feet.y + 1.8, feet.z + .3);
            h.assertTrue(level.noCollision(body), "Normal player does not fit cabin entry: " + facing);
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void ostrichIncubatorOneReceiptReadyAppearanceAndUpperAccess(GameTestHelper h) {
        var level = h.getLevel();
        var block = (OstrichIncubatorBlock) GingerIslandBlocks.get("ginger_ostrich_incubator_empty");
        var pos = h.absolutePos(new BlockPos(5, 3, 5));
        var state = block.defaultBlockState();
        level.setBlock(pos, state, 3);
        block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
        h.assertTrue(level.getBlockEntity(pos.above()) == null, "Upper cell created another incubation receipt");
        var be = (com.stardew.craft.blockentity.IncubatorBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, pos.above(), Direction.UP) == be.getAutomationItemHandler(),
                "Upper cell does not forward to its single incubation input");
        var tag = new CompoundTag();
        tag.put("Input", new ItemStack(ModItems.OSTRICH_EGG.get()).save(level.registryAccess()));
        tag.putBoolean("Ready", true); tag.putLong("ReadyAt", 0); tag.putUUID("NewbornReceipt", UUID.randomUUID());
        be.loadWithComponents(tag, level.registryAccess());
        h.assertTrue(be.isReady() && be.hasInput() && !be.isWorking(), "Ready egg state was lost");
        h.assertTrue(!be.canApplyFairyDust(), "Source machine forbids fairy dust");
        h.assertTrue(OstrichIncubatorBlock.completionMorning(0, 9000) == 9600
                && OstrichIncubatorBlock.completionMorning(1000, 4500) == 6400
                && OstrichIncubatorBlock.completionMorning(0, 9600) == 9600,
                "Incubation must finish overnight, including profession-adjusted durations");
        h.runAfterDelay(25, () -> {
            h.assertTrue(level.getBlockState(pos).getValue(OstrichIncubatorBlock.LOADED)
                    && level.getBlockState(pos.above()).getValue(OstrichIncubatorBlock.LOADED),
                    "Ready egg must remain visible even when working is false");
            level.removeBlock(pos, false);
            h.assertTrue(level.isEmptyBlock(pos.above()), "Upper incubator survived removing main");
            h.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void fireplaceStateLightingAndExtensionInteraction(GameTestHelper h) {
        var level = h.getLevel();
        var fireplace = (IslandFlameBlock) GingerIslandBlocks.get("ginger_stove_fireplace");
        var pos = h.absolutePos(new BlockPos(6, 3, 6));
        var state = fireplace.defaultBlockState();
        level.setBlock(pos, state, 2 | 16);
        h.assertTrue(fireplace.placeExtensions(level, pos, state), "Fireplace placement failed");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Fireplace test"));
        var extension = pos.above();
        h.assertTrue(level.getBlockState(extension).is(fireplace), "Missing interactive chimney cell");
        var hit = new BlockHitResult(Vec3.atCenterOf(extension), Direction.NORTH, extension, false);
        h.assertTrue(level.getBlockState(extension).useWithoutItem(level, player, hit).consumesAction(), "Extension click was ignored");
        h.assertTrue(level.getBlockState(pos).getValue(IslandFlameBlock.LIT)
                && level.getBlockState(extension).getValue(IslandFlameBlock.LIT), "Lit state did not propagate");
        h.assertTrue(fireplace.getLightEmission(level.getBlockState(pos), level, pos) == 15, "Lit fireplace emits no light");
        level.getBlockState(extension).useWithoutItem(level, player, hit);
        h.assertTrue(!level.getBlockState(pos).getValue(IslandFlameBlock.LIT)
                && fireplace.getLightEmission(level.getBlockState(pos), level, pos) == 0, "Extinguished fireplace still lights");
        h.succeed();
    }

    private static BlockPos rotate(BlockPos offset, Direction facing) {
        return switch (facing) {
            case NORTH -> offset;
            case EAST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> throw new IllegalArgumentException();
        };
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_placement", template = "large_empty")
    public static void tropicalBedTwoLanesAllFacingsAndCleanup(GameTestHelper h) {
        prepareAssemblySpace(h);
        var level = h.getLevel();
        var bed = (TropicalBedBlock) GingerIslandBlocks.get("ginger_tropical_bed");
        BlockPos main = h.absolutePos(new BlockPos(40, 4, 40)).above(bed.placementAnchorYOffset());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertFootprintInside(h, bed, main, facing);
            var state = bed.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
            level.setBlock(main, state, 2 | 16);
            h.assertTrue(bed.placeExtensions(level, main, state), "Bed footprint failed: " + facing);
            for (int lane = 0; lane < 2; lane++) {
                BlockPos foot = main.offset(rotate(new BlockPos(lane, 0, 0), facing));
                BlockPos head = main.offset(rotate(new BlockPos(lane, 0, 2), facing));
                h.assertTrue(level.getBlockState(head).is(bed) && bed.sleepAnchor(level, foot, level.getBlockState(foot)).equals(head),
                        "Wrong lane/head position: " + facing + "/" + lane);
                h.assertTrue(bed.sleepAnchor(level, head, level.getBlockState(head)).equals(head), "Head must resolve its own lane");
            }
            h.assertTrue(bed.sleepYOffset() == 15.0 / 16.0, "Sleeper does not match authored blanket height");
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
            for (BlockPos pos : BlockPos.betweenClosed(main.offset(-5, -1, -5), main.offset(5, 3, 5))) {
                h.assertTrue(!level.getBlockState(pos).is(bed), "Bed extension remained after removal: " + facing + " " + pos);
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void heavyTapperOneJobRoundedDaysAndUpperAutomation(GameTestHelper h) {
        var level = h.getLevel();
        var registry = PrefabTreeRegistry.get(level);
        var def = WildTrees.OAK;
        BlockPos root = h.absolutePos(new BlockPos(6, 3, 6)), log = root.above(), pos = root.south();
        level.setBlock(root, def.modernRoot().get().defaultBlockState(), 2);
        level.setBlock(log, def.modernLog().get().defaultBlockState(), 2);
        registry.register(root, def.id(), 1, Set.of(root, log));
        try {
            var heavy = (HeavyTapperBlock) GingerIslandBlocks.get("ginger_heavy_tapper");
            var state = heavy.defaultBlockState().setValue(TapperBlock.FACING, Direction.NORTH);
            level.setBlock(pos, state, 3);
            heavy.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
            h.assertTrue(level.getBlockState(pos.above()).getValue(HeavyTapperBlock.UPPER), "Upper part not installed");
            h.assertTrue(level.getBlockEntity(pos.above()) == null, "Upper part created a second job");
            var be = (TapperBlockEntity) level.getBlockEntity(pos);
            be.ensureCycleStarted(state);
            h.assertTrue(be.hasProduct() && be.isProductionSiteValid(), "Heavy tapper did not produce");
            h.assertTrue(TapperBlock.attachedTappers(level, Set.of(root, log)).equals(Set.of(pos)), "Upper half counted as another tapper");
            h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, pos.above(), Direction.UP) == be.getAutomationItemHandler(),
                    "Upper automation must resolve the main job");
            long heavyTime = be.getRemainingAbsMinutes();
            level.removeBlock(pos, false);
            h.assertTrue(level.isEmptyBlock(pos.above()), "Upper part survived main removal");
            var ordinary = ModBlocks.TAPPER.get();
            var normalState = ordinary.defaultBlockState().setValue(TapperBlock.FACING, Direction.NORTH);
            level.setBlock(pos, normalState, 3);
            ordinary.setPlacedBy(level, pos, normalState, null, ItemStack.EMPTY);
            var normal = (TapperBlockEntity) level.getBlockEntity(pos);
            normal.ensureCycleStarted(normalState);
            h.assertTrue(normal.getRemainingAbsMinutes() - heavyTime == 4L * 1600,
                    "Oak must round 7 days to 3 mornings, rather than halve the remaining minutes");
        } finally {
            level.removeBlock(pos, false);
            registry.unregister(registry.getByRoot(root));
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_ginger_placement", template = "large_empty")
    public static void forgeExtensionOpensSameMenuAndTracksWorkstation(GameTestHelper h) {
        prepareAssemblySpace(h);
        var level = h.getLevel();
        var forge = (MapDecorStaticBlock) GingerIslandBlocks.get("ginger_caldera_forge");
        var pos = h.absolutePos(new BlockPos(40, 4, 40)).above(forge.placementAnchorYOffset());
        assertFootprintInside(h, forge, pos, Direction.NORTH);
        var state = forge.defaultBlockState();
        level.setBlock(pos, state, 2 | 16);
        h.assertTrue(forge.placeExtensions(level, pos, state), "Forge footprint failed");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Forge test")) {
            @Override public OptionalInt openMenu(MenuProvider provider) {
                containerMenu = provider.createMenu(1, getInventory(), this);
                return OptionalInt.of(1);
            }
        };
        BlockPos cell = BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 4, 4)).iterator().next();
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 4, 4))) {
            if (level.getBlockState(candidate).is(forge) && !candidate.equals(pos)) { cell = candidate.immutable(); break; }
        }
        h.assertTrue(!cell.equals(pos) && level.getBlockState(cell).is(forge), "No interactive forge extension");
        player.setPos(Vec3.atCenterOf(cell));
        var result = level.getBlockState(cell).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false));
        h.assertTrue(result.consumesAction() && player.containerMenu instanceof MiniForgeMenu, "Extension did not open forge rules");
        h.assertTrue(player.containerMenu.stillValid(player), "Nearby user rejected");
        player.setPos(Vec3.atCenterOf(cell).add(30, 0, 0));
        h.assertTrue(!player.containerMenu.stillValid(player), "Menu survived walking away");
        player.setPos(Vec3.atCenterOf(cell));
        level.removeBlock(pos, false);
        h.assertTrue(!player.containerMenu.stillValid(player), "Menu survived destroying workstation");
        player.containerMenu.removed(player);
        h.succeed();
    }

    private static void prepareAssemblySpace(GameTestHelper h) {
        var level = h.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            BlockPos pos = h.absolutePos(relative);
            h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(pos)), "Assembly setup escaped the real fixture");
            level.setBlock(pos, relative.getY() == 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
    }

    private static void assertFootprintInside(GameTestHelper h, MapDecorStaticBlock block, BlockPos main, Direction facing) {
        for (BlockPos cell : block.placementPositions(main, facing)) {
            h.assertTrue(h.getBounds().contains(Vec3.atCenterOf(cell)), "Assembly footprint escaped the real fixture: " + cell);
        }
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void walnutFarmIsolationPersistenceAndDebrisIdentity(GameTestHelper h) {
        var level = h.getLevel();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var a = GoldenWalnutData.get(level.getServer(), first);
        var b = GoldenWalnutData.get(level.getServer(), second);
        h.assertTrue(a == GoldenWalnutData.get(level.getServer(), first) && a != b, "Farm members must share only their farm's wallet");
        h.assertTrue(a.markCollectedNut("BananaShrine") && !a.markCollectedNut("BananaShrine"), "Puzzle debris may be issued twice");
        a.discover(3);
        h.assertTrue(a.spend(2) && !a.spend(2) && a.balance() == 1 && a.found() == 3, "Spending changed discovery or overspent");
        for (int i = 0; i < 5; i++) h.assertTrue(a.reserveLimitedDrop("Fishing", 5), "Premature quota exhaustion");
        h.assertTrue(!a.reserveLimitedDrop("Fishing", 5) && b.markCollectedNut("BananaShrine") && b.limitedDrops("Fishing") == 0,
                "Reward quota leaked across farms");
        var restored = GoldenWalnutData.load(a.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(restored.balance() == 1 && restored.found() == 3 && restored.isCollected("BananaShrine")
                && !restored.reserveLimitedDrop("Fishing", 5), "Reward state did not persist");
        ItemStack x = new ItemStack(ModItems.GOLDEN_WALNUT.get()), y = x.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, x, tag -> tag.putUUID(WalnutDebris.FARM_ID, first));
        CustomData.update(DataComponents.CUSTOM_DATA, y, tag -> tag.putUUID(WalnutDebris.FARM_ID, second));
        h.assertTrue(!ItemStack.isSameItemSameComponents(x, y) && WalnutDebris.owner(x).orElseThrow().equals(first),
                "Vanilla merging could combine different farms' walnut rewards");
        h.succeed();
    }

    private static String location(GameTestHelper h, BlockPos pos, String ledger, String farm, String floor) {
        JsonObject root = JsonParser.parseString(InteriorRegionRegistry.getCachedJson()).getAsJsonObject();
        JsonObject entry = new JsonObject(), properties = new JsonObject();
        entry.addProperty("dimension", h.getLevel().dimension().location().toString());
        entry.addProperty("ledger_id", ledger);
        entry.addProperty("priority", 1000000);
        JsonArray min = new JsonArray(), max = new JsonArray();
        for (int value : new int[]{pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2}) min.add(value);
        for (int value : new int[]{pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2}) max.add(value);
        entry.add("min", min); entry.add("max", max);
        if (farm != null) properties.addProperty(IslandContext.FARM_INSTANCE.toString(), farm);
        if (floor != null) properties.addProperty(IslandContext.VOLCANO_FLOOR.toString(), floor);
        entry.add("properties", properties);
        root.add("stardewcraft:ginger_test_region", entry);
        return root.toString();
    }

    @GameTest(templateNamespace = NS, template = "empty")
    public static void coolingConnectionsAndProtectedLocations(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(6, 3, 6));
        String previous = InteriorRegionRegistry.getCachedJson();
        try {
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2 | 16);
            InteriorRegionRegistry.applyFromJson(location(h, pos, "Caldera", null, null));
            h.assertTrue(!VolcanoCooling.canCool(level, pos), "Caldera should only steam");
            InteriorRegionRegistry.applyFromJson(location(h, pos, "VolcanoDungeon", null, "5"));
            h.assertTrue(!VolcanoCooling.canCool(level, pos), "Fifth floor may not cool");
            InteriorRegionRegistry.applyFromJson(location(h, pos, "VolcanoDungeon", null, "4"));
            h.assertTrue(VolcanoCooling.cool(level, pos), "Dungeon lava did not cool");
            var stone = level.getBlockState(pos);
            h.assertTrue(stone.getCollisionShape(level, pos).min(Direction.Axis.Y) == 14.0 / 16.0,
                    "Cooled surface must preserve the authored thin stone plate");
            level.setBlock(pos.east(), Blocks.LAVA.defaultBlockState(), 2 | 16);
            h.assertTrue(VolcanoCooling.cool(level, pos.east()), "Adjacent tile did not cool");
            h.assertTrue(level.getBlockState(pos).getValue(CooledLavaBlock.NEIGHBORS) == 2
                    && level.getBlockState(pos.east()).getValue(CooledLavaBlock.NEIGHBORS) == 8, "Shared edge masks did not update");
            level.removeBlock(pos.east(), false);
            h.assertTrue(level.getBlockState(pos).getValue(CooledLavaBlock.NEIGHBORS) == 0, "Removed neighbor retained its edge");
            InteriorRegionRegistry.applyFromJson(location(h, pos, "VolcanoDungeon", "invalid-farm", "4"));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Island visitor"));
            h.assertTrue(IslandContext.isBound(level, pos) && !IslandContext.canModifyAt(player, pos), "Malformed ownership allowed mutation");
        } finally { InteriorRegionRegistry.applyFromJson(previous); }
        h.succeed();
    }
}
