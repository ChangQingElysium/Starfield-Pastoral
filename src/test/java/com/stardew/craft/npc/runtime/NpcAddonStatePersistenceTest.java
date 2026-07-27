package com.stardew.craft.npc.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAddonStatePersistenceTest {
    private static final HolderLookup.Provider REGISTRIES =
            VanillaRegistries.createLookup();
    private static final String NPC_ID =
            "example_stardew_addon:archivist";

    @Test
    void friendshipSurvivesWithoutConsultingTheActiveNpcCatalog() {
        UUID player = UUID.randomUUID();
        NpcFriendshipDataManager original =
                new NpcFriendshipDataManager();
        NpcFriendshipDataManager.FriendshipState state =
                original.getOrCreate(player, NPC_ID);
        state.addPoints(725, 2_500);
        state.setLastTalkDayKey(41);
        state.applyGiftCounters(42, 6);

        CompoundTag saved = original.save(
                new CompoundTag(), REGISTRIES);
        NpcFriendshipDataManager restored =
                NpcFriendshipDataManager.load(
                        saved, REGISTRIES);

        NpcFriendshipDataManager.FriendshipState restoredState =
                restored.get(player, NPC_ID);
        assertEquals(725, restoredState.points());
        assertEquals(41, restoredState.lastTalkDayKey());
        assertEquals(42, restoredState.lastGiftDayKey());
        assertEquals(1, restoredState.giftsThisWeek());
    }

    @Test
    void runtimeRouteStateSurvivesWithoutAnEntityOrProfile() {
        NpcRuntimeDataManager original =
                new NpcRuntimeDataManager();
        NpcRuntimeState state =
                original.getOrCreate(NPC_ID);
        state.setLocationName(
                "example_stardew_addon:orchard");
        state.setActiveScheduleKey("spring");
        state.setScheduleCheckpoint(1_800);
        state.setTileX(-149);
        state.setTileY(82);
        state.setNamedPointId(
                "example_stardew_addon:orchard_stage");
        state.setPathingSuppressed(true);

        CompoundTag saved = original.save(
                new CompoundTag(), REGISTRIES);
        NpcRuntimeDataManager restored =
                NpcRuntimeDataManager.load(
                        saved, REGISTRIES);

        assertTrue(restored.states().containsKey(NPC_ID));
        NpcRuntimeState restoredState =
                restored.states().get(NPC_ID);
        assertEquals(
                "example_stardew_addon:orchard",
                restoredState.locationName());
        assertEquals("spring",
                restoredState.activeScheduleKey());
        assertEquals(1_800,
                restoredState.scheduleCheckpoint());
        assertEquals(-149, restoredState.tileX());
        assertEquals(82, restoredState.tileY());
        assertEquals(
                "example_stardew_addon:orchard_stage",
                restoredState.namedPointId());
        assertTrue(restoredState.pathingSuppressed());
    }
}
