package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.network.ClientContentSyncService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.stardew.craft.port.net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Exercises login callbacks and wire codecs; does not claim a real client handshake. */
@GameTestHolder("stardewcraft_login")
@PrefixGameTestTemplate(false)
public final class DedicatedLoginGameTests {
    @GameTest(templateNamespace = "stardewcraft_login", template = "ring_utilities")
    public static void partialReplayServerUsesTransientMiningDataAndSkipsPlayerTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        helper.assertTrue(server.getLevel(com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) == null,
                "Replay fixture unexpectedly has the mining dimension");
        var player = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "ReplayViewerProbe"));
        try {
            var mining = MiningDataManager.getPlayerData(player);
            mining.setCurrentFloor(37);
            helper.assertTrue(MiningDataManager.getPlayerData(player) == mining
                            && MiningDataManager.getPlayerData(player).getMaxFloorReached() == 37,
                    "Transient mining state is not stable for the replay server");
            helper.assertTrue(PlayerDataManager.get().getData(player.getUUID()) == null,
                    "Replay fixture started with persistent Stardew player data");
            var projected = new PlayerStardewData(player.getUUID());
            PlayerDataEventHandler.syncPlayerData(player, projected);
            PlayerDataEventHandler.syncPlayerVitals(player, projected);
            PlayerDataEventHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(PlayerDataManager.get().getData(player.getUUID()) == null,
                    "Replay player tick created persistent Stardew player data");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_login", template = "ring_utilities", timeoutTicks = 200)
    public static void coldPortalPlacementDoesNotLoadChunksInline(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(4096, 3, 4096));
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null, "Fixture chunk must start unloaded");
        var vanillaForced = new java.util.HashSet<Long>(level.getForcedChunks());
        com.stardew.craft.interior.InteriorSubspaceManager.placePortalTriggerArea(
                level, pos, 2, 1, 1, "sdv_portal_marker:login_probe", "sdv_portal_target:login_probe");
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "Portal restoration loaded a cold chunk synchronously during startup");
        helper.succeedWhen(() -> {
            com.stardew.craft.interior.InteriorSubspaceManager.tickPendingPortals(level);
            helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) != null, "Portal chunk not ready yet");
            helper.assertTrue(level.getBlockEntity(pos) instanceof com.stardew.craft.blockentity.PortalTriggerBlockEntity be
                    && "login_probe".equals(be.getTargetId()), "Deferred portal target missing");
            helper.assertTrue(vanillaForced.equals(new java.util.HashSet<Long>(level.getForcedChunks())),
                    "Portal placement modified vanilla forced chunks");
        });
    }

    @GameTest(templateNamespace = "stardewcraft_login", template = "ring_utilities", timeoutTicks = 200)
    public static void loginContentAndPlayerPacketsSurviveWireEncoding(GameTestHelper helper) {
        withMiningDataLevel(helper, () -> exerciseLogin(helper));
        helper.succeed();
    }

    private static void exerciseLogin(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "LoginWireProbe"));
        Set<String> sent = new HashSet<>();
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                player) {
            @Override
            public void send(Packet<?> packet) {
                inspect(packet);
            }

            private void inspect(Packet<?> packet) {
                if (packet instanceof ClientboundBundlePacket bundle) {
                    bundle.subPackets().forEach(this::inspect);
                } else if (ClientboundCustomPayloadPacket.unwrap(packet) instanceof ClientboundCustomPayloadPacket custom) {
                    String id = custom.payload().type().id().toString();
                    var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
                    var roundTrip = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
                    try {
                        ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(encoded, custom);
                        byte[] original = new byte[encoded.readableBytes()];
                        encoded.getBytes(encoded.readerIndex(), original);
                        var decoded = ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(encoded);
                        helper.assertTrue(encoded.readableBytes() == 0, "Unread login packet bytes: " + id);
                        ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(roundTrip, decoded);
                        byte[] replay = new byte[roundTrip.readableBytes()];
                        roundTrip.readBytes(replay);
                        helper.assertTrue(java.util.Arrays.equals(original, replay), "Login packet changed on wire: " + id);
                        sent.add(id);
                        StardewCraft.LOGGER.info("[LOGIN_WIRE] {} bytes={}", id, original.length);
                    } catch (Throwable failure) {
                        throw new IllegalStateException("Login wire failure: " + id, failure);
                    } finally {
                        encoded.release();
                        roundTrip.release();
                    }
                }
            }
        };
        try {
            ClientContentSyncService.onDatapackSync(new OnDatapackSyncEvent(server.getPlayerList(), player));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(sent.contains("stardewcraft:data_registry_sync"), "Missing login content snapshot");
            helper.assertTrue(sent.contains("stardewcraft:jei_catalog_sync"), "Missing login catalog snapshot");
            helper.assertTrue(sent.size() >= 10, "Login did not exercise player synchronization: " + sent);
            StardewCraft.LOGGER.info("[LOGIN_WIRE] Validated {} payload types", sent.size());
        } finally {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            player.discard();
        }
    }

    // GameTestServer only creates vanilla dimensions; normal dedicated servers load this one.
    @SuppressWarnings("unchecked")
    private static void withMiningDataLevel(GameTestHelper helper, Runnable action) {
        try {
            var field = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
            field.setAccessible(true);
            var levels = (java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                    net.minecraft.server.level.ServerLevel>) field.get(helper.getLevel().getServer());
            var key = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
            var previous = levels.putIfAbsent(key, helper.getLevel());
            try {
                action.run();
            } finally {
                if (previous == null) levels.remove(key);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot prepare login test dimension", failure);
        }
    }
}
