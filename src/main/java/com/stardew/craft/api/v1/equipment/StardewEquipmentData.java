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
        float attackMultiplier,
        float critChance,
        float critPower,
        int magneticRadius,
        float knockbackBonus,
        float weaponSpeedMultiplier,
        float luck,
        int lightLevel,
        List<ResourceLocation> effects,
        Optional<Weapon> weapon
) {
    public static final Codec<StardewEquipmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "slot",
                    new ResourceLocation("stardewcraft", "other"))
                    .forGetter(StardewEquipmentData::slot),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "defense", 0).forGetter(StardewEquipmentData::defense),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "immunity", 0).forGetter(StardewEquipmentData::immunity),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "attack", 0).forGetter(StardewEquipmentData::attack),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "attack_multiplier", 0.0F).forGetter(StardewEquipmentData::attackMultiplier),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "crit_chance", 0.0F).forGetter(StardewEquipmentData::critChance),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "crit_power", 0.0F).forGetter(StardewEquipmentData::critPower),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "magnetic_radius", 0).forGetter(StardewEquipmentData::magneticRadius),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "knockback_bonus", 0.0F).forGetter(StardewEquipmentData::knockbackBonus),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "weapon_speed_multiplier", 0.0F).forGetter(StardewEquipmentData::weaponSpeedMultiplier),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "luck", 0.0F).forGetter(StardewEquipmentData::luck),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "light_level", 0).forGetter(StardewEquipmentData::lightLevel),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC.listOf(), "effects", List.of())
                    .forGetter(StardewEquipmentData::effects),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Weapon.CODEC, "weapon").forGetter(StardewEquipmentData::weapon)
    ).apply(instance, StardewEquipmentData::new));

    public StardewEquipmentData {
        effects = List.copyOf(effects == null ? List.of() : effects);
        weapon = weapon == null ? Optional.empty() : weapon;
    }

    /** Source-compatible constructor for integrations compiled against API v1. */
    public StardewEquipmentData(
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
        this(
                slot,
                defense,
                immunity,
                attack,
                0.0F,
                critChance,
                critPower,
                magneticRadius,
                knockbackBonus,
                0.0F,
                luck,
                lightLevel,
                effects,
                weapon
        );
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
            Optional<ResourceLocation> secondarySkill,
            Optional<Integer> rawSpeed
    ) {
        public static final Codec<Weapon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "type", "sword").forGetter(Weapon::type),
                Codec.FLOAT.fieldOf("min_damage").forGetter(Weapon::minDamage),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Weapon::maxDamage),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "base_crit_chance", 0.02F).forGetter(Weapon::baseCritChance),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "speed", 0).forGetter(Weapon::speed),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "defense", 0).forGetter(Weapon::defense),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "precision", 0.0F).forGetter(Weapon::precision),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.FLOAT, "knockback", -1.0F).forGetter(Weapon::knockback),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "primary_skill").forGetter(Weapon::primarySkill),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "secondary_skill").forGetter(Weapon::secondarySkill),
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "raw_speed").forGetter(Weapon::rawSpeed)
        ).apply(instance, Weapon::new));

        public Weapon {
            primarySkill = primarySkill == null ? Optional.empty() : primarySkill;
            secondarySkill = secondarySkill == null ? Optional.empty() : secondarySkill;
            rawSpeed = rawSpeed == null ? Optional.empty() : rawSpeed;
        }

        /** Source- and binary-compatible constructor for existing API v1 integrations. */
        public Weapon(
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
            this(
                    type,
                    minDamage,
                    maxDamage,
                    baseCritChance,
                    speed,
                    defense,
                    precision,
                    knockback,
                    primarySkill,
                    secondarySkill,
                    Optional.empty()
            );
        }
    }
}
