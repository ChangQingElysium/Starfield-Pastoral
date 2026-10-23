package com.stardew.craft.festival;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.ParkedVehicleBlock;
import com.stardew.craft.blockentity.ParkedVehicleBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.desert.DesertBusService;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.festival.desert.DesertFestivalHandler;
import com.stardew.craft.network.payload.OpenDesertBusConfirmPayload;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.stardew.craft.port.PortBlockInteraction;

/** Opt-in: -PgameTestNamespaces=stardewcraft_desert_festival_bus. No client or player save required. */
@GameTestHolder("stardewcraft_desert_festival_bus")
@PrefixGameTestTemplate(false)
public final class DesertFestivalBusGameTests {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockPos MIN = new BlockPos(-229, 64, -183);
    private static final BlockPos MAX = new BlockPos(-215, 69, -178);

    private DesertFestivalBusGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_desert_festival_bus", template = "ring_utilities")
    public static void festivalTemplateUsesCurrentBusAndKeepsArrivalClear(GameTestHelper h) {
        var definition = FestivalMapOverlayRegistry.get("DesertFestival").orElseThrow();
        var snapshot = SchematicSnapshot.load(definition.festivalSchematicPath());
        var legacy = SchematicSnapshot.load(DesertFestivalBusLayout.LEGACY_SCHEMATIC_PATH);
        var bus = ModBlocks.BUS.get();
        int cells = 0, owners = 0;
        for (int y = 0; y < snapshot.height(); y++) for (int z = 0; z < snapshot.length(); z++)
            for (int x = 0; x < snapshot.width(); x++) {
                var state = snapshot.block(x, y, z);
                if (!state.is(bus)) continue;
                cells++;
                var pos = definition.origin().offset(x, y, z);
                h.assertTrue(state.getValue(MapDecorStaticBlock.FACING) == Direction.WEST, "Festival bus facing differs from pregen");
                var offset = ParkedVehicleBlock.rotateOffset(ParkedVehicleBlock.localOffset(state.getValue(ParkedVehicleBlock.CELL)), Direction.WEST);
                h.assertTrue(pos.subtract(offset).equals(DesertFestivalBusLayout.BUS_ANCHOR), "Bus cell points to another owner");
                if (state.getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.MAIN) {
                    owners++;
                    h.assertTrue(pos.equals(DesertFestivalBusLayout.BUS_ANCHOR), "Wrong bus model anchor");
                    h.assertTrue("stardewcraft:parked_vehicle".equals(snapshot.blockEntity(x, y, z).getString("Id")), "Bus renderer block entity missing");
                } else h.assertTrue(snapshot.blockEntity(x, y, z) == null, "Extension contains an extra vehicle renderer");
            }
        h.assertTrue(cells == 108 && owners == 1, "Festival bus footprint or single renderer contract changed");
        for (int y = 0; y < legacy.height(); y++) for (int z = 0; z < legacy.length(); z++)
            for (int x = 0; x < legacy.width(); x++) {
                if (legacy.block(x, y, z).isAir()) continue;
                var local = DesertFestivalBusLayout.LEGACY_ORIGIN.offset(x, y, z).subtract(definition.origin());
                var current = snapshot.block(local.getX(), local.getY(), local.getZ());
                h.assertTrue(current.isAir() || current.is(bus), "Retired bus material remains in the festival template");
            }
        for (BlockPos pos : List.of(DesertConstants.ARRIVAL_OFFSET, DesertConstants.ARRIVAL_OFFSET.above())) {
            var local = pos.subtract(definition.origin());
            h.assertTrue(snapshot.block(local.getX(), local.getY(), local.getZ()).isAir(), "Bus arrival obstructed by the new model");
        }
        var planter = new BlockPos(-227, 64, -183).subtract(definition.origin());
        h.assertTrue(snapshot.block(planter.getX(), planter.getY(), planter.getZ()).is(Blocks.SPRUCE_TRAPDOOR), "Bus update removed adjacent planter");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_desert_festival_bus", template = "ring_utilities", timeoutTicks = 200)
    public static void oldSavedFestivalAndRestoreRecoverWithoutLosingEditsOrFreeReturn(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        if (level.getGameTime() == 0 || level.getGameTime() == 55 || level.getGameTime() == 60 || level.getGameTime() == 90) {
            // The production ride tag uses zero as "not riding". Avoid a test timestamp collision without changing world time.
            h.runAfterDelay(1, () -> {
                try { oldSavedFestivalAndRestoreRecoverWithoutLosingEditsOrFreeReturn(h); }
                catch (Exception exception) { h.fail(exception.toString()); }
            });
            return;
        }
        for (int cx = MIN.getX() >> 4; cx <= MAX.getX() >> 4; cx++) level.getChunk(cx, MIN.getZ() >> 4);
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> beforeEntities = new LinkedHashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(MIN.below(), MAX)) {
            var pos = cursor.immutable();
            before.put(pos, level.getBlockState(pos));
            var entity = level.getBlockEntity(pos);
            if (entity != null) beforeEntities.put(pos, entity.saveWithFullMetadata());
        }
        var dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        var previousDimension = dimension.get(level);
        dimension.set(level, ModDimensions.STARDEW_VALLEY);
        var worldOverlay = FestivalWorldData.get(level).getOrCreateOverlayState("DesertFestival");
        var previousOverlay = worldOverlay.save();
        try {
            installLegacy(level);
            // A player-owned block in a necessary new cell must prevent a partial/destructive migration.
            var obstruction = new BlockPos(-228, 64, -180);
            level.setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), FLAGS);
            h.assertTrue(!DesertFestivalBusLayout.repair(level), "Migration erased a player obstruction");
            h.assertTrue(level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK)
                    && level.getBlockState(new BlockPos(-215, 69, -179)).is(Blocks.LIGHT_GRAY_CONCRETE), "Blocked repair changed part of the old bus");
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), FLAGS);
            var lecternPos = new BlockPos(-226, 65, -181);
            var lectern = (LecternBlockEntity) level.getBlockEntity(lecternPos);
            lectern.setBook(new ItemStack(Items.WRITABLE_BOOK));
            h.assertTrue(!DesertFestivalBusLayout.repair(level), "Migration discarded a changed legacy block entity");
            h.assertTrue(lectern.getBook().is(Items.WRITABLE_BOOK), "Player's book was removed");
            installLegacy(level);
            var chestPos = new BlockPos(-219, 65, -178);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), FLAGS);
            ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.DIAMOND, 7));
            var planterPos = new BlockPos(-227, 64, -183);
            level.setBlock(planterPos, Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), FLAGS);
            var persisted = new FestivalMapOverlayState("DesertFestival");
            persisted.begin("DesertFestival", 1, 0, 15, FestivalMapOverlayPhase.APPLIED);
            persisted.setRuntimePatch(new FestivalMapPatch("DesertFestival", BlockPos.ZERO, 1, 1, 1,
                    List.of(new FestivalMapPatchEntry(BlockPos.ZERO, Blocks.STONE.defaultBlockState(),
                            Blocks.SAND.defaultBlockState(), null, null))));
            persisted = FestivalMapOverlayState.load(persisted.save());
            worldOverlay.begin(persisted.festivalId(), persisted.year(), persisted.season(), persisted.day(), persisted.phase());
            worldOverlay.setRuntimePatch(persisted.runtimePatch());
            var backup = worldOverlay.save();
            h.assertTrue(FestivalMapOverlayManager.beginApply(level, FestivalRegistry.get("DesertFestival").orElseThrow(), 1, 0, 15),
                    "Already-applied saved overlay could not resume");
            h.assertTrue(backup.equals(worldOverlay.save()), "Repair rewrote the persisted original map backup");
            h.assertTrue(DesertFestivalBusLayout.repair(level), "Repaired bus is not idempotent");
            h.assertTrue(level.getBlockEntity(DesertFestivalBusLayout.BUS_ANCHOR) instanceof ParkedVehicleBlockEntity, "Missing native bus renderer");
            h.assertTrue(level.getBlockState(new BlockPos(-215, 69, -179)).isAir(), "Retired bus roof survived");
            h.assertTrue(level.getBlockState(planterPos).is(Blocks.SPRUCE_TRAPDOOR)
                    && ((ChestBlockEntity) level.getBlockEntity(chestPos)).getItem(0).getCount() == 7, "Repair changed neighboring player content");
            var packets = new ArrayList<OpenDesertBusConfirmPayload>();
            var fades = new ArrayList<DesertBusFadePayload>();
            var player = player(level, packets, fades);
            player.setPos(Vec3.atCenterOf(DesertConstants.ARRIVAL_OFFSET));
            int money = PlayerStardewDataAPI.getMoney(player), clicks = 0;
            for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
                var state = level.getBlockState(pos);
                if (!state.is(ModBlocks.BUS.get())) continue;
                clicks++;
                h.assertTrue(DesertFestivalBusLayout.BUS_ANCHOR.equals(ModBlocks.BUS.get().findMainPos(level, pos, state)), "Restored bus has an orphan cell");
                packets.clear();
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
                h.assertTrue(PortBlockInteraction.stateUseWithoutItem(state, level, player, hit).consumesAction(), "Bus return interaction missing");
                h.assertTrue(packets.size() == 1 && com.stardew.craft.port.PortJava.getFirst(packets).price() == 0, "Bus return is not free");
            }
            h.assertTrue(clicks == 108 && PlayerStardewDataAPI.getMoney(player) == money && !DesertBusService.isRiding(player), "Opening return confirmation charged or started travel");
            h.assertTrue(!PlayerStardewDataAPI.getData(player).hasMailFlag(CCStoryFlags.CC_VAULT), "Return test unexpectedly has the bus unlock");
            DesertBusService.onPlayerConfirmed(player);
            h.assertTrue(DesertBusService.isRiding(player) && fades.size() == 1 && com.stardew.craft.port.PortJava.getFirst(fades).phase() == 0,
                    "Confirmed free return did not start its fade and actual ride");
            // GameTest creates only the overworld. Install this alias for one synchronous warp call only,
            // restoring it before the server can iterate/tick worlds, so no extra world is ticked twice.
            var levelsField = MinecraftServer.class.getDeclaredField("levels");
            levelsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var levels = (Map<ResourceKey<Level>, ServerLevel>) levelsField.get(level.getServer());
            boolean hadValley = levels.containsKey(ModDimensions.STARDEW_VALLEY);
            var previousValley = levels.put(ModDimensions.STARDEW_VALLEY, level);
            try {
                tickRideAtElapsed(player, 55);
            } finally {
                if (hadValley) levels.put(ModDimensions.STARDEW_VALLEY, previousValley);
                else levels.remove(ModDimensions.STARDEW_VALLEY);
            }
            var town = DesertConstants.TOWN_RETURN_ARRIVAL;
            h.assertTrue(player.position().distanceToSqr(new Vec3(town.getX() + .5, town.getY(), town.getZ() + .5)) < 1E-8
                    && Math.abs(player.getYRot() - DesertConstants.TOWN_RETURN_YAW) < 1E-5,
                    "Confirmed festival bus failed to return to the real town arrival anchor");
            tickRideAtElapsed(player, 60);
            h.assertTrue(fades.size() == 2 && fades.get(1).phase() == 1 && fades.get(1).ticks() == 20, "Return warp left the screen black");
            tickRideAtElapsed(player, 90);
            h.assertTrue(!DesertBusService.isRiding(player) && PlayerStardewDataAPI.getMoney(player) == money,
                    "Completed return retained ride state or charged a fare");
            installLegacy(level); // An old saved runtime backup restored the pre-migration map.
            new DesertFestivalHandler().onMapOverlayRestored(level, null, null);
            h.assertTrue(level.getBlockState(DesertFestivalBusLayout.BUS_ANCHOR).is(ModBlocks.BUS.get()), "Old map restore stranded the player again");
        } finally {
            try {
                MapDecorStaticBlock.runWithDropsSuppressed(() -> {
                    level.removeBlock(DesertFestivalBusLayout.BUS_ANCHOR, false);
                    before.forEach((pos, state) -> { level.removeBlockEntity(pos); level.setBlock(pos, state, FLAGS); });
                    beforeEntities.forEach((pos, tag) -> {
                        var entity = BlockEntity.loadStatic(pos, before.get(pos), tag);
                        if (entity != null) level.setBlockEntity(entity);
                    });
                });
                var restoreOverlay = FestivalMapOverlayState.load(previousOverlay);
                worldOverlay.begin(restoreOverlay.festivalId(), restoreOverlay.year(), restoreOverlay.season(), restoreOverlay.day(), restoreOverlay.phase());
                worldOverlay.setRuntimePatch(restoreOverlay.runtimePatch());
                worldOverlay.setCursor(restoreOverlay.cursor());
            } finally { dimension.set(level, previousDimension); }
        }
        h.succeed();
    }

    private static void installLegacy(ServerLevel level) {
        var snapshot = SchematicSnapshot.load(DesertFestivalBusLayout.LEGACY_SCHEMATIC_PATH);
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            level.removeBlock(DesertFestivalBusLayout.BUS_ANCHOR, false);
            for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) { level.removeBlockEntity(pos); level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS); }
            for (BlockPos pos : BlockPos.betweenClosed(MIN.below(), MAX.atY(MIN.getY() - 1))) level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
            for (int y = 0; y < snapshot.height(); y++) for (int z = 0; z < snapshot.length(); z++)
                for (int x = 0; x < snapshot.width(); x++) {
                    var pos = DesertFestivalBusLayout.LEGACY_ORIGIN.offset(x, y, z);
                    var state = snapshot.block(x, y, z);
                    level.setBlock(pos, state, FLAGS);
                    var raw = snapshot.blockEntity(x, y, z);
                    if (raw == null) continue;
                    var tag = raw.contains("Data", Tag.TAG_COMPOUND) ? raw.getCompound("Data").copy() : raw.copy();
                    tag.putString("id", raw.getString("Id"));
                    tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
                    var entity = BlockEntity.loadStatic(pos, state, tag);
                    if (entity != null) level.setBlockEntity(entity);
                }
        });
    }

    private static void tickRideAtElapsed(ServerPlayer player, int elapsed) throws ReflectiveOperationException {
        var startTag = DesertBusService.class.getDeclaredField("TAG_RIDE_START");
        startTag.setAccessible(true);
        player.getPersistentData().putLong((String) startTag.get(null), player.serverLevel().getGameTime() - elapsed);
        DesertBusService.onPlayerTick(new PlayerTickEvent.Post(player));
    }

    private static ServerPlayer player(ServerLevel level, List<OpenDesertBusConfirmPayload> packets, List<DesertBusFadePayload> fades) {
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "FestivalBusReader"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {
                if (ClientboundCustomPayloadPacket.unwrap(packet) instanceof ClientboundCustomPayloadPacket custom) {
                    if (custom.payload() instanceof OpenDesertBusConfirmPayload confirm) packets.add(confirm);
                    else if (custom.payload() instanceof DesertBusFadePayload fade) fades.add(fade);
                }
            }
        };
        return player;
    }
}
