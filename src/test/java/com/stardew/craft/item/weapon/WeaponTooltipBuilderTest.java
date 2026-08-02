package com.stardew.craft.item.weapon;

import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponType;
import com.stardew.craft.combat.equipment.EquipmentSlotResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaponTooltipBuilderTest {
    @BeforeAll
    static void registerPublicWeapon() {
        StardewEquipmentDataApi.registerProvider(
                ResourceLocation.fromNamespaceAndPath(
                        "tooltip_test",
                        "critical_stats"
                ),
                100,
                stack -> stack.is(Items.FEATHER)
                        ? publicWeaponData()
                        : null
        );
    }

    @Test
    void publicWeaponShowsCompleteSharedCombatStats() {
        List<Component> lines = WeaponTooltipBuilder
                .buildPublicApiCombatAttributes(
                        new ItemStack(Items.FEATHER)
                );

        assertEquals(5, lines.size());
        assertEquals(
                Set.of(
                        "stardewcraft.weapon.tooltip.damage",
                        "stardewcraft.weapon.tooltip.speed",
                        "stardewcraft.weapon.tooltip.crit_chance",
                        "stardewcraft.weapon.tooltip.crit_damage",
                        "stardewcraft.weapon.tooltip.defense"
                ),
                lines.stream()
                        .map(WeaponTooltipBuilderTest::lastTranslation)
                        .map(TranslatableContents::getKey)
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(
                "6%",
                translationArgument(
                        lines.get(2),
                        "stardewcraft.weapon.tooltip.attr_value"
                )
        );
        assertEquals(
                "+50%",
                translationArgument(
                        lines.get(3),
                        "stardewcraft.weapon.tooltip.attr_value"
                )
        );
        assertEquals(
                "stardewcraft.weapon.tooltip.crit_damage",
                lastTranslation(lines.get(3)).getKey()
        );
    }

    @Test
    void dragonToothCriticalBonusesUseTheirActualPercentUnits() {
        assertEquals(
                "4%",
                translationArgument(
                        WeaponTooltipBuilder.formatDragonTooth(
                                new WeaponForgeData.DragonToothBonus(
                                        "crit",
                                        2
                                )
                        ),
                        "stardewcraft.weapon.tooltip.dragon_tooth.crit"
                )
        );
        assertEquals(
                "100%",
                translationArgument(
                        WeaponTooltipBuilder.formatDragonTooth(
                                new WeaponForgeData.DragonToothBonus(
                                        "crit_power",
                                        2
                                )
                        ),
                        "stardewcraft.weapon.tooltip.dragon_tooth.crit_power"
                )
        );
    }

    @Test
    void fractionalKnockbackKeepsItsRuntimePrecision() {
        assertEquals(
                "+0.5",
                WeaponTooltipBuilder.formatSignedNumber(0.5F)
        );
        assertEquals(
                "+1.5",
                WeaponTooltipBuilder.formatSignedNumber(1.5F)
        );
        assertEquals(
                "-0.5",
                WeaponTooltipBuilder.formatSignedNumber(-0.5F)
        );
    }

    @Test
    void daggerTooltipIncludesItsIntrinsicCriticalFormula() {
        WeaponStats dagger = WeaponStats.builder()
                .weaponType(WeaponType.DAGGER)
                .critChance(0.04F)
                .build();
        assertEquals(
                0.0504F,
                WeaponTooltipBuilder.effectiveWeaponCriticalChance(dagger),
                0.000001F
        );
    }

    private static StardewEquipmentData publicWeaponData() {
        return new StardewEquipmentData(
                EquipmentSlotResolver.WEAPON,
                0,
                0,
                0,
                0.0f,
                0.04f,
                0.5f,
                0,
                0.0f,
                0.0f,
                0.0f,
                0,
                List.of(),
                Optional.of(new StardewEquipmentData.Weapon(
                        "sword",
                        1.0f,
                        2.0f,
                        0.02f,
                        2,
                        2,
                        3.0f,
                        1.0f,
                        Optional.empty(),
                        Optional.empty()
                ))
        );
    }

    private static Object translationArgument(
            Component component,
            String key
    ) {
        return translations(component).stream()
                .filter(contents -> key.equals(contents.getKey()))
                .findFirst()
                .orElseThrow()
                .getArgs()[0];
    }

    private static TranslatableContents lastTranslation(Component component) {
        List<TranslatableContents> translations = translations(component);
        return translations.getLast();
    }

    private static List<TranslatableContents> translations(
            Component component
    ) {
        List<TranslatableContents> result = new ArrayList<>();
        collectTranslations(component, result);
        return result;
    }

    private static void collectTranslations(
            Component component,
            List<TranslatableContents> result
    ) {
        if (component.getContents() instanceof TranslatableContents contents) {
            result.add(contents);
        }
        for (Component sibling : component.getSiblings()) {
            collectTranslations(sibling, result);
        }
    }
}
