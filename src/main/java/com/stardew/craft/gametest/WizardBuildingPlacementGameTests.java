package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.block.utility.WizardBuildingBlock;
import com.stardew.craft.block.utility.WizardBuildingKind;
import com.stardew.craft.blockentity.WizardBuildingBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.item.WizardBuildingItem;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("stardewcraft_wizard_placement")
@PrefixGameTestTemplate(false)
public final class WizardBuildingPlacementGameTests {
    @GameTest(templateNamespace="stardewcraft_wizard_placement", template="empty")
    public static void hutProfileOverridesAutomaticBoundsWithoutClippingAboveGroundGeometry(GameTestHelper h) {
        var kind = WizardBuildingKind.JUNIMO_HUT;
        var model = kind.model().toString();
        var geometry = ModelVoxelShapeCache.geoBoundsFromModelId(model);
        var collision = ModelVoxelShapeCache.shapeFromModelId(kind.shapeModelId()).bounds();
        h.assertTrue(ModelVoxelShapeCache.hasCollisionProfile(kind.shapeModelId())
                        && collision.equals(ModelVoxelShapeCache.shapeFromModelId(model).bounds()),
                "Automatic AABB variant ignores the authored collision profile");
        // Rotated root tips dip below the floor; they must not reserve foundation blocks.
        h.assertTrue(collision.minY == 0 && geometry != null
                        && collision.minX <= (geometry.minX() + 8) / 16
                        && collision.minZ <= (geometry.minZ() + 8) / 16
                        && collision.maxX >= (geometry.maxX() + 8) / 16
                        && collision.maxZ >= (geometry.maxZ() + 8) / 16
                        && collision.maxY >= geometry.maxY() / 16,
                "Hut collision includes underground cells or clips visible geometry");
        h.succeed();
    }

    private static UseOnContext context(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false));
    }

    private static ItemStack equip(ServerPlayer player, WizardBuildingBlock block, Direction facing) {
        var stack = new ItemStack(block);
        WizardBuildingItem.bindTo(stack, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setYRot(facing.getOpposite().toYRot());
        return stack;
    }

    private static List<BlockPos> occupiedCells(BlockState state, Level level, BlockPos pos) {
        var bounds = state.getShape(level, pos).bounds();
        var cells = new ArrayList<BlockPos>();
        for (int x = (int) Math.floor(bounds.minX + 1.0E-7); x < Math.ceil(bounds.maxX - 1.0E-7); x++)
            for (int y = (int) Math.floor(bounds.minY + 1.0E-7); y < Math.ceil(bounds.maxY - 1.0E-7); y++)
                for (int z = (int) Math.floor(bounds.minZ + 1.0E-7); z < Math.ceil(bounds.maxZ - 1.0E-7); z++)
                    cells.add(pos.offset(x, y, z));
        return cells;
    }

    private static void clearAboveGround(Level level, BlockPos pos) {
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            for (var cell : BlockPos.betweenClosed(pos.offset(-3, 0, -3), pos.offset(3, 5, 3)))
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 18);
        });
    }

    @GameTest(templateNamespace="stardewcraft_wizard_placement", template="empty", timeoutTicks=200)
    public static void wizardBuildingsPlaceOnSolidGroundInEveryDirection(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = Level.class.getDeclaredField("dimension"); dimension.setAccessible(true);
        var previousDimension = dimension.get(level);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "WizardPlacement"));
        var farms = FarmInstanceRegistry.get(level.getServer());
        var farm = farms.createFarm(player.getUUID(), "WizardPlacement", "WizardPlacement", FarmType.STANDARD);
        // Reused farm slots can retain legacy livestock around the (12, 2, 12) fixture.
        var pos = farm.getFarmBoundsMin().offset(24, 2, 24);
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        try {
            dimension.set(level, ModDimensions.STARDEW_VALLEY);
            player.setPos(Vec3.atBottomCenterOf(pos.north(5)));
            for (var cell : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 5, 3))) {
                previous.put(cell.immutable(), level.getBlockState(cell));
                level.setBlock(cell, cell.getY() < pos.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            for (var registered : List.of(ModBlocks.JUNIMO_HUT, ModBlocks.EARTH_OBELISK,
                    ModBlocks.WATER_OBELISK, ModBlocks.DESERT_OBELISK, ModBlocks.ISLAND_OBELISK, ModBlocks.GOLD_CLOCK)) {
                var block = (WizardBuildingBlock) registered.get();
                for (var facing : Direction.Plane.HORIZONTAL) {
                    clearAboveGround(level, pos);
                    var stack = equip(player, block, facing);
                    h.assertTrue(stack.getItem().useOn(context(player, pos)).consumesAction(),
                            "Building cannot be placed on clear solid ground: " + block.kind() + " " + facing
                                    + "; bounds=" + block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing).getShape(level, pos).bounds());
                    var state = level.getBlockState(pos);
                    h.assertTrue(state.is(block) && state.getValue(MapDecorStaticBlock.FACING) == facing
                                    && state.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN,
                            "Wrong building anchor or facing");
                    h.assertTrue(level.getBlockEntity(pos) instanceof WizardBuildingBlockEntity building
                                    && player.getUUID().equals(building.owner()), "Placed building lost ownership");
                    h.assertTrue(stack.isEmpty(), "Successful placement did not consume the building item");
                    for (var occupied : occupiedCells(state, level, pos)) {
                        h.assertTrue(occupied.getY() >= pos.getY() && level.getBlockState(occupied).is(block)
                                        && pos.equals(block.findMainPos(level, occupied, level.getBlockState(occupied))),
                                "Reserved cell below ground, missing, or orphaned: " + occupied);
                    }
                    for (var cell : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, -1, 3)))
                        h.assertTrue(level.getBlockState(cell).is(Blocks.STONE), "Building replaced the ground");
                    clearAboveGround(level, pos);
                    h.assertTrue(!farm.hasGoldClock(), "Removed clock left the farm marked as built");
                }
            }
        } finally {
            MapDecorStaticBlock.runWithDropsSuppressed(() -> previous.forEach((cell, state) -> level.setBlock(cell, state, 18)));
            player.getInventory().clearContent(); farms.deleteFarm(player.getUUID());
            PlayerDataManager.get().removePlayerData(player.getUUID()); dimension.set(level, previousDimension);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_wizard_placement", template="empty", timeoutTicks=200)
    public static void hutStillRejectsObstructionsOtherOwnersAndFarmEdges(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var dimension = Level.class.getDeclaredField("dimension"); dimension.setAccessible(true);
        var previousDimension = dimension.get(level);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "HutPlacementGates"));
        var farms = FarmInstanceRegistry.get(level.getServer());
        var farm = farms.createFarm(player.getUUID(), "HutPlacementGates", "HutPlacementGates", FarmType.STANDARD);
        // Keep rejection causes isolated from the same legacy livestock fixture.
        var pos = farm.getFarmBoundsMin().offset(24, 2, 24);
        var edge = new BlockPos(farm.getFarmBoundsMin().getX(), pos.getY(), pos.getZ());
        var block = (WizardBuildingBlock) ModBlocks.JUNIMO_HUT.get();
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        try {
            dimension.set(level, ModDimensions.STARDEW_VALLEY);
            player.setPos(Vec3.atBottomCenterOf(pos.north(5)));
            for (var anchor : List.of(pos, edge))
                for (var cell : BlockPos.betweenClosed(anchor.offset(-3, -1, -3), anchor.offset(3, 5, 3))) {
                    previous.putIfAbsent(cell.immutable(), level.getBlockState(cell));
                    level.setBlock(cell, cell.getY() < pos.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
                }
            for (var facing : Direction.Plane.HORIZONTAL) {
                var stack = equip(player, block, facing);
                var obstruction = occupiedCells(block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing), level, pos).stream()
                        .filter(cell -> cell.getY() == pos.getY() && !cell.equals(pos)).findFirst().orElseThrow();
                level.setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 18);
                h.assertTrue(!stack.getItem().useOn(context(player, pos)).consumesAction()
                                && stack.getCount() == 1 && level.getBlockState(pos).isAir()
                                && level.getBlockState(obstruction).is(Blocks.CHEST),
                        "Obstructed hut consumed its item or overwrote a chest");
                level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 18);
            }
            var foreign = new ItemStack(block);
            WizardBuildingItem.bindTo(foreign, UUID.randomUUID(), "Other farmer");
            player.setItemInHand(InteractionHand.MAIN_HAND, foreign);
            h.assertTrue(!foreign.getItem().useOn(context(player, pos)).consumesAction()
                    && foreign.getCount() == 1 && level.getBlockState(pos).isAir(), "Foreign building was accepted");
            var stack = equip(player, block, Direction.NORTH);
            h.assertTrue(!stack.getItem().useOn(context(player, edge)).consumesAction()
                    && stack.getCount() == 1 && level.getBlockState(edge).isAir(), "Hut footprint crossed the farm boundary");
        } finally {
            MapDecorStaticBlock.runWithDropsSuppressed(() -> previous.forEach((cell, state) -> level.setBlock(cell, state, 18)));
            player.getInventory().clearContent(); farms.deleteFarm(player.getUUID());
            PlayerDataManager.get().removePlayerData(player.getUUID()); dimension.set(level, previousDimension);
        }
        h.succeed();
    }
}
