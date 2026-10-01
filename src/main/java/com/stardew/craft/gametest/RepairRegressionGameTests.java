package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.greenhouse.GreenhouseBuildings;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.qi.MrQiQuestRules;
import com.stardew.craft.world.interaction.MapInteractionEvents;
import com.stardew.craft.world.interaction.MapInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("stardewcraft_repair_regressions")
@PrefixGameTestTemplate(false)
public final class RepairRegressionGameTests {
    @GameTest(templateNamespace = "stardewcraft_repair_regressions", template = "empty", timeoutTicks = 200)
    public static void greenhousePortalIsWrittenInsideProtectedClaimAndRecovers(GameTestHelper h) {
        var level = h.getLevel();
        var data = BuildingWorldData.get(level.getServer());
        var family = PrefabDefinitions.get(GreenhouseBuildings.FAMILY);
        var tier = family.tier(1);
        var template = PrefabDefinitions.template(level, tier);
        BlockPos anchor = h.absolutePos(new BlockPos(3, 2, 3));
        var farms = com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer());
        for (var facing : Direction.Plane.HORIZONTAL) {
            var owner = UUID.randomUUID();
            var farm = farms.createFarm(owner, "PortalCheck", "PortalCheck", com.stardew.craft.farm.FarmType.STANDARD);
            var rotation = PrefabDefinitions.rotation(facing);
            var claim = PrefabDefinitions.transform(family.reservation(), anchor, rotation);
            var record = new BuildingRecord(UUID.randomUUID(), farm.getInstanceId(), farm.getSlotIndex(), GreenhouseBuildings.FAMILY,
                    BuildingRecord.Mode.PREFAB, level.dimension().location(), anchor,
                    PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, rotation), facing, claim,
                    BuildingRecord.Phase.READY, 1, BuildingRecord.Residence.VALID, 0, "");
            var portal = GreenhouseBuildings.portal(record);
            var door = PrefabDefinitions.world(new BlockPos(7, 1, 11), tier.anchor(), anchor, rotation);
            // Also load the neighbor border used by deferred portal placement.
            for (int x = (portal.getX() - 2) >> 4; x <= (portal.getX() + 2) >> 4; x++)
                for (int z = (portal.getZ() - 2) >> 4; z <= (portal.getZ() + 2) >> 4; z++) level.getChunk(x, z);
            BuildingProtection.internal(() -> {
                for (int y = 0; y < 2; y++) level.setBlock(portal.above(y), Blocks.AIR.defaultBlockState(), 18);
                for (var cell : template.cells()) {
                    if (cell.pos().equals(new BlockPos(7, 1, 11)) || cell.pos().equals(new BlockPos(7, 2, 11)))
                        level.setBlock(PrefabDefinitions.world(cell.pos(), tier.anchor(), anchor, rotation),
                                cell.state().rotate(rotation), 18);
                }
            });
            try {
                h.assertTrue(data.importCompletedPrefab("portal-test:" + record.id(), record) == BuildingWorldData.Result.SUCCESS,
                        "Could not register ready greenhouse");
                h.assertTrue(BuildingProtection.protects(level, portal), "Test did not exercise protected entrance air");
                h.assertTrue(!level.setBlock(portal, Blocks.STONE.defaultBlockState(), 18), "Normal writes bypass greenhouse protection");
                GreenhouseBuildings.ensurePortal(level, record);
                assertPortal(h, portal);
                h.assertTrue(level.getBlockState(door).is(Blocks.OAK_DOOR), "Entrance replaced the door");
                h.assertTrue(!level.setBlock(portal, Blocks.AIR.defaultBlockState(), 18), "Portal is no longer protected");
                // Existing READY saves must recover a missing portal without rebuilding the greenhouse.
                BuildingProtection.internal(() -> level.setBlock(portal, Blocks.AIR.defaultBlockState(), 18));
                GreenhouseBuildings.ensurePortal(level, record);
                assertPortal(h, portal);
                // Legacy portals placed on the door must be removed despite the same protection.
                InteriorSubspaceManager.spawnGreenhouseOutdoorPortalAt(level, door);
                GreenhouseBuildings.ensurePortal(level, record);
                assertPortal(h, portal);
                h.assertTrue(level.getBlockState(door).is(Blocks.OAK_DOOR)
                        && level.getBlockState(door.above()).is(Blocks.OAK_DOOR), "Legacy entrance did not restore both door halves");
            } finally {
                InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(level, portal);
                InteriorSubspaceManager.removeGreenhouseOutdoorPortalAt(level, door);
                data.removeFarm(record.farmId());
                farms.deleteFarm(owner);
            }
        }
        h.succeed();
    }

    private static void assertPortal(GameTestHelper h, BlockPos portal) {
        for (int y = 0; y < 2; y++) {
            h.assertTrue(h.getLevel().getBlockState(portal.above(y)).is(ModBlocks.PORTAL_TRIGGER.get()), "Missing entrance block " + y);
            h.assertTrue(h.getLevel().getBlockEntity(portal.above(y)) instanceof PortalTriggerBlockEntity be
                    && "greenhouse_enter".equals(be.getTargetId()), "Missing greenhouse target " + y);
        }
    }

    @GameTest(templateNamespace = "stardewcraft_repair_regressions", template = "empty", timeoutTicks = 200)
    public static void railroadBoxAcceptsHeldShellOfEveryQualityExactlyOnce(GameTestHelper h) throws ReflectiveOperationException {
        // GameTestServer only creates its test level. Match the authored dimension for this synchronous fixture.
        var level = h.getLevel();
        var dimension = net.minecraft.world.level.Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        var previousDimension = dimension.get(level);
        dimension.set(level, ModDimensions.STARDEW_VALLEY);
        try {
        var pos = new BlockPos(28, 86, -209);
        for (int quality = 0; quality <= 3; quality++) {
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "QiShell" + quality));
            try {
                player.setPos(Vec3.atCenterOf(pos.relative(Direction.SOUTH)));
                var data = PlayerDataManager.getPlayerData(player);
                data.addMailFlag(MrQiQuestRules.TUNNEL_FLAG);
                var shell = new ItemStack(ModItems.VANILLA_CATEGORY_ITEMS.get("rainbow_shell").get(), 2);
                QualityHelper.setQuality(shell, quality);
                player.setItemInHand(InteractionHand.MAIN_HAND, shell);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
                h.assertTrue(MapInteractionService.acceptsHeldItems(player, pos), "Authored railroad box cannot accept a held item");
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
                var offhand = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.OFF_HAND, pos, hit);
                MapInteractionEvents.onRightClickBlock(offhand);
                h.assertTrue(!offhand.isCanceled() && shell.getCount() == 2, "Offhand dispatched delivery twice");
                var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
                MapInteractionEvents.onRightClickBlock(event);
                h.assertTrue(event.isCanceled() && shell.getCount() == 1, "Held shell was not consumed exactly once, quality=" + quality);
                h.assertTrue(data.hasMailFlag(MrQiQuestRules.RAILROAD_FLAG)
                        && data.getQuestManager().hasQuest(MrQiQuestRules.QUEST_MAYOR_FRIDGE), "Quest did not advance");
                MapInteractionEvents.onRightClickBlock(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
                h.assertTrue(shell.getCount() == 1, "Repeated click consumed another shell");
                h.assertTrue(QualityHelper.getQuality(shell) == quality, "Remaining shell lost its quality");
                var readingPos = new BlockPos(30, 37, -42);
                h.assertTrue(!MapInteractionService.acceptsHeldItems(player, readingPos), "Readable decoration steals held-item interaction");
                var reading = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, readingPos,
                        new BlockHitResult(Vec3.atCenterOf(readingPos), Direction.UP, readingPos, false));
                MapInteractionEvents.onRightClickBlock(reading);
                h.assertTrue(!reading.isCanceled(), "Held item still intercepted by a readable decoration");
            } finally {
                player.getInventory().clearContent();
                PlayerDataManager.get().removePlayerData(player.getUUID());
            }
        }
        } finally { dimension.set(level, previousDimension); }
        h.succeed();
    }
}
