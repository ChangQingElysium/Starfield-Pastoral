package com.stardew.craft.api.v1.npc;

import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.NpcDataRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewNpcContentsTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void joinsRegisteredProfileAndNamespacedDataContent() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("content_" + suffix);
        StardewNpcProfiles.register(
                id("content_registration_" + suffix),
                100,
                new StardewNpcDefinition(
                        npcId,
                        new StardewNpcProfile(
                                npcId, true, false, "idle_only",
                                0, 0, 0, 0, 0, false),
                        new StardewNpcDisplay(
                                npcId,
                                "entity.npc_content_test.npc.content",
                                id("textures/portraits/content.png"),
                                128,
                                320,
                                id("textures/mugshots/content.png"),
                                16,
                                24,
                                "npc_content_test.relationship.friend",
                                false)));

        Map<String, JsonObject> oldDialogues =
                new LinkedHashMap<>(NpcDataRegistry.dialogues());
        Map<String, JsonObject> oldSchedules =
                new LinkedHashMap<>(NpcDataRegistry.schedules());
        Map<String, JsonObject> oldTastes =
                new LinkedHashMap<>(NpcDataRegistry.tastes());
        try {
            Map<String, JsonObject> dialogues = new LinkedHashMap<>(oldDialogues);
            Map<String, JsonObject> schedules = new LinkedHashMap<>(oldSchedules);
            Map<String, JsonObject> tastes = new LinkedHashMap<>(oldTastes);
            dialogues.put(npcId.toString(), new JsonObject());
            schedules.put(npcId.toString(), new JsonObject());
            tastes.put(npcId.toString(), new JsonObject());
            NpcDataRegistry.replaceDialogues(dialogues);
            NpcDataRegistry.replaceSchedules(schedules);
            NpcDataRegistry.replaceTastes(tastes);

            StardewNpcContentSnapshot snapshot =
                    StardewNpcContents.inspect(npcId);
            assertTrue(snapshot.hasProfile());
            assertTrue(snapshot.hasDialogue());
            assertTrue(snapshot.hasSchedule());
            assertTrue(snapshot.hasGiftTastes());
            assertTrue(snapshot.valid());
            assertTrue(StardewNpcContents.ids().contains(npcId));
        } finally {
            NpcDataRegistry.replaceDialogues(oldDialogues);
            NpcDataRegistry.replaceSchedules(oldSchedules);
            NpcDataRegistry.replaceTastes(oldTastes);
        }
    }

    @Test
    void reportsOrphanedNamespacedContent() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation npcId = id("orphan_" + suffix);
        Map<String, JsonObject> oldDialogues =
                new LinkedHashMap<>(NpcDataRegistry.dialogues());
        try {
            Map<String, JsonObject> dialogues = new LinkedHashMap<>(oldDialogues);
            dialogues.put(npcId.toString(), new JsonObject());
            NpcDataRegistry.replaceDialogues(dialogues);

            StardewNpcContentSnapshot snapshot =
                    StardewNpcContents.inspect(npcId);
            assertFalse(snapshot.hasProfile());
            assertTrue(snapshot.issues().contains("missing_profile"));
        } finally {
            NpcDataRegistry.replaceDialogues(oldDialogues);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("npc_content_test", path);
    }
}
