package com.stardew.craft.client.weapon.presentation;

import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class SkillPresentationContext {
    private final WeaponSkillAnimPayload payload;
    private final ClientLevel level;
    private final long playbackStartTick;

    SkillPresentationContext(
            WeaponSkillAnimPayload payload,
            ClientLevel level,
            long playbackStartTick
    ) {
        this.payload = payload;
        this.level = level;
        this.playbackStartTick = playbackStartTick >= 0L
                ? playbackStartTick
                : level.getGameTime();
    }

    WeaponSkillAnimPayload payload() {
        return payload;
    }

    ClientLevel level() {
        return level;
    }

    @Nullable
    Player caster() {
        Entity entity = level.getEntity(payload.casterEntityId());
        return entity instanceof Player player ? player : null;
    }

    boolean isLocalCaster() {
        Player local = Minecraft.getInstance().player;
        return local != null && local.getId() == payload.casterEntityId();
    }

    Vec3 origin() {
        return new Vec3(payload.originX(), payload.originY(), payload.originZ());
    }

    Vec3 anchor() {
        Player caster = caster();
        return caster != null ? caster.position() : origin();
    }

    Vec3 forward() {
        double radians = Math.toRadians(payload.yaw());
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    Vec3 right() {
        Vec3 forward = forward();
        return new Vec3(forward.z, 0.0, -forward.x);
    }

    RandomSource random(long salt) {
        return RandomSource.create(payload.seed() ^ salt);
    }

    float actionAge(float partialTick) {
        return Math.max(
                0.0f,
                (level.getGameTime() - playbackStartTick) + partialTick
        );
    }

    float actionProgress(float partialTick) {
        return Mth.clamp(
                actionAge(partialTick) / Math.max(1, payload.actionDurationTicks()),
                0.0f,
                1.0f
        );
    }
}
