package com.stardew.craft.combat.skill;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillContextStoreTest {
    @Test
    void contextTagRoundTripPreservesEveryDamageInput() {
        SkillContext source = SkillContext.builder()
                .skillId("critical_skill")
                .tier(SkillContext.SkillTier.MAJOR)
                .damageMultiplier(1.75F)
                .ignoreDefense(true)
                .guaranteedCrit(true)
                .critChanceBonus(0.35F)
                .build();

        CompoundTag tag =
                WeaponSkillContextStore.writeContextTag(source, 123L);
        SkillContext restored =
                WeaponSkillContextStore.readContextTag(tag);

        assertEquals(source.getSkillId(), restored.getSkillId());
        assertEquals(source.getTier(), restored.getTier());
        assertEquals(source.getDamageMultiplier(), restored.getDamageMultiplier());
        assertEquals(source.getCritChanceBonus(), restored.getCritChanceBonus());
        assertTrue(restored.isIgnoreDefense());
        assertTrue(restored.isGuaranteedCrit());
    }

    @Test
    void legacyTagWithoutCriticalBonusDefaultsToZero() {
        CompoundTag legacyTag = WeaponSkillContextStore.writeContextTag(
                SkillContext.normalAttack(),
                10L
        );
        legacyTag.remove("CritChanceBonus");
        SkillContext restored =
                WeaponSkillContextStore.readContextTag(legacyTag);

        assertEquals(0.0F, restored.getCritChanceBonus());
    }
}
