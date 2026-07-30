package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.combat.skill.handler.BuiltinWeaponSkillHandlers;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only inventory of weapon skill definitions and their current execution path.
 *
 * <p>The inventory is derived from the weapon registry and runtime registrations so
 * migration tooling never needs a second hand-maintained list of skill ids.</p>
 */
public final class WeaponSkillMigrationAudit {
    private WeaponSkillMigrationAudit() {}

    public static Snapshot snapshot() {
        BuiltinWeaponSkillHandlers.bootstrap();

        Map<ResourceLocation, List<DefinitionUse>> mutableDefinitions = new LinkedHashMap<>();
        WeaponRegistry.getAll().stream()
                .sorted(Comparator.comparing(WeaponData::getId))
                .forEach(weapon -> {
                    addDefinition(mutableDefinitions, weapon, SkillSlot.PRIMARY);
                    addDefinition(mutableDefinitions, weapon, SkillSlot.SECONDARY);
                });

        Map<ResourceLocation, List<DefinitionUse>> definitions = new LinkedHashMap<>();
        mutableDefinitions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)
                ))
                .forEach(entry -> definitions.put(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                ));

        Set<ResourceLocation> runtimeSkills = new LinkedHashSet<>(
                WeaponSkillRuntime.registeredSkillIds()
        );
        Set<ResourceLocation> legacySkills = new LinkedHashSet<>();
        for (ResourceLocation skillId : definitions.keySet()) {
            if (!runtimeSkills.contains(skillId)) {
                legacySkills.add(skillId);
            }
        }
        return new Snapshot(definitions, runtimeSkills, legacySkills);
    }

    private static void addDefinition(
            Map<ResourceLocation, List<DefinitionUse>> definitions,
            WeaponData weapon,
            SkillSlot slot
    ) {
        WeaponSkillData skill = weapon.getSkill(slot == SkillSlot.SECONDARY);
        if (skill == null) {
            return;
        }
        definitions.computeIfAbsent(skill.getResourceId(), ignored -> new ArrayList<>())
                .add(new DefinitionUse(weapon.getId(), slot));
    }

    public enum SkillSlot {
        PRIMARY,
        SECONDARY
    }

    public record DefinitionUse(String weaponId, SkillSlot slot) {
        public DefinitionUse {
            Objects.requireNonNull(weaponId, "weaponId");
            Objects.requireNonNull(slot, "slot");
        }
    }

    public record Snapshot(
            Map<ResourceLocation, List<DefinitionUse>> definitions,
            Set<ResourceLocation> runtimeSkills,
            Set<ResourceLocation> legacySkills
    ) {
        public Snapshot {
            Objects.requireNonNull(definitions, "definitions");
            Objects.requireNonNull(runtimeSkills, "runtimeSkills");
            Objects.requireNonNull(legacySkills, "legacySkills");

            Map<ResourceLocation, List<DefinitionUse>> definitionsCopy =
                    new LinkedHashMap<>();
            definitions.forEach((skillId, uses) -> definitionsCopy.put(
                    Objects.requireNonNull(skillId, "skillId"),
                    List.copyOf(uses)
            ));
            definitions = Collections.unmodifiableMap(definitionsCopy);
            runtimeSkills = Collections.unmodifiableSet(new LinkedHashSet<>(runtimeSkills));
            legacySkills = Collections.unmodifiableSet(new LinkedHashSet<>(legacySkills));

            Set<ResourceLocation> overlap = new LinkedHashSet<>(runtimeSkills);
            overlap.retainAll(legacySkills);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                        "Skills cannot be both runtime and legacy: " + overlap
                );
            }

            Set<ResourceLocation> classified = new LinkedHashSet<>(runtimeSkills);
            classified.addAll(legacySkills);
            if (!classified.equals(definitions.keySet())) {
                throw new IllegalArgumentException(
                        "Every defined skill must have exactly one execution path"
                );
            }
        }

        public int definedSkillCount() {
            return definitions.size();
        }

        public int definedSlotCount() {
            return definitions.values().stream().mapToInt(List::size).sum();
        }
    }
}
