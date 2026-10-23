package com.stardew.craft.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.joja.JojaCDService;
import com.stardew.craft.joja.MorrisService;
import com.stardew.craft.joja.network.JojaPurchaseResultPayload;
import com.stardew.craft.joja.network.OpenJojaCDMenuPayload;
import com.stardew.craft.network.payload.AnswerNpcQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.npc.runtime.NpcQuestionAuthority;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.money.SharedMoneyService;
import com.stardew.craft.time.StardewTimeManager;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real question callback and persisted next-day membership, without a client. */
@GameTestHolder("stardewcraft_joja_membership")
@PrefixGameTestTemplate(false)
public final class JojaMembershipGameTests {
    private static final String PREFIX = "stardewcraft.npc.morris.";

    @GameTest(templateNamespace = "stardewcraft_joja_membership", template = "ring_utilities")
    public static void confirmationSurvivesThenMembershipOpensDevelopmentForm(GameTestHelper helper) {
        try (var reader = new Reader(helper, 20000)) {
            reader.interact();
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "greeting"), "First meeting skipped greeting");
            reader.interact();
            reader.answer(MorrisService.NODE_JOIN_YES);
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "signup_confirm"), "Invitation did not open confirmation");
            helper.assertTrue(reader.money() == 20000, "Invitation charged before confirmation");
            reader.answer(MorrisService.NODE_SIGNUP_YES);
            helper.assertTrue(reader.money() == 15000, "Second confirmation was rejected: membership did not charge exactly 5000g");
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "signed_up"), "Signup acknowledgement missing");
            helper.assertTrue(reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_MEMBER), "Membership was not queued");
            helper.assertTrue(!reader.data().hasMailFlag(CCStoryFlags.JOJA_MEMBER), "Membership activated before next day");
            reader.interact();
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "come_back_later"), "Same-day interaction offers another membership");
            helper.assertTrue(reader.money() == 15000, "Same-day interaction charged again");

            int today = StardewTimeManager.get().getAbsoluteDay();
            var restored = PlayerStardewData.fromNBT(reader.data().toNBT(helper.getLevel().registryAccess()),
                    reader.player.getUUID(), helper.getLevel().registryAccess());
            PlayerDataManager.get().savePlayerData(reader.player.getUUID(), restored);
            restored.deliverTomorrowMail(today);
            helper.assertTrue(!CCStoryFlags.isJojaMember(reader.player), "Reload activates membership on purchase day");
            restored.deliverTomorrowMail(today + 1);
            helper.assertTrue(CCStoryFlags.isJojaMember(reader.player), "Saved pending membership did not activate next day");
            reader.interact();
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "cd_form_offer"), "Member cannot access development offer");
            reader.answer(MorrisService.NODE_CD_YES);
            var form = reader.last(OpenJojaCDMenuPayload.class);
            helper.assertTrue(form != null && form.money() == 15000 && form.completedMask() == 0, "Development form did not open");
            JojaCDService.handlePurchase(reader.player, 1);
            helper.assertTrue(reader.money() == 0, "Boiler-room project did not charge vanilla 15000g");
            helper.assertTrue(reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_BOILER_ROOM)
                    && reader.data().hasMailFlagForTomorrow(CCStoryFlags.CC_BOILER_ROOM), "Project did not queue both next-day flags");
            helper.assertTrue(reader.last(JojaPurchaseResultPayload.class).resultCode() == 0, "Development purchase failed");
            JojaCDService.handleClose(reader.player, true);
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "cd_confirm"), "Purchase close acknowledgement missing");
            reader.interact();
            helper.assertTrue(reader.dialogue().translateKey().equals(PREFIX + "processing"), "Pending project should be processing");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_joja_membership", template = "ring_utilities")
    public static void eitherDeclineClosesWithoutCharging(GameTestHelper helper) {
        for (boolean reachConfirmation : List.of(false, true)) {
            try (var reader = new Reader(helper, 5000)) {
                reader.openInvitation();
                if (reachConfirmation) reader.answer(MorrisService.NODE_JOIN_YES);
                reader.answer(MorrisService.NODE_DISMISS);
                helper.assertTrue(reader.money() == 5000, "Declining charged money");
                helper.assertTrue(!reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_MEMBER), "Declining scheduled membership");
                helper.assertTrue(!NpcInteractionService.isDialogueMovementLocked("morris"), "Decline retained dialogue lock");
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_joja_membership", template = "ring_utilities")
    public static void insufficientFundsGiveFeedbackAndCanRetry(GameTestHelper helper) {
        try (var reader = new Reader(helper, 4999)) {
            reader.openInvitation();
            reader.answer(MorrisService.NODE_JOIN_YES);
            reader.answer(MorrisService.NODE_SIGNUP_YES);
            helper.assertTrue(reader.money() == 4999, "Insufficient funds changed balance");
            helper.assertTrue(!reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_MEMBER), "Insufficient funds scheduled membership");
            var message = reader.last(OpenObjectDialoguePayload.class);
            helper.assertTrue(message != null && !message.messages().isEmpty(), "Insufficient funds were silently ignored");
            SharedMoneyService.setMoney(reader.player, 5000);
            reader.openInvitation();
            reader.answer(MorrisService.NODE_JOIN_YES);
            reader.answer(MorrisService.NODE_SIGNUP_YES);
            helper.assertTrue(reader.money() == 0 && reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_MEMBER),
                    "Retry with exactly 5000g failed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_joja_membership", template = "ring_utilities")
    public static void continuationStillRejectsForgedClosedAndReplayedAnswers(GameTestHelper helper) {
        try (var reader = new Reader(helper, 10000)) {
            reader.openInvitation();
            reader.send("morris", MorrisService.NODE_SIGNUP_YES, -1, "-1");
            reader.send("wizard", MorrisService.NODE_JOIN_YES, -1, "-1");
            reader.send("morris", MorrisService.NODE_JOIN_YES, 10000, "-1");
            helper.assertTrue(reader.money() == 10000 && reader.dialogue().translateKey().endsWith("membership"),
                    "Forged response bypassed invitation");
            reader.answer(MorrisService.NODE_JOIN_YES);
            reader.send("morris", MorrisService.NODE_JOIN_YES, -1, "-1");
            NpcInteractionService.handleDialogueClosed(reader.player, "morris");
            reader.send("morris", MorrisService.NODE_SIGNUP_YES, -1, "-1");
            helper.assertTrue(reader.money() == 10000, "Closed response charged money");
            reader.openInvitation();
            reader.answer(MorrisService.NODE_JOIN_YES);
            reader.answer(MorrisService.NODE_SIGNUP_YES);
            reader.send("morris", MorrisService.NODE_SIGNUP_YES, -1, "-1");
            helper.assertTrue(reader.money() == 5000 && reader.data().hasMailFlagForTomorrow(CCStoryFlags.JOJA_MEMBER),
                    "Confirmation replay was charged or valid confirmation was rejected");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_joja_membership", template = "ring_utilities")
    public static void allShippedMorrisChoicesMatchServerAuthority(GameTestHelper helper) {
        try (var reader = new Reader(helper, 5000)) {
            for (String language : List.of("de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
                    "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn")) {
                var translations = translations(language);
                for (String page : List.of("first_membership", "weekend_membership", "signup_confirm", "cd_form_offer")) {
                    String key = PREFIX + page;
                    var choices = NpcQuestionAuthority.parse(translations.get(key).getAsString());
                    helper.assertTrue(choices.size() == 2, language + " / " + page + " must contain Yes/No");
                    for (var choice : choices) {
                        NpcQuestionAuthority.open(reader.player, "morris", key);
                        helper.assertTrue(NpcQuestionAuthority.consume(reader.player, "morris", choice.id(), choice.points(), choice.next()) != null,
                                language + " / " + page + " has an unauthorized answer");
                    }
                }
            }
        }
        helper.succeed();
    }

    private static JsonObject translations(String language) {
        String path = "assets/stardewcraft/lang/" + language + ".json";
        try (var stream = JojaMembershipGameTests.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Reader implements AutoCloseable {
        final ServerPlayer player;
        final StardewNpcEntity morris;
        final List<CustomPacketPayload> packets = new ArrayList<>();

        Reader(GameTestHelper helper, int money) {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "JojaReader"),
                    ClientInformation.createDefault());
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom) packets.add(custom.payload());
                }
            };
            SharedMoneyService.setMoney(player, money);
            morris = new StardewNpcEntity(ModEntities.STARDEW_NPC.get(), level);
            morris.setNpcId("morris");
            morris.setNoAi(true);
        }

        PlayerStardewData data() { return PlayerDataManager.getPlayerData(player); }
        int money() { return PlayerStardewDataAPI.getMoney(player); }
        void interact() { NpcInteractionService.onInteract(player, morris, InteractionHand.MAIN_HAND); }
        void openInvitation() {
            data().addMailFlag(CCStoryFlags.JOJA_GREETING);
            interact();
        }
        OpenNpcDialogueScreenPayload dialogue() { return last(OpenNpcDialogueScreenPayload.class); }
        <T extends CustomPacketPayload> T last(Class<T> type) {
            for (int i = packets.size() - 1; i >= 0; i--) if (type.isInstance(packets.get(i))) return type.cast(packets.get(i));
            return null;
        }
        void answer(String next) {
            String text = translations("en_us").get(dialogue().translateKey()).getAsString();
            var answer = NpcQuestionAuthority.parse(text).stream().filter(choice -> choice.next().equals(next)).findFirst().orElseThrow();
            send("morris", answer.next(), answer.points(), answer.id());
        }
        void send(String npc, String next, int points, String id) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AnswerNpcQuestionPayload.STREAM_CODEC.encode(buffer, new AnswerNpcQuestionPayload(npc, next, points, id));
                var decoded = AnswerNpcQuestionPayload.STREAM_CODEC.decode(buffer);
                NpcInteractionService.handleClientQuestionAnswer(player, decoded.npcId(), decoded.nextDialogueNode(),
                        decoded.friendshipDelta(), decoded.answerId());
            } finally {
                buffer.release();
            }
        }
        @Override public void close() {
            NpcInteractionService.onPlayerLogout(player);
            JojaCDService.handleClose(player, false);
            PlayerDataManager.get().removePlayerData(player.getUUID());
        }
    }
}
