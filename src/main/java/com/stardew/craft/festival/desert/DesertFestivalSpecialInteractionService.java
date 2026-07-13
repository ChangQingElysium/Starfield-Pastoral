package com.stardew.craft.festival.desert;

import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.totem.TeleportTotemItem;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.festival.FestivalFarmWarperDisplayService;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@SuppressWarnings("null")
public final class DesertFestivalSpecialInteractionService {
    public static final String SCHOLAR_TARGET_ID = "desert_festival_scholar";
    public static final String SCHOLAR_MARKER_TAG = "sdv_festival_marker:desert_scholar";
    public static final BlockPos SCHOLAR_INTERACTION_MIN = new BlockPos(-196, 64, -210);
    public static final BlockPos SCHOLAR_INTERACTION_MAX = new BlockPos(-196, 65, -210);

    public static final String WARPER_TARGET_ID = "desert_festival_warper";
    public static final String WARPER_MARKER_TAG = "sdv_festival_marker:desert_warper";
    public static final BlockPos WARPER_INTERACTION_POS = new BlockPos(-208, 65, -214);

    public static final String FESTIVAL_TRAVELING_CART_MARKER_TAG = "stardewcraft_desert_festival_traveling_cart";
    private static final int FESTIVAL_TRAVELING_CART_OPEN_TIME = 1200;
    private static final double FESTIVAL_TRAVELING_CART_X = -191.0D;
    private static final double FESTIVAL_TRAVELING_CART_Y = 64.0D;
    private static final double FESTIVAL_TRAVELING_CART_Z = -198.0D;
    private static final float TRAVELING_CART_FACING_YAW = 90.0F;

    private static final String WARPER_DISPLAY_TAG = "sdv_festival_marker:desert_warper_display";
    private static final double WARPER_DISPLAY_X = -207.5D;
    private static final double WARPER_DISPLAY_Y = 65.6875D;
    private static final double WARPER_DISPLAY_Z = -213.4375D;
    private static final Vec3 WARPER_DISPLAY_POS = new Vec3(WARPER_DISPLAY_X, WARPER_DISPLAY_Y, WARPER_DISPLAY_Z);

    private static final String SCHOLAR_YEAR_KEY = "desert_festival_scholar_year";
    private static final String SCHOLAR_STATE_KEY = "desert_festival_scholar_state";
    private static final int SCHOLAR_FAILED = 98;
    private static final int SCHOLAR_DONE = 99;
    private static final int SCHOLAR_QUESTIONS = 4;

    private static final String YES_ID = "yes";
    private static final String NO_ID = "no";
    private static final String CORRECT_ID = "correct";
    private static final String WRONG_ID = "wrong";

    private static final ScholarQuestion[][] SCHOLAR_DATA = new ScholarQuestion[][] {
        {
            new ScholarQuestion("0.0", 6, 8),
            new ScholarQuestion("0.1", 0, 4),
            new ScholarQuestion("0.2", 0, 9)
        },
        {
            new ScholarQuestion("1.0", 3, 3),
            new ScholarQuestion("1.1", 3, 3),
            new ScholarQuestion("1.2", 0, 7)
        },
        {
            new ScholarQuestion("2.0", 4, 4),
            new ScholarQuestion("2.1", 0, 0),
            new ScholarQuestion("2.2", 5, 7)
        },
        {
            new ScholarQuestion("3.0", 3, 6),
            new ScholarQuestion("3.1", 0, 5),
            new ScholarQuestion("3.2", 0, 6)
        }
    };

    private DesertFestivalSpecialInteractionService() {
    }

    public static void openScholar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!DesertFestivalService.isFestivalOpen()) {
            sendDialogue(player, "scholar", "stardewcraft.desert_festival.scholar.closed");
            return;
        }

        int state = scholarStateForCurrentYear(player);
        if (state == SCHOLAR_DONE) {
            sendDialogue(player, "scholar", "stardewcraft.desert_festival.scholar.done_this_year");
            return;
        }
        if (state == SCHOLAR_FAILED) {
            sendDialogue(player, "scholar", "stardewcraft.desert_festival.scholar.failed");
            return;
        }
        if (state >= 1 && state <= SCHOLAR_QUESTIONS) {
            sendScholarQuestion(player, state - 1, "");
            return;
        }

        sendQuestion(player, "scholar_intro", -1, "", Component.translatable("stardewcraft.desert_festival.scholar.intro"), List.of(
            response(YES_ID, Component.translatable("message.stardewcraft.festival.confirm.yes"), player),
            response(NO_ID, Component.translatable("message.stardewcraft.festival.confirm.no"), player)
        ));
    }

    public static void openWarper(ServerPlayer player) {
        if (player == null || !DesertFestivalService.isFestivalOpen()) {
            return;
        }
        sendQuestion(player, "warper", -1, "", Component.translatable("stardewcraft.desert_festival.warper.question"), List.of(
            response(YES_ID, Component.translatable("message.stardewcraft.festival.confirm.yes"), player),
            response(NO_ID, Component.translatable("message.stardewcraft.festival.confirm.no"), player)
        ));
    }

    public static void handleQuestionResponse(ServerPlayer player, String context, int questionIndex, String choiceId) {
        if (player == null || context == null || choiceId == null) {
            return;
        }
        if ("scholar_intro".equals(context)) {
            if (YES_ID.equals(choiceId)) {
                sendScholarQuestion(player, 0, "stardewcraft.desert_festival.scholar.intro2");
            }
            return;
        }
        if ("scholar".equals(context)) {
            handleScholarAnswer(player, questionIndex, choiceId);
            return;
        }
        if ("warper".equals(context) && YES_ID.equals(choiceId)) {
            handleWarperYes(player);
        }
    }

    public static void spawnWarperDisplay(ServerLevel level) {
        FestivalFarmWarperDisplayService.spawn(level, WARPER_DISPLAY_TAG, WARPER_DISPLAY_POS);
    }

    public static void spawnWarperInteraction(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (hasWarperInteraction(level)) {
            return;
        }
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:interaction");
            tag.putFloat("width", 1.0F);
            tag.putFloat("height", 1.0F);
            tag.putBoolean("response", true);

            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(WARPER_INTERACTION_POS.getX() + 0.5D));
            pos.add(DoubleTag.valueOf(WARPER_INTERACTION_POS.getY()));
            pos.add(DoubleTag.valueOf(WARPER_INTERACTION_POS.getZ() + 0.5D));
            tag.put("Pos", pos);

            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(WARPER_MARKER_TAG));
            tag.put("Tags", tags);

            Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
            if (entity != null) {
                level.addFreshEntity(entity);
            }
        } catch (Exception e) {
            StardewCraft.LOGGER.error("[DESERT_FESTIVAL] Failed to spawn warper interaction", e);
        }
    }

    public static void removeWarperInteraction(ServerLevel level) {
        if (level == null) {
            return;
        }
        AABB box = new AABB(WARPER_INTERACTION_POS).inflate(1.0D);
        level.getEntitiesOfClass(Entity.class, box,
            entity -> entity.getTags().contains(WARPER_MARKER_TAG))
            .forEach(Entity::discard);
    }

    public static void spawnFestivalTravelingCart(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (!isFestivalTravelingCartPresent()) {
            removeFestivalTravelingCart(level);
            return;
        }
        if (hasFestivalTravelingCart(level)) {
            return;
        }
        removeFestivalTravelingCart(level);
        TravelingCartEntity cart = ModEntities.TRAVELING_CART.get().create(level);
        if (cart == null) {
            return;
        }
        cart.moveTo(FESTIVAL_TRAVELING_CART_X, FESTIVAL_TRAVELING_CART_Y, FESTIVAL_TRAVELING_CART_Z,
            TRAVELING_CART_FACING_YAW, 0.0F);
        cart.setYHeadRot(TRAVELING_CART_FACING_YAW);
        cart.setYBodyRot(TRAVELING_CART_FACING_YAW);
        cart.setNoAi(true);
        cart.setInvulnerable(true);
        cart.setPersistenceRequired();
        cart.setSilent(true);
        cart.setCustomName(Component.translatable("entity.stardewcraft.traveling_cart"));
        cart.setCustomNameVisible(false);
        cart.addTag(FESTIVAL_TRAVELING_CART_MARKER_TAG);
        level.addFreshEntity(cart);
    }

    public static void syncFestivalTravelingCart(ServerLevel level) {
        if (level == null) {
            return;
        }
        if (isFestivalTravelingCartPresent()) {
            spawnFestivalTravelingCart(level);
        } else {
            removeFestivalTravelingCart(level);
        }
    }

    public static void removeFestivalTravelingCart(ServerLevel level) {
        if (level == null) {
            return;
        }
        AABB box = new AABB(
            FESTIVAL_TRAVELING_CART_X - 4.0D, FESTIVAL_TRAVELING_CART_Y - 4.0D, FESTIVAL_TRAVELING_CART_Z - 4.0D,
            FESTIVAL_TRAVELING_CART_X + 4.0D, FESTIVAL_TRAVELING_CART_Y + 4.0D, FESTIVAL_TRAVELING_CART_Z + 4.0D
        );
        level.getEntitiesOfClass(TravelingCartEntity.class, box,
            entity -> entity.getTags().contains(FESTIVAL_TRAVELING_CART_MARKER_TAG))
            .forEach(Entity::discard);
    }

    public static void removeWarperDisplay(ServerLevel level) {
        FestivalFarmWarperDisplayService.remove(level, WARPER_DISPLAY_TAG, WARPER_DISPLAY_POS);
    }

    private static boolean hasWarperInteraction(ServerLevel level) {
        AABB box = new AABB(WARPER_INTERACTION_POS).inflate(1.0D);
        return !level.getEntitiesOfClass(Entity.class, box,
            entity -> entity.getTags().contains(WARPER_MARKER_TAG)).isEmpty();
    }

    private static boolean hasFestivalTravelingCart(ServerLevel level) {
        AABB box = new AABB(
            FESTIVAL_TRAVELING_CART_X - 4.0D, FESTIVAL_TRAVELING_CART_Y - 4.0D, FESTIVAL_TRAVELING_CART_Z - 4.0D,
            FESTIVAL_TRAVELING_CART_X + 4.0D, FESTIVAL_TRAVELING_CART_Y + 4.0D, FESTIVAL_TRAVELING_CART_Z + 4.0D
        );
        return !level.getEntitiesOfClass(TravelingCartEntity.class, box,
            entity -> entity.getTags().contains(FESTIVAL_TRAVELING_CART_MARKER_TAG)).isEmpty();
    }

    public static boolean isFestivalTravelingCartShopOpen() {
        return isFestivalTravelingCartPresent()
            && FestivalService.currentTimeOfDay() >= FESTIVAL_TRAVELING_CART_OPEN_TIME;
    }

    private static boolean isFestivalTravelingCartPresent() {
        return DesertFestivalService.isFestivalOpen();
    }

    private static void handleScholarAnswer(ServerPlayer player, int questionIndex, String choiceId) {
        int state = scholarStateForCurrentYear(player);
        if (questionIndex < 0 || questionIndex >= SCHOLAR_QUESTIONS || state != questionIndex + 1) {
            openScholar(player);
            return;
        }

        if (!CORRECT_ID.equals(choiceId)) {
            setScholarState(player, SCHOLAR_FAILED);
            sendDialogue(player, "scholar", "stardewcraft.desert_festival.scholar.wrong");
            return;
        }

        if (questionIndex + 1 >= SCHOLAR_QUESTIONS) {
            setScholarState(player, SCHOLAR_DONE);
            DesertFestivalService.giveEggs(player, 50);
            sendDialogue(player, "scholar", "stardewcraft.desert_festival.scholar.win");
            return;
        }

        sendScholarQuestion(player, questionIndex + 1, "stardewcraft.desert_festival.scholar.correct");
    }

    private static void handleWarperYes(ServerPlayer player) {
        if (PlayerStardewDataAPI.getMoney(player) < 250) {
            sendDialogue(player, "warper", "stardewcraft.desert_festival.warper.no_money");
            return;
        }
        PlayerStardewDataAPI.removeMoney(player, 250);
        if (ModItems.WARP_TOTEM_FARM.get() instanceof TeleportTotemItem totem
            && totem.getTotemType() == TotemType.FARM) {
            totem.performFreeWarp(player);
        }
    }

    private static void sendScholarQuestion(ServerPlayer player, int questionIndex, String preDialogueKey) {
        int whichQuestion = scholarVariant(player);
        ScholarQuestion question = SCHOLAR_DATA[questionIndex][whichQuestion];
        Random random = scholarRandom(player);
        random.nextInt(3);

        int optionIndex = 0;
        Component questionText;
        if (question.optionCount() > 0) {
            optionIndex = random.nextInt(question.optionCount());
            questionText = Component.translatable(questionKey(question.id()), Component.translatable(optionKey(question.id(), optionIndex)));
        } else {
            questionText = Component.translatable(questionKey(question.id()));
        }

        List<OpenDesertFestivalQuestionPayload.ResponseOption> responses = new ArrayList<>();
        if (questionIndex == 2 && whichQuestion == 1) {
            int steps = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) / 100);
            responses.add(response(CORRECT_ID, Component.literal(Integer.toString(steps)), player));
            responses.add(response(WRONG_ID, Component.literal(Integer.toString(steps * 2)), player));
            responses.add(response(WRONG_ID, Component.literal(Integer.toString(steps / 2)), player));
        } else {
            responses.add(response(CORRECT_ID, Component.translatable(answerKey(question.id(), optionIndex)), player));
            int firstWrong = optionIndex;
            while (firstWrong == optionIndex) {
                firstWrong = random.nextInt(question.answerCount());
            }
            responses.add(response(WRONG_ID, Component.translatable(answerKey(question.id(), firstWrong)), player));
            int secondWrong = optionIndex;
            while (secondWrong == optionIndex || secondWrong == firstWrong) {
                secondWrong = random.nextInt(question.answerCount());
            }
            responses.add(response(WRONG_ID, Component.translatable(answerKey(question.id(), secondWrong)), player));
        }
        Collections.shuffle(responses, random);
        setScholarState(player, questionIndex + 1);
        sendQuestion(player, "scholar", questionIndex, preDialogueKey, questionText, responses);
    }

    private static int scholarVariant(ServerPlayer player) {
        int whichQuestion = scholarRandom(player).nextInt(3);
        whichQuestion += StardewTimeManager.get().getCurrentYear();
        return Math.floorMod(whichQuestion, 3);
    }

    private static Random scholarRandom(ServerPlayer player) {
        long seed = player.server.overworld().getSeed();
        return new Random(seed);
    }

    private static int scholarStateForCurrentYear(ServerPlayer player) {
        int year = Math.max(1, StardewTimeManager.get().getCurrentYear());
        if (PlayerStardewDataAPI.getStat(player, SCHOLAR_YEAR_KEY) != year) {
            PlayerStardewDataAPI.setStat(player, SCHOLAR_YEAR_KEY, year);
            PlayerStardewDataAPI.setStat(player, SCHOLAR_STATE_KEY, 0);
            return 0;
        }
        return PlayerStardewDataAPI.getStat(player, SCHOLAR_STATE_KEY);
    }

    private static void setScholarState(ServerPlayer player, int state) {
        PlayerStardewDataAPI.setStat(player, SCHOLAR_YEAR_KEY, Math.max(1, StardewTimeManager.get().getCurrentYear()));
        PlayerStardewDataAPI.setStat(player, SCHOLAR_STATE_KEY, state);
    }

    private static void sendQuestion(ServerPlayer player, String context, int questionIndex, String preDialogueKey,
                                     Component question, List<OpenDesertFestivalQuestionPayload.ResponseOption> responses) {
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            context,
            questionIndex,
            preDialogueKey == null ? "" : preDialogueKey,
            Component.Serializer.toJson(question, player.registryAccess()),
            responses
        ));
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(String id, Component label, ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
    }

    private static void sendDialogue(ServerPlayer player, String npcId, String key) {
        PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(npcId, key, 0));
    }

    private static String questionKey(String id) {
        return "stardewcraft.desert_festival.scholar.question." + id;
    }

    private static String optionKey(String id, int index) {
        return "stardewcraft.desert_festival.scholar.question." + id + ".option." + index;
    }

    private static String answerKey(String id, int index) {
        return "stardewcraft.desert_festival.scholar.question." + id + ".answer." + index;
    }

    private record ScholarQuestion(String id, int optionCount, int answerCount) {
    }
}
