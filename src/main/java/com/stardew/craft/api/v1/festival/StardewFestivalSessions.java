package com.stardew.craft.api.v1.festival;

import com.stardew.craft.festival.FestivalDefinition;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.festival.FestivalSessionState;
import com.stardew.craft.festival.FestivalWorldData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/** Read-only server facade for current and historical festival sessions. */
public final class StardewFestivalSessions {
    private StardewFestivalSessions() {
    }

    public static Optional<StardewFestivalSessionSnapshot> find(
            ServerLevel level,
            ResourceLocation festivalId
    ) {
        if (level == null || festivalId == null) {
            return Optional.empty();
        }
        String runtimeId = FestivalRegistry.get(festivalId)
                .map(FestivalDefinition::id)
                .orElse(festivalId.toString());
        return FestivalWorldData.get(level).getSession(runtimeId)
                .map(StardewFestivalSessions::snapshot);
    }

    public static List<StardewFestivalSessionSnapshot> all(
            ServerLevel level
    ) {
        if (level == null) {
            return List.of();
        }
        return FestivalWorldData.get(level).sessions().stream()
                .map(StardewFestivalSessions::snapshot)
                .toList();
    }

    public static StardewFestivalSessionSnapshot snapshot(
            FestivalSessionState state
    ) {
        ResourceLocation resourceId = FestivalRegistry
                .get(state.festivalId())
                .map(FestivalDefinition::resourceId)
                .orElseGet(() -> {
                    ResourceLocation parsed = ResourceLocation.tryParse(
                            state.festivalId());
                    return parsed != null ? parsed
                            : ResourceLocation.fromNamespaceAndPath(
                                    "stardewcraft",
                                    state.festivalId().toLowerCase(
                                            java.util.Locale.ROOT));
                });
        return new StardewFestivalSessionSnapshot(
                resourceId,
                state.festivalId(),
                state.year(),
                state.season(),
                state.day(),
                StardewFestivalSessionSnapshot.Phase.valueOf(
                        state.phase().name()),
                StardewFestivalSessionSnapshot.MapPhase.valueOf(
                        state.mapOverlayPhase().name()),
                state.participants());
    }
}
