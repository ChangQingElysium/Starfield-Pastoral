package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.festival.EggFestivalNpcService;
import com.stardew.craft.festival.FestivalNpcActorRuntime;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.npc.runtime.NpcExecutionCoordinator;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import com.stardew.craft.port.net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("stardewcraft_festival_dialogue")
@PrefixGameTestTemplate(false)
public final class FestivalDialogueGameTests {
    @GameTest(templateNamespace = "stardewcraft_festival_dialogue", template = "ring_utilities")
    public static void festivalAllowsFacingAndRestoresItsHeading(GameTestHelper h) throws Exception {
        withFestival(() -> {
            for (boolean nativeAttention : List.of(false, true)) {
                var npc = actor(h, nativeAttention);
                var player = player(h.getLevel(), new ArrayList<>());
                player.setPos(npc.position().add(0, 0, -2));
                int[] calls = {0};
                try {
                    h.assertTrue(!NpcExecutionCoordinator.autonomous(npc), "Fixture must be festival controlled");
                    npc.facePlayerTemporarily(player, 2, () -> calls[0]++);
                    h.assertTrue(calls[0] == 0, "Dialogue opens before turning");
                    tick(npc, 160);
                    h.assertTrue(calls[0] == 1, "Festival discarded the facing callback; native=" + nativeAttention);
                    h.assertTrue(!npc.isFacingOverrideActive(), "Dialogue never gives heading back to festival");
                    h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(npc.getYRot())) < .01,
                            "NPC did not restore the festival heading");
                    h.assertTrue(!npc.isAttentionActive(), "Idle attention resumed during festival");
                } finally { npc.prepareForNpcRelocation(); }
            }
        });
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_festival_dialogue", template = "ring_utilities")
    public static void cancelledFestivalDialogueDoesNotConsumeConversation(GameTestHelper h) throws Exception {
        withFestival(() -> {
            var packets = new ArrayList<OpenNpcDialogueScreenPayload>();
            var player = player(h.getLevel(), packets);
            var npc = actor(h, false);
            var manager = NpcFriendshipDataManager.get(h.getLevel());
            var state = manager.getOrCreate(player.getUUID(), npc.getNpcId());
            int beforeDay = state.lastTalkDayKey();
            int beforePoints = state.points();
            try {
                player.setPos(npc.position().add(0, 0, -2));
                NpcExecutionCoordinator.claim(npc, "test:cutscene", 100, 200);
                requestEggDialogue(player, npc, state, manager);
                h.assertTrue(!npc.isFacingOverrideActive() && packets.isEmpty()
                                && state.lastTalkDayKey() == beforeDay && state.points() == beforePoints,
                        "Rejected interaction consumed conversation or stole cutscene control");
                NpcExecutionCoordinator.release(npc, "test:cutscene");
                requestEggDialogue(player, npc, state, manager);
                h.assertTrue(state.lastTalkDayKey() == beforeDay && state.points() == beforePoints,
                        "Festival records conversation before facing and opening dialogue");
                h.assertTrue(packets.isEmpty(), "Dialogue packet sent before facing");
                player.setPos(npc.position().add(100, 0, 0));
                tick(npc, 100);
                h.assertTrue(packets.isEmpty() && state.lastTalkDayKey() == beforeDay && state.points() == beforePoints,
                        "Leaving before facing consumed conversation");
                player.setPos(npc.position().add(0, 0, -2));
                requestEggDialogue(player, npc, state, manager);
                tick(npc, 4);
                h.assertTrue(packets.size() == 1 && state.lastTalkDayKey() != beforeDay && state.points() > beforePoints,
                        "Retry did not open dialogue and commit conversation together");
                int earned = state.points();
                requestEggDialogue(player, npc, state, manager);
                tick(npc, 4);
                h.assertTrue(state.points() == earned, "Repeated click grants daily friendship twice");
            } finally { npc.prepareForNpcRelocation(); }
        });
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_festival_dialogue", template = "ring_utilities")
    public static void festivalKeepsFacingUntilDialogueCloses(GameTestHelper h) throws Exception {
        withFestival(() -> {
            for (boolean nativeAttention : List.of(false, true)) {
                var packets = new ArrayList<OpenNpcDialogueScreenPayload>();
                var npc = actor(h, nativeAttention);
                var player = player(h.getLevel(), packets);
                var manager = NpcFriendshipDataManager.get(h.getLevel());
                var state = manager.getOrCreate(player.getUUID(), npc.getNpcId());
                try {
                    player.setPos(npc.position().add(0, 0, -2));
                    requestEggDialogue(player, npc, state, manager);
                    tick(npc, 160);
                    h.assertTrue(packets.size() == 1 && npc.isFacingOverrideActive(),
                            "Server tick ended facing while the dialogue is open; native=" + nativeAttention);
                    NpcInteractionService.cancelNpcSessions(npc.getNpcId());
                    tick(npc, 160);
                    h.assertTrue(!npc.isFacingOverrideActive()
                                    && Math.abs(net.minecraft.util.Mth.wrapDegrees(npc.getYRot())) < .01,
                            "Closing dialogue did not return festival heading");
                    requestEggDialogue(player, npc, state, manager);
                    NpcExecutionCoordinator.claim(npc, "test:cutscene", 100, 200);
                    tick(npc, 80);
                    h.assertTrue(packets.size() == 1 && !npc.isFacingOverrideActive(),
                            "Revoked dialogue callback survived a scene takeover");
                } finally { npc.prepareForNpcRelocation(); }
            }
        });
        h.succeed();
    }

    private static void requestEggDialogue(ServerPlayer player, StardewNpcEntity npc,
            NpcFriendshipDataManager.FriendshipState state, NpcFriendshipDataManager manager) throws Exception {
        var contextMethod = NpcInteractionService.class.getDeclaredMethod("currentDayContext", ServerLevel.class);
        contextMethod.setAccessible(true);
        var context = contextMethod.invoke(null, player.serverLevel());
        var method = NpcInteractionService.class.getDeclaredMethod("tryHandleEggFestivalDialogue",
                ServerPlayer.class, StardewNpcEntity.class, String.class,
                NpcFriendshipDataManager.FriendshipState.class, context.getClass(), NpcFriendshipDataManager.class);
        method.setAccessible(true);
        if (!(boolean) method.invoke(null, player, npc, npc.getNpcId(), state, context, manager)) {
            throw new AssertionError("Fixture has no egg festival dialogue");
        }
    }

    private static StardewNpcEntity actor(GameTestHelper h, boolean nativeAttention) {
        var npc = new StardewNpcEntity(ModEntities.STARDEW_NPC.get(), h.getLevel()) {
            @Override public boolean usesNativeAttention() { return nativeAttention; }
        };
        npc.setNpcId("sam");
        // Keep this unregistered fixture out of persistence and duplicate-instance cleanup.
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setNoAi(true);
        npc.setNoGravity(true);
        npc.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 3, 3))));
        npc.setYRot(0); npc.setYBodyRot(0); npc.setYHeadRot(0);
        return npc;
    }

    private static void tick(StardewNpcEntity npc, int count) {
        var level = (ServerLevel) npc.level();
        long time = level.getGameTime();
        try {
            for (int i = 0; i < count; i++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + i + 1);
                npc.tick();
            }
        } finally { ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time); }
    }

    private static ServerPlayer player(ServerLevel level, List<OpenNpcDialogueScreenPayload> packets) {
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "FestivalReader"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player) {
            @Override public void send(Packet<?> packet) {
                if (ClientboundCustomPayloadPacket.unwrap(packet) instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof OpenNpcDialogueScreenPayload dialogue) packets.add(dialogue);
            }
        };
        return player;
    }

    private static void withFestival(CheckedAction action) throws Exception {
        var field = EggFestivalNpcService.class.getDeclaredField("NPC_ACTORS");
        field.setAccessible(true);
        var actors = (FestivalNpcActorRuntime) field.get(null);
        boolean active = actors.isActorsActive();
        actors.setActorsActive(true);
        try { action.run(); } finally { actors.setActorsActive(active); }
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
