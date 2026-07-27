package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FestivalWorldData extends SavedData {
    private static final String DATA_NAME = "stardew_festival_world";

    private final Set<String> activePassiveFestivalIds = new LinkedHashSet<>();
    private final Map<String, FestivalSessionState> sessions = new LinkedHashMap<>();
    private final Map<String, FestivalMapOverlayState> overlayStates = new LinkedHashMap<>();
    private transient ServerLevel level;

    public static FestivalWorldData get(ServerLevel level) {
        FestivalWorldData data =
                level.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(FestivalWorldData::new, FestivalWorldData::load),
            DATA_NAME
        );
        ServerLevel festivalLevel = level.getServer()
                .getLevel(ModDimensions.STARDEW_VALLEY);
        data.attachLevel(festivalLevel != null ? festivalLevel : level);
        return data;
    }

    public Set<String> activePassiveFestivalIds() {
        return Set.copyOf(activePassiveFestivalIds);
    }

    public void setActivePassiveFestivalIds(Collection<String> ids) {
        activePassiveFestivalIds.clear();
        if (ids != null) {
            activePassiveFestivalIds.addAll(ids);
        }
        setDirty();
    }

    public Optional<FestivalSessionState> getSession(String festivalId) {
        return Optional.ofNullable(sessions.get(sessionKey(festivalId)));
    }

    public Collection<FestivalSessionState> sessions() {
        return java.util.List.copyOf(sessions.values());
    }

    public FestivalSessionState getOrCreateSession(FestivalDefinition definition, int year, int season, int day) {
        String key = sessionKey(definition.id());
        FestivalSessionState existing = sessions.get(key);
        if (existing != null && existing.year() == year && existing.season() == season && existing.day() == day) {
            return existing;
        }
        FestivalSessionState created = new FestivalSessionState(definition.id(), year, season, day);
        created.attachLevel(level);
        sessions.put(key, created);
        setDirty();
        return created;
    }

    public void closeStaleSessions(int year, int season, int day) {
        boolean changed = false;
        for (FestivalSessionState session : sessions.values()) {
            if (session.year() != year || session.season() != season || session.day() != day) {
                if (session.phase() != FestivalSessionPhase.CLOSED) {
                    session.setPhase(FestivalSessionPhase.CLOSED);
                    changed = true;
                }
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public Optional<FestivalMapOverlayState> getOverlayState(String overlayId) {
        return Optional.ofNullable(overlayStates.get(key(overlayId)));
    }

    public FestivalMapOverlayState getOrCreateOverlayState(String overlayId) {
        return overlayStates.computeIfAbsent(key(overlayId), ignored -> {
            setDirty();
            return new FestivalMapOverlayState(overlayId);
        });
    }

    public Collection<FestivalMapOverlayState> overlayStates() {
        return java.util.List.copyOf(overlayStates.values());
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        ListTag activeList = new ListTag();
        for (String festivalId : activePassiveFestivalIds) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", festivalId);
            activeList.add(entry);
        }
        tag.put("ActivePassiveFestivals", activeList);

        ListTag sessionList = new ListTag();
        for (FestivalSessionState session : sessions.values()) {
            sessionList.add(session.save());
        }
        tag.put("Sessions", sessionList);

        ListTag overlayList = new ListTag();
        for (FestivalMapOverlayState overlayState : overlayStates.values()) {
            overlayList.add(overlayState.save());
        }
        tag.put("OverlayStates", overlayList);
        return tag;
    }

    private static FestivalWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        FestivalWorldData data = new FestivalWorldData();
        ListTag activeList = tag.getList("ActivePassiveFestivals", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            String id = activeList.getCompound(i).getString("Id");
            if (!id.isBlank()) {
                data.activePassiveFestivalIds.add(id);
            }
        }

        ListTag sessionList = tag.getList("Sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < sessionList.size(); i++) {
            FestivalSessionState session = FestivalSessionState.load(sessionList.getCompound(i));
            if (!session.festivalId().isBlank()) {
                data.sessions.put(key(session.festivalId()), session);
            }
        }

        ListTag overlayList = tag.getList("OverlayStates", Tag.TAG_COMPOUND);
        for (int i = 0; i < overlayList.size(); i++) {
            FestivalMapOverlayState overlayState = FestivalMapOverlayState.load(overlayList.getCompound(i));
            if (!overlayState.overlayId().isBlank()) {
                data.overlayStates.put(key(overlayState.overlayId()), overlayState);
            }
        }
        StardewCraft.LOGGER.info("[FESTIVAL] Loaded {} sessions, {} overlay states, and {} active passive festivals",
            data.sessions.size(), data.overlayStates.size(), data.activePassiveFestivalIds.size());
        return data;
    }

    private static String sessionKey(String festivalId) {
        return key(festivalId);
    }

    private void attachLevel(ServerLevel level) {
        this.level = level;
        for (FestivalSessionState session : sessions.values()) {
            session.attachLevel(level);
        }
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
