package com.stardew.craft.money;

import com.stardew.craft.network.payload.OpenLewisConfirmPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.player.PlayerDisplayName;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedMoneyService {
    private static final int CONFIRM_KIND = OpenLewisConfirmPayload.KIND_MONEY_SHARE;
    private static final Map<UUID, PendingShare> PENDING_SHARES = new ConcurrentHashMap<>();

    private SharedMoneyService() {
    }

    public static int getMoney(ServerPlayer player) {
        return SharedMoneyData.get().getMoney(player);
    }

    public static int getMoney(UUID playerId) {
        return SharedMoneyData.get().getMoney(playerId);
    }

    public static void setMoney(ServerPlayer player, int money) {
        SharedMoneyData.get().setMoney(player, money);
    }

    public static void addMoney(ServerPlayer player, int amount) {
        SharedMoneyData.get().addMoney(player, amount);
    }

    public static void addMoney(UUID playerId, int amount) {
        SharedMoneyData.get().addMoney(playerId, amount);
    }

    public static boolean removeMoney(ServerPlayer player, int amount) {
        return SharedMoneyData.get().removeMoney(player, amount);
    }

    public static boolean removeMoney(UUID playerId, int amount) {
        return SharedMoneyData.get().removeMoney(playerId, amount);
    }

    public static boolean sameGroup(ServerPlayer a, ServerPlayer b) {
        return SharedMoneyData.get().sameGroup(a, b);
    }

    public static boolean sameGroup(UUID a, UUID b) {
        return SharedMoneyData.get().sameGroup(a, b);
    }

    public static void beginContractInvite(ServerPlayer requester, ServerPlayer target) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.displayClientMessage(Component.translatable("stardewcraft.lewis.money_share.self"), true);
            playCancel(requester);
            return;
        }
        SharedMoneyData data = SharedMoneyData.get();
        if (data.sameGroup(requester, target)) {
            requester.displayClientMessage(Component.translatable("stardewcraft.lewis.money_share.already_shared",
                    PlayerDisplayName.get(target)), true);
            playCancel(requester);
            return;
        }

        UUID requestId = UUID.randomUUID();
        PendingShare pending = new PendingShare(requestId, requester.getUUID(), target.getUUID(),
            PlayerDisplayName.get(requester), PlayerDisplayName.get(target), Set.of(target.getUUID()));
        PENDING_SHARES.put(requestId, pending);

        PacketDistributor.sendToPlayer(target, new OpenLewisConfirmPayload(
            requestId, CONFIRM_KIND, "stardewcraft.lewis.money_share.target_question",
            List.of(PlayerDisplayName.get(requester), PlayerDisplayName.get(requester),
                    PlayerDisplayName.get(target)),
                "stardewcraft.dialog.yes",
                "stardewcraft.dialog.no"));
        requester.displayClientMessage(Component.translatable("stardewcraft.lewis.money_share.sent",
                PlayerDisplayName.get(target)), true);
        requester.playNotifySound(ModSounds.BOOK_READ.get(), SoundSource.PLAYERS, 0.58f, 1.12f);
    }

    public static void handleConfirm(ServerPlayer responder, UUID requestId, boolean accepted) {
        PendingShare pending = PENDING_SHARES.get(requestId);
        if (pending == null || !pending.required().contains(responder.getUUID())) {
            return;
        }
        if (!accepted) {
            PENDING_SHARES.remove(requestId);
            notifyParticipants(pending, Component.translatable("stardewcraft.lewis.money_share.rejected",
                    PlayerDisplayName.get(responder)));
            playForParticipants(pending, false);
            return;
        }

        PendingShare next = pending.withAccepted(responder.getUUID());
        if (responder.getUUID().equals(pending.targetId()) && next.accepted().size() == 1) {
            next = requestOtherOnlineMembers(responder.server, next);
        }
        PENDING_SHARES.put(requestId, next);

        if (next.accepted().containsAll(next.required())) {
            PENDING_SHARES.remove(requestId);
            ServerPlayer requester = responder.server.getPlayerList().getPlayer(next.requesterId());
            ServerPlayer target = responder.server.getPlayerList().getPlayer(next.targetId());
            if (requester == null || target == null) {
                return;
            }
            SharedMoneyData.get().merge(requester, target);
            notifyParticipants(next, Component.translatable("stardewcraft.lewis.money_share.accepted",
                next.requesterName(), next.targetName()));
            playForParticipants(next, true);
        }
    }

    private static PendingShare requestOtherOnlineMembers(MinecraftServer server, PendingShare pending) {
        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterId());
        ServerPlayer target = server.getPlayerList().getPlayer(pending.targetId());
        if (requester == null || target == null) {
            return pending;
        }

        SharedMoneyData data = SharedMoneyData.get();
        Set<UUID> involved = new LinkedHashSet<>();
        involved.addAll(data.membersFor(requester));
        involved.addAll(data.membersFor(target));

        Set<UUID> required = new LinkedHashSet<>(pending.required());
        List<String> involvedNames = new ArrayList<>();
        for (UUID memberId : involved) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            involvedNames.add(online != null
                    ? PlayerDisplayName.get(online)
                    : PlayerDisplayName.get(server, memberId));
            if (!memberId.equals(pending.requesterId()) && !memberId.equals(pending.targetId()) && online != null) {
                required.add(memberId);
            }
        }

        PendingShare next = pending.withRequired(required);
        String names = String.join(", ", involvedNames);
        for (UUID memberId : required) {
            if (memberId.equals(pending.targetId()) || next.accepted().contains(memberId)) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                continue;
            }
            PacketDistributor.sendToPlayer(online, new OpenLewisConfirmPayload(
                pending.requestId(), CONFIRM_KIND, "stardewcraft.lewis.money_share.member_question",
                List.of(names, pending.requesterName(), pending.targetName()),
                "stardewcraft.dialog.yes",
                "stardewcraft.dialog.no"));
        }
        return next;
    }

    private static void notifyParticipants(PendingShare pending, Component message) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(pending.requesterId());
        recipients.add(pending.targetId());
        recipients.addAll(pending.required());
        for (UUID recipient : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(recipient);
            if (player != null) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static void playForParticipants(PendingShare pending, boolean accepted) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(pending.requesterId());
        recipients.add(pending.targetId());
        recipients.addAll(pending.required());
        for (UUID recipient : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(recipient);
            if (player == null) {
                continue;
            }
            if (accepted) {
                player.playNotifySound(ModSounds.BOOK_READ.get(), SoundSource.PLAYERS, 0.58f, 1.18f);
                player.playNotifySound(ModSounds.COIN.get(), SoundSource.PLAYERS, 0.42f, 1.24f);
            } else {
                playCancel(player);
            }
        }
    }

    private static void playCancel(ServerPlayer player) {
        player.playNotifySound(ModSounds.CANCEL.get(), SoundSource.PLAYERS, 0.42f, 0.92f);
    }

    private record PendingShare(
        UUID requestId,
        UUID requesterId,
        UUID targetId,
        String requesterName,
        String targetName,
        Set<UUID> required,
        Set<UUID> accepted
    ) {
        PendingShare(UUID requestId, UUID requesterId, UUID targetId, String requesterName, String targetName, Set<UUID> required) {
            this(requestId, requesterId, targetId, requesterName, targetName,
                new LinkedHashSet<>(required), new LinkedHashSet<>());
        }

        PendingShare withAccepted(UUID playerId) {
            Set<UUID> nextAccepted = new LinkedHashSet<>(accepted);
            nextAccepted.add(playerId);
            return new PendingShare(requestId, requesterId, targetId, requesterName, targetName, required, nextAccepted);
        }

        PendingShare withRequired(Set<UUID> nextRequired) {
            return new PendingShare(requestId, requesterId, targetId, requesterName, targetName,
                new LinkedHashSet<>(nextRequired), accepted);
        }
    }
}
