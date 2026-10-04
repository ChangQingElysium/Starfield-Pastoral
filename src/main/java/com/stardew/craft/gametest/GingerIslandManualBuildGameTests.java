package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.PortGameTests;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.TapperBlock;
import com.stardew.craft.blockentity.TapperBlockEntity;
import com.stardew.craft.blockentity.TimedProductionBlockEntity;
import com.stardew.craft.blockentity.UtilityAutomationAccess;
import com.stardew.craft.port.PortMachineExtensions;
import com.stardew.craft.gingerisland.GingerIslandAssets;
import com.stardew.craft.gingerisland.GingerIslandBlocks;
import com.stardew.craft.gingerisland.GingerIslandSurfaceBlock;
import com.stardew.craft.gingerisland.GingerIslandStateDecorBlock;
import com.stardew.craft.gingerisland.HeavyTapperBlock;
import com.stardew.craft.gingerisland.VolcanoFloorSwitchBlock;
import com.stardew.craft.tree.WildTrees;
import com.stardew.craft.tree.prefab.PrefabTreeRegistry;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Physical building contracts, complementing the registered-item and gameplay-specific tests. */
@GameTestHolder("stardewcraft_ginger_placement")
@PrefixGameTestTemplate(false)
public final class GingerIslandManualBuildGameTests {
    private static final String NS = "stardewcraft_ginger_placement";

    private GingerIslandManualBuildGameTests() {}

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void all133ModelPropsHaveCoherentFootprintsCollisionAndOutsideStandingSpace(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = PortGameTests.makeMockPlayer(h, GameType.CREATIVE);
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!(GingerIslandBlocks.get(asset.id()) instanceof MapDecorStaticBlock block)) continue;
            owners++;
            BlockPos main = bottom.above(block.placementAnchorYOffset());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                Set<BlockPos> cells = block.placementPositions(main, facing);
                for (BlockPos cell : cells) assertInside(h, cell);
                level.setBlock(main, state, 2 | 16);
                h.assertTrue(block.placeExtensions(level, main, state), "Model footprint cannot be installed: " + asset.id() + "/" + facing);
                VoxelShape whole = state.getShape(level, main);
                assertFinitePositive(h, whole, asset.id());
                VoxelShape collision = state.getCollisionShape(level, main);
                h.assertTrue(collision.isEmpty() == asset.passable(), "Collision policy differs from the catalog: " + asset.id());
                var ownerDrops = Block.getDrops(level.getBlockState(main), level, main, level.getBlockEntity(main));
                var itemOwner = GingerIslandBlocks.get(asset.catalog_owner() == null || asset.catalog_owner().isBlank()
                        ? asset.id() : asset.catalog_owner()).asItem();
                h.assertTrue(ownerDrops.size() == 1 && com.stardew.craft.port.PortJava.getFirst(ownerDrops).is(itemOwner)
                        && com.stardew.craft.port.PortJava.getFirst(ownerDrops).getCount() == 1, "Model owner has no single packed item drop: " + asset.id());
                for (BlockPos cell : cells) {
                    var part = level.getBlockState(cell);
                    h.assertTrue(part.is(block) && main.equals(block.findMainPos(level, cell, part)),
                            "Occupied cell is not owned by its model: " + asset.id() + "/" + cell);
                    VoxelShape translated = part.getShape(level, cell).move(cell.getX() - main.getX(),
                            cell.getY() - main.getY(), cell.getZ() - main.getZ());
                    h.assertTrue(!Shapes.joinIsNotEmpty(whole, translated, BooleanOp.NOT_SAME),
                            "MAIN and EXT disagree about world geometry: " + asset.id() + "/" + facing + "/" + cell);
                    h.assertTrue(part.getCollisionShape(level, cell).isEmpty() == asset.passable(),
                            "EXT has another collision policy: " + asset.id() + "/" + cell);
                    if (!cell.equals(main)) h.assertTrue(Block.getDrops(part, level, cell, level.getBlockEntity(cell)).isEmpty(),
                            "Extension duplicates its owner's item: " + asset.id() + "/" + cell);
                }
                // Verify the real coarse volumes reserve their cells. Selection may span
                // unused interior air; that interior must not become a solid bounding box.
                for (AABB box : whole.toAabbs()) {
                    for (int x = (int) Math.floor(box.minX + 1E-7); x < Math.ceil(box.maxX - 1E-7); x++) {
                        for (int y = (int) Math.floor(box.minY + 1E-7); y < Math.ceil(box.maxY - 1E-7); y++) {
                            for (int z = (int) Math.floor(box.minZ + 1E-7); z < Math.ceil(box.maxZ - 1E-7); z++) {
                                h.assertTrue(cells.contains(main.offset(x, y, z)),
                                        "Collision occupies an unreserved cell: " + asset.id() + "/" + facing);
                            }
                        }
                    }
                }
                AABB bounds = whole.bounds();
                double standX = main.getX() + bounds.maxX + .301;
                double standZ = main.getZ() + (bounds.minZ + bounds.maxZ) / 2;
                AABB body = new AABB(standX - .3, bottom.getY() + .001, standZ - .3,
                        standX + .3, bottom.getY() + 1.801, standZ + .3);
                h.assertTrue(com.stardew.craft.port.PortGameTests.getBounds(h).contains(body.getCenter()) && level.noCollision(body),
                        "Normal player cannot stand next to the model: " + asset.id() + "/" + facing);
                // Exercise MAIN and EXT removal in different directions rather than
                // merely deleting a whole coordinate range around the object.
                BlockPos broken = facing == Direction.NORTH || facing == Direction.EAST ? main
                        : cells.stream().filter(cell -> !cell.equals(main)).findFirst().orElse(main);
                MapDecorStaticBlock.runWithDropsSuppressed(() -> {
                    block.playerWillDestroy(level, broken, level.getBlockState(broken), player);
                    level.removeBlock(broken, false);
                });
                for (BlockPos cell : cells) {
                    h.assertTrue(level.isEmptyBlock(cell) && level.getBlockEntity(cell) == null,
                            "Manual removal left a part or BE: " + asset.id() + "/" + facing + "/" + cell);
                }
                h.assertTrue(level.getBlockState(bottom.below()).is(Blocks.STONE), "Removal damaged the building foundation");
            }
        }
        h.assertTrue(owners == 133, "Model-prop audit omitted registered owners: " + owners);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void all12SingleCellBuildersUseRegisteredHandItemsAndRemoveCleanly(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(pos.north(3)));
        int owners = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!List.of("cube", "volcano_floor", "caldera_floor", "cooled_lava", "volcano_switch").contains(asset.kind())) continue;
            owners++;
            Block block = GingerIslandBlocks.get(asset.id());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                player.setYRot(direction.toYRot());
                ItemStack hand = new ItemStack(block, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.getItem().useOn(groundContext(player, pos)).consumesAction(),
                        "Single-cell hand item rejects placement: " + asset.id() + "/" + direction);
                var placed = level.getBlockState(pos);
                h.assertTrue(placed.is(block) && hand.getCount() == 1, "Single-cell placement lost its item or target: " + asset.id());
                assertFinitePositive(h, placed.getShape(level, pos), asset.id());
                h.assertTrue(!placed.getCollisionShape(level, pos).isEmpty(), "Solid builder has no player collision: " + asset.id());
                AABB body = new AABB(pos.getX() + 1.001, pos.getY() + .001, pos.getZ() + .2,
                        pos.getX() + 1.601, pos.getY() + 1.801, pos.getZ() + .8);
                h.assertTrue(level.noCollision(body), "Single-cell shape leaks into an adjacent player's standing space: " + asset.id());
                block.playerWillDestroy(level, pos, placed, player);
                level.removeBlock(pos, false);
                h.assertTrue(level.isEmptyBlock(pos) && level.getBlockEntity(pos) == null
                        && level.getBlockState(pos.below()).is(Blocks.STONE), "Single-cell removal left state or damaged the support");
            }
        }
        h.assertTrue(owners == 12, "Single-cell audit omitted a builder: " + owners);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void forgedCalderaTextureCannotSurviveTheSecondBlockItemStateApplication(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(pos.north(3)));
        var block = (GingerIslandSurfaceBlock) GingerIslandBlocks.get("ginger_caldera_floor");
        ItemStack hand = new ItemStack(block, 2);
        PortItemData.set(hand, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("variant", "5")));
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        h.assertTrue(hand.getItem().useOn(groundContext(player, pos)).consumesAction(), "Forged floor item cannot use normal hand placement");
        var placed = level.getBlockState(pos);
        h.assertTrue(placed.is(block) && placed.getValue(GingerIslandSurfaceBlock.VARIANT) == 0,
                "BlockItem's second state application restored a caldera texture without an authored model");
        h.assertTrue(hand.getCount() == 1, "Floor normalization changed item consumption");
        level.removeBlock(pos, false);
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void forgedPressedSwitchStartsRaisedButRealGroundedStepsStillLatchIt(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(40, 4, 40));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Switch construction"));
        player.setGameMode(GameType.SURVIVAL);
        var block = (VolcanoFloorSwitchBlock) GingerIslandBlocks.get("ginger_volcano_floor_switch");
        try {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                player.setPos(Vec3.atBottomCenterOf(pos.north(3)));
                player.setYRot(facing.toYRot());
                ItemStack hand = new ItemStack(block, 2);
                PortItemData.set(hand, DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("pressed", "true")));
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                h.assertTrue(hand.getItem().useOn(groundContext(player, pos)).consumesAction() && hand.getCount() == 1,
                        "Switch cannot be hand placed with a forged process component");
                var placed = level.getBlockState(pos);
                h.assertTrue(placed.is(block) && !placed.getValue(VolcanoFloorSwitchBlock.PRESSED)
                                && placed.getCollisionShape(level, pos).max(Direction.Axis.Y) == 4.0 / 16,
                        "Forged component latched the switch before the player stepped on it");
                player.setPos(Vec3.atBottomCenterOf(pos).add(0, 4.0 / 16, 0));
                player.setOnGround(true);
                block.stepOn(level, pos, placed, player);
                var pressed = level.getBlockState(pos);
                h.assertTrue(pressed.getValue(VolcanoFloorSwitchBlock.PRESSED)
                                && pressed.getCollisionShape(level, pos).max(Direction.Axis.Y) == 2.0 / 16,
                        "Placement normalization disabled the real grounded switch process");
                h.assertTrue(ItemStack.isSameItemSameTags(new ItemStack(block), block.getCloneItemStack(level, pos, pressed)),
                        "Picking a latched switch encodes its gameplay state");
                level.removeBlock(pos, false);
            }
        } finally {
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void heavyTapperRealTreeUsePlacesOneJobAndRejectsOccupiedUpperCells(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos root = h.absolutePos(new BlockPos(40, 4, 40));
        BlockPos support = root.above();
        var def = WildTrees.OAK;
        var registry = PrefabTreeRegistry.get(level);
        var heavy = (HeavyTapperBlock) GingerIslandBlocks.get("ginger_heavy_tapper");
        Player player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        level.setBlock(root, def.modernRoot().get().defaultBlockState(), 2);
        level.setBlock(support, def.modernLog().get().defaultBlockState(), 2);
        registry.register(root, def.id(), 1, Set.of(root, support));
        try {
            for (Direction face : Direction.Plane.HORIZONTAL) {
                player.setPos(Vec3.atBottomCenterOf(root.relative(face, 3)));
                ItemStack hand = new ItemStack(heavy, 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, hand);
                var hit = new BlockHitResult(Vec3.atCenterOf(support)
                        .add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)), face, support, false);
                h.assertTrue(hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                        "Registered heavy TapperItem cannot attach to a real tree: " + face);
                Set<BlockPos> attached = TapperBlock.attachedTappers(level, Set.of(root, support));
                h.assertTrue(attached.size() == 1 && hand.getCount() == 1, "Heavy tapper creates more than one job or consumes incorrectly");
                BlockPos main = attached.iterator().next();
                var state = level.getBlockState(main);
                var upperState = level.getBlockState(main.above());
                BlockEntity owner = level.getBlockEntity(main);
                BlockEntity bridge = level.getBlockEntity(main.above());
                h.assertTrue(state.is(heavy) && !state.getValue(HeavyTapperBlock.UPPER)
                        && state.getValue(TapperBlock.FACING) == face.getOpposite()
                        && upperState.is(heavy) && upperState.getValue(HeavyTapperBlock.UPPER)
                        && upperState.getValue(TapperBlock.FACING) == face.getOpposite()
                        && owner instanceof TapperBlockEntity
                        && bridge instanceof PortMachineExtensions.ExtensionBlockEntity,
                        "Heavy attachment did not create exactly one lower job and one upper cell");
                // Forge needs an upper-position capability bridge, never another inventory or production job.
                h.assertTrue(!(bridge instanceof UtilityAutomationAccess) && !(bridge instanceof Container)
                        && !(bridge instanceof TimedProductionBlockEntity)
                        && heavy.getTicker(level, upperState, PortMachineExtensions.TYPE.get()) == null
                        && heavy.getTicker(level, upperState, owner.getType()) == null,
                        "Heavy upper capability bridge acquired inventory or a production ticker");
                var upperHandler = bridge.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
                h.assertTrue(upperHandler != null
                        && upperHandler == owner.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null)
                        && upperHandler == ((TapperBlockEntity) owner).getAutomationItemHandler(),
                        "Heavy upper capability bridge does not expose the sole lower tree job");
                assertFinitePositive(h, state.getShape(level, main), "ginger_heavy_tapper");
                h.assertTrue(!state.getCollisionShape(level, main).isEmpty(), "Heavy tapper is missing collision");
                var mainDrops = Block.getDrops(state, level, main, level.getBlockEntity(main));
                var upperDrops = Block.getDrops(level.getBlockState(main.above()), level, main.above(), null);
                h.assertTrue(mainDrops.size() == 1 && com.stardew.craft.port.PortJava.getFirst(mainDrops).is(heavy.asItem()) && upperDrops.isEmpty(),
                        "Heavy upper cell duplicates the machine drop");
                level.removeBlock(main, false);
                h.assertTrue(level.isEmptyBlock(main) && level.isEmptyBlock(main.above())
                        && level.getBlockEntity(main) == null && TapperBlock.attachedTappers(level, Set.of(root, support)).isEmpty(),
                        "Heavy tapper removal leaves another part/job attached to the tree");
            }
            // Block every candidate's upper cell. The item may choose another
            // face, so blocking only the clicked face would be an invalid test.
            for (BlockPos log : List.of(root, support)) for (Direction face : Direction.Plane.HORIZONTAL) {
                level.setBlock(log.relative(face).above(), Blocks.STONE.defaultBlockState(), 18);
            }
            player.setPos(Vec3.atBottomCenterOf(root.south(3)));
            ItemStack blocked = new ItemStack(heavy, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, blocked);
            var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0, .5), Direction.SOUTH, support, false);
            h.assertTrue(!blocked.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                            && blocked.getCount() == 2 && TapperBlock.attachedTappers(level, Set.of(root, support)).isEmpty(),
                    "Heavy tapper overwrote an upper obstruction or consumed a rejected item");
        } finally {
            registry.unregister(registry.getByRoot(root));
            player.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 100)
    public static void breakingHeavyTapperUpperRemovesOneJobAndDropsOneMachine(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos root = h.absolutePos(new BlockPos(40, 4, 40));
        BlockPos support = root.above();
        var def = WildTrees.OAK;
        var registry = PrefabTreeRegistry.get(level);
        var heavy = (HeavyTapperBlock) GingerIslandBlocks.get("ginger_heavy_tapper");
        Player player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(root.south(3)));
        level.setBlock(root, def.modernRoot().get().defaultBlockState(), 2);
        level.setBlock(support, def.modernLog().get().defaultBlockState(), 2);
        registry.register(root, def.id(), 1, Set.of(root, support));
        ItemStack hand = new ItemStack(heavy);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0, .5), Direction.SOUTH, support, false);
        h.assertTrue(hand.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                "Upper-removal scenario cannot hand-place its heavy tapper");
        BlockPos main = TapperBlock.attachedTappers(level, Set.of(root, support)).iterator().next();
        level.destroyBlock(main.above(), true, player);
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(level.isEmptyBlock(main) && level.isEmptyBlock(main.above())
                        && level.getBlockEntity(main) == null && TapperBlock.attachedTappers(level, Set.of(root, support)).isEmpty(),
                        "Breaking upper leaves a lower tapper or production job");
                int machines = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new AABB(main).inflate(4)).stream().filter(entity -> entity.getItem().is(heavy.asItem()))
                        .mapToInt(entity -> entity.getItem().getCount()).sum();
                h.assertTrue(machines == 1, "Breaking upper must drop exactly one machine: " + machines);
                h.assertTrue(level.getBlockState(root).is(def.modernRoot().get())
                        && level.getBlockState(support).is(def.modernLog().get()), "Machine removal damaged its tree");
                h.succeed();
            } finally {
                registry.unregister(registry.getByRoot(root));
                player.getInventory().clearContent();
            }
        });
    }

    private static void assertFinitePositive(GameTestHelper h, VoxelShape shape, String id) {
        h.assertTrue(!shape.isEmpty(), "Selection shape is missing: " + id);
        for (AABB box : shape.toAabbs()) {
            h.assertTrue(Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                    && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                    && box.minX < box.maxX && box.minY < box.maxY && box.minZ < box.maxZ,
                    "Nonfinite or collapsed selection volume: " + id);
        }
    }

    @GameTest(templateNamespace = NS, template = "large_empty", timeoutTicks = 200)
    public static void all133ModelHandItemsIgnoreInternalComponentsAndPreserveRealAppearance(GameTestHelper h) {
        clearWithFoundation(h);
        var level = h.getLevel();
        BlockPos bottom = h.absolutePos(new BlockPos(40, 4, 40));
        Player player = PortGameTests.makeMockPlayer(h, GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(bottom.south(25)));
        player.setYRot(0);
        int checked = 0;
        for (var asset : GingerIslandAssets.blocks()) {
            if (!(GingerIslandBlocks.get(asset.id()) instanceof MapDecorStaticBlock block)) continue;
            var properties = new LinkedHashMap<>(Map.of("part", "extension", "facing", "south",
                    "waterlogged", "true", "lit", "true"));
            if (block instanceof GingerIslandStateDecorBlock decor) properties.put(decor.visualStateProperty(),
                    com.stardew.craft.port.PortJava.getLast(decor.visualStateValues()));
            ItemStack hand = new ItemStack(block, 2);
            var original = new BlockItemStateProperties(properties);
            PortItemData.set(hand, DataComponents.BLOCK_STATE, original);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            BlockPos main = bottom.above(block.placementAnchorYOffset());
            h.assertTrue(hand.getItem().useOn(groundContext(player, bottom)).consumesAction(),
                    "Internal component prevents a model's real hand placement: " + asset.id());
            var placed = level.getBlockState(main);
            h.assertTrue(placed.is(block) && placed.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN
                            && placed.getValue(MapDecorStaticBlock.FACING) == Direction.NORTH,
                    "Held part/facing overwrote the footprint that was validated: " + asset.id());
            h.assertTrue(hand.getCount() == 1 && original.equals(PortItemData.get(hand, DataComponents.BLOCK_STATE)),
                    "Sanitizing placement mutated the remaining inventory stack: " + asset.id());
            if (block instanceof GingerIslandStateDecorBlock decor) {
                var expected = decor.itemState(hand).apply(decor.defaultBlockState());
                h.assertTrue(decor.modelForState(placed).equals(decor.modelForState(expected)),
                        "Internal-state filtering discarded a real appearance or advanced progress: " + asset.id());
            }
            var cells = block.placementPositions(main, Direction.NORTH);
            for (BlockPos cell : cells) h.assertTrue(level.getBlockState(cell).is(block)
                            && main.equals(block.findMainPos(level, cell, level.getBlockState(cell))),
                    "Component application installed an orphan part: " + asset.id());
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.removeBlock(main, false));
            for (BlockPos cell : cells) h.assertTrue(level.isEmptyBlock(cell), "Component cleanup left a part: " + asset.id());
            checked++;
        }
        player.getInventory().clearContent();
        h.assertTrue(checked == 133, "Component audit skipped a model owner: " + checked);
        h.succeed();
    }

    private static void assertInside(GameTestHelper h, BlockPos pos) {
        h.assertTrue(com.stardew.craft.port.PortGameTests.getBounds(h).contains(Vec3.atCenterOf(pos)), "Manual-build test escaped the real structure: " + pos);
    }

    private static UseOnContext groundContext(Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, .5, 0), Direction.UP, pos.below(), false));
    }

    private static void clearWithFoundation(GameTestHelper h) {
        var level = h.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(10, 3, 10, 69, 23, 69)) {
            BlockPos pos = h.absolutePos(relative);
            assertInside(h, pos);
            level.setBlock(pos, relative.getY() == 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
    }
}
