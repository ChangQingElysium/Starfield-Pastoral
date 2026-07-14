package com.stardew.craft.secretnote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNoteStoryTriggerServiceTest {
    @Test
    void qiCaveDialogueBoundaryMatchesOriginal() {
        assertTrue(SecretNoteStoryTriggerService.isHonorableQiCaveRun(0));
        assertTrue(SecretNoteStoryTriggerService.isHonorableQiCaveRun(10));
        assertFalse(SecretNoteStoryTriggerService.isHonorableQiCaveRun(11));
    }
}
