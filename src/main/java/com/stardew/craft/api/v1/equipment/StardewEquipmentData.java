package com.stardew.craft.api.v1.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Static ring, boots or weapon metadata attached through an item Data Map. */
public record StardewEquipmentData(
        ResourceLocation slot,
        int defense,
        int immunity,
        int attack,
        float critChance,
        float critPower,
        int magneticRadius,
        float knockbackBonus,
        float luck,
        int lightLevel,
        List<ResourceLocation> effects,
        Optional<Weapon> weapon
) {
    public static final Codec<StardewEquipmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("slot",
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "other"))
                    .forGetter(StardewEquipmentData::slot),
            Codec.INT.optionalFieldOf("defense", 0).forGetter(StardewEquipmentData::defense),
            Codec.INT.optionalFieldOf("immunity", 0).forGetter(StardewEquipmentData::immunity),
            Codec.INT.optionalFieldOf("attack", 0).forGetter(StardewEquipmentData::attack),
            Codec.FLOAT.optionalFieldOf("crit_chance", 0.0F).forGetter(StardewEquipmentData::critChance),
            Codec.FLOAT.optionalFieldOf("crit_power", 0.0F).forGetter(StardewEquipmentData::critPower),
            Codec.INT.optionalFieldOf("magnetic_radius", 0).forGetter(StardewEquipmentData::magneticRadius),
            Codec.FLOAT.optionalFieldOf("knockback_bonus", 0.0F).forGetter(StardewEquipmentData::knockbackBonus),
            Codec.FLOAT.optionalFieldOf("luck", 0.0F).forGetter(StardewEquipmentData::luck),
            Codec.INT.optionalFieldOf("light_level", 0).forGetter(StardewEquipmentData::lightLevel),
            ResourceLocation.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(StardewEquipmentData::effects),
            Weapon.CODEC.optionalFieldOf("weapon").forGetter(StardewEquipmentData::weapon)
    ).apply(instance, StardewEquipmentData::new));

    public StardewEquipmentData {
        effects = List.copyOf(effects == null ? List.of() : effects);
        weapon = weapon == null ? Optional.empty() : weapon;
    }

    public record Weapon(
            String type,
            float minDamage,
            float maxDamage,
            float baseCritChance,
            int speed,
            int defense,
            float precision,
            float knockback,
            Optional<ResourceLocation> primarySkill,
            Optional<ResourceLocation> secondarySkill
    ) {
        public static final Codec<Weapon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("type", "sword").forGetter(Weapon::type),
                Codec.FLOAT.fieldOf("min_damage").forGetter(Weapon::minDamage),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Weapon::maxDamage),
                Codec.FLOAT.optionalFieldOf("base_crit_chance", 0.02F).forGetter(Weapon::baseCritChance),
                Codec.INT.optionalFieldOf("speed", 0).forGetter(Weapon::speed),
                Codec.INT.optionalFieldOf("defense", 0).forGetter(Weapon::defense),
                Codec.FLOAT.optionalFieldOf("precision", 0.0F).forGetter(Weapon::precision),
                Codec.FLOAT.optionalFieldOf("knockback", 0.0F).forGetter(Weapon::knockback),
                ResourceLocation.CODEC.optionalFieldOf("primary_skill").forGetter(Weapon::primarySkill),
                ResourceLocation.CODEC.optionalFieldOf("secondary_skill").forGetter(Weapon::secondarySkill)
        ).apply(instance, Weapon::new));

        public Weapon {
            primarySkill = primarySkill == null ? Optional.empty() : primarySkill;
            secondarySkill = secondarySkill == null ? Optional.empty() : secondarySkill;
        }
    }
}
