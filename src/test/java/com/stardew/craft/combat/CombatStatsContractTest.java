package com.stardew.craft.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatStatsContractTest {
    @Test
    void monsterStatsPersistDamageResilienceAndSchemaVersion() {
        MonsterStats source = MonsterStats.builder()
                .damage(18.0f)
                .resilience(7.0f)
                .missChance(0.15f)
                .experience(12)
                .isDangerous(true)
                .build();

        CompoundTag saved = source.toNBT();
        MonsterStats restored = MonsterStats.fromNBT(saved);

        assertEquals(MonsterStats.CURRENT_DATA_VERSION,
                saved.getInt(MonsterStats.TAG_DATA_VERSION));
        assertEquals(18.0f, restored.getDamage());
        assertEquals(7.0f, restored.getResilience());
        assertEquals(0.15f, restored.getMissChance());
        assertEquals(12, restored.getExperience());
        assertTrue(restored.isDangerous());
    }

    @Test
    void monsterResilienceIsAlwaysSubtractedInFull() {
        assertEquals(4.0f,
                DamagePipeline.calculateDefenseReduction(
                        10.0f, 4.0f,
                        DamageRequest.DefenseRule.FIXED_RESILIENCE, 1.0f));
        assertEquals(5.0f,
                DamagePipeline.calculateDefenseReduction(
                        10.0f, 5.0f,
                        DamageRequest.DefenseRule.FIXED_RESILIENCE, 1.0f));
    }

    @Test
    void playerDefenseUsesStardewsDiscreteZeroTenTwentyPercentDecay() {
        assertEquals(5.0f,
                DamagePipeline.calculateDefenseReduction(
                        10.0f, 5.0f,
                        DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE, 0.0f));
        assertEquals(4.5f,
                DamagePipeline.calculateDefenseReduction(
                        10.0f, 5.0f,
                        DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE, 0.34f));
        assertEquals(4.0f,
                DamagePipeline.calculateDefenseReduction(
                        10.0f, 5.0f,
                        DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE, 0.99f));
    }

    @Test
    void outgoingDamageMappingIsIdenticalInEveryDimension() {
        assertEquals(25.0f, DimensionDamageMapper.mapDamage(25.0f, true));
        assertEquals(25.0f, DimensionDamageMapper.mapDamage(25.0f, false));
        assertEquals(1.0f, DimensionDamageMapper.getDamageRatio());
    }

    @Test
    void attackMultiplierScalesWeaponRangeBeforeTheRollUsingStardewTruncation() {
        assertEquals(2.0f, DamageCalculator.applyAttackMultiplierToWeaponRange(2.0f, 1.10f));
        assertEquals(11.0f, DamageCalculator.applyAttackMultiplierToWeaponRange(10.0f, 1.10f));
    }

    @Test
    void luckScalesTheAlreadyResolvedCriticalChanceLikeStardew() {
        assertEquals(
                0.022f,
                DamageCalculator.applyLuckToCriticalChance(0.02f, 4.0f),
                0.00001f
        );
    }

    @Test
    void critPowerUsesStardewsFiftyPointScale() {
        assertEquals(
                3.5f,
                DamageCalculator.weaponCriticalMultiplier(
                        WeaponStats.builder()
                                .bonusCritPower(25.0f)
                                .build()
                )
        );
        assertEquals(
                7.0f,
                DamageCalculator.weaponCriticalMultiplier(
                        WeaponStats.builder()
                                .bonusCritPower(200.0f)
                                .build()
                )
        );
    }

    @Test
    void jadeAndInnateCritPowerKeepTheirDistinctStardewSemantics() {
        WeaponStats jadeForged = WeaponStats.applyForgeData(
                WeaponStats.builder().bonusCritPower(25.0f).build(),
                new WeaponForgeData.State(
                        List.of(new WeaponForgeData.GemForge("minecraft:jade", 1)),
                        "",
                        List.of(),
                        0,
                        "",
                        "",
                        false
                )
        );
        assertEquals(30.0f, jadeForged.getBonusCritPower());
        assertEquals(3.6f, DamageCalculator.weaponCriticalMultiplier(jadeForged), 0.00001f);

        WeaponStats innateForged = WeaponStats.applyForgeData(
                WeaponStats.builder().bonusCritPower(25.0f).build(),
                new WeaponForgeData.State(
                        List.of(),
                        "",
                        List.of(),
                        0,
                        "",
                        "crit_power:2",
                        false
                )
        );
        assertEquals(25.0f, innateForged.getBonusCritPower());
        assertEquals(1.0f, innateForged.getCritPowerMultiplierBonus());
        assertEquals(7.0f, DamageCalculator.weaponCriticalMultiplier(innateForged), 0.00001f);
    }

    @Test
    void emeraldAndInnateSpeedKeepTheirDistinctStardewSemantics() {
        WeaponStats emerald = WeaponStats.applyForgeData(
                WeaponStats.builder()
                        .weaponType(WeaponType.SWORD)
                        .rawSpeed(0)
                        .build(),
                new WeaponForgeData.State(
                        List.of(new WeaponForgeData.GemForge("minecraft:emerald", 1)),
                        "", List.of(), 0, "", "", false
                )
        );
        assertEquals(5, emerald.getRawSpeed());
        assertEquals(0.0F, emerald.getWeaponSpeedMultiplier());

        WeaponStats innate = WeaponStats.applyForgeData(
                WeaponStats.builder()
                        .weaponType(WeaponType.SWORD)
                        .rawSpeed(0)
                        .build(),
                new WeaponForgeData.State(
                        List.of(), "", List.of(), 0, "", "speed:2", false
                )
        );
        assertEquals(0, innate.getRawSpeed());
        assertEquals(0.2F, innate.getWeaponSpeedMultiplier(), 0.00001F);
    }

    @Test
    void publicWeaponCritPowerUsesTheRelativeMultiplierContract() {
        WeaponStats publicWeapon = WeaponStats.builder()
                .critPowerMultiplierBonus(0.5f)
                .build();

        assertEquals(4.5f, DamageCalculator.weaponCriticalMultiplier(publicWeapon));
    }

    @Test
    void weaponStatsMarkOldSnapshotsForMigration() {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        assertFalse(WeaponStats.hasCurrentDataVersion(stack));

        WeaponStats.builder()
                .weaponType(WeaponType.SWORD)
                .minDamage(8.0f)
                .maxDamage(12.0f)
                .build()
                .writeToItemStack(stack);
        assertTrue(WeaponStats.hasCurrentDataVersion(stack));

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = data.copyTag();
        CompoundTag weapon = root.getCompound(
                WeaponStats.TAG_STARDEW_WEAPON);
        weapon.putInt(
                WeaponStats.TAG_DATA_VERSION,
                WeaponStats.CURRENT_DATA_VERSION - 1
        );
        root.put(WeaponStats.TAG_STARDEW_WEAPON, weapon);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        assertFalse(WeaponStats.hasCurrentDataVersion(stack));
    }
}
