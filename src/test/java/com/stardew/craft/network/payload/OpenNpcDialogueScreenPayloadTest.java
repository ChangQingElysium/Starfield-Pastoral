package com.stardew.craft.network.payload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenNpcDialogueScreenPayloadTest {
    @Test
    void prerequisiteBranchUsesPersistedDialogueAnswer() {
        String raw = "$p 27#answered|not answered";
        assertEquals("answered",
                OpenNpcDialogueScreenPayload.resolveDialogueCommands(raw, List.of("27")));
        assertEquals("not answered",
                OpenNpcDialogueScreenPayload.resolveDialogueCommands(raw, List.of()));
    }

    @Test
    void worldStateBranchesAreNotHardcodedToTheFirstOption() {
        assertEquals("bus is broken",
                OpenNpcDialogueScreenPayload.resolveDialogueCommands(
                        "$d bus#bus is fixed|bus is broken", List.of()));
        assertEquals("community center unavailable",
                OpenNpcDialogueScreenPayload.resolveDialogueCommands(
                        "$d cc#community center available|community center unavailable", List.of()));
    }
}
