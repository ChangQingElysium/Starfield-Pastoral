package com.stardew.craft.combat;

import com.stardew.craft.combat.network.LavaKatanaImpactPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Cosmetic feedback from the final health-damage boundary, including normal AoE hits. */
final class LavaKatanaHitPresentation {
    private LavaKatanaHitPresentation() {}

    static void emit(ResolvedWeaponHit hit) {
        if (!shouldPresent(hit.weaponIdentity().logicId(), hit.authoredSkillContext().getSkillId(),
                hit.dealtPositiveDamage()) || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        boolean burn = "lava_katana_burn".equals(hit.authoredSkillContext().getSkillId());
        if (burn && !com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler.isActive(player, hit.gameTick())) return;
        AABB bounds = hit.target().getBoundingBox();
        Vec3 center = bounds.getCenter();
        Vec3 eye = player.getEyePosition();
        // The actual hurt call has no contact point. Project toward the target's surface.
        Vec3 contact = bounds.clip(eye, center).orElse(center);
        Vec3 direction = center.subtract(eye).normalize();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new LavaKatanaImpactPayload(
                player.getId(), hit.target().getId(), hit.gameTick(),
                contact.x, contact.y, contact.z,
                (float) direction.x, (float) direction.y, (float) direction.z,
                hit.displayCritical(), "lava_katana_brand".equals(hit.authoredSkillContext().getSkillId()),
                "lava_katana_finisher".equals(hit.authoredSkillContext().getSkillId()), burn));
    }

    static boolean shouldPresent(String weaponId, String skillId, boolean positiveDamage) {
        return positiveDamage && "lava_katana".equals(weaponId)
                && ("normal".equals(skillId) || "lava_katana_brand".equals(skillId)
                    || "lava_katana_finisher".equals(skillId) || "lava_katana_burn".equals(skillId));
    }
}
