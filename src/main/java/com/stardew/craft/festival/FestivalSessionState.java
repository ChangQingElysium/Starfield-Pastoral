package com.stardew.craft.festival;

import com.stardew.craft.api.v1.festival.StardewFestivalSessionPersistentData;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvent;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEventType;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalSessionEventRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class FestivalSessionState {
    private final String festivalId;
    private final int year;
    private final int season;
    private final int day;
    private FestivalSessionPhase phase;
    private FestivalMapOverlayPhase mapOverlayPhase;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<ResourceLocation, Set<UUID>> rewardClaims =
            new LinkedHashMap<>();
    private StardewFestivalSessionPersistentData persistentData =
            StardewFestivalSessionPersistentData.empty();
    private transient ServerLevel level;

    public FestivalSessionState(String festivalId, int year, int season, int day) {
        this(festivalId, year, season, day, FestivalSessionPhase.SCHEDULED, FestivalMapOverlayPhase.NONE);
    }

    private FestivalSessionState(String festivalId, int year, int season, int day,
                                 FestivalSessionPhase phase, FestivalMapOverlayPhase mapOverlayPhase) {
        this.festivalId = festivalId == null ? "" : festivalId;
        this.year = year;
        this.season = season;
        this.day = day;
        this.phase = phase == null ? FestivalSessionPhase.SCHEDULED : phase;
        this.mapOverlayPhase = mapOverlayPhase == null ? FestivalMapOverlayPhase.NONE : mapOverlayPhase;
    }

    public String festivalId() {
        return festivalId;
    }

    public int year() {
        return year;
    }

    public int season() {
        return season;
    }

    public int day() {
        return day;
    }

    public FestivalSessionPhase phase() {
        return phase;
    }

    public void setPhase(FestivalSessionPhase phase) {
        FestivalSessionPhase next = phase == null
                ? FestivalSessionPhase.SCHEDULED : phase;
        FestivalSessionPhase previous = this.phase;
        if (previous == next) {
            return;
        }
        this.phase = next;
        if (level != null) {
            StardewFestivalSessionEventRegistry.dispatch(
                    new StardewFestivalSessionEvent(
                            StardewFestivalSessionEventType.PHASE_CHANGED,
                            level,
                            StardewFestivalSessions.snapshot(this),
                            Optional.of(
                                    StardewFestivalSessionSnapshot.Phase
                                            .valueOf(previous.name())),
                            Optional.empty(),
                            Optional.empty()));
        }
    }

    public FestivalMapOverlayPhase mapOverlayPhase() {
        return mapOverlayPhase;
    }

    public void setMapOverlayPhase(FestivalMapOverlayPhase mapOverlayPhase) {
        FestivalMapOverlayPhase next = mapOverlayPhase == null
                ? FestivalMapOverlayPhase.NONE : mapOverlayPhase;
        FestivalMapOverlayPhase previous = this.mapOverlayPhase;
        if (previous == next) {
            return;
        }
        this.mapOverlayPhase = next;
        if (level != null) {
            StardewFestivalSessionEventRegistry.dispatch(
                    new StardewFestivalSessionEvent(
                            StardewFestivalSessionEventType.MAP_PHASE_CHANGED,
                            level,
                            StardewFestivalSessions.snapshot(this),
                            Optional.empty(),
                            Optional.of(
                                    StardewFestivalSessionSnapshot.MapPhase
                                            .valueOf(previous.name())),
                            Optional.empty()));
        }
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public void addParticipant(UUID playerId) {
        if (playerId != null && participants.add(playerId)
                && level != null) {
            StardewFestivalSessionEventRegistry.dispatch(
                    new StardewFestivalSessionEvent(
                            StardewFestivalSessionEventType
                                    .PARTICIPANT_JOINED,
                            level,
                            StardewFestivalSessions.snapshot(this),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(playerId)));
        }
    }

    public boolean removeParticipant(UUID playerId) {
        if (playerId == null || !participants.remove(playerId)) {
            return false;
        }
        if (level != null) {
            StardewFestivalSessionEventRegistry.dispatch(
                    new StardewFestivalSessionEvent(
                            StardewFestivalSessionEventType
                                    .PARTICIPANT_LEFT,
                            level,
                            StardewFestivalSessions.snapshot(this),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(playerId)));
        }
        return true;
    }

    public void attachLevel(ServerLevel level) {
        this.level = level;
    }

    public StardewFestivalSessionPersistentData persistentData() {
        return persistentData;
    }

    public boolean hasRewardClaim(
            ResourceLocation rewardId,
            UUID playerId
    ) {
        return rewardId != null
                && playerId != null
                && rewardClaims.getOrDefault(rewardId, Set.of())
                        .contains(playerId);
    }

    public boolean addRewardClaim(
            ResourceLocation rewardId,
            UUID playerId
    ) {
        if (rewardId == null || playerId == null) {
            return false;
        }
        return rewardClaims.computeIfAbsent(
                rewardId, ignored -> new LinkedHashSet<>())
                .add(playerId);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("FestivalId", festivalId);
        tag.putInt("Year", year);
        tag.putInt("Season", season);
        tag.putInt("Day", day);
        tag.putString("Phase", phase.name());
        tag.putString("MapOverlayPhase", mapOverlayPhase.name());
        ListTag participantList = new ListTag();
        for (UUID participant : participants) {
            CompoundTag participantTag = new CompoundTag();
            participantTag.putUUID("Uuid", participant);
            participantList.add(participantTag);
        }
        tag.put("Participants", participantList);
        ListTag rewardList = new ListTag();
        for (var reward : rewardClaims.entrySet()) {
            CompoundTag rewardTag = new CompoundTag();
            rewardTag.putString("RewardId", reward.getKey().toString());
            ListTag claimantList = new ListTag();
            for (UUID claimant : reward.getValue()) {
                CompoundTag claimantTag = new CompoundTag();
                claimantTag.putUUID("Uuid", claimant);
                claimantList.add(claimantTag);
            }
            rewardTag.put("Claimants", claimantList);
            rewardList.add(rewardTag);
        }
        tag.put("RewardClaims", rewardList);
        if (!persistentData.isEmpty()) {
            tag.put("AddonData", persistentData.toTag());
        }
        return tag;
    }

    static FestivalSessionState load(CompoundTag tag) {
        FestivalSessionState state = new FestivalSessionState(
            tag.getString("FestivalId"),
            tag.getInt("Year"),
            tag.getInt("Season"),
            tag.getInt("Day"),
            parsePhase(tag.getString("Phase")),
            parseOverlayPhase(tag.getString("MapOverlayPhase"))
        );
        ListTag participantList = tag.getList("Participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < participantList.size(); i++) {
            CompoundTag participantTag = participantList.getCompound(i);
            if (participantTag.hasUUID("Uuid")) {
                state.addParticipant(participantTag.getUUID("Uuid"));
            }
        }
        ListTag rewardList = tag.getList(
                "RewardClaims", Tag.TAG_COMPOUND);
        for (int index = 0; index < rewardList.size(); index++) {
            CompoundTag rewardTag = rewardList.getCompound(index);
            ResourceLocation rewardId = ResourceLocation.tryParse(
                    rewardTag.getString("RewardId"));
            if (rewardId == null) {
                continue;
            }
            ListTag claimantList = rewardTag.getList(
                    "Claimants", Tag.TAG_COMPOUND);
            for (int claimantIndex = 0;
                 claimantIndex < claimantList.size();
                 claimantIndex++) {
                CompoundTag claimantTag =
                        claimantList.getCompound(claimantIndex);
                if (claimantTag.hasUUID("Uuid")) {
                    state.addRewardClaim(
                            rewardId,
                            claimantTag.getUUID("Uuid"));
                }
            }
        }
        if (tag.contains("AddonData", Tag.TAG_COMPOUND)) {
            state.persistentData =
                    StardewFestivalSessionPersistentData.fromTag(
                            tag.getCompound("AddonData"));
        }
        return state;
    }

    private static FestivalSessionPhase parsePhase(String value) {
        try {
            return FestivalSessionPhase.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return FestivalSessionPhase.SCHEDULED;
        }
    }

    private static FestivalMapOverlayPhase parseOverlayPhase(String value) {
        try {
            return FestivalMapOverlayPhase.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return FestivalMapOverlayPhase.NONE;
        }
    }
}
