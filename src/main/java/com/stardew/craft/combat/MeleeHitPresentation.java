package com.stardew.craft.combat;

import com.stardew.craft.combat.network.MeleeImpactPayload;
import com.stardew.craft.combat.network.MeleeImpactPayload.Kind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

final class MeleeHitPresentation {
    private MeleeHitPresentation() {}

    static void emit(ResolvedWeaponHit hit) {
        // Stance-enhanced normals retain their authored normal context; use the resolved stance ID for presentation.
        String visualSkill = "normal".equals(hit.authoredSkillContext().getSkillId())
                && ("dragontooth_shiv_breath".equals(hit.skillId()) || "insect_eye_stance".equals(hit.skillId()) || "iridium_needle_frenzy".equals(hit.skillId()) || "steel_spine_fury".equals(hit.skillId()) || "steel_spine_fury_weak".equals(hit.skillId())) ? hit.skillId() : hit.authoredSkillContext().getSkillId();
        Kind kind = select(hit.weaponIdentity().logicId(), visualSkill,
                hit.dealtPositiveDamage(), hit.authoredSkillContext().isGuaranteedCrit());
        if (kind == null || !(hit.attacker() instanceof ServerPlayer player)) return;
        var bounds = hit.target().getBoundingBox();
        Vec3 point = bounds.clip(player.getEyePosition(), bounds.getCenter()).orElse(bounds.getCenter());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new MeleeImpactPayload(
                player.getId(), hit.target().getId(), hit.gameTick(), kind, hit.weaponIdentity().logicId(),
                point.x, point.y, point.z, hit.displayCritical()));
    }

    static Kind select(String weaponId, String skillId, boolean positiveDamage) {
        return select(weaponId, skillId, positiveDamage, false);
    }

    static Kind select(String weaponId, String skillId, boolean positiveDamage, boolean authoredGuaranteedCrit) {
        if (!positiveDamage) return null;
        if ("normal".equals(skillId) && !"lava_katana".equals(weaponId)) {
            if ("elf_blade".equals(weaponId)) return Kind.ELF_STAB;
            if ("dragontooth_shiv".equals(weaponId)) return Kind.SHIV_HIT;
            var profile = WeaponMeleeProfile.get(weaponId);
            if (profile == null) return null;
            return switch (profile.type()) {
                case SWORD -> Kind.SWORD;
                case DAGGER -> Kind.DAGGER;
                case CLUB -> Kind.CLUB;
                default -> null;
            };
        }
        return switch (weaponId) {
            case "cutlass" -> "crescent_slash".equals(skillId)?Kind.CRESCENT_SLASH:null;
            case "steel_falchion" -> "steel_falchion_line_dot".equals(skillId)?Kind.FALCHION_DOT:"steel_falchion_trace".equals(skillId)?Kind.FALCHION_BURST:null;
            case "rusty_sword" -> "tetanus_strike".equals(skillId)?Kind.RUST_STRIKE:null;
            case "wooden_blade" -> "tree_blessing".equals(skillId)?Kind.WOOD_BLESS:null;
            case "steel_smallsword" -> "light_counter".equals(skillId)?Kind.LIGHT_COUNTER:null;
            case "iron_edge" -> "steel_spine_fury".equals(skillId)?Kind.SPINE_STRIKE:"steel_spine_fury_weak".equals(skillId)?Kind.SPINE_WEAK:null;
            case "pirate_sword" -> "desperate_plunder".equals(skillId) ? Kind.PIRATE_PLUNDER : null;
            case "silver_saber" -> "silver_foldback".equals(skillId) ? Kind.SILVER_HIT : null;
            case "iron_dirk" -> "iron_dirk_thrust".equals(skillId) ? Kind.IRON_THRUST : null;
            case "wind_spire" -> "wind_spire_thrust".equals(skillId) ? Kind.WIND_THRUST : null;
            case "bone_sword" -> "bone_fracture".equals(skillId) ? Kind.BONE_FRACTURE : null;
            case "claymore" -> switch (skillId) {
                case "claymore_foldback" -> Kind.CLAYMORE_OUT;
                case "claymore_foldback_return" -> Kind.CLAYMORE_RETURN;
                default -> null;
            };
            case "dwarf_sword" -> switch (skillId) {
                case "dwarf_rune_guard" -> Kind.DWARF_GUARD;
                case "dwarf_fortress" -> Kind.DWARF_SHOCK;
                default -> null;
            };
            case "dwarf_dagger" -> "dwarf_dagger_thrust".equals(skillId) ? Kind.DWARF_THRUST : null;
            case "iridium_needle" -> switch (skillId) {
                case "iridium_needle_thrust" -> authoredGuaranteedCrit ? Kind.NEEDLE_FINAL : Kind.NEEDLE_STRIKE;
                case "iridium_needle_frenzy" -> Kind.NEEDLE_FRENZY;
                default -> null;
            };
            case "burglars_shank" -> "burglar_shank".equals(skillId) ? Kind.BURGLAR_STRIKE : null;
            case "shadow_dagger" -> switch (skillId) {
                case "shadow_dagger_execute" -> Kind.SHADOW_EXECUTE;
                case "shadow_dagger_execute_bonus" -> Kind.SHADOW_FINISH;
                default -> null;
            };
            case "insect_head" -> switch (skillId) {
                case "insect_eye_stance" -> Kind.INSECT_EYE;
                case "insect_dash" -> Kind.INSECT_DASH;
                default -> null;
            };
            case "crystal_dagger" -> switch(skillId) {
                case "crystal_dagger_layer" -> Kind.CRYSTAL_LAYER;
                case "crystal_dagger_burst" -> Kind.CRYSTAL_BURST;
                default -> null;
            };
            case "wicked_kris" -> switch(skillId) {
                case "wicked_kris_venom_ripple" -> Kind.VENOM_RIPPLE;
                case "wicked_kris_nest_burst" -> Kind.VENOM_NEST;
                case "wicked_kris_poison_dot" -> Kind.VENOM_DOT;
                case "wicked_kris_poison_burst" -> Kind.VENOM_BURST;
                default -> null;
            };
            case "dark_sword" -> switch (skillId) {
                case "dark_sword_blood_debt" -> Kind.DARK_DEBT;
                case "dark_sword_blood_moon_burst" -> Kind.DARK_BURST;
                default -> null;
            };
            case "tempered_broadsword" -> switch (skillId) {
                case "tempered_quench" -> Kind.FORGE_QUENCH;
                case "tempered_quench_blast" -> Kind.FORGE_BLAST;
                case "tempered_billet" -> Kind.FORGE_BILLET;
                case "tempered_billet_fire_ring" -> Kind.FORGE_RING;
                default -> null;
            };
            case "obsidian_edge" -> switch (skillId) {
                case "obsidian_resonance" -> Kind.OBSIDIAN_RESONANCE;
                case "obsidian_crack" -> Kind.OBSIDIAN_CRACK;
                default -> null;
            };
            case "ossified_blade" -> switch (skillId) {
                case "ossified_mark_bonus" -> Kind.OSSIFIED_BONUS;
                case "ossified_execution_dot" -> Kind.OSSIFIED_PULSE;
                default -> null;
            };
            case "meowmere" -> ("meowmere_shot".equals(skillId) || "meowmere_symphony".equals(skillId)) ? Kind.MEOW_PROJECTILE : null;
            case "holy_blade" -> switch (skillId) {
                case "holy_smite" -> Kind.HOLY_SMITE;
                case "holy_domain" -> Kind.HOLY_PULSE;
                default -> null;
            };
            case "templars_blade" -> "templar_vow".equals(skillId) ? Kind.TEMPLAR_STRIKE : null;
            case "forest_sword" -> "forest_blessing".equals(skillId) ? Kind.FOREST_RELEASE : null;
            case "elf_blade" -> "elf_blade_leaf".equals(skillId) ? Kind.ELF_LEAF : null;
            case "neptunes_glaive" -> switch (skillId) {
                case "tide_anchor" -> Kind.TIDE_ANCHOR;
                case "tide_mark_bonus" -> Kind.TIDE_BONUS;
                default -> null;
            };
            case "broken_trident" -> switch (skillId) {
                case "fishcatch_thrust" -> Kind.TIDE_STAB;
                case "tide_reel" -> Kind.TIDE_REEL;
                default -> null;
            };
            case "dragontooth_shiv" -> switch (skillId) {
                case "dragontooth_shiv_stab" -> Kind.SHIV_STAB;
                case "dragontooth_shiv_breath" -> Kind.SHIV_BREATH_HIT;
                default -> null;
            };
            case "infinity_blade" -> switch (skillId) {
                case "singularity_evolve" -> Kind.INFINITY_EVOLVE;
                case "eternal_collapse" -> Kind.INFINITY_COLLAPSE;
                case "singularity_rift_path" -> Kind.INFINITY_RIFT;
                default -> null;
            };
            case "infinity_dagger" -> switch (skillId) {
                case "infinity_dagger_singularity_stab" -> Kind.INFINITY_STAB;
                case "infinity_dagger_singularity_backstab" -> Kind.INFINITY_BACK;
                default -> null;
            };
            case "galaxy_sword" -> switch (skillId) {
                case "startrail_rift" -> Kind.GALAXY_RIFT;
                case "galaxy_judgement" -> Kind.GALAXY_JUDGEMENT;
                default -> null;
            };
            case "galaxy_dagger" -> switch (skillId) {
                case "galaxy_dagger_starstab" -> Kind.GALAXY_STAB;
                case "galaxy_dagger_starleap" -> Kind.GALAXY_LEAP;
                default -> null;
            };
            case "yeti_tooth" -> switch (skillId) {
                case "yeti_tooth_mark" -> Kind.YETI_MARK;
                case "yeti_tooth_spine" -> Kind.YETI_SPINE;
                default -> null;
            };
            case "dragontooth_cutlass" -> switch (skillId) {
                case "dragon_breath_thrust" -> Kind.DRAGON_PIERCE;
                case "dragon_breath_judgement" -> Kind.DRAGON_JUDGEMENT;
                default -> null;
            };
            case "carving_knife" -> switch (skillId) {
                case "carving_thrust" -> Kind.THRUST;
                case "carving_thrust_bonus" -> Kind.BONUS_THRUST;
                default -> null;
            };
            case "femur" -> switch (skillId) {
                case "femur_slam" -> Kind.SLAM;
                default -> null;
            };
            default -> null;
        };
    }
}
