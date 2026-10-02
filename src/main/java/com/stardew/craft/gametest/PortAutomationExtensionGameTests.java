package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.animal.runtime.FarmFeed;
import com.stardew.craft.animal.runtime.LivestockHomes;
import com.stardew.craft.animal.runtime.LivestockSpecies;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.IncubatorBlockEntity;
import com.stardew.craft.blockentity.KegBlockEntity;
import com.stardew.craft.blockentity.StorageChestBlockEntity;
import com.stardew.craft.blockentity.TapperBlockEntity;
import com.stardew.craft.blockentity.TimedProductionBlockEntity;
import com.stardew.craft.blockentity.UtilityAutomationAccess;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingResidence;
import com.stardew.craft.building.runtime.BuildingService;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.capability.UtilityAutomationCapabilities;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.HeavyTapperBlock;
import com.stardew.craft.gingerisland.OstrichIncubatorBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.port.PortCapabilities;
import com.stardew.craft.port.PortMachineExtensions;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Forge-only structure adaptation; checks external Forge queries, not only the port's own block query. */
@GameTestHolder("stardewcraft")
@PrefixGameTestTemplate(false)
public final class PortAutomationExtensionGameTests {
    private PortAutomationExtensionGameTests() {}

    private static FakePlayer prepare(GameTestHelper helper) {
        var level = helper.getLevel();
        for (BlockPos cell : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(15, 5, 15)))) {
            level.setBlock(cell, cell.getY() == helper.absolutePos(BlockPos.ZERO).getY()
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "port-automation"));
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(20, 1, 20))));
        return player;
    }

    private static List<BlockPos> place(GameTestHelper helper, FakePlayer player, Block block, BlockPos origin, Direction facing) {
        player.setYRot(facing.toYRot());
        helper.getLevel().setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(origin.below()).add(0, 0.5, 0), Direction.UP, origin.below(), false)));
        helper.assertTrue(((BlockItem) context.getItemInHand().getItem()).place(context).consumesAction(),
                "Machine placement failed: " + block + " " + facing);
        return BlockPos.betweenClosedStream(origin.offset(-3, 0, -3), origin.offset(3, 3, 3))
                .filter(cell -> helper.getLevel().getBlockState(cell).is(block)).map(BlockPos::immutable).toList();
    }

    private static List<Direction> sides() {
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        sides.add(null); // Forge's unsided third-party queries must also forward correctly.
        return sides;
    }

    private record HomeFixture(FarmInstanceRegistry farms, UUID owner, UUID farmId, ServerLevel level,
                               List<BlockPos> facilities) implements AutoCloseable {
        @Override public void close() {
            BuildingWorldData.get(level.getServer()).removeFarm(farmId);
            for (BlockPos pos : facilities) level.removeBlock(pos, false);
            farms.deleteFarm(owner);
        }
    }

    private static HomeFixture prepareOstrichHome(GameTestHelper helper, FakePlayer player, BlockPos origin) {
        var level = helper.getLevel();
        var farms = FarmInstanceRegistry.get(level.getServer());
        var family = LivestockSpecies.OSTRICH.family();
        helper.assertTrue(family.equals(PrefabDefinitions.COOP), "Project ostriches must retain their approved coop family");
        var farm = farms.createFarm(player.getUUID(), "PortCoop", "Automation coop", FarmType.STANDARD);
        BlockPos manager = origin.north(4);
        var bounds = PrefabDefinitions.get(family).selfBounds(manager);
        List<BlockPos> facilities = new ArrayList<>();
        HomeFixture fixture = new HomeFixture(farms, player.getUUID(), farm.getInstanceId(), level, facilities);
        try {
            LivestockHomes.load(level, bounds);
            facilities.add(manager);
            level.setBlock(manager, ModBlocks.COOP_MANAGER.get().defaultBlockState(), 3);
            for (int i = 1; i <= 4; i++) {
                for (BlockPos trough : List.of(manager.east(i), manager.west(i))) {
                    facilities.add(trough);
                    level.setBlock(trough, ModBlocks.FEED_TROUGH.get().defaultBlockState(), 3);
                }
            }
            BlockPos hopper = manager.north(2);
            facilities.add(hopper);
            level.setBlock(hopper, ModBlocks.HAY_HOPPER.get().defaultBlockState(), 3);
            // Keep this validation facility outside the tested machine's +/-3-cell query box.
            BlockPos incubatorPos = manager.west(4).north(1);
            var incubatorBlock = ModBlocks.INCUBATOR.get();
            var incubatorState = incubatorBlock.defaultBlockState();
            facilities.add(incubatorPos);
            facilities.add(incubatorPos.above());
            level.setBlock(incubatorPos, incubatorState, 3);
            incubatorBlock.setPlacedBy(level, incubatorPos, incubatorState, null, ItemStack.EMPTY);
            var home = BuildingRecord.waiting(farm.getInstanceId(), farm.getSlotIndex(), family,
                    BuildingRecord.Mode.SELF_BUILT, level.dimension().location(), manager, manager, Direction.SOUTH, bounds);
            var buildings = BuildingWorldData.get(level.getServer());
            helper.assertTrue(buildings.register(home) == BuildingWorldData.Result.SUCCESS, "Ostrich home fixture claim overlaps");
            var assessment = BuildingResidence.scan(level, bounds, family);
            helper.assertTrue(assessment.loaded() && assessment.troughs() == 8 && assessment.hoppers() == 1
                    && assessment.incubators() == 1 && assessment.eligibleTier() == 2,
                    "Ostrich home fixture facilities were not validated");
            helper.assertTrue(buildings.acceptSelf(home.id(), home.revision(), assessment.eligibleTier())
                    == BuildingWorldData.Result.SUCCESS, "Ostrich home fixture was not accepted");
            home = buildings.find(home.id());
            helper.assertTrue(buildings.acceptSelf(home.id(), home.revision(), assessment.eligibleTier())
                    == BuildingWorldData.Result.SUCCESS, "Ostrich home fixture was not upgraded to tier two");
            BuildingResidence.refresh(level, buildings.find(home.id()));
            home = buildings.find(home.id());
            helper.assertTrue(home.phase() == BuildingRecord.Phase.READY && home.tier() == 2
                    && home.residence() == BuildingRecord.Residence.VALID
                    && home.farmId().equals(farm.getInstanceId()) && home.farmSlot() == farm.getSlotIndex()
                    && LivestockHomes.accepts(level, home, LivestockSpecies.OSTRICH)
                    && bounds.contains(origin) && bounds.contains(origin.above())
                    && home.equals(FarmFeed.home(level, origin)) && home.equals(FarmFeed.home(level, origin.above()))
                    && BuildingService.canManage(player, home),
                    "Ostrich fixture must be a real, valid tier-two coop managed by the placing player");
            return fixture;
        } catch (RuntimeException exception) {
            fixture.close();
            throw exception;
        }
    }

    @GameTest(templateNamespace = "stardewcraft_b008", template = "machine_test", timeoutTicks = 100)
    public static void everyRegisteredMachineSharesOneOwnerInAllFourFacings(GameTestHelper helper) {
        FakePlayer player = prepare(helper);
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        try (HomeFixture fixture = prepareOstrichHome(helper, player, origin)) {
            helper.assertTrue(java.util.Arrays.asList(UtilityAutomationCapabilities.multiblockAutomationBlocks())
                    .contains(GingerIslandBlocks.get("ginger_ostrich_incubator_empty")),
                    "Registered machine coverage must not omit the ostrich incubator");
            for (Block block : UtilityAutomationCapabilities.multiblockAutomationBlocks()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    List<BlockPos> cells = place(helper, player, block, origin, facing);
                    String label = block + " " + facing;
                    helper.assertTrue(cells.size() >= 2, "Missing occupied extension: " + label);
                    BlockEntity owner = level.getBlockEntity(origin);
                    helper.assertTrue(owner instanceof UtilityAutomationAccess, "Missing owner inventory: " + label);
                    if (block instanceof OstrichIncubatorBlock) {
                        helper.assertTrue(owner instanceof IncubatorBlockEntity incubator
                                && incubator.getContainingRuntimeBuilding(level).family().equals(LivestockSpecies.OSTRICH.family()),
                                "Ostrich placement did not retain its real approved home: " + label);
                    }
                    helper.assertTrue(cells.stream().filter(cell -> level.getBlockEntity(cell) instanceof UtilityAutomationAccess).count() == 1,
                            "Machine must have exactly one real inventory: " + label);
                    helper.assertTrue(cells.stream().filter(cell -> level.getBlockEntity(cell) instanceof TimedProductionBlockEntity).count()
                            == (owner instanceof TimedProductionBlockEntity ? 1 : 0), "Duplicate production entity: " + label);
                    helper.assertTrue(PortMachineExtensions.createExtension(origin, level.getBlockState(origin)) == null,
                            "Factory accepted a main part: " + label);
                    List<LazyOptional<IItemHandler>> allFresh = new ArrayList<>();
                    for (BlockPos extension : cells) {
                        if (extension.equals(origin)) continue;
                        BlockEntity bridge = level.getBlockEntity(extension);
                        helper.assertTrue(bridge instanceof PortMachineExtensions.ExtensionBlockEntity
                                && !(bridge instanceof UtilityAutomationAccess) && !(bridge instanceof Container),
                                "Extension acquired an independent inventory: " + label);
                        var state = level.getBlockState(extension);
                        helper.assertTrue(((EntityBlock) block).getTicker(level, state, PortMachineExtensions.TYPE.get()) == null,
                                "Extension acquired a production/client ticker: " + label);
                        helper.assertTrue(((EntityBlock) block).getTicker(level, state, owner.getType()) == null,
                                "Extension acquired its owner's production ticker: " + label);
                        List<LazyOptional<IItemHandler>> issued = new ArrayList<>();
                        for (Direction side : sides()) {
                            LazyOptional<IItemHandler> mainOptional = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                            helper.assertTrue(mainOptional.isPresent(), "Main external Forge query failed: " + label + " " + side);
                            LazyOptional<IItemHandler> extensionOptional = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                            IItemHandler handler = extensionOptional.resolve().orElse(null);
                            helper.assertTrue(handler != null, "Extension external Forge query failed: " + label + " " + side);
                            helper.assertTrue(handler == mainOptional.resolve().orElse(null),
                                    "Main and extension external queries returned different handlers: " + label + " " + side);
                            LazyOptional<IItemHandler> mainAgain = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                            LazyOptional<IItemHandler> extensionAgain = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                            helper.assertTrue(mainAgain.resolve().orElse(null) == handler
                                    && extensionAgain.resolve().orElse(null) == handler
                                    && mainOptional.isPresent() && extensionOptional.isPresent(),
                                    "Repeated external queries changed the handler or invalidated an issued optional: " + label + " " + side);
                            if (owner instanceof TimedProductionBlockEntity machine) {
                                helper.assertTrue(handler == machine.getAutomationItemHandler(), "Extension did not expose the owner's handler: " + label);
                            }
                            issued.add(extensionOptional);
                        }
                        PortCapabilities.invalidateCapabilities(level, origin);
                        helper.assertTrue(issued.stream().noneMatch(LazyOptional::isPresent), "Owner invalidation left stale bridge optionals: " + label);
                        LazyOptional<IItemHandler> fresh = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
                        helper.assertTrue(fresh.isPresent(), "Bridge cannot re-query the owner: " + label);
                        PortCapabilities.invalidateCapabilities(level, extension);
                        helper.assertTrue(!fresh.isPresent() && owner.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                                "Extension invalidation failed or invalidated the owner itself: " + label);
                        fresh = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
                        bridge.onChunkUnloaded();
                        helper.assertTrue(!fresh.isPresent() && !bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()
                                && owner.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                                "Unloaded extension stayed queryable or invalidated its loaded owner: " + label);
                        bridge.reviveCaps();
                        allFresh.add(bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP));
                    }
                    BlockPos extension = cells.stream().filter(cell -> !cell.equals(origin)).findFirst().orElseThrow();
                    BlockEntity removedBridge = level.getBlockEntity(extension);
                    level.removeBlock(extension, false);
                    helper.assertTrue(allFresh.stream().noneMatch(LazyOptional::isPresent)
                            && !removedBridge.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
                            "Removed bridge retained a usable Forge capability: " + label);
                    helper.assertTrue(cells.stream().allMatch(cell -> !level.getBlockState(cell).is(block)), "Removal left machine parts: " + label);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_b008", template = "machine_test", timeoutTicks = 100)
    public static void realHoppersInsertAndExtractThroughMainAndExtensionWithoutDuplication(GameTestHelper helper) {
        FakePlayer player = prepare(helper);
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean extensionTarget : new boolean[]{false, true}) {
                List<BlockPos> cells = place(helper, player, ModBlocks.KEG.get(), origin, facing);
                BlockPos target = extensionTarget ? cells.stream().filter(cell -> !cell.equals(origin)).findFirst().orElseThrow() : origin;
                helper.assertTrue(target.getY() == origin.getY(), "Keg extension must leave room for a real output hopper");
                KegBlockEntity owner = (KegBlockEntity) level.getBlockEntity(origin);
                BlockPos inputPos = target.above();
                BlockState hopperState = Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN);
                level.setBlock(inputPos, hopperState, 3);
                HopperBlockEntity input = (HopperBlockEntity) level.getBlockEntity(inputPos);
                input.setItem(0, new ItemStack(ModItems.WHEAT.get(), 3));
                // Actual vanilla hopper tick invokes Forge's adjacent-BE capability path.
                HopperBlockEntity.pushItemsTick(level, inputPos, hopperState, input);
                helper.assertTrue(input.getItem(0).getCount() == 2 && owner.getAutomationInput().getCount() == 1,
                        "Hopper must consume exactly one wheat through " + (extensionTarget ? "extension " : "main ") + facing);
                input.clearContent();
                level.removeBlock(inputPos, false);
                owner.advanceDays(30);
                helper.assertTrue(owner.getAutomationOutput().is(ModItems.BEER.get()) && owner.getAutomationOutput().getCount() == 1,
                        "Existing keg recipe/timing must retain one beer");
                BlockPos outputPos = target.below();
                level.setBlock(outputPos, hopperState, 3);
                HopperBlockEntity output = (HopperBlockEntity) level.getBlockEntity(outputPos);
                helper.assertTrue(HopperBlockEntity.suckInItems(level, output), "Actual hopper extraction could not see the machine part");
                helper.assertTrue(output.getItem(0).is(ModItems.BEER.get()) && output.getItem(0).getCount() == 1
                        && owner.getAutomationOutput().isEmpty(), "Hopper extraction duplicated or lost the owner's product");
                helper.assertTrue(!HopperBlockEntity.suckInItems(level, output), "A second extraction duplicated the finished product");
                output.clearContent();
                level.removeBlock(outputPos, false);
                level.removeBlock(origin, false);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_b008", template = "machine_test", timeoutTicks = 100)
    public static void missingOwnerAndOwnerRemovalInvalidateExternalQueries(GameTestHelper helper) {
        FakePlayer player = prepare(helper);
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        try (HomeFixture fixture = prepareOstrichHome(helper, player, origin)) {
            for (Block block : UtilityAutomationCapabilities.multiblockAutomationBlocks()) {
                List<BlockPos> cells = place(helper, player, block, origin, Direction.NORTH);
                BlockPos extension = cells.stream().filter(cell -> !cell.equals(origin)).findFirst().orElseThrow();
                BlockState extensionState = level.getBlockState(extension);
                BlockEntity bridge = level.getBlockEntity(extension);
                LazyOptional<IItemHandler> issued = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                helper.assertTrue(issued.isPresent(), "External query unavailable before owner removal: " + block);
                level.removeBlock(origin, false);
                helper.assertTrue(!issued.isPresent() && !bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                        "Owner removal left an externally usable handler: " + block);
                // Flags 2 deliberately retain an orphan occupied part instead of allowing neighbour-shape removal.
                level.setBlock(extension, extensionState, 2);
                BlockEntity orphan = level.getBlockEntity(extension);
                helper.assertTrue(orphan instanceof PortMachineExtensions.ExtensionBlockEntity
                        && !orphan.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(), "Orphan extension invented an inventory: " + block);
                level.removeBlock(extension, false);
            }
            for (Block block : new Block[]{ModBlocks.FARM_COMPUTER.get(), ModBlocks.MINI_OBELISK.get()}) {
                // A mini-obelisk legitimately requires the player's farm. This is a no-BE structure fixture,
                // not a test of (or a reason to bypass) its ordinary player-placement permission.
                var decor = (com.stardew.craft.block.utility.MapUtilityStaticBlock) block;
                BlockState state = decor.defaultBlockState().setValue(com.stardew.craft.block.utility.MapUtilityStaticBlock.FACING, Direction.NORTH);
                level.setBlock(origin, state, 3);
                helper.assertTrue(decor.placeExtensions(level, origin, state), "Decorative fixture could not reserve its footprint: " + block);
                List<BlockPos> cells = BlockPos.betweenClosedStream(origin.offset(-3, 0, -3), origin.offset(3, 3, 3))
                        .filter(cell -> level.getBlockState(cell).is(block)).map(BlockPos::immutable).toList();
                helper.assertTrue(cells.size() >= 2, "Decorative no-BE fixture must include an extension: " + block);
                helper.assertTrue(cells.stream().allMatch(cell -> level.getBlockEntity(cell) == null), "Decorative block acquired a bridge BE: " + block);
                helper.assertTrue(cells.stream().allMatch(cell -> PortMachineExtensions.createExtension(cell, level.getBlockState(cell)) == null),
                        "Unregistered decorative block escaped the whitelist: " + block);
                level.removeBlock(origin, false);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_b008", template = "machine_test", timeoutTicks = 100)
    public static void registeredChestWrappersRemainStableUntilInvalidationOrSharedOwnerLoss(GameTestHelper helper) {
        FakePlayer player = prepare(helper);
        var level = helper.getLevel();
        List<BlockPos> placed = new ArrayList<>();
        try {
            int index = 0;
            for (Block block : new Block[]{ModBlocks.WOODEN_CHEST.get(), ModBlocks.STONE_CHEST.get()}) {
                BlockPos pos = helper.absolutePos(new BlockPos(4 + index++ * 6, 1, 4));
                placed.add(pos);
                place(helper, player, block, pos, Direction.NORTH);
                BlockEntity chest = level.getBlockEntity(pos);
                List<LazyOptional<IItemHandler>> issued = stableChestHandlers(helper, chest, block.toString());
                PortCapabilities.invalidateCapabilities(level, pos);
                helper.assertTrue(issued.stream().noneMatch(LazyOptional::isPresent),
                        "Explicit chest invalidation retained an issued optional: " + block);
                List<LazyOptional<IItemHandler>> refreshed = stableChestHandlers(helper, chest, block.toString());
                level.removeBlock(pos, false);
                helper.assertTrue(refreshed.stream().noneMatch(LazyOptional::isPresent),
                        "Removing the chest retained an externally usable optional: " + block);
            }

            Block block = ModBlocks.JUNIMO_CHEST.get();
            BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 8));
            BlockPos peerPos = helper.absolutePos(new BlockPos(10, 1, 8));
            placed.add(pos);
            placed.add(peerPos);
            place(helper, player, block, pos, Direction.NORTH);
            place(helper, player, block, peerPos, Direction.NORTH);
            var chest = (StorageChestBlockEntity) level.getBlockEntity(pos);
            var peer = (StorageChestBlockEntity) level.getBlockEntity(peerPos);
            helper.assertTrue(chest != null && peer != null && chest.isSharedStorage() && peer.isSharedStorage()
                            && player.getUUID().equals(chest.sharedOwner()) && player.getUUID().equals(peer.sharedOwner()),
                    "Real Junimo placement did not bind both physical chests to the placing owner");
            List<LazyOptional<IItemHandler>> issued = stableChestHandlers(helper, chest, "Junimo main");
            List<LazyOptional<IItemHandler>> peerIssued = stableChestHandlers(helper, peer, "Junimo peer");
            helper.assertTrue(issued.get(0).resolve().orElse(null) != peerIssued.get(0).resolve().orElse(null),
                    "Distinct physical containers were merged merely because they share a Junimo owner");

            // A saved owner may disappear without a prior explicit invalidation. Every provider
            // query must still re-evaluate its null guard instead of reusing the cached wrapper.
            chest.load(new CompoundTag());
            assertUnboundChestQueries(helper, chest, issued);
            helper.assertTrue(peerIssued.stream().allMatch(LazyOptional::isPresent),
                    "One shared chest losing its owner invalidated the other physical chest");
            chest.bindOwner(player);
            helper.assertTrue(player.getUUID().equals(chest.sharedOwner()), "Real owner binding did not recover the unbound chest");
            List<LazyOptional<IItemHandler>> rebound = stableChestHandlers(helper, chest, "Junimo rebound");
            UUID replacementOwner = UUID.randomUUID();
            CompoundTag replacement = new CompoundTag();
            replacement.putUUID("SharedOwner", replacementOwner);
            chest.load(replacement);
            PortCapabilities.invalidateCapabilities(level, pos);
            helper.assertTrue(rebound.stream().noneMatch(LazyOptional::isPresent),
                    "Explicit shared-owner replacement invalidation retained an issued optional");
            helper.assertTrue(replacementOwner.equals(chest.sharedOwner()), "Saved shared-owner replacement was not loaded");
            List<LazyOptional<IItemHandler>> changed = stableChestHandlers(helper, chest, "Junimo changed owner");
            helper.assertTrue(peerIssued.stream().allMatch(LazyOptional::isPresent),
                    "Replacing one chest's owner invalidated an unrelated physical chest");
            chest.load(new CompoundTag());
            assertUnboundChestQueries(helper, chest, changed);
            level.removeBlock(peerPos, false);
            helper.assertTrue(peerIssued.stream().noneMatch(LazyOptional::isPresent),
                    "Removing the physical shared chest retained its optional");
        } finally {
            for (BlockPos pos : placed) level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    private static List<LazyOptional<IItemHandler>> stableChestHandlers(GameTestHelper helper, BlockEntity chest, String label) {
        helper.assertTrue(chest instanceof Container, "Missing real chest container: " + label);
        List<LazyOptional<IItemHandler>> issued = new ArrayList<>();
        for (Direction side : sides()) {
            LazyOptional<IItemHandler> first = chest.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            IItemHandler handler = first.resolve().orElse(null);
            helper.assertTrue(handler != null && handler.getClass() == InvWrapper.class
                            && ((InvWrapper) handler).getInv() == chest,
                    "Chest wrapper does not forward to the actual physical container: " + label + " " + side);
            LazyOptional<IItemHandler> again = chest.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            helper.assertTrue(again == first && again.resolve().orElse(null) == handler && first.isPresent(),
                    "Repeated chest query replaced its handler or invalidated an issued optional: " + label + " " + side);
            issued.add(first);
        }
        return issued;
    }

    private static void assertUnboundChestQueries(GameTestHelper helper, StorageChestBlockEntity chest,
            List<LazyOptional<IItemHandler>> issued) {
        helper.assertTrue(chest.sharedOwner() == null, "Negative fixture still has a shared owner");
        List<Direction> sides = sides();
        for (int index = 0; index < sides.size(); index++) {
            helper.assertTrue(!chest.getCapability(ForgeCapabilities.ITEM_HANDLER, sides.get(index)).isPresent()
                            && !issued.get(index).isPresent(),
                    "An ownerless shared chest reused its cached inventory: " + sides.get(index));
        }
    }

    @GameTest(templateNamespace = "stardewcraft_b008", template = "machine_test", timeoutTicks = 100)
    public static void separatelyRegisteredHeavyTapperSharesOneTreeJobInAllFourFacings(GameTestHelper helper) {
        FakePlayer player = prepare(helper);
        var level = helper.getLevel();
        var registry = PrefabTreeRegistry.get(level);
        var tree = WildTrees.OAK;
        var block = (HeavyTapperBlock) GingerIslandBlocks.get("ginger_heavy_tapper");
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos root = origin.relative(facing);
            BlockPos log = root.above();
            level.setBlock(root, tree.modernRoot().get().defaultBlockState(), 2);
            level.setBlock(log, tree.modernLog().get().defaultBlockState(), 2);
            registry.register(root, tree.id(), 1, Set.of(root, log));
            try {
                Direction face = facing.getOpposite();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(root).add(face.getStepX() * .5, 0, face.getStepZ() * .5),
                                face, root, false)));
                helper.assertTrue(((BlockItem) context.getItemInHand().getItem()).place(context).consumesAction(),
                        "Heavy tapper real tree placement failed: " + facing);
                BlockEntity owner = level.getBlockEntity(origin);
                helper.assertTrue(owner instanceof TapperBlockEntity, "Heavy tapper has no real tree job: " + facing);
                ((TapperBlockEntity) owner).ensureCycleStarted(level.getBlockState(origin));
                helper.assertTrue(((TapperBlockEntity) owner).hasProduct() && ((TapperBlockEntity) owner).isProductionSiteValid(),
                        "Heavy tapper fixture must retain its real, valid tree production: " + facing);
                BlockPos upper = origin.above();
                BlockState upperState = level.getBlockState(upper);
                helper.assertTrue(upperState.is(block) && upperState.getValue(HeavyTapperBlock.UPPER)
                        && upperState.getValue(HeavyTapperBlock.FACING) == facing, "Heavy tapper upper part is missing: " + facing);
                BlockEntity bridge = level.getBlockEntity(upper);
                helper.assertTrue(bridge instanceof PortMachineExtensions.ExtensionBlockEntity
                        && !(bridge instanceof UtilityAutomationAccess) && !(bridge instanceof Container)
                        && !(bridge instanceof TimedProductionBlockEntity), "Heavy tapper upper part acquired a second job: " + facing);
                helper.assertTrue(PortMachineExtensions.createExtension(origin, level.getBlockState(origin)) == null,
                        "Heavy tapper bridge factory accepted its main part: " + facing);
                helper.assertTrue(block.getTicker(level, upperState, PortMachineExtensions.TYPE.get()) == null
                        && block.getTicker(level, upperState, owner.getType()) == null,
                        "Heavy tapper upper part acquired a production/client ticker: " + facing);
                helper.assertTrue(com.stardew.craft.block.utility.TapperBlock.attachedTappers(level, Set.of(root, log)).equals(Set.of(origin)),
                        "Heavy tapper upper part counted as another tree attachment: " + facing);
                List<LazyOptional<IItemHandler>> issued = new ArrayList<>();
                for (Direction side : sides()) {
                    var mainOptional = owner.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                    var extensionOptional = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
                    var handler = extensionOptional.resolve().orElse(null);
                    helper.assertTrue(mainOptional.isPresent() && handler != null
                            && handler == mainOptional.resolve().orElse(null)
                            && handler == ((TapperBlockEntity) owner).getAutomationItemHandler(),
                            "Heavy tapper main/upper must expose the exact same tree-job handler: " + facing + " " + side);
                    issued.add(extensionOptional);
                }
                PortCapabilities.invalidateCapabilities(level, origin);
                helper.assertTrue(issued.stream().noneMatch(LazyOptional::isPresent), "Heavy tapper owner invalidation left stale upper handlers");
                var fresh = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                helper.assertTrue(fresh.isPresent(), "Heavy tapper upper cannot re-query its owner");
                PortCapabilities.invalidateCapabilities(level, upper);
                helper.assertTrue(!fresh.isPresent() && owner.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                        "Heavy tapper upper invalidation also invalidated its owner");
                fresh = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                bridge.onChunkUnloaded();
                helper.assertTrue(!fresh.isPresent() && !bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()
                        && owner.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                        "Unloaded heavy tapper upper stayed queryable or invalidated its loaded owner");
                bridge.reviveCaps();
                fresh = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                helper.assertTrue(fresh.isPresent(), "Revived heavy tapper upper cannot re-query its owner");
                level.removeBlock(origin, false);
                helper.assertTrue(!fresh.isPresent() && !bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()
                        && level.isEmptyBlock(upper), "Heavy tapper owner removal left a usable upper job");
                level.setBlock(upper, upperState, 2);
                var orphan = level.getBlockEntity(upper);
                helper.assertTrue(orphan instanceof PortMachineExtensions.ExtensionBlockEntity
                        && !orphan.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                        "Orphan heavy tapper upper invented an inventory");
            } finally {
                level.removeBlock(origin, false);
                level.removeBlock(origin.above(), false);
                var instance = registry.getByRoot(root);
                if (instance != null) registry.unregister(instance);
                level.removeBlock(root, false);
                level.removeBlock(log, false);
            }
        }
        helper.succeed();
    }
}
