package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.desert.DesertBusService;
import com.stardew.craft.desert.DesertConstants;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.network.payload.OpenDesertBusConfirmPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in headless verification: -PgameTestNamespaces=stardewcraft_desert_travel. */
@GameTestHolder("stardewcraft_desert_travel")
@PrefixGameTestTemplate(false)
public final class DesertTravelGameTests {
    private DesertTravelGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_desert_travel", template = "ring_utilities")
    public static void desertBusAllCellsOfferFreeReturnWithoutStartingRide(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var pos = new BlockPos(-220, 280, -180);
        var packets = new ArrayList<OpenDesertBusConfirmPayload>();
        var player = player(level, packets);
        player.setPos(Vec3.atCenterOf(pos));
        int money = PlayerStardewDataAPI.getMoney(player);
        var bus = ModBlocks.BUS.get();
        try (var ignored = new ValleyDimension(level)) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var state = bus.defaultBlockState().setValue(MapDecorStaticBlock.FACING, facing);
                level.setBlock(pos, state, 2);
                try {
                    h.assertTrue(bus.placeExtensions(level, pos, state), "Cannot place test bus");
                    for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-5, 0, -5), pos.offset(5, 3, 5))) {
                        var part = level.getBlockState(cell);
                        if (!part.is(bus)) continue;
                        var hit = new BlockHitResult(Vec3.atCenterOf(cell), Direction.NORTH, cell, false);
                        packets.clear();
                        h.assertTrue(part.useWithoutItem(level, player, hit).consumesAction(), "Bus cell ignored return click " + cell);
                        h.assertTrue(packets.size() == 1 && packets.getFirst().price() == 0, "Return click did not offer a free ticket");
                        h.assertTrue(PlayerStardewDataAPI.getMoney(player) == money && !DesertBusService.isRiding(player),
                                "Opening confirmation charged or teleported player");
                    }
                } finally { level.removeBlock(pos, false); }
            }
        }
        // Identical coordinates in another dimension must remain ordinary decoration.
        var other = h.getLevel();
        other.setBlock(pos, bus.defaultBlockState(), 2);
        try {
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
            h.assertTrue(!other.getBlockState(pos).useWithoutItem(other, player, hit).consumesAction(), "Overworld bus offers a desert return");
        } finally { other.removeBlock(pos, false); }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_desert_travel", template = "ring_utilities")
    public static void loadedLegacyBusTriggersAreRemovedAndCannotBeRecreated(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var pos = new BlockPos(-220, 280, -170);
        try (var ignored = new ValleyDimension(level)) {
            for (String target : List.of("desert_bus", "desert_bus_return", "desert_mine_enter", "oasis_enter")) {
                var state = ModBlocks.PORTAL_TRIGGER.get().defaultBlockState();
                level.setBlock(pos, state, 2);
                try {
                    var portal = (PortalTriggerBlockEntity) level.getBlockEntity(pos);
                    portal.configure(target, "sdv_portal_marker:" + target);
                    portal.onLoad(); // Same path as loading a saved block entity from disk.
                    boolean retired = DesertConstants.isLegacyBusTarget(target);
                    if (retired) h.assertTrue(level.getBlockTicks().hasScheduledTick(pos, state.getBlock()), "Load did not schedule cleanup");
                    state.tick(level, pos, level.random);
                    h.assertTrue(level.getBlockState(pos).isAir() == retired, "Cleanup removed an unrelated portal or kept a bus trigger");
                    if (retired) {
                        InteriorSubspaceManager.placePortalTriggerArea(level, pos, 1, 1, 1,
                                "sdv_portal_marker:" + target, "sdv_portal_target:" + target);
                        h.assertTrue(level.getBlockState(pos).isAir(), "Self-repair resurrected a bus trigger");
                    }
                } finally { level.removeBlock(pos, false); }
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_desert_travel", template = "ring_utilities")
    public static void desertSandAndTilledSandStayConstantInEverySeason(GameTestHelper h) {
        for (int season = 0; season < 4; season++) {
            for (int x : new int[]{-311, -310, -225, -158, -157}) for (int z : new int[]{-242, -241, -177, -113, -112}) {
                var pos = new BlockPos(x, 64, z);
                int expected = x >= -310 && x <= -158 && z >= -241 && z <= -113 ? 0 : season;
                for (var block : List.of(ModBlocks.SAND.get(), ModBlocks.SANDY_FARMLAND.get())) {
                    h.assertTrue(TerrainSeasonTextures.textureSetAt(season, ModDimensions.STARDEW_VALLEY, pos, block.defaultBlockState()) == expected,
                            "Desert sand season or region boundary incorrect");
                    h.assertTrue(TerrainSeasonTextures.textureSetAt(season, Level.OVERWORLD, pos, block.defaultBlockState()) == season,
                            "Season rule leaked into another dimension");
                }
                h.assertTrue(TerrainSeasonTextures.textureSetAt(season, ModDimensions.STARDEW_VALLEY, pos, ModBlocks.DIRT.get().defaultBlockState()) == season,
                        "Sand rule changed another material");
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_desert_travel", template = "ring_utilities")
    public static void ticketMachineStillHonorsUnlockAndDoesNotChargeOnClick(GameTestHelper h) {
        BusPropsGameTests.bothTicketMachineCellsUseTheBusGateWithoutCharging(h);
    }

    /** GameTestServer only creates the overworld. Restore the key before returning to its tick loop. */
    private static final class ValleyDimension implements AutoCloseable {
        private final ServerLevel level;
        private final java.lang.reflect.Field dimension;
        private final Object previous;
        ValleyDimension(ServerLevel level) throws ReflectiveOperationException {
            this.level = level;
            for (int x = -15; x <= -14; x++) for (int z = -12; z <= -11; z++) level.getChunk(x, z);
            dimension = Level.class.getDeclaredField("dimension");
            dimension.setAccessible(true);
            previous = dimension.get(level);
            dimension.set(level, ModDimensions.STARDEW_VALLEY);
        }
        @Override public void close() throws IllegalAccessException { dimension.set(level, previous); }
    }

    private static ServerPlayer player(ServerLevel level, List<OpenDesertBusConfirmPayload> packets) {
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "BusReader"), ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof OpenDesertBusConfirmPayload confirm)
                    packets.add(confirm);
            }
        };
        return player;
    }
}
