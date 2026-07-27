package com.stardew.craft.festival;

import com.stardew.craft.api.v1.festival.StardewFestivalSessionPersistentData;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionSnapshot;
import com.stardew.craft.api.v1.festival.StardewFestivalSessions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FestivalSessionExtensionTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void snapshotAndNamespacedStateRoundTripWithoutExposingMutableSession() {
        ResourceLocation festivalId = ResourceLocation.fromNamespaceAndPath(
                "festival_session_test", "apple_day");
        ResourceLocation stateId = ResourceLocation.fromNamespaceAndPath(
                "festival_session_test",
                "session_" + IDS.incrementAndGet());
        ResourceLocation rewardId = ResourceLocation.fromNamespaceAndPath(
                "festival_session_test", "apple_prize");
        StardewFestivalSessionPersistentData.Key key =
                StardewFestivalSessionPersistentData.register(stateId, 3);
        FestivalSessionState state = new FestivalSessionState(
                festivalId.toString(), 4, 2, 17);
        UUID participant = UUID.randomUUID();
        state.addParticipant(participant);
        state.setPhase(FestivalSessionPhase.MAIN_EVENT);
        state.setMapOverlayPhase(FestivalMapOverlayPhase.APPLIED);
        CompoundTag payload = new CompoundTag();
        payload.putInt("score", 42);
        state.persistentData().writeEntry(key, payload);
        state.addRewardClaim(rewardId, participant);

        StardewFestivalSessionSnapshot snapshot =
                StardewFestivalSessions.snapshot(state);
        assertEquals(festivalId, snapshot.festivalId());
        assertEquals(StardewFestivalSessionSnapshot.Phase.MAIN_EVENT,
                snapshot.phase());
        assertEquals(StardewFestivalSessionSnapshot.MapPhase.APPLIED,
                snapshot.mapPhase());
        assertEquals(java.util.Set.of(participant),
                snapshot.participants());

        FestivalSessionState loaded =
                FestivalSessionState.load(state.save());
        var stored = loaded.persistentData()
                .readEntry(key).orElseThrow();
        assertEquals(3, stored.storedVersion());
        assertEquals(42, stored.payload().getInt("score"));
        assertEquals(java.util.Set.of(participant),
                loaded.participants());
        assertTrue(loaded.hasRewardClaim(rewardId, participant));
    }

    @Test
    void unknownAddonSessionStateSurvivesLoadAndRewrite() {
        FestivalSessionState state = new FestivalSessionState(
                "festival_session_test:missing_addon", 1, 0, 5);
        CompoundTag saved = state.save();
        CompoundTag payload = new CompoundTag();
        payload.putString("variant", "extended");
        CompoundTag entry = new CompoundTag();
        entry.putInt("version", 8);
        entry.put("payload", payload);
        CompoundTag addonData = new CompoundTag();
        addonData.put("missing_addon:festival_state", entry);
        saved.put("AddonData", addonData);

        CompoundTag rewritten =
                FestivalSessionState.load(saved).save();

        assertEquals("extended",
                rewritten.getCompound("AddonData")
                        .getCompound("missing_addon:festival_state")
                        .getCompound("payload")
                        .getString("variant"));
        assertTrue(rewritten.contains("AddonData"));
    }
}
