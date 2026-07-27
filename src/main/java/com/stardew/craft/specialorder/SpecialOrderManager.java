package com.stardew.craft.specialorder;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.specialorder.SpecialOrderObjectiveContext;
import com.stardew.craft.api.v1.specialorder.SpecialOrderProgressEvent;
import com.stardew.craft.api.v1.specialorder.SpecialOrderRewardContext;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjectives;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderRewards;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressCauses;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressRequirements;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.lostandfound.LostAndFoundService;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SpecialOrderManager {
    public static final int UNLOCK_DAYS_AFTER_FARM_CREATION = 87;
    public static final String BOARD_UNLOCK_FLAG = "specialOrdersBoardUnlocked";

    public record NpcDeliveryResult(boolean delivered, String messageKey) {
        public static final NpcDeliveryResult NONE = new NpcDeliveryResult(false, "");
    }

    private SpecialOrderManager() {
    }

    public static boolean isUnlocked() {
        return StardewTimeManager.get().getAbsoluteDay() >= 1 + UNLOCK_DAYS_AFTER_FARM_CREATION;
    }

    public static boolean isUnlockedFor(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(BOARD_UNLOCK_FLAG);
    }

    public static boolean isUnlockedFor(int firstJoinDay, int currentAbsoluteDay) {
        return isOldEnoughForBoard(firstJoinDay, currentAbsoluteDay);
    }

    public static boolean isOldEnoughForBoard(int firstJoinDay, int currentAbsoluteDay) {
        if (firstJoinDay < 0) {
            return false;
        }
        return currentAbsoluteDay - firstJoinDay >= UNLOCK_DAYS_AFTER_FARM_CREATION;
    }

    public static void openBoard(ServerPlayer player) {
        if (!isUnlockedFor(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        refreshAvailableIfNeeded(level, false, true, mailFlagsFor(player));
        sendBoard(player);
        player.playNotifySound(ModSounds.BIG_SELECT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void syncState(ServerPlayer player) {
        boolean unlocked = isUnlockedFor(player);
        refreshAvailableIfNeeded(player.serverLevel(), false, unlocked, mailFlagsFor(player));
        migrateQueuedDonations(player);
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.SpecialOrderStateSyncPayload(snapshot(player, data)));
        SpecialOrderDropBoxService.syncHints(player);
    }

    public static void sendBoard(ServerPlayer player) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.OpenSpecialOrdersBoardPayload(snapshot(player, data)));
        SpecialOrderDropBoxService.syncHints(player);
    }

    public static void accept(ServerPlayer player, String orderId) {
        var key = StardewProgressRegistry.specialOrderKey(orderId);
        var requirements =
                StardewProgressRequirements.requirements(
                        player, key,
                        StardewProgressOperation.ACCEPT);
        if (!requirements.satisfied()) {
            if (requirements.blocking().stream()
                    .anyMatch(requirement -> requirement.type()
                            .equals(StardewRequirementTypes
                                    .PROGRESS_ACCEPTANCE_SLOT_AVAILABLE))) {
                player.displayClientMessage(
                        Component.translatable(
                                "stardewcraft.special_orders.accept.already_active"),
                        true);
            }
            if (isUnlockedFor(player)) {
                sendBoard(player);
            }
            return;
        }
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        Optional<SpecialOrderInstance> selected = data.available().stream()
            .filter(order -> order.orderId().equals(orderId))
            .findFirst();
        if (selected.isEmpty()) {
            sendBoard(player);
            return;
        }
        SpecialOrderInstance order = selected.get();
        var before = StardewProgressRegistry.specialOrderSnapshot(
                player, order, StardewProgressPhase.AVAILABLE);
        order.setAccepted(true);
        order.addParticipant(player.getUUID());
        data.active().add(order);
        data.setNormalOrderAcceptedThisRefresh(true);
        data.setDirty();
        PlayerDataManager.getPlayerData(player).markDirty();
        dispatchProgress(
                StardewProgressEventType.ACCEPTED,
                player,
                before,
                StardewProgressRegistry.specialOrderSnapshot(
                        player, order, null),
                StardewProgressCauses.ACCEPT);
        player.playNotifySound(ModSounds.NEW_ARTIFACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        syncAll(player.server);
    }

    public static void claimReward(ServerPlayer player, String orderId) {
        if (!StardewProgressRequirements.requirements(
                        player,
                        StardewProgressRegistry
                                .specialOrderKey(orderId),
                        StardewProgressOperation.CLAIM_REWARD)
                .satisfied()) {
            sendBoard(player);
            return;
        }
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        SpecialOrderInstance order = findActive(data, orderId).orElse(null);
        if (order == null || !order.hasUnclaimedReward(player.getUUID())) {
            sendBoard(player);
            return;
        }
        SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
        if (definition == null) {
            return;
        }
        var before = StardewProgressRegistry.specialOrderSnapshot(
                player, order, null);
        grantRewards(player, definition, order);
        cleanupTemporaryOrderState(List.of(player), definition);
        order.markRewardClaimed(player.getUUID());
        if (!definition.repeatable() && order.allParticipantRewardsClaimed()) {
            data.completedOrderIds().add(order.orderId());
        }
        if (order.allParticipantRewardsClaimed()) {
            data.active().remove(order);
        }
        data.setDirty();
        dispatchProgress(
                StardewProgressEventType.REWARD_CLAIMED,
                player,
                before,
                StardewProgressRegistry.specialOrderSnapshot(
                        player, order, StardewProgressPhase.COMPLETED),
                StardewProgressCauses.REWARD_CLAIM);
        player.playNotifySound(ModSounds.QUEST_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        syncAll(player.server);
    }

    public static void onNewDay(ServerLevel level, List<ServerPlayer> players) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(level);
        int today = StardewTimeManager.get().getAbsoluteDay();
        migrateLegacyReturnedDonations(level, data);
        boolean changed = false;
        for (SpecialOrderInstance order : new ArrayList<>(data.active())) {
            if (order.accepted() && !order.complete() && order.dueDay() <= today) {
                SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
                var before = StardewProgressRegistry.specialOrderSnapshot(
                        (UUID) null, order, StardewProgressPhase.ACTIVE);
                queueReturnedDonations(level, players, order);
                cleanupTemporaryOrderState(players, definition);
                order.setFailed(true);
                data.active().remove(order);
                StardewProgressRegistry.dispatch(new StardewProgressEvent(
                        StardewProgressEventType.FAILED,
                        level,
                        Optional.empty(),
                        Optional.of(before),
                        StardewProgressRegistry.specialOrderSnapshot(
                                (UUID) null, order,
                                StardewProgressPhase.FAILED),
                        StardewProgressCauses.DAY_TICK));
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
        boolean unlockedForAnyone = isUnlocked() || players.stream().anyMatch(SpecialOrderManager::isUnlockedFor);
        refreshAvailableIfNeeded(level, true, unlockedForAnyone, mailFlagsFor(players));
        for (ServerPlayer player : players) {
            syncState(player);
        }
    }

    public static void refreshAvailableIfNeeded(ServerLevel level, boolean forceForWeek) {
        refreshAvailableIfNeeded(level, forceForWeek, isUnlocked(), mailFlagsFor(level.getServer().getPlayerList().getPlayers()));
    }

    private static void refreshAvailableIfNeeded(ServerLevel level, boolean forceForWeek, boolean unlocked) {
        refreshAvailableIfNeeded(level, forceForWeek, unlocked, Set.of());
    }

    private static void refreshAvailableIfNeeded(ServerLevel level, boolean forceForWeek, boolean unlocked, Set<String> mailFlags) {
        if (!unlocked) {
            return;
        }
        StardewTimeManager time = StardewTimeManager.get();
        int day = time.getCurrentDay();
        int absoluteDay = time.getAbsoluteDay();
        boolean weeklyRefreshDay = day == 1 || day == 8 || day == 15 || day == 22;
        SpecialOrderWorldData data = SpecialOrderWorldData.get(level);
        if (forceForWeek && !weeklyRefreshDay) {
            return;
        }
        if (!forceForWeek && !data.available().isEmpty()) {
            return;
        }
        if (data.lastRefreshDay() == absoluteDay && !data.available().isEmpty()) {
            return;
        }
        List<SpecialOrderDefinition> candidatesIncludingCompleted = eligibleDefinitions(data, time, mailFlags, true);
        if (candidatesIncludingCompleted.isEmpty()) {
            return;
        }
        List<SpecialOrderDefinition> candidates = candidatesIncludingCompleted.stream()
            .filter(definition -> definition.repeatable() || !data.completedOrderIds().contains(definition.id()))
            .toList();
        if (candidates.isEmpty()) {
            candidates = candidatesIncludingCompleted;
        }
        Random random = new Random(level.getSeed() ^ (long) absoluteDay * 1300L);
        List<SpecialOrderDefinition> pool = new ArrayList<>(candidates);
        data.available().clear();
        if (forceForWeek || !data.normalOrderAcceptedThisRefresh()) {
            data.setNormalOrderAcceptedThisRefresh(false);
        }
        for (int i = 0; i < 2 && !pool.isEmpty(); i++) {
            SpecialOrderDefinition picked = pool.remove(random.nextInt(pool.size()));
            int dueDay = dueDayFor(picked.duration(), time);
            SpecialOrderInstance instance = SpecialOrderInstance.create(picked, absoluteDay, dueDay);
            rollRandomElements(instance, picked, random, time, mailFlags);
            data.available().add(instance);
        }
        data.setLastRefreshDay(absoluteDay);
        data.setDirty();
    }

    public static void recordItemReceived(ServerPlayer player, ItemStack stack, int count) {
        progressItem(player, stack, count, Set.of(SpecialOrderDefinition.ObjectiveType.COLLECT));
        progressExtensions(player, new SpecialOrderProgressEvent(
                SpecialOrderProgressEvent.Kind.ITEM_RECEIVED, stack, count, null));
    }

    public static void recordFishCaught(ServerPlayer player, ItemStack stack, int count) {
        progressItem(player, stack, count, Set.of(SpecialOrderDefinition.ObjectiveType.FISH));
        progressExtensions(player, new SpecialOrderProgressEvent(
                SpecialOrderProgressEvent.Kind.FISH_CAUGHT, stack, count, null));
    }

    public static void recordShipped(ServerPlayer player, ItemStack stack, int count) {
        progressItem(player, stack, count, Set.of(SpecialOrderDefinition.ObjectiveType.SHIP));
        progressExtensions(player, new SpecialOrderProgressEvent(
                SpecialOrderProgressEvent.Kind.ITEM_SHIPPED, stack, count, null));
    }

    public static boolean canDonateToDropBox(ServerPlayer player, String dropBoxId, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.type() != SpecialOrderDefinition.ObjectiveType.DONATE) continue;
                if (!dropBoxId.equals(objective.dropBoxId())) continue;
                if (order.objectives().get(i).isComplete()) continue;
                if (SpecialOrderContextTagService.matches(stack, objective.acceptedTags(), order)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void confirmDropBoxDonations(ServerPlayer player, SpecialOrderDropBoxAnchor anchor, Container container) {
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            changed |= donateStackFromDropBox(player, anchor, stack);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (changed) {
            SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
            data.setDirty();
            syncAll(player.server);
        }
    }

    public static NpcDeliveryResult recordNpcDelivery(ServerPlayer player, String npcId, ItemStack held) {
        if (held.isEmpty()) {
            return NpcDeliveryResult.NONE;
        }
        ItemStack deliveredStack = held.copy();
        int deliveredCount = held.getCount();
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        boolean changed = false;
        String messageKey = "";
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.type() != SpecialOrderDefinition.ObjectiveType.DELIVER) continue;
                if (!objective.targetName().equalsIgnoreCase(npcId)) continue;
                if (!SpecialOrderContextTagService.matches(held, objective.acceptedTags(), order)) continue;
                SpecialOrderInstance.ObjectiveState state = order.objectives().get(i);
                if (state.isComplete()) continue;
                int amount = state.requiredCount() - state.progress();
                if (held.getCount() < amount) {
                    continue;
                }
                String itemId = SpecialOrderContextTagService.itemId(held);
                if (!player.getAbilities().instabuild) {
                    held.shrink(amount);
                }
                order.donatedItems().add(new SpecialOrderInstance.DonatedItem(itemId, amount));
                changed |= addObjectiveProgress(player, order, state, amount, false);
                messageKey = objective.messageKey();
                break;
            }
            changed |= completeIfReady(player, data, definition, order);
        }
        if (changed) {
            data.setDirty();
            syncAll(player.server);
        }
        boolean customChanged = progressExtensions(player, new SpecialOrderProgressEvent(
                SpecialOrderProgressEvent.Kind.NPC_DELIVERY, deliveredStack, deliveredCount, npcId));
        return changed || customChanged ? new NpcDeliveryResult(true, messageKey) : NpcDeliveryResult.NONE;
    }

    public static void recordMonsterSlain(ServerPlayer player, String monsterName) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        boolean changed = false;
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.type() != SpecialOrderDefinition.ObjectiveType.SLAY) continue;
                String target = SpecialOrderText.resolveRaw(objective.targetName(), order);
                if (!monsterMatches(monsterName, target)) continue;
                changed |= addObjectiveProgress(player, order, order.objectives().get(i), 1, false);
            }
            changed |= completeIfReady(player, data, definition, order);
        }
        if (changed) {
            data.setDirty();
            syncAll(player.server);
        }
        progressExtensions(player, new SpecialOrderProgressEvent(
                SpecialOrderProgressEvent.Kind.MONSTER_SLAIN, ItemStack.EMPTY, 1, monsterName));
    }

    public static boolean donateHeldStack(ServerPlayer player, SpecialOrderDropBoxAnchor anchor, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        boolean changed = donateStackFromDropBox(player, anchor, held);
        if (changed) {
            SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
            data.setDirty();
            syncAll(player.server);
        }
        return changed;
    }

    private static boolean donateStackFromDropBox(ServerPlayer player, SpecialOrderDropBoxAnchor anchor, ItemStack held) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        boolean changed = false;
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.type() != SpecialOrderDefinition.ObjectiveType.DONATE) continue;
                if (!anchor.dropBoxId().equals(objective.dropBoxId())) continue;
                SpecialOrderInstance.ObjectiveState state = order.objectives().get(i);
                if (state.isComplete()) continue;
                if (!SpecialOrderContextTagService.matches(held, objective.acceptedTags(), order)) continue;
                int amount = Math.min(held.getCount(), state.requiredCount() - state.progress());
                if (amount <= 0) continue;
                String itemId = SpecialOrderContextTagService.itemId(held);
                held.shrink(amount);
                order.donatedItems().add(new SpecialOrderInstance.DonatedItem(itemId, amount));
                boolean wasComplete = state.isComplete();
                changed |= addObjectiveProgress(player, order, state, amount, false);
                if (!wasComplete && state.isComplete()) {
                    player.playNotifySound(ModSounds.NEW_ARTIFACT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            changed |= completeIfReady(player, data, definition, order);
        }
        return changed;
    }

    public static Set<String> activeDropBoxIds(ServerPlayer player) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        Set<String> ids = new HashSet<>();
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.type() == SpecialOrderDefinition.ObjectiveType.DONATE
                    && !order.objectives().get(i).isComplete()
                    && !objective.dropBoxId().isBlank()) {
                    ids.add(objective.dropBoxId());
                }
            }
        }
        return ids;
    }

    public static boolean hasActiveIncompleteOrder(ServerPlayer player, String orderId) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        return data.active().stream().anyMatch(order ->
            order.orderId().equals(orderId) && order.accepted() && !order.complete() && !order.failed());
    }

    public static boolean hasSpecialDropFlag(ServerPlayer player, String flag) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(flag) || data.hasMailFlagForTomorrow(flag);
    }

    public static void markSpecialDropFlag(ServerPlayer player, String flag) {
        if (!hasSpecialDropFlag(player, flag)) {
            MailService.addMailFlagForTomorrow(player, flag);
        }
    }

    public static CompoundTag snapshot(ServerPlayer player, SpecialOrderWorldData data) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Unlocked", isUnlockedFor(player));
        tag.putInt("AbsoluteDay", StardewTimeManager.get().getAbsoluteDay());
        tag.putBoolean("AcceptedThisRefresh", data.normalOrderAcceptedThisRefresh());
        tag.put("Available", writeOrderList(player, data.available()));
        tag.put("Active", writeOrderList(player, data.active()));
        return tag;
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SpecialOrderDropBoxService.syncHints(player);
            if (player.containerMenu == player.inventoryMenu) {
                PacketDistributor.sendToPlayer(player, new com.stardew.craft.network.payload.SpecialOrderStateSyncPayload(
                    snapshot(player, SpecialOrderWorldData.get(player.serverLevel()))));
            }
        }
    }

    private static Set<String> mailFlagsFor(ServerPlayer player) {
        return mailFlagsFor(List.of(player));
    }

    private static Set<String> mailFlagsFor(List<ServerPlayer> players) {
        Set<String> flags = new HashSet<>();
        for (ServerPlayer player : players) {
            flags.addAll(PlayerDataManager.getPlayerData(player).getMailFlags());
        }
        return flags;
    }

    private static ListTag writeOrderList(ServerPlayer player, List<SpecialOrderInstance> orders) {
        ListTag list = new ListTag();
        for (SpecialOrderInstance order : orders) {
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", order.orderId());
            tag.putString("Requester", definition.requester());
            tag.putString("TitleKey", definition.titleKey());
            tag.putString("TextKey", definition.textKey());
            tag.putInt("DueDay", order.dueDay());
            tag.putInt("DaysLeft", order.daysLeft(StardewTimeManager.get().getAbsoluteDay()));
            tag.putBoolean("Accepted", order.accepted());
            tag.putBoolean("Complete", order.complete());
            tag.putBoolean("Failed", order.failed());
            tag.putBoolean("RewardClaimed", !order.hasUnclaimedReward(player.getUUID()));
            CompoundTag randomTag = new CompoundTag();
            for (Map.Entry<String, Map<String, String>> entry : order.randomValues().entrySet()) {
                CompoundTag values = new CompoundTag();
                for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                    values.putString(value.getKey(), value.getValue());
                }
                randomTag.put(entry.getKey(), values);
            }
            tag.put("RandomValues", randomTag);
            ListTag objectives = new ListTag();
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                SpecialOrderInstance.ObjectiveState state = order.objectives().get(i);
                CompoundTag objectiveTag = new CompoundTag();
                objectiveTag.putString("TextKey", objective.textKey());
                objectiveTag.putString("Type", objective.typeName());
                objectiveTag.putInt("Progress", state.progress());
                objectiveTag.putInt("Required", state.requiredCount());
                objectiveTag.putString("DropBox", objective.dropBoxId());
                objectives.add(objectiveTag);
            }
            tag.put("Objectives", objectives);
            tag.putInt("RewardMoney", order.hasUnclaimedReward(player.getUUID()) ? rewardMoney(definition, order) : 0);
            list.add(tag);
        }
        return list;
    }

    private static void progressItem(ServerPlayer player, ItemStack stack, int count, Set<SpecialOrderDefinition.ObjectiveType> types) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        boolean changed = false;
        for (SpecialOrderInstance order : data.active()) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (!types.contains(objective.type())) continue;
                if (!SpecialOrderContextTagService.matches(stack, objective.acceptedTags(), order)) continue;
                changed |= addObjectiveProgress(player, order, order.objectives().get(i), count, false);
            }
            changed |= completeIfReady(player, data, definition, order);
        }
        if (changed) {
            data.setDirty();
            syncAll(player.server);
        }
    }

    private static boolean progressExtensions(ServerPlayer player, SpecialOrderProgressEvent event) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        boolean changed = false;
        for (SpecialOrderInstance order : List.copyOf(data.active())) {
            if (!order.accepted() || order.complete() || order.failed()) continue;
            SpecialOrderDefinition definition = SpecialOrderDefinitions.get(order.orderId());
            if (definition == null) continue;
            for (int i = 0; i < definition.objectives().size(); i++) {
                SpecialOrderDefinition.ObjectiveDefinition objective = definition.objectives().get(i);
                if (objective.extension() == null) continue;
                SpecialOrderInstance.ObjectiveState state = order.objectives().get(i);
                if (state.isComplete()) continue;
                int amount = StardewSpecialOrderObjectives.progress(
                                objective.extension(),
                                new SpecialOrderObjectiveContext(
                                        player, order.orderId(), state.progress(), state.requiredCount()),
                                event)
                        .resultOrPartial(message -> StardewCraft.LOGGER.error(
                                "[Special order] Objective {} failed for {}: {}",
                                objective.typeName(), order.orderId(), message))
                        .orElse(0);
                if (amount > 0) changed |= addObjectiveProgress(player, order, state, amount, false);
            }
            changed |= completeIfReady(player, data, definition, order);
        }
        if (changed) {
            data.setDirty();
            syncAll(player.server);
        }
        return changed;
    }

    private static boolean completeIfReady(ServerPlayer player, SpecialOrderWorldData data,
                                           SpecialOrderDefinition definition, SpecialOrderInstance order) {
        if (order.complete() || !order.isObjectivesComplete()) {
            return false;
        }
        var before = StardewProgressRegistry.specialOrderSnapshot(
                player, order, null);
        order.setComplete(true);
        order.addParticipant(player.getUUID());
        data.setDirty();
        dispatchProgress(
                StardewProgressEventType.REWARD_BECAME_AVAILABLE,
                player,
                before,
                StardewProgressRegistry.specialOrderSnapshot(
                        player, order, null),
                StardewProgressCauses.OBJECTIVE_EVENT);
        return true;
    }

    private static boolean addObjectiveProgress(ServerPlayer player, SpecialOrderInstance order,
                                                SpecialOrderInstance.ObjectiveState state, int amount,
                                                boolean suppressJingle) {
        var before = StardewProgressRegistry.specialOrderSnapshot(
                player, order, null);
        boolean wasComplete = state.isComplete();
        boolean changed = state.add(amount);
        if (changed) {
            dispatchProgress(
                    StardewProgressEventType.PROGRESSED,
                    player,
                    before,
                    StardewProgressRegistry.specialOrderSnapshot(
                            player, order, null),
                    StardewProgressCauses.OBJECTIVE_EVENT);
        }
        if (changed && !wasComplete && state.isComplete() && !order.isObjectivesComplete() && !suppressJingle) {
            player.playNotifySound(ModSounds.JINGLE1.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return changed;
    }

    private static void dispatchProgress(
            StardewProgressEventType type,
            ServerPlayer player,
            com.stardew.craft.api.v1.progress.StardewProgressSnapshot before,
            com.stardew.craft.api.v1.progress.StardewProgressSnapshot after,
            ResourceLocation cause
    ) {
        StardewProgressRegistry.dispatch(new StardewProgressEvent(
                type,
                player.serverLevel(),
                Optional.of(player.getUUID()),
                Optional.of(before),
                after,
                cause));
    }

    private static void grantRewards(ServerPlayer player, SpecialOrderDefinition definition, SpecialOrderInstance order) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int money = rewardMoney(definition, order);
        if (money > 0) {
            com.stardew.craft.player.PlayerStardewDataAPI.addMoney(player, money);
        }
        for (SpecialOrderDefinition.RewardDefinition reward : definition.rewards()) {
            if (reward.extension() != null) {
                StardewSpecialOrderRewards.grant(
                                reward.extension(), new SpecialOrderRewardContext(player, order.orderId()))
                        .resultOrPartial(message -> StardewCraft.LOGGER.error(
                                "[Special order] Reward {} failed for {}: {}",
                                reward.typeName(), order.orderId(), message));
                continue;
            }
            if (reward.type() == SpecialOrderDefinition.RewardType.MAIL && !reward.mailId().isBlank()) {
                String mailId = reward.mailId();
                if ("ClintReward".equals(mailId) && data.hasMailFlag("ClintReward")) {
                    data.removeMailFlag("ClintReward2");
                    mailId = "ClintReward2";
                }
                if (reward.noLetter()) {
                    MailService.addMailFlagForTomorrow(player, mailId);
                } else if (reward.host()) {
                    for (ServerPlayer target : player.server.getPlayerList().getPlayers()) {
                        MailService.addMailForTomorrow(target, mailId);
                    }
                } else {
                    MailService.addMailForTomorrow(player, mailId);
                }
            }
            if (reward.type() == SpecialOrderDefinition.RewardType.FRIENDSHIP && reward.amount() != 0) {
                String npcId = definition.requester().toLowerCase(Locale.ROOT);
                NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(player.serverLevel());
                NpcFriendshipDataManager.FriendshipState state = friendship.getOrCreate(player.getUUID(), npcId);
                state.addPoints(reward.amount(), NpcInteractionService.getMaxFriendshipPointsFor(npcId));
                friendship.setDirty();
            }
        }
        data.addSpecialOrderPrizeTickets(1);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    private static int rewardMoney(SpecialOrderDefinition definition, SpecialOrderInstance order) {
        int total = 0;
        for (SpecialOrderDefinition.RewardDefinition reward : definition.rewards()) {
            if (reward.type() != SpecialOrderDefinition.RewardType.MONEY) continue;
            if (reward.amount() >= 0) {
                total += reward.amount();
            } else {
                int multiplier = -reward.amount();
                String raw = SpecialOrderText.resolveRaw(reward.mailId(), order);
                try {
                    total += Integer.parseInt(raw) * multiplier;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    private static List<SpecialOrderDefinition> eligibleDefinitions(SpecialOrderWorldData data, StardewTimeManager time,
                                                                    Set<String> mailFlags, boolean includeCompleted) {
        List<SpecialOrderDefinition> out = new ArrayList<>();
        for (SpecialOrderDefinition definition : SpecialOrderDefinitions.all()) {
            if (!includeCompleted && !definition.repeatable() && data.completedOrderIds().contains(definition.id())) {
                continue;
            }
            if (definition.duration() == SpecialOrderDefinition.Duration.MONTH && time.getCurrentDay() >= 16) {
                continue;
            }
            if (data.active().stream().anyMatch(order -> order.orderId().equals(definition.id()))) {
                continue;
            }
            boolean ok = true;
            for (String tag : definition.requiredTags()) {
                if (!SpecialOrderContextTagService.hasWorldTag(tag, time.getCurrentSeason(), mailFlags)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(definition);
        }
        out.sort(Comparator.comparing(SpecialOrderDefinition::id));
        return out;
    }

    private static int dueDayFor(SpecialOrderDefinition.Duration duration, StardewTimeManager time) {
        int absolute = time.getAbsoluteDay();
        int day = time.getCurrentDay();
        int startOfWeek = absolute - Math.floorMod(day - 1, 7);
        return switch (duration) {
            case WEEK -> startOfWeek + 7;
            case TWO_WEEKS -> startOfWeek + 14;
            case MONTH -> absolute - (day - 1) + 28;
        };
    }

    private static void rollRandomElements(SpecialOrderInstance instance, SpecialOrderDefinition definition, Random random,
                                           StardewTimeManager time, Set<String> mailFlags) {
        for (SpecialOrderDefinition.RandomElement element : definition.randomElements()) {
            List<SpecialOrderDefinition.RandomOption> eligible = element.options().stream()
                .filter(option -> option.requiredTags().stream()
                    .allMatch(tag -> SpecialOrderContextTagService.hasWorldTag(tag, time.getCurrentSeason(), mailFlags)))
                .toList();
            if (eligible.isEmpty()) continue;
            SpecialOrderDefinition.RandomOption picked = eligible.get(random.nextInt(eligible.size()));
            Map<String, String> values = new LinkedHashMap<>(picked.values());
            if (values.containsKey("OptionCount")) {
                int count = Integer.parseInt(values.get("OptionCount"));
                int index = random.nextInt(Math.max(1, count));
                Map<String, String> single = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    String prefix = "option." + index + ".";
                    if (entry.getKey().startsWith(prefix)) {
                        single.put(entry.getKey().substring(prefix.length()), entry.getValue());
                    }
                }
                values = single;
            }
            instance.randomValues().put(element.name(), values);
        }
    }

    private static Optional<SpecialOrderInstance> findActive(SpecialOrderWorldData data, String orderId) {
        return data.active().stream().filter(order -> order.orderId().equals(orderId)).findFirst();
    }

    private static boolean monsterMatches(String actual, String target) {
        String a = normalizeMonster(actual);
        String t = normalizeMonster(target);
        return a.equals(t);
    }

    private static String normalizeMonster(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
            .replace("sd_mob_", "")
            .replace("_", " ")
            .replace("dust sprite", "dust spirit")
            .replace("bat", "bat")
            .trim();
    }

    private static void returnDonatedItems(List<ServerPlayer> players, SpecialOrderInstance order) {
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer target = players.stream()
            .filter(player -> order.participants().contains(player.getUUID()))
            .findFirst()
            .orElse(players.get(0));
        for (SpecialOrderInstance.DonatedItem donated : order.donatedItems()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(donated.itemId()));
            if (item == net.minecraft.world.item.Items.AIR || donated.count() <= 0) continue;
            ItemStack stack = new ItemStack(item, donated.count());
            if (!target.getInventory().add(stack)) {
                target.drop(stack, false);
            }
        }
    }

    private static void queueReturnedDonations(ServerLevel level, List<ServerPlayer> players, SpecialOrderInstance order) {
        if (order.donatedItems().isEmpty()) {
            return;
        }
        if (order.participants().isEmpty()) {
            returnDonatedItems(players, order);
            return;
        }
        UUID recipient = order.participants().get(0);
        LostAndFoundService.queueForPlayer(
            level,
            recipient,
            donatedStacks(order.donatedItems()),
            StardewTimeManager.get().getAbsoluteDay());
    }

    public static void migrateQueuedDonations(ServerPlayer player) {
        SpecialOrderWorldData data = SpecialOrderWorldData.get(player.serverLevel());
        List<SpecialOrderInstance.DonatedItem> queued = data.returnedDonations().remove(player.getUUID());
        if (queued == null || queued.isEmpty()) {
            return;
        }
        LostAndFoundService.queueForPlayer(
            player.serverLevel(),
            player.getUUID(),
            donatedStacks(queued),
            StardewTimeManager.get().getAbsoluteDay());
        data.setDirty();
        LostAndFoundService.onNewDay(player.serverLevel());
    }

    private static void migrateLegacyReturnedDonations(ServerLevel level, SpecialOrderWorldData data) {
        if (data.returnedDonations().isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, List<SpecialOrderInstance.DonatedItem>> entry
                : new ArrayList<>(data.returnedDonations().entrySet())) {
            LostAndFoundService.queueForPlayer(
                level,
                entry.getKey(),
                donatedStacks(entry.getValue()),
                StardewTimeManager.get().getAbsoluteDay());
        }
        data.returnedDonations().clear();
        data.setDirty();
    }

    private static List<ItemStack> donatedStacks(List<SpecialOrderInstance.DonatedItem> donatedItems) {
        List<ItemStack> stacks = new ArrayList<>();
        for (SpecialOrderInstance.DonatedItem donated : donatedItems) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(donated.itemId()));
            if (item != net.minecraft.world.item.Items.AIR && donated.count() > 0) {
                stacks.add(new ItemStack(item, donated.count()));
            }
        }
        return stacks;
    }

    private static void cleanupTemporaryOrderState(List<ServerPlayer> players, SpecialOrderDefinition definition) {
        if (definition == null) return;
        ResourceLocation itemId = definition.itemToRemoveOnEnd() == null || definition.itemToRemoveOnEnd().isBlank()
            ? null : ResourceLocation.parse(definition.itemToRemoveOnEnd());
        String mailFlag = definition.mailToRemoveOnEnd();
        for (ServerPlayer player : players) {
            if (itemId != null) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != net.minecraft.world.item.Items.AIR) {
                    removeAllFromInventory(player, item);
                }
            }
            if (mailFlag != null && !mailFlag.isBlank()) {
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                data.removeMailFlag(mailFlag);
            }
        }
    }

    private static void removeAllFromInventory(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.getInventory().setChanged();
    }
}
