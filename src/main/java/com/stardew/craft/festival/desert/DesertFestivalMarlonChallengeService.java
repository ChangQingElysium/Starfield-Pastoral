package com.stardew.craft.festival.desert;

import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalMarlonChallengesPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DesertFestivalMarlonChallengeService {
    private static final int SKULL_CAVERN_FIRST_FLOOR = 121;
    private static final String INTRO_FLAG = "Desert_Festival_Marlon";

    private static final Map<String, ChallengeDefinition> DEFINITIONS = Map.of(
        "slay_serpent", new ChallengeDefinition("slay_serpent", ChallengeType.SLAY,
            "stardewcraft.desert_festival.marlon.challenge.title.slay",
            "stardewcraft.desert_festival.marlon.challenge.text.slay",
            "stardewcraft.desert_festival.marlon.challenge.objective.slay",
            "stardewcraft.desert_festival.marlon.target.serpent", 10, 35,
            Set.of("sd_mob_serpent"), ""),
        "slay_sludge", new ChallengeDefinition("slay_sludge", ChallengeType.SLAY,
            "stardewcraft.desert_festival.marlon.challenge.title.slay",
            "stardewcraft.desert_festival.marlon.challenge.text.slay",
            "stardewcraft.desert_festival.marlon.challenge.objective.slay",
            "stardewcraft.desert_festival.marlon.target.sludge", 10, 35,
            Set.of("sd_mob_slime", "sd_tier_3"), ""),
        "slay_mummy", new ChallengeDefinition("slay_mummy", ChallengeType.SLAY,
            "stardewcraft.desert_festival.marlon.challenge.title.slay",
            "stardewcraft.desert_festival.marlon.challenge.text.slay",
            "stardewcraft.desert_festival.marlon.challenge.objective.slay",
            "stardewcraft.desert_festival.marlon.target.mummy", 10, 35,
            Set.of("sd_mob_mummy"), ""),
        "reach_30", new ChallengeDefinition("reach_30", ChallengeType.REACH_SKULL_DEPTH,
            "stardewcraft.desert_festival.marlon.challenge.title.reach",
            "stardewcraft.desert_festival.marlon.challenge.text.reach",
            "stardewcraft.desert_festival.marlon.challenge.objective.reach",
            "", 30, 50, Set.of(), ""),
        "collect_omni_geode", new ChallengeDefinition("collect_omni_geode", ChallengeType.COLLECT,
            "stardewcraft.desert_festival.marlon.challenge.title.collect",
            "stardewcraft.desert_festival.marlon.challenge.text.collect",
            "stardewcraft.desert_festival.marlon.challenge.objective.collect",
            "item.stardewcraft.omni_geode", 12, 40, Set.of(), "stardewcraft:omni_geode"),
        "collect_iridium_ore", new ChallengeDefinition("collect_iridium_ore", ChallengeType.COLLECT,
            "stardewcraft.desert_festival.marlon.challenge.title.collect",
            "stardewcraft.desert_festival.marlon.challenge.text.collect",
            "stardewcraft.desert_festival.marlon.challenge.objective.collect",
            "item.stardewcraft.iridium_ore", 15, 40, Set.of(), "stardewcraft:iridium_ore")
    );

    private DesertFestivalMarlonChallengeService() {
    }

    public static void openChallengeDialog(ServerPlayer player) {
        if (player == null) return;
        if (!DesertFestivalService.isFestivalOpen()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.not_festival"));
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(INTRO_FLAG)) {
            data.addMailFlag(INTRO_FLAG);
            PlayerDataEventHandler.syncPlayerData(player, data);
            com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player,
                new OpenNpcDialogueScreenPayload("marlon", "stardewcraft.desert_festival.marlon.challenge.intro", 0));
            return;
        }
        List<OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry> entries = availableToday(player).stream()
            .map(DesertFestivalMarlonChallengeService::toPayloadEntry)
            .toList();
        ActiveChallenge active = activeChallenge(data);
        if (active != null && active.rewardClaimed()) {
            int festivalDay = FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID);
            com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload("marlon", festivalDay == 3
                ? "stardewcraft.desert_festival.marlon.challenge.finished_last_day"
                : "stardewcraft.desert_festival.marlon.challenge.finished", 0));
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalMarlonChallengesPayload(
            entries,
            active == null ? "" : active.definition().id(),
            active == null ? 0 : active.progress(),
            active != null && active.rewardClaimed()
        ));
    }

    public static void handleChallengeChoice(ServerPlayer player, String challengeId, boolean claimReward) {
        if (player == null || challengeId == null || challengeId.isBlank()) return;
        if (!DesertFestivalService.isFestivalOpen()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.not_festival"));
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (claimReward) {
            claimReward(player, data, challengeId);
            return;
        }
        ActiveChallenge active = activeChallenge(data);
        if (active != null) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.challenge.already_active"));
            openChallengeDialog(player);
            return;
        }
        ChallengeDefinition definition = definition(challengeId);
        if (definition == null || availableToday(player).stream().noneMatch(entry -> entry.id().equals(challengeId))) {
            return;
        }
        data.setDesertFestivalMarlonChallenge(currentAbsoluteDay(), challengeId, 0, false);
        PlayerDataEventHandler.syncPlayerData(player, data);
        player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.challenge.accepted"));
    }

    public static void recordMonsterSlain(ServerPlayer player, Set<String> tags) {
        if (player == null || tags == null || !DesertFestivalService.isFestivalOpen()) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        ActiveChallenge active = activeChallenge(data);
        if (active == null || active.definition().type() != ChallengeType.SLAY || active.rewardClaimed()) return;
        if (!tags.containsAll(active.definition().requiredTags())) return;
        addProgress(player, data, active.definition(), active.progress() + 1);
    }

    public static void recordItemReceived(ServerPlayer player, String itemId, int count) {
        if (player == null || itemId == null || count <= 0 || !DesertFestivalService.isFestivalOpen()) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        ActiveChallenge active = activeChallenge(data);
        if (active == null || active.definition().type() != ChallengeType.COLLECT || active.rewardClaimed()) return;
        if (!active.definition().itemId().equals(itemId)) return;
        addProgress(player, data, active.definition(), active.progress() + count);
    }

    public static void recordSkullCavernFloorReached(ServerPlayer player, int targetFloor) {
        if (player == null || !DesertFestivalService.isFestivalOpen()) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        ActiveChallenge active = activeChallenge(data);
        if (active == null || active.definition().type() != ChallengeType.REACH_SKULL_DEPTH || active.rewardClaimed()) return;
        int depth = Math.max(0, targetFloor - SKULL_CAVERN_FIRST_FLOOR);
        if (depth <= active.progress()) return;
        addProgress(player, data, active.definition(), depth);
    }

    private static void claimReward(ServerPlayer player, PlayerStardewData data, String challengeId) {
        ActiveChallenge active = activeChallenge(data);
        if (active == null || !active.definition().id().equals(challengeId)) return;
        if (active.rewardClaimed()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.challenge.already_claimed"));
            return;
        }
        if (active.progress() < active.definition().targetCount()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.challenge.incomplete"));
            return;
        }
        data.setDesertFestivalMarlonChallengeRewardClaimed(true);
        DesertFestivalService.giveEggs(player, active.definition().rewardEggs());
        PlayerDataEventHandler.syncPlayerData(player, data);
        int festivalDay = FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID);
        player.sendSystemMessage(Component.translatable(festivalDay == 3
            ? "stardewcraft.desert_festival.marlon.challenge.finished_last_day"
            : "stardewcraft.desert_festival.marlon.challenge.finished"));
        openChallengeDialog(player);
    }

    private static void addProgress(ServerPlayer player, PlayerStardewData data, ChallengeDefinition definition, int progress) {
        int previous = Math.min(data.getDesertFestivalMarlonChallengeProgress(), definition.targetCount());
        int updated = Math.min(Math.max(0, progress), definition.targetCount());
        if (updated <= previous) return;
        data.setDesertFestivalMarlonChallengeProgress(updated);
        if (updated >= definition.targetCount() && previous < definition.targetCount()) {
            data.setDesertFestivalMarlonChallengeRewardClaimed(true);
            DesertFestivalService.giveEggs(player, definition.rewardEggs());
            PlayerDataEventHandler.syncPlayerData(player, data);
            player.sendSystemMessage(Component.translatable(
                "stardewcraft.desert_festival.marlon.challenge.complete", definition.rewardEggs()));
        } else {
            PlayerDataEventHandler.syncPlayerData(player, data);
            player.sendSystemMessage(Component.translatable(
                "stardewcraft.desert_festival.marlon.challenge.progress", updated, definition.targetCount()));
        }
    }

    private static ActiveChallenge activeChallenge(PlayerStardewData data) {
        if (data == null || data.getDesertFestivalMarlonChallengeDay() != currentAbsoluteDay()) return null;
        ChallengeDefinition definition = definition(data.getDesertFestivalMarlonChallengeId());
        if (definition == null) return null;
        return new ActiveChallenge(definition,
            Math.min(data.getDesertFestivalMarlonChallengeProgress(), definition.targetCount()),
            data.isDesertFestivalMarlonChallengeRewardClaimed());
    }

    private static List<ChallengeDefinition> availableToday(ServerPlayer player) {
        int day = currentAbsoluteDay();
        long worldSeed = player.serverLevel().getSeed();
        java.util.Random orderRandom = new java.util.Random(worldSeed ^ Double.doubleToLongBits(day * 1.3D));
        List<String> orderKeys = new ArrayList<>();
        Collections.addAll(orderKeys, "slay", "reach", "collect");
        List<ChallengeDefinition> result = new ArrayList<>(2);
        for (int i = 0; i < 2 && !orderKeys.isEmpty(); i++) {
            String orderKey = orderKeys.remove(orderRandom.nextInt(orderKeys.size()));
            int generationSeed = orderRandom.nextInt();
            ChallengeDefinition definition = switch (orderKey) {
                case "slay" -> slayVariant(generationSeed);
                case "collect" -> collectVariant(generationSeed);
                default -> definition("reach_30");
            };
            if (definition != null) {
                result.add(definition);
            }
        }
        return List.copyOf(result);
    }

    private static ChallengeDefinition slayVariant(int generationSeed) {
        return switch (new java.util.Random(generationSeed).nextInt(3)) {
            case 0 -> definition("slay_serpent");
            case 1 -> definition("slay_sludge");
            default -> definition("slay_mummy");
        };
    }

    private static ChallengeDefinition collectVariant(int generationSeed) {
        return new java.util.Random(generationSeed).nextInt(2) == 0
            ? definition("collect_omni_geode")
            : definition("collect_iridium_ore");
    }

    private static OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry toPayloadEntry(ChallengeDefinition definition) {
        return new OpenDesertFestivalMarlonChallengesPayload.ChallengeEntry(
            definition.id(), definition.titleKey(), definition.textKey(), definition.objectiveKey(), definition.targetKey(),
            definition.targetCount(), definition.rewardEggs());
    }

    private static ChallengeDefinition definition(String id) {
        return id == null ? null : DEFINITIONS.get(id);
    }

    private static int currentAbsoluteDay() {
        return StardewTimeManager.get().getAbsoluteDay();
    }

    private enum ChallengeType {
        SLAY,
        REACH_SKULL_DEPTH,
        COLLECT
    }

    private record ChallengeDefinition(String id, ChallengeType type, String titleKey, String textKey, String objectiveKey,
                                       String targetKey, int targetCount, int rewardEggs,
                                       Set<String> requiredTags, String itemId) {
    }

    private record ActiveChallenge(ChallengeDefinition definition, int progress, boolean rewardClaimed) {
    }
}