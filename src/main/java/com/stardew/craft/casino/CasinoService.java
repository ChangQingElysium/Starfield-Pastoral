package com.stardew.craft.casino;

import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.CasinoGameActionPayload;
import com.stardew.craft.network.payload.CasinoGameStatePayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Server-authoritative implementation of the original Club computer, Calico Jack and Slots. */
public final class CasinoService {
    private static final int CALICO_NORMAL_BET = 100;
    private static final int CALICO_HIGH_STAKES_BET = 1000;
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();
    private static final Map<UUID, CasinoSession> SESSIONS = new ConcurrentHashMap<>();

    private CasinoService() {
    }

    public static void openFarmerFile(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        StardewTimeManager time = StardewTimeManager.get();
        int stepsTaken = Math.max(0, (
                player.getStats().getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                        + player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                        + player.getStats().getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))
        ) / 100);
        Map<String, Integer> recipeCounts = data.getAllRecipeCraftCounts();
        int itemsCooked = recipeCounts.entrySet().stream()
                .filter(entry -> RecipeCatalogData.getCookingRecipeIds().contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        int itemsCrafted = recipeCounts.values().stream().mapToInt(Integer::intValue).sum() - itemsCooked;
        int recycled = Math.max(data.getStat("PiecesOfTrashRecycled"), data.getStat("piecesOfTrashRecycled"));
        int monstersKilled = data.getStat("MonstersKilled");
        int timesFished = Math.max(data.getStat("TimesFished"), data.getStat("timesFished"));
        int seedsSown = Math.max(data.getStat("SeedsSown"), data.getStat("seedsSown"));
        int dirtHoed = Math.max(data.getStat("DirtHoed"), data.getStat("dirtHoed"));
        int itemsShipped = data.getAllItemsShipped().values().stream().mapToInt(Integer::intValue).sum();

        ObjectDialogueService.show(player, List.of(
                Component.translatable(
                        "stardewcraft.casino.farmer_file.1",
                        player.getGameProfile().getName(),
                        stepsTaken,
                        data.getGiftsGivenTotal(),
                        time.getAbsoluteDay(),
                        dirtHoed,
                        itemsCrafted,
                        itemsCooked,
                        recycled
                ),
                Component.translatable(
                        "stardewcraft.casino.farmer_file.2",
                        monstersKilled,
                        data.getPreciseFishCaught(),
                        timesFished,
                        seedsSown,
                        itemsShipped
                )
        ));
    }

    public static void openCalicoJack(ServerPlayer player, boolean highStakes) {
        PacketDistributor.sendToPlayer(player,
                new com.stardew.craft.network.payload.OpenCalicoJackPromptPayload(highStakes));
    }

    private static void startCalicoJack(ServerPlayer player, boolean highStakes) {
        int bet = highStakes ? CALICO_HIGH_STAKES_BET : CALICO_NORMAL_BET;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getClubCoins() < bet) {
            ObjectDialogueService.show(player, highStakes
                    ? "stardewcraft.casino.not_enough_club_coins_high_stakes"
                    : "stardewcraft.casino.not_enough_club_coins");
            return;
        }
        startCalicoRound(player, bet, highStakes);
    }

    public static void openSlots(ServerPlayer player) {
        SlotSession session = new SlotSession(nextSessionId(), new Random(seed(player, "slots")));
        // The original minigame rolls once while opening, so the cabinet doesn't
        // start with three blank reels and the first paid spin uses the next roll.
        rollSlots(player, session);
        SESSIONS.put(player.getUUID(), session);
        sendSlots(player, session, CasinoGameStatePayload.PHASE_SLOTS_IDLE);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        CasinoSession session = SESSIONS.remove(player.getUUID());
        if (session instanceof SlotSession slots && slots.awaitingCollection) {
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            data.addClubCoins(slots.bet * slots.payoutMultiplier);
        }
    }

    public static void clearSessions() {
        SESSIONS.clear();
    }

    public static void handleAction(ServerPlayer player, long sessionId, int action) {
        if (!CasinoAccessService.isCasinoPosition(player.getX(), player.getY(), player.getZ())) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        if (action == CasinoGameActionPayload.CALICO_START) {
            startCalicoJack(player, sessionId == 1L);
            return;
        }
        CasinoSession session = SESSIONS.get(player.getUUID());
        if (session == null || session.id() != sessionId) {
            return;
        }
        if (session instanceof CalicoSession calico) {
            handleCalicoAction(player, calico, action);
        } else if (session instanceof SlotSession slots) {
            handleSlotAction(player, slots, action);
        }
    }

    private static void handleCalicoAction(ServerPlayer player, CalicoSession session, int action) {
        if (action == CasinoGameActionPayload.CLOSE) {
            SESSIONS.remove(player.getUUID(), session);
            return;
        }
        if (session.result != 0) {
            if (action == CasinoGameActionPayload.CALICO_DOUBLE_OR_NOTHING && session.result > 0) {
                startCalicoRound(player, session.bet * 2, session.highStakes);
            } else if (action == CasinoGameActionPayload.CALICO_PLAY_AGAIN) {
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                if (data.getClubCoins() >= session.bet) {
                    startCalicoRound(player,
                            session.highStakes ? CALICO_HIGH_STAKES_BET : CALICO_NORMAL_BET,
                            session.highStakes);
                }
            }
            return;
        }
        if (action == CasinoGameActionPayload.CALICO_HIT) {
            int playerTotal = total(session.playerCards);
            int nextCard = 1 + session.random.nextInt(9);
            int distance = 21 - playerTotal;
            if (distance > 1 && distance < 6 && session.random.nextDouble() < 1.0D / distance) {
                nextCard = session.random.nextBoolean() ? distance : distance - 1;
            }
            session.playerCards.add(nextCard);
            if (total(session.playerCards) >= 21) {
                finishCalico(player, session);
            } else {
                sendCalico(player, session);
            }
        } else if (action == CasinoGameActionPayload.CALICO_STAND) {
            playDealerTurn(player, session);
            finishCalico(player, session);
        }
    }

    private static void playDealerTurn(ServerPlayer player, CalicoSession session) {
        int playerTotal = total(session.playerCards);
        int dealerTotal = total(session.dealerCards);
        while (dealerTotal < 18 || (dealerTotal < playerTotal && playerTotal <= 21)) {
            int nextCard = 1 + session.random.nextInt(9);
            int dealerDistance = 21 - dealerTotal;
            if (playerTotal == 20 && session.random.nextBoolean()) {
                nextCard = dealerDistance + 1 + session.random.nextInt(3);
            } else if (playerTotal == 19 && session.random.nextDouble() < 0.25D) {
                nextCard = dealerDistance + 1 + session.random.nextInt(3);
            } else if (playerTotal == 18 && session.random.nextDouble() < 0.1D) {
                nextCard = dealerDistance + 1 + session.random.nextInt(3);
            }
            double batChance = Math.max(0.0005D,
                    0.001D + PlayerStardewDataAPI.getDailyLuck(player) / 20.0D
                            + PlayerStardewDataAPI.getLuckLevel(player) * 0.002D);
            if (session.random.nextDouble() < batChance) {
                nextCard = 999;
                session.bet *= 3;
            }
            session.dealerCards.add(nextCard);
            dealerTotal += nextCard;
            if (dealerTotal > 21) {
                break;
            }
        }
    }

    private static void finishCalico(ServerPlayer player, CalicoSession session) {
        int playerTotal = total(session.playerCards);
        int dealerTotal = total(session.dealerCards);
        int outcome;
        if (playerTotal == 21) {
            outcome = 1;
        } else if (playerTotal > 21) {
            outcome = -1;
        } else if (dealerTotal > 21) {
            outcome = 1;
        } else if (playerTotal == dealerTotal) {
            outcome = 2;
        } else {
            outcome = playerTotal > dealerTotal ? 1 : -1;
        }
        session.result = outcome;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (outcome > 0 && outcome != 2) {
            data.addClubCoins(session.bet);
        } else if (outcome < 0) {
            data.addClubCoins(-session.bet);
        }
        sync(player, data);
        sendCalico(player, session);
    }

    private static void handleSlotAction(ServerPlayer player, SlotSession session, int action) {
        if (action == CasinoGameActionPayload.CLOSE) {
            if (session.awaitingCollection) {
                collectSlotResult(player, session);
            }
            SESSIONS.remove(player.getUUID(), session);
            return;
        }
        if (action == CasinoGameActionPayload.SLOTS_COLLECT && session.awaitingCollection) {
            collectSlotResult(player, session);
            sendSlots(player, session, CasinoGameStatePayload.PHASE_SLOTS_IDLE);
            return;
        }
        int bet = switch (action) {
            case CasinoGameActionPayload.SLOTS_SPIN_10 -> 10;
            case CasinoGameActionPayload.SLOTS_SPIN_100 -> 100;
            default -> 0;
        };
        if (bet == 0 || session.awaitingCollection) {
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.consumeClubCoins(bet)) {
            sendSlots(player, session, CasinoGameStatePayload.PHASE_SLOTS_IDLE);
            return;
        }
        data.incrementStat("TimesPlayedSlots", 1);
        session.bet = bet;
        rollSlots(player, session);
        session.awaitingCollection = true;
        sync(player, data);
        sendSlots(player, session, CasinoGameStatePayload.PHASE_SLOTS_SPINNING);
    }

    private static void collectSlotResult(ServerPlayer player, SlotSession session) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addClubCoins(session.bet * session.payoutMultiplier);
        if (session.payoutMultiplier == 2500) {
            player.server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("stardewcraft.casino.slots.jackpot", player.getGameProfile().getName()),
                    false);
        }
        session.awaitingCollection = false;
        sync(player, data);
    }

    private static void rollSlots(ServerPlayer player, SlotSession session) {
        double roll = session.random.nextDouble();
        double modifier = 1.0D + PlayerStardewDataAPI.getDailyLuck(player) * 2.0D
                + PlayerStardewDataAPI.getLuckLevel(player) * 0.08D;
        if (roll < 0.001D * modifier) {
            session.setAll(5, 2500);
        } else if (roll < 0.0016D * modifier) {
            session.setAll(6, 1000);
        } else if (roll < 0.0025D * modifier) {
            session.setAll(7, 500);
        } else if (roll < 0.005D * modifier) {
            session.setAll(4, 200);
        } else if (roll < 0.007D * modifier) {
            session.setAll(3, 120);
        } else if (roll < 0.01D * modifier) {
            session.setAll(2, 80);
        } else if (roll < 0.02D * modifier) {
            session.setAll(1, 30);
        } else if (roll < 0.12D * modifier) {
            int nonStar = session.random.nextInt(3);
            for (int i = 0; i < 3; i++) {
                session.slots[i] = i == nonStar ? session.random.nextInt(7) : 7;
            }
            session.payoutMultiplier = 3;
        } else if (roll < 0.2D * modifier) {
            session.setAll(0, 5);
        } else if (roll < 0.4D * modifier) {
            int star = session.random.nextInt(3);
            for (int i = 0; i < 3; i++) {
                session.slots[i] = i == star ? 7 : session.random.nextInt(7);
            }
            session.payoutMultiplier = 2;
        } else {
            int[] used = new int[8];
            for (int i = 0; i < 3; i++) {
                int next = session.random.nextInt(6);
                while (used[next] > 1) {
                    next = session.random.nextInt(6);
                }
                session.slots[i] = next;
                used[next]++;
            }
            session.payoutMultiplier = 0;
        }
    }

    private static void startCalicoRound(ServerPlayer player, int bet, boolean highStakes) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int timesPlayed = data.incrementStat("TimesPlayedCalicoJack", 1);
        Random random = new Random(seed(player, "calico_jack") ^ timesPlayed);
        CalicoSession session = new CalicoSession(nextSessionId(), random, Math.max(1, bet), highStakes);
        session.dealerCards.add(1 + random.nextInt(11));
        session.dealerCards.add(1 + random.nextInt(9));
        session.playerCards.add(1 + random.nextInt(11));
        session.playerCards.add(1 + random.nextInt(9));
        SESSIONS.put(player.getUUID(), session);
        sendCalico(player, session);
    }

    private static void sendCalico(ServerPlayer player, CalicoSession session) {
        int phase = session.result == 0
                ? CasinoGameStatePayload.PHASE_CALICO_PLAYING
                : CasinoGameStatePayload.PHASE_CALICO_RESULT;
        PacketDistributor.sendToPlayer(player, new CasinoGameStatePayload(
                CasinoGameStatePayload.GAME_CALICO_JACK,
                session.id,
                PlayerDataManager.getPlayerData(player).getClubCoins(),
                session.bet,
                session.highStakes,
                phase,
                session.result,
                List.copyOf(session.playerCards),
                List.copyOf(session.dealerCards),
                -1, -1, -1, 0
        ));
    }

    private static void sendSlots(ServerPlayer player, SlotSession session, int phase) {
        PacketDistributor.sendToPlayer(player, new CasinoGameStatePayload(
                CasinoGameStatePayload.GAME_SLOTS,
                session.id,
                PlayerDataManager.getPlayerData(player).getClubCoins(),
                session.bet,
                false,
                phase,
                0,
                List.of(),
                List.of(),
                session.slots[0],
                session.slots[1],
                session.slots[2],
                session.payoutMultiplier
        ));
    }

    private static int total(List<Integer> cards) {
        return cards.stream().mapToInt(Integer::intValue).sum();
    }

    private static long seed(ServerPlayer player, String salt) {
        long seed = player.serverLevel().getSeed();
        seed = seed * 31L + StardewTimeManager.get().getAbsoluteDay();
        seed = seed * 31L + player.getUUID().getMostSignificantBits();
        seed = seed * 31L + player.getUUID().getLeastSignificantBits();
        return seed * 31L + salt.hashCode();
    }

    private static long nextSessionId() {
        return NEXT_SESSION_ID.incrementAndGet();
    }

    private static void sync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private sealed interface CasinoSession permits CalicoSession, SlotSession {
        long id();
    }

    private static final class CalicoSession implements CasinoSession {
        private final long id;
        private final Random random;
        private final boolean highStakes;
        private final List<Integer> playerCards = new ArrayList<>();
        private final List<Integer> dealerCards = new ArrayList<>();
        private int bet;
        /** 0=playing, 1=win, -1=loss, 2=tie. */
        private int result;

        private CalicoSession(long id, Random random, int bet, boolean highStakes) {
            this.id = id;
            this.random = random;
            this.bet = bet;
            this.highStakes = highStakes;
        }

        @Override
        public long id() {
            return id;
        }
    }

    private static final class SlotSession implements CasinoSession {
        private final long id;
        private final Random random;
        private final int[] slots = {-1, -1, -1};
        private int bet = 10;
        private int payoutMultiplier;
        private boolean awaitingCollection;

        private SlotSession(long id, Random random) {
            this.id = id;
            this.random = random;
        }

        private void setAll(int icon, int payout) {
            slots[0] = icon;
            slots[1] = icon;
            slots[2] = icon;
            payoutMultiplier = payout;
        }

        @Override
        public long id() {
            return id;
        }
    }
}
