package com.stardew.craft.combat.skill;

import java.util.Objects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Creates a per-hit damage source that bypasses only vanilla's post-hit
 * cooldown check.
 *
 * <p>The wrapped source retains the original damage-type holder, direct and
 * causing entities, and explicit source position. All damage-type queries
 * except {@link DamageTypeTags#BYPASSES_COOLDOWN} therefore keep their
 * original meaning.</p>
 */
public final class HitCooldownDamageSource extends DamageSource {
    private HitCooldownDamageSource(DamageSource source) {
        super(
                source.typeHolder(),
                source.getDirectEntity(),
                source.getEntity(),
                source.sourcePositionRaw()
        );
    }

    /**
     * Returns a source scoped to one authored hit. Already-bypassing sources
     * are returned unchanged.
     */
    public static DamageSource bypassVanillaCooldown(DamageSource source) {
        Objects.requireNonNull(source, "source");
        return source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                ? source
                : new HitCooldownDamageSource(source);
    }

    @Override
    public boolean is(TagKey<DamageType> damageTypeKey) {
        return DamageTypeTags.BYPASSES_COOLDOWN.equals(damageTypeKey)
                || super.is(damageTypeKey);
    }
}
