package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.network.WeaponSkillCounterAnimPayload;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WeaponSkillAnimationDispatcher {
    private static final Set<String> WORLD_PRESENTATION_SKILLS = Set.of(
            "crescent_slash",
            "forest_blessing"
    );

    private WeaponSkillAnimationDispatcher() {}

    @SuppressWarnings("null")
    public static void sendSkillAnim(ServerPlayer player, String weaponId, String skillId, int durationTicks) {
        sendSkillAnim(player, weaponId, skillId, durationTicks, durationTicks, 0);
    }

    @SuppressWarnings("null")
    public static void sendSkillAnim(
            ServerPlayer player,
            String weaponId,
            String skillId,
            int actionDurationTicks,
            int presentationDurationTicks
    ) {
        sendSkillAnim(
                player,
                weaponId,
                skillId,
                actionDurationTicks,
                presentationDurationTicks,
                0
        );
    }

    @SuppressWarnings("null")
    public static void sendSkillAnim(
            ServerPlayer player,
            String weaponId,
            String skillId,
            int actionDurationTicks,
            int presentationDurationTicks,
            int activeTickOffset
    ) {
        if (WeaponSkillRuntime.deferIfPreparing(() -> sendSkillAnim(
                player,
                weaponId,
                skillId,
                actionDurationTicks,
                presentationDurationTicks,
                activeTickOffset
        ))) {
            return;
        }
        WeaponSkillAnimPayload payload = new WeaponSkillAnimPayload(
                player.getId(),
                weaponId,
                skillId,
                Math.max(1, actionDurationTicks),
                Math.max(actionDurationTicks, presentationDurationTicks),
                player.level().getGameTime(),
                Math.clamp(activeTickOffset, 0, Math.max(0, actionDurationTicks - 1)),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getRandom().nextLong()
        );
        if (WORLD_PRESENTATION_SKILLS.contains(skillId)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        } else {
            // Legacy effects still contain local-player assumptions. Keep their old
            // recipient scope until each skill moves into the presentation runtime.
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendImpact(
            ServerPlayer player,
            String skillId,
            List<Integer> targetEntityIds,
            long seed
    ) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new WeaponSkillImpactPayload(
                        player.getId(),
                        skillId,
                        targetEntityIds,
                        seed
                )
        );
    }

    @SuppressWarnings("null")
    public static void sendCounterAnim(ServerPlayer player, String weaponId, String skillId, int durationTicks) {
        PacketDistributor.sendToPlayer(
                player,
                new WeaponSkillCounterAnimPayload(weaponId, skillId, durationTicks)
        );
    }
}
