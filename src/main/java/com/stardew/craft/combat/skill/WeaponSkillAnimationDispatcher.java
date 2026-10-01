package com.stardew.craft.combat.skill;

import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class WeaponSkillAnimationDispatcher {
    private static final Set<String> WORLD_PRESENTATION_SKILLS = Set.of(
            "light_counter", "light_counter_counter", "steel_spine_fury_enter", "steel_spine_fury", "steel_spine_fury_weak",
            "tetanus_strike", "tree_blessing",
            "steel_falchion_line", "steel_falchion_trace",
            "crescent_slash",
            "desperate_plunder", "silver_foldback", "silver_foldback_return", "silver_foldback_stay", "silver_foldback_empty",
            "iron_dirk_thrust", "wind_spire_thrust",
            "bone_fracture", "claymore_foldback", "claymore_foldback_return",
            "dwarf_rune_guard", "dwarf_fortress", "dwarf_dagger_thrust", "dwarf_dagger_rush",
            "iridium_needle_thrust", "iridium_needle_strike", "iridium_needle_final", "iridium_needle_frenzy", "burglar_shank",
            "shadow_dagger_execute", "insect_eye_stance", "insect_dash",
            "crystal_dagger_layer", "wicked_kris_venom_ripple", "wicked_kris_nest_burst",
            "dark_sword_blood_debt", "dark_sword_blood_moon", "tempered_quench", "tempered_billet",
            "obsidian_crack", "ossified_mark", "ossified_execution",
            "meowmere_shot", "meowmere_symphony",
            "forest_blessing",
            "lava_katana_brand",
            "lava_katana_reverb",
            "carving_thrust",
            "carving_thrust_strike",
            "carving_thrust_bonus",
            "dragon_breath_thrust",
            "dragon_breath_judgement",
            "dragontooth_shiv_stab",
            "dragontooth_shiv_breath",
            "startrail_rift",
            "galaxy_judgement",
            "galaxy_dagger_ready",
            "galaxy_dagger_starstab",
            "galaxy_dagger_starleap",
            "singularity_evolve",
            "singularity_release",
            "eternal_collapse",
            "forest_blessing_prepare", "elf_blade_leaf",
            "holy_smite", "holy_domain", "templar_vow", "templar_vow_strike", "templar_vow_end", "templar_judgement",
            "tide_mark", "tide_anchor", "fishcatch_ready", "fishcatch_thrust", "tide_reel",
            "infinity_dagger_ready",
            "infinity_dagger_singularity_stab",
            "infinity_dagger_singularity_backstab",
            "yeti_tooth_mark",
            "yeti_tooth_spine",
            "galaxy_hammer_starshock_sweep", "galaxy_hammer_starfall_quake",
            "infinity_gavel_singularity_press", "infinity_gavel_endless_pounding", "infinity_gavel_pound",
            "wood_club_whirl", "wood_mallet_leap",
            "lead_rod_press", "kudgel_sweep",
            "dragontooth_club_jaw", "dragontooth_club_breath", "rapier_riposte",
            "slammer_upheaval", "slammer_rampage", "dwarf_hammer_rebound", "dwarf_hammer_faultline",
            "femur_slam"
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

}
