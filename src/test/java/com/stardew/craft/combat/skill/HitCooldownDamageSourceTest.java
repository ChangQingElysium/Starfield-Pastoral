package com.stardew.craft.combat.skill;

import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitCooldownDamageSourceTest {
    private static final ResourceKey<DamageType> TYPE_KEY =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            "stardewcraft_test",
                            "authored_hit"
                    )
            );
    private static final TagKey<DamageType> ORIGINAL_TAG = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft_test",
                    "original_semantics"
            )
    );
    private static final TagKey<DamageType> UNRELATED_TAG = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                    "stardewcraft_test",
                    "unrelated"
            )
    );

    @Test
    void wrapperAddsOnlyCooldownBypassAndRetainsSourceSemantics() {
        DamageType type = new DamageType(
                "stardewcraft_test.authored_hit",
                DamageScaling.NEVER,
                0.35F,
                DamageEffects.THORNS,
                DeathMessageType.INTENTIONAL_GAME_DESIGN
        );
        TaggedHolder holder = new TaggedHolder(
                type,
                TYPE_KEY,
                Set.of(ORIGINAL_TAG)
        );
        Vec3 position = new Vec3(2.5D, 4.0D, -7.25D);
        DamageSource original = new DamageSource(holder, position);

        DamageSource wrapped =
                HitCooldownDamageSource.bypassVanillaCooldown(original);

        assertNotSame(original, wrapped);
        assertTrue(wrapped.is(DamageTypeTags.BYPASSES_COOLDOWN));
        assertTrue(wrapped.is(ORIGINAL_TAG));
        assertFalse(wrapped.is(UNRELATED_TAG));
        assertTrue(wrapped.is(TYPE_KEY));
        assertSame(holder, wrapped.typeHolder());
        assertSame(type, wrapped.type());
        assertEquals(original.getMsgId(), wrapped.getMsgId());
        assertEquals(original.getFoodExhaustion(), wrapped.getFoodExhaustion());
        assertSame(position, wrapped.sourcePositionRaw());
        assertSame(position, wrapped.getSourcePosition());
        assertSame(original.getDirectEntity(), wrapped.getDirectEntity());
        assertSame(original.getEntity(), wrapped.getEntity());
    }

    @Test
    void alreadyBypassingSourceIsNotWrappedAgain() {
        DamageType type = new DamageType("existing_bypass", 0.0F);
        DamageSource original = new DamageSource(new TaggedHolder(
                type,
                TYPE_KEY,
                Set.of(DamageTypeTags.BYPASSES_COOLDOWN)
        ));

        assertSame(
                original,
                HitCooldownDamageSource.bypassVanillaCooldown(original)
        );
    }

    @Test
    void policyDefaultsRemainExplicitAndNonBypassing() {
        DamageSource original = new DamageSource(Holder.direct(
                new DamageType("policy", 0.0F)
        ));

        assertSame(
                original,
                WeaponSkillDamage.applyHitCooldownPolicy(
                        original,
                        WeaponSkillDamage.HitCooldownPolicy.RESPECT_VANILLA
                )
        );
        assertTrue(WeaponSkillDamage.applyHitCooldownPolicy(
                original,
                WeaponSkillDamage.HitCooldownPolicy
                        .BYPASS_FOR_AUTHORED_SEQUENCE
        ).is(DamageTypeTags.BYPASSES_COOLDOWN));
        assertThrows(
                NullPointerException.class,
                () -> WeaponSkillDamage.applyHitCooldownPolicy(
                        original,
                        null
                )
        );
    }

    @Test
    void wrapperCopiesBothEntityRolesAndRawPosition() throws IOException {
        String source = Files.readString(Path.of(
                System.getProperty("stardewcraft.projectDir", "."),
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "HitCooldownDamageSource.java"
        )).replace("\r\n", "\n");

        assertTrue(source.contains("source.typeHolder(),"));
        assertTrue(source.contains("source.getDirectEntity(),"));
        assertTrue(source.contains("source.getEntity(),"));
        assertTrue(source.contains("source.sourcePositionRaw()"));
        assertFalse(source.contains("BYPASSES_INVULNERABILITY"));
    }

    private record TaggedHolder(
            DamageType value,
            ResourceKey<DamageType> key,
            Set<TagKey<DamageType>> assignedTags
    ) implements Holder<DamageType> {
        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(ResourceLocation location) {
            return key.location().equals(location);
        }

        @Override
        public boolean is(ResourceKey<DamageType> candidate) {
            return key.equals(candidate);
        }

        @Override
        public boolean is(Predicate<ResourceKey<DamageType>> predicate) {
            return predicate.test(key);
        }

        @Override
        public boolean is(TagKey<DamageType> tagKey) {
            return assignedTags.contains(tagKey);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean is(Holder<DamageType> holder) {
            return holder.is(key);
        }

        @Override
        public Stream<TagKey<DamageType>> tags() {
            return assignedTags.stream();
        }

        @Override
        public Either<ResourceKey<DamageType>, DamageType> unwrap() {
            return Either.left(key);
        }

        @Override
        public Optional<ResourceKey<DamageType>> unwrapKey() {
            return Optional.of(key);
        }

        @Override
        public Kind kind() {
            return Kind.REFERENCE;
        }

        @Override
        public boolean canSerializeIn(HolderOwner<DamageType> owner) {
            return true;
        }
    }
}
