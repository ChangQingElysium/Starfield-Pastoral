package com.stardew.craft.leaderboard;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.mining.MiningDataManager;
import com.stardew.craft.mining.MiningPlayerData;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LeaderboardService {
    private static final int DEFAULT_LIMIT = 10;
    private static final long CACHE_TTL_MILLIS = 5000L;
    private static final Map<CacheKey, CachedSnapshot> CACHE = new HashMap<>();

    private LeaderboardService() {
    }

    public static void invalidateCache() {
        CACHE.clear();
    }

    public static LeaderboardSnapshot buildSnapshot(ServerPlayer requester, LeaderboardMetric metric, LeaderboardPeriod period, int page) {
        int safePage = Math.max(0, page);
        LeaderboardPeriod safePeriod = metric.supportsPeriod(period) ? period : LeaderboardPeriod.TOTAL;
        CacheKey key = new CacheKey(requester.getUUID(), metric, safePeriod, safePage);
        CachedSnapshot cached = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAtMillis() <= CACHE_TTL_MILLIS) {
            return cached.snapshot();
        }

        PlayerDataManager playerDataManager = PlayerDataManager.get();
        Map<UUID, PlayerStardewData> playerData = playerDataManager.getAllPlayerData();
        Map<UUID, MiningPlayerData> miningData = MiningDataManager.getAllPlayerData(requester);
        AnimalWorldData animalData = AnimalWorldData.get(requester.serverLevel());

        Set<UUID> playerIds = new HashSet<>();
        playerIds.addAll(playerData.keySet());
        playerIds.addAll(miningData.keySet());
        requester.server.getPlayerList().getPlayers().forEach(player -> playerIds.add(player.getUUID()));

        ArrayList<UnrankedEntry> unranked = new ArrayList<>();
        for (UUID playerId : playerIds) {
            PlayerStardewData data = playerData.get(playerId);
            long value = valueFor(metric, safePeriod, data, miningData.get(playerId), animalData, playerId);
            unranked.add(new UnrankedEntry(playerId, resolvePlayerName(requester.server, playerId), value,
                    requester.server.getPlayerList().getPlayer(playerId) != null,
                    requester.getUUID().equals(playerId)));
        }

        Comparator<UnrankedEntry> valueComparator = Comparator.comparingLong(UnrankedEntry::value);
        if (metric.descending()) {
            valueComparator = valueComparator.reversed();
        }
        unranked.sort(valueComparator
            .thenComparing(UnrankedEntry::playerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.playerId().toString()));

        ArrayList<LeaderboardEntry> ranked = new ArrayList<>(unranked.size());
        LeaderboardEntry selfEntry = null;
        for (int i = 0; i < unranked.size(); i++) {
            UnrankedEntry entry = unranked.get(i);
            LeaderboardEntry rankedEntry = new LeaderboardEntry(
                    entry.playerId(), entry.playerName(), entry.value(), i + 1, entry.online(), entry.self());
            ranked.add(rankedEntry);
            if (entry.self()) {
                selfEntry = rankedEntry;
            }
        }

        int from = safePage * DEFAULT_LIMIT;
        int to = Math.min(ranked.size(), from + DEFAULT_LIMIT);
        var rows = from >= ranked.size() ? java.util.List.<LeaderboardEntry>of() : java.util.List.copyOf(ranked.subList(from, to));
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot(metric, safePeriod, rows, selfEntry, ranked.size(), now);
        CACHE.put(key, new CachedSnapshot(snapshot, now));
        return snapshot;
    }

    private static long valueFor(LeaderboardMetric metric, LeaderboardPeriod period, PlayerStardewData playerData,
                                 MiningPlayerData miningData, AnimalWorldData animalData, UUID playerId) {
        if (period != null && !period.isTotal()) {
            return playerData == null ? 0L : playerData.getLeaderboardPeriodValue(period, metric.id());
        }
        return switch (metric) {
            case MONEY -> playerData == null ? 0L : playerData.getMoney();
            case MINE_DEPTH -> miningData == null ? 0L : miningData.getMaxFloorReached();
            case MINE_BLOCKS_BROKEN -> playerData == null ? 0L : playerData.getMineBlocksBrokenTotal();
            case MINE_STONES_BROKEN -> playerData == null ? 0L : playerData.getMineStonesBroken();
            case MINE_ORES_BROKEN -> playerData == null ? 0L : playerData.getMineOresBroken();
            case MINE_GEM_ORES_BROKEN -> playerData == null ? 0L : playerData.getMineGemOresBroken();
            case MINE_MINERAL_NODES_BROKEN -> playerData == null ? 0L : playerData.getMineMineralNodesBroken();
            case MINE_BLOCKS_BOMBED -> playerData == null ? 0L : playerData.getMineBlocksBombed();
            case FISH_CAUGHT -> playerData == null ? 0L : playerData.getPreciseFishCaught();
            case ITEMS_SHIPPED -> playerData == null ? 0L : playerData.getAllItemsShipped().values().stream().mapToLong(Integer::longValue).sum();
            case SHIPPING_VALUE -> playerData == null ? 0L : playerData.getTotalShippingGold();
            case SHIPPING_VARIETY -> playerData == null ? 0L : playerData.getShippedBasic().size();
            case MONSTERS_SLAIN -> playerData == null ? 0L : playerData.getAllMonsterKills().values().stream().mapToLong(Integer::longValue).sum();
            case SKILL_FARMING -> skillExperience(playerData, SkillType.FARMING);
            case SKILL_FISHING -> skillExperience(playerData, SkillType.FISHING);
            case SKILL_FORAGING -> skillExperience(playerData, SkillType.FORAGING);
            case SKILL_MINING -> skillExperience(playerData, SkillType.MINING);
            case SKILL_COMBAT -> skillExperience(playerData, SkillType.COMBAT);
            case GIFTS_GIVEN -> playerData == null ? 0L : playerData.getGiftsGivenTotal();
            case COOKING_COUNT -> playerData == null ? 0L : cookingCount(playerData);
            case ANIMALS_OWNED -> animalsOwned(animalData, playerId);
            case ANIMAL_PRODUCTS_COLLECTED -> playerData == null ? 0L : playerData.getAnimalProductsCollectedTotal();
            case PASS_OUTS -> playerData == null ? 0L : playerData.getPassOutsTotal();
            case COMBAT_DEATHS -> playerData == null ? 0L : playerData.getCombatDeaths();
            case BASEMENT_SHORTS_STOLEN -> playerData == null ? 0L : playerData.getBasementShortsStolen();
            case TRASH_CANS_CHECKED -> playerData == null ? 0L : playerData.getTrashCansChecked();
        };
    }

    private static long skillExperience(PlayerStardewData playerData, SkillType skill) {
        return playerData == null ? 0L : playerData.getSkillExperience(skill);
    }

    private static long cookingCount(PlayerStardewData playerData) {
        return RecipeCatalogData.getCookingRecipeIds().stream()
                .mapToLong(recipeId -> playerData.getRecipeCraftCount(recipeId))
                .sum();
    }

    private static long animalsOwned(AnimalWorldData animalData, UUID playerId) {
        if (animalData == null || playerId == null) {
            return 0L;
        }
        String owner = playerId.toString();
        return animalData.getBuildings().stream()
                .filter(building -> owner.equals(building.ownerPlayerUuid()))
                .mapToLong(building -> building.memberAnimalIds().size())
                .sum();
    }

    private static String resolvePlayerName(MinecraftServer server, UUID playerId) {
        return PlayerDisplayName.get(server, playerId);
    }

    private record UnrankedEntry(UUID playerId, String playerName, long value, boolean online, boolean self) {
    }

    private record CacheKey(UUID requesterId, LeaderboardMetric metric, LeaderboardPeriod period, int page) {
    }

    private record CachedSnapshot(LeaderboardSnapshot snapshot, long createdAtMillis) {
    }
}
