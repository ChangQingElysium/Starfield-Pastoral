package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.DimensionEventHandler;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.player.PassOutRecoveryData;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import com.stardew.craft.port.net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_overnight_transition")
@PrefixGameTestTemplate(false)
public final class OvernightTransitionGameTests {
    @GameTest(templateNamespace = "stardewcraft_overnight_transition", template = "ring_utilities")
    public static void homeWarpPrecedesRecoveryConsumptionAndSave(GameTestHelper h) throws Exception {
        try (var fixture = new Fixture(h, false)) {
            fixture.advance();
            h.assertTrue(fixture.steps.equals(List.of("warp", "settlement")),
                    "Morning consumed recovery/saved the mine position before returning home: " + fixture.steps);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_overnight_transition", template = "ring_utilities")
    public static void failureAfterRecoveryConsumptionStillReleasesBlackScreen(GameTestHelper h) throws Exception {
        try (var fixture = new Fixture(h, true)) {
            fixture.advance();
            h.assertTrue(!PassOutService.hasPendingPassOutResult(fixture.player.getUUID()),
                    "Fixture must fail after the recovery record was consumed");
            h.assertTrue(fixture.packets.contains("stardewcraft:overnight_collapse_cancel"),
                    "Consumed recovery made settlement failure silently strand the collapse screen");
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_overnight_transition", template = "ring_utilities")
    public static void logoutThresholdWaitsForCollapseBeforeAdvancing(GameTestHelper h) throws Exception {
        var scheduled = DimensionEventHandler.class.getDeclaredField("passOutAdvanceScheduled");
        scheduled.setAccessible(true);
        var tasksField = com.stardew.craft.time.ServerRealTickTaskScheduler.class.getDeclaredField("TASKS");
        tasksField.setAccessible(true);
        @SuppressWarnings("unchecked") var tasks = (List<Object>) tasksField.get(null);
        var previousTasks = new ArrayList<>(tasks);
        boolean previousScheduled = scheduled.getBoolean(null);
        try (var fixture = new Fixture(h, false)) {
            DimensionEventHandler.triggerAdvance(fixture.valley, 1500, "sleep_vote_logout");
            h.assertTrue(fixture.steps.isEmpty() && scheduled.getBoolean(null) && tasks.size() == previousTasks.size() + 1,
                    "Logout bypassed the collapse animation deadline");
        } finally {
            tasks.clear(); tasks.addAll(previousTasks);
            scheduled.setBoolean(null, previousScheduled);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_overnight_transition", template = "ring_utilities")
    public static void warpFailureDoesNotConsumeRecoveryOrStrandBlackScreen(GameTestHelper h) throws Exception {
        try (var fixture = new Fixture(h, false)) {
            fixture.failWarp = true;
            fixture.advance();
            h.assertTrue(fixture.steps.equals(List.of("warp"))
                            && PassOutService.hasPendingPassOutResult(fixture.player.getUUID()),
                    "Failed home warp still consumed recovery or advanced the day");
            h.assertTrue(fixture.packets.contains("stardewcraft:overnight_collapse_cancel"),
                    "Home warp failure left the collapse waiting forever");
        }
        h.succeed();
    }

    /** Isolates the day-work boundary; no terrain generation or real save is needed to test its ordering. */
    private static final class Fixture implements AutoCloseable {
        final ServerLevel valley;
        final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel> levels;
        final ServerLevel previousValley;
        final long previousDayTime;
        final ServerPlayer player;
        final StardewTimeManager previousTime;
        final FarmInstanceRegistry previousFarms;
        final List<ServerPlayer> online;
        boolean failWarp;
        final List<String> steps = new ArrayList<>();
        final List<String> packets = new ArrayList<>();

        Fixture(GameTestHelper h, boolean failAfterConsumption) throws Exception {
            var server = h.getLevel().getServer();
            // GameTestServer only loads its test level. Alias it for the production
            // destination lookup; the player below records warps instead of loading chunks.
            var levelsField = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            levelsField.setAccessible(true);
            @SuppressWarnings("unchecked") var serverLevels =
                    (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, ServerLevel>) levelsField.get(server);
            levels = serverLevels;
            previousValley = levels.putIfAbsent(ModDimensions.STARDEW_VALLEY, h.getLevel());
            valley = server.getLevel(ModDimensions.STARDEW_VALLEY);
            previousDayTime = valley.getDayTime();
            previousTime = StardewTimeManager.get();
            previousFarms = FarmInstanceRegistry.get();
            player = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "OvernightProbe"),
                    ClientInformation.createDefault()) {
                @Override public void teleportTo(ServerLevel target, double x, double y, double z, float yaw, float pitch) {
                    steps.add("warp");
                    if (failWarp) throw new IllegalStateException("Injected home warp failure");
                    setServerLevel(target);
                    setPos(x, y, z);
                }
            };
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom) packets.add(custom.payload().type().id().toString());
                }
            };
            var playersField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked") var players = (List<ServerPlayer>) playersField.get(server.getPlayerList());
            online = players;
            online.add(player);
            var storage = server.overworld().getDataStorage();
            storage.set("stardew_farm_instances", new FarmInstanceRegistry() {
                @Override public BlockPos getFarmSpawnPoint(UUID id) { return new BlockPos(1, 64, 1); }
            });
            storage.set("stardew_time_data", new StardewTimeManager() {
                @Override public void advanceDayWithSleepTime(int minute) {
                    steps.add("settlement");
                    PassOutService.consumePassOutResult(player.getUUID());
                    if (failAfterConsumption) throw new IllegalStateException("Injected post-consumption save failure");
                }
            });
            PassOutRecoveryData.get(server).put(player.getUUID(), new PassOutRecoveryData.Entry(1,
                    PassOutService.PassOutType.EXHAUSTION_2AM, false, PassOutRecoveryData.Stage.AWAITING_OVERNIGHT,
                    100, List.of(), 2, "", ""));
        }

        void advance() throws Exception {
            var method = DimensionEventHandler.class.getDeclaredMethod("advanceToNextMorning", ServerLevel.class, int.class, String.class);
            method.setAccessible(true);
            method.invoke(null, valley, 1560, "test_pass_out");
        }

        @Override public void close() {
            online.remove(player);
            PassOutRecoveryData.get(player.server).remove(player.getUUID());
            com.stardew.craft.interior.CrossDimensionTeleporter.consumeSkipAutoTeleport(player.getUUID());
            var storage = player.server.overworld().getDataStorage();
            storage.set("stardew_time_data", previousTime);
            storage.set("stardew_farm_instances", previousFarms);
            valley.setDayTime(previousDayTime);
            if (previousValley == null) levels.remove(ModDimensions.STARDEW_VALLEY);
        }
    }
}
