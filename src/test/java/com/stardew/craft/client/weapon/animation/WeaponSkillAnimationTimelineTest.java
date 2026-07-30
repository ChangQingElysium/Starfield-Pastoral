package com.stardew.craft.client.weapon.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillAnimationTimelineTest {
    private static final WeaponSkillPose BASE =
            new WeaponSkillPose(0f, -90f, 25f, 1.13f, 3.2f, 1.13f);

    @Test
    void crescentSlashStartsAndEndsAtRestWithContactOnTickThree() {
        assertEquals(BASE, CutlassCrescentSlashAnimation.sampleRightAtTick(0.0f));
        assertEquals(BASE, CutlassCrescentSlashAnimation.sampleRightAtTick(8.0f));

        WeaponSkillPose compressed =
                CutlassCrescentSlashAnimation.sampleRightAtTick(2.0f);
        WeaponSkillPose contact =
                CutlassCrescentSlashAnimation.sampleRightAtTick(3.0f);
        WeaponSkillPose follow =
                CutlassCrescentSlashAnimation.sampleRightAtTick(4.25f);

        assertTrue(contact.rz() > compressed.rz() + 60.0f);
        assertTrue(follow.rz() > contact.rz() + 50.0f);
        assertTrue(contact.tx() < compressed.tx());
    }

    @Test
    void forestBlessingIsARisingCutWithContactOnTickThree() {
        assertEquals(BASE, ForestBlessingCastAnimation.sampleRightAtTick(0.0f));
        assertEquals(BASE, ForestBlessingCastAnimation.sampleRightAtTick(8.0f));

        WeaponSkillPose compressed =
                ForestBlessingCastAnimation.sampleRightAtTick(2.0f);
        WeaponSkillPose contact =
                ForestBlessingCastAnimation.sampleRightAtTick(3.0f);
        WeaponSkillPose release =
                ForestBlessingCastAnimation.sampleRightAtTick(4.25f);

        assertTrue(contact.rz() < compressed.rz() - 60.0f);
        assertTrue(release.rz() < contact.rz() - 25.0f);
        assertTrue(contact.ty() < compressed.ty());
    }
}
