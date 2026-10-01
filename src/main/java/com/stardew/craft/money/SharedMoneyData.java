package com.stardew.craft.money;

import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("null")
public class SharedMoneyData extends SavedData {
    private static final String DATA_NAME = "stardewcraft_shared_money";

    private final Map<UUID, UUID> playerToGroup = new HashMap<>();
    private final Map<UUID, Group> groups = new HashMap<>();

    private record Group(UUID id, Set<UUID> members, int balance) {
        Group withBalance(int nextBalance) {
            return new Group(id, members, Math.max(0, nextBalance));
        }
    }

    public static SharedMoneyData get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new SharedMoneyData();
        }
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public int getMoney(ServerPlayer player) {
        Group group = ensureGroup(player);
        return group.balance();
    }

    public int getMoney(UUID playerId) {
        Group group = ensureGroup(playerId);
        return group.balance();
    }

    public void setMoney(ServerPlayer player, int money) {
        Group group = ensureGroup(player);
        groups.put(group.id(), group.withBalance(money));
        setDirty();
        syncGroup(group.id());
    }

    public void addMoney(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        Group group = ensureGroup(player);
        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        playerData.addMoney(amount);
        groups.put(group.id(), group.withBalance(group.balance() + amount));
        setDirty();
        syncGroup(group.id());
    }

    public void addMoney(UUID playerId, int amount) {
        if (amount <= 0) {
            return;
        }
        Group group = ensureGroup(playerId);
        groups.put(group.id(), group.withBalance(group.balance() + amount));
        setDirty();
        syncGroup(group.id());
    }

    public boolean removeMoney(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return true;
        }
        Group group = ensureGroup(player);
        if (group.balance() < amount) {
            syncGroup(group.id());
            return false;
        }
        groups.put(group.id(), group.withBalance(group.balance() - amount));
        setDirty();
        syncGroup(group.id());
        return true;
    }

    public boolean removeMoney(UUID playerId, int amount) {
        if (amount <= 0) {
            return true;
        }
        Group group = ensureGroup(playerId);
        if (group.balance() < amount) {
            syncGroup(group.id());
            return false;
        }
        groups.put(group.id(), group.withBalance(group.balance() - amount));
        setDirty();
        syncGroup(group.id());
        return true;
    }

    public boolean sameGroup(ServerPlayer a, ServerPlayer b) {
        return ensureGroup(a).id().equals(ensureGroup(b).id());
    }

    public boolean sameGroup(UUID a, UUID b) {
        return ensureGroup(a).id().equals(ensureGroup(b).id());
    }

    public List<UUID> membersFor(ServerPlayer player) {
        return List.copyOf(ensureGroup(player).members());
    }

    public UUID groupId(ServerPlayer player) {
        return ensureGroup(player).id();
    }

    public List<UUID> membersForGroup(UUID groupId) {
        Group group = groups.get(groupId);
        return group == null ? List.of() : List.copyOf(group.members());
    }

    public void merge(ServerPlayer requester, ServerPlayer target) {
        Group a = ensureGroup(requester);
        Group b = ensureGroup(target);
        if (a.id().equals(b.id())) {
            syncGroup(a.id());
            return;
        }

        Set<UUID> members = new LinkedHashSet<>();
        members.addAll(a.members());
        members.addAll(b.members());
        int mergedBalance = Math.max(0, a.balance()) + Math.max(0, b.balance());

        UUID mergedId = UUID.randomUUID();
        Group merged = new Group(mergedId, members, mergedBalance);
        groups.remove(a.id());
        groups.remove(b.id());
        groups.put(mergedId, merged);
        for (UUID member : members) {
            playerToGroup.put(member, mergedId);
        }
        setDirty();
        syncGroup(mergedId);
    }

    public void debugMergeWithMember(ServerPlayer player, UUID memberId, int memberBalance) {
        Group group = ensureGroup(player);
        if (group.members().contains(memberId)) {
            syncGroup(group.id());
            return;
        }

        UUID oldGroupId = playerToGroup.get(memberId);
        Group oldGroup = oldGroupId == null ? null : groups.get(oldGroupId);
        if (oldGroup != null) {
            Set<UUID> oldMembers = new LinkedHashSet<>(oldGroup.members());
            oldMembers.remove(memberId);
            if (oldMembers.isEmpty()) {
                groups.remove(oldGroupId);
            } else {
                groups.put(oldGroupId, new Group(oldGroupId, oldMembers, oldGroup.balance()));
            }
        }

        Set<UUID> nextMembers = new LinkedHashSet<>(group.members());
        nextMembers.add(memberId);
        groups.put(group.id(), new Group(group.id(), nextMembers, group.balance() + Math.max(0, memberBalance)));
        playerToGroup.put(memberId, group.id());
        setDirty();
        syncGroup(group.id());
    }

    public void debugResetPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID oldGroupId = playerToGroup.get(playerId);
        Group oldGroup = oldGroupId == null ? null : groups.get(oldGroupId);
        if (oldGroup != null) {
            Set<UUID> oldMembers = new LinkedHashSet<>(oldGroup.members());
            oldMembers.remove(playerId);
            if (oldMembers.isEmpty()) {
                groups.remove(oldGroupId);
            } else {
                groups.put(oldGroupId, new Group(oldGroupId, oldMembers, oldGroup.balance()));
            }
        }

        UUID newGroupId = UUID.randomUUID();
        Set<UUID> members = new LinkedHashSet<>();
        members.add(playerId);
        int balance = PlayerDataManager.getPlayerData(player).getMoney();
        groups.put(newGroupId, new Group(newGroupId, members, balance));
        playerToGroup.put(playerId, newGroupId);
        setDirty();
        syncGroup(newGroupId);
    }

    public void syncFor(ServerPlayer player) {
        syncGroup(ensureGroup(player).id());
    }

    public void syncGroup(UUID groupId) {
        Group group = groups.get(groupId);
        if (group == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (UUID member : group.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online == null) {
                continue;
            }
            PlayerStardewData data = PlayerDataManager.getPlayerData(online);
            data.setMoney(group.balance());
            PlayerDataEventHandler.syncPlayerData(online, data);
        }
    }

    private Group ensureGroup(ServerPlayer player) {
        UUID playerId = player.getUUID();
        UUID groupId = playerToGroup.get(playerId);
        Group existing = groupId == null ? null : groups.get(groupId);
        if (existing != null && existing.members().contains(playerId)) {
            return existing;
        }

        UUID newGroupId = UUID.randomUUID();
        Set<UUID> members = new LinkedHashSet<>();
        members.add(playerId);
        int balance = PlayerDataManager.getPlayerData(player).getMoney();
        Group group = new Group(newGroupId, members, Math.max(0, balance));
        groups.put(newGroupId, group);
        playerToGroup.put(playerId, newGroupId);
        setDirty();
        return group;
    }

    private Group ensureGroup(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                return ensureGroup(online);
            }
        }
        UUID groupId = playerToGroup.get(playerId);
        Group existing = groupId == null ? null : groups.get(groupId);
        if (existing != null && existing.members().contains(playerId)) {
            return existing;
        }

        UUID newGroupId = UUID.randomUUID();
        Set<UUID> members = new LinkedHashSet<>();
        members.add(playerId);
        int balance = PlayerDataManager.get().getOrCreateData(playerId).getMoney();
        Group group = new Group(newGroupId, members, Math.max(0, balance));
        groups.put(newGroupId, group);
        playerToGroup.put(playerId, newGroupId);
        setDirty();
        return group;
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        ListTag groupList = new ListTag();
        for (Group group : groups.values()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putUUID("Id", group.id());
            groupTag.putInt("Balance", group.balance());
            ListTag membersTag = new ListTag();
            for (UUID member : group.members()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("UUID", member);
                membersTag.add(memberTag);
            }
            groupTag.put("Members", membersTag);
            groupList.add(groupTag);
        }
        tag.put("Groups", groupList);
        return tag;
    }

    private static SharedMoneyData load(CompoundTag tag, HolderLookup.Provider provider) {
        SharedMoneyData data = new SharedMoneyData();
        ListTag groupList = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupList.size(); i++) {
            CompoundTag groupTag = groupList.getCompound(i);
            UUID groupId = groupTag.getUUID("Id");
            int balance = Math.max(0, groupTag.getInt("Balance"));
            Set<UUID> members = new LinkedHashSet<>();
            ListTag membersTag = groupTag.getList("Members", Tag.TAG_COMPOUND);
            for (int j = 0; j < membersTag.size(); j++) {
                UUID member = membersTag.getCompound(j).getUUID("UUID");
                members.add(member);
                data.playerToGroup.put(member, groupId);
            }
            if (!members.isEmpty()) {
                data.groups.put(groupId, new Group(groupId, members, balance));
            }
        }

        List<UUID> stalePlayers = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : data.playerToGroup.entrySet()) {
            if (!data.groups.containsKey(entry.getValue())) {
                stalePlayers.add(entry.getKey());
            }
        }
        stalePlayers.forEach(data.playerToGroup::remove);
        return data;
    }

    public static SavedData.Factory<SharedMoneyData> factory() {
        return new SavedData.Factory<>(SharedMoneyData::new, SharedMoneyData::load);
    }
}
