package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.combat.WeaponMeleeProfile;
import com.stardew.craft.combat.WeaponMeleeProfile.Material;
import com.stardew.craft.combat.WeaponType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.MeleeImpactPayload;
import com.stardew.craft.combat.network.MeleeImpactPayload.Kind;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/** Bounded presentation for the swift dagger and weighted club, using final hit events. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class MeleeWeaponVisuals {
    public static final String CUTLASS_SWING="cutlass_swing", CRESCENT_SLASH="crescent_slash", FALCHION_SWING="steel_falchion_swing", FALCHION_LINE="steel_falchion_line", FALCHION_TRACE="steel_falchion_trace";
    public static final String RUST_SWING="rusty_sword_swing", RUST_STRIKE="tetanus_strike", WOOD_SWING="wooden_blade_swing", WOOD_BLESS="tree_blessing";
    public static final String LIGHT_SWING="steel_smallsword_swing", LIGHT_GUARD="light_counter", LIGHT_COUNTER="light_counter_counter";
    public static final String SPINE_SWING="iron_edge_swing", SPINE_ENTER="steel_spine_fury_enter", SPINE_STRIKE="steel_spine_fury", SPINE_WEAK="steel_spine_fury_weak";
    public static final String PIRATE_SWING = "pirate_sword_swing";
    public static final String PIRATE_PLUNDER = "desperate_plunder";
    public static final String SILVER_SWING = "silver_saber_swing";
    public static final String SILVER_OUT = "silver_foldback";
    public static final String SILVER_RETURN = "silver_foldback_return";
    public static final String SILVER_STAY = "silver_foldback_stay";
    public static final String SILVER_EMPTY = "silver_foldback_empty";
    public static final String IRON_SWING = "iron_dirk_swing";
    public static final String IRON_THRUST = "iron_dirk_thrust";
    public static final String WIND_SWING = "wind_spire_swing";
    public static final String WIND_THRUST = "wind_spire_thrust";
    public static final String BONE_SWORD_SWING = "bone_sword_swing";
    public static final String BONE_FRACTURE = "bone_fracture";
    public static final String CLAYMORE_SWING = "claymore_swing";
    public static final String CLAYMORE_OUT = "claymore_foldback";
    public static final String CLAYMORE_RETURN = "claymore_foldback_return";
    public static final String DWARF_SWORD_SWING = "dwarf_sword_swing";
    public static final String DWARF_DAGGER_SWING = "dwarf_dagger_swing";
    public static final String DWARF_GUARD = "dwarf_rune_guard";
    public static final String DWARF_FORTRESS = "dwarf_fortress";
    public static final String DWARF_THRUST = "dwarf_dagger_thrust";
    public static final String DWARF_RUSH = "dwarf_dagger_rush";
    public static final String NEEDLE_SWING = "iridium_needle_swing";
    public static final String NEEDLE_READY = "iridium_needle_thrust";
    public static final String NEEDLE_STRIKE = "iridium_needle_strike";
    public static final String NEEDLE_FINAL = "iridium_needle_final";
    public static final String NEEDLE_FRENZY = "iridium_needle_frenzy";
    public static final String BURGLAR_SWING = "burglar_swing";
    public static final String BURGLAR_STRIKE = "burglar_shank";
    public static final String SHADOW_SWING = "shadow_dagger_swing";
    public static final String SHADOW_EXECUTE = "shadow_dagger_execute";
    public static final String INSECT_SWING = "insect_head_swing";
    public static final String INSECT_STANCE = "insect_eye_stance";
    public static final String INSECT_DASH = "insect_dash";
    public static final String CRYSTAL_SWING = "crystal_swing";
    public static final String CRYSTAL_LAYER = "crystal_dagger_layer";
    public static final String VENOM_SWING = "venom_swing";
    public static final String VENOM_RIPPLE = "wicked_kris_venom_ripple";
    public static final String VENOM_NEST = "wicked_kris_nest_burst";
    public static final String DARK_SWING = "dark_sword_swing";
    public static final String DARK_DEBT = "dark_sword_blood_debt";
    public static final String DARK_MOON = "dark_sword_blood_moon";
    public static final String FORGE_SWING = "tempered_swing";
    public static final String FORGE_QUENCH = "tempered_quench";
    public static final String FORGE_BILLET = "tempered_billet";
    public static final String OBSIDIAN_SWING = "obsidian_swing";
    public static final String OBSIDIAN_CRACK = "obsidian_crack";
    public static final String OSSIFIED_SWING = "ossified_swing";
    public static final String OSSIFIED_MARK = "ossified_mark";
    public static final String OSSIFIED_EXECUTION = "ossified_execution";
    public static final String MEOW_SWING = "meowmere_swing";
    public static final String MEOW_SHOT = "meowmere_shot";
    public static final String MEOW_FAN = "meowmere_symphony";
    public static final String HOLY_SWING = "holy_blade_swing";
    public static final String TEMPLAR_SWING = "templar_blade_swing";
    public static final String HOLY_SMITE = "holy_smite";
    public static final String HOLY_DOMAIN = "holy_domain";
    public static final String TEMPLAR_VOW = "templar_vow";
    public static final String TEMPLAR_STRIKE = "templar_vow_strike";
    public static final String TEMPLAR_END = "templar_vow_end";
    public static final String TEMPLAR_JUDGEMENT = "templar_judgement";
    public static final String FOREST_READY = "forest_blessing_prepare";
    public static final String FOREST_RELEASE = "forest_blessing";
    public static final String ELF_CAST = "elf_blade_leaf";
    public static final String ELF_SWING = "elf_blade_swing";
    public static final String TIDE_MARK = "tide_mark";
    public static final String TIDE_ANCHOR = "tide_anchor";
    public static final String TIDE_READY = "fishcatch_ready";
    public static final String TIDE_STAB = "fishcatch_thrust";
    public static final String TIDE_REEL = "tide_reel";
    public static final String INFINITY_EVOLVE = "singularity_evolve";
    public static final String INFINITY_RELEASE = "singularity_release";
    public static final String INFINITY_COLLAPSE = "eternal_collapse";
    public static final String INFINITY_READY = "infinity_dagger_ready";
    public static final String INFINITY_STAB = "infinity_dagger_singularity_stab";
    public static final String INFINITY_BACK = "infinity_dagger_singularity_backstab";
    public static final String GALAXY_RIFT = "startrail_rift";
    public static final String GALAXY_JUDGEMENT = "galaxy_judgement";
    public static final String GALAXY_READY = "galaxy_dagger_ready";
    public static final String GALAXY_STAB = "galaxy_dagger_starstab";
    public static final String GALAXY_LEAP = "galaxy_dagger_starleap";
    public static final String SHIV_SWING = "shiv_swing";
    public static final String SHIV_EMPOWERED = "shiv_empowered";
    public static final String SHIV_STAB = "dragontooth_shiv_stab";
    public static final String SHIV_BREATH = "dragontooth_shiv_breath";
    public static final String YETI_MARK = "yeti_tooth_mark";
    public static final String YETI_SPINE = "yeti_tooth_spine";
    public static final String DRAGON_PIERCE = "dragon_breath_thrust";
    public static final String DRAGON_JUDGEMENT = "dragon_breath_judgement";
    public static final String SWORD_SWING = "melee_sword_swing";
    public static final String CARVING_SWING = "carving_knife_swing";
    public static final String CARVING_READY = "carving_thrust";
    public static final String CARVING_STRIKE = "carving_thrust_strike";
    public static final String CARVING_BONUS = "carving_thrust_bonus";
    public static final String FEMUR_SWING = "femur_swing";
    public static final String FEMUR_CHARGE = "femur_charge";
    public static final String FEMUR_SLAM = "femur_slam";
    private static final double RANGE_SQR = 48 * 48;
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<GroundPulse> GROUND = new ArrayList<>();
    private static final Map<Integer, Action> RELEASES = new HashMap<>();
    private static final Map<Integer, Long> HIT_SOUNDS = new HashMap<>();
    private static final Map<Integer, Long> CHARGE_SPARKS = new HashMap<>();
    private static ClientLevel activeLevel;
    private static Freeze freeze;

    private MeleeWeaponVisuals() {}

    public record Action(String skillId, long startTick, float progress) {}
    private record Freeze(String skillId, long startTick, float progress, double from, double until) {}

    @Nullable
    public static Action action(LivingEntity entity, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || entity.level() != mc.level || !entity.isAlive()
                || !(entity.getMainHandItem().getItem() instanceof IStardewWeapon weapon)) return null;
        String id = weapon.getWeaponId();
        var profile = WeaponMeleeProfile.get(id);
        if (profile == null || "lava_katana".equals(id)) return null;
        float progress = WeaponSkillAnimationClient.getWorldActionProgress(entity.getId(), partialTick);
        var skill = WeaponSkillAnimationClient.getWorldAction(entity.getId());
        boolean yieldToSwing = skill != null && (FALCHION_TRACE.equals(skill.skillId()) || SPINE_ENTER.equals(skill.skillId()) || DWARF_FORTRESS.equals(skill.skillId()) || NEEDLE_FRENZY.equals(skill.skillId()) || INSECT_STANCE.equals(skill.skillId()) || DARK_MOON.equals(skill.skillId()) || FORGE_BILLET.equals(skill.skillId()) || VENOM_RIPPLE.equals(skill.skillId()))
                && entity.getAttackAnim(partialTick) > 0;
        Action result;
        // Replicated item-use state also ends the charging pose immediately on cancellation.
        if ("femur".equals(id) && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            float use = Math.max(0, entity.getTicksUsingItem() - 1 + partialTick);
            return new Action(FEMUR_CHARGE, mc.level.getGameTime() - entity.getTicksUsingItem(),
                    Mth.clamp(use / 20, 0, 1));
        } else if (progress >= 0 && skill != null && !yieldToSwing) {
            if (!id.equals(skill.weaponId()) || !supportsSkill(id, skill.skillId())) return null;
            result = new Action(skill.skillId(), skill.startGameTick(), progress);
        } else {
            if (!yieldToSwing && entity == mc.player && WeaponSkillAnimationClient.getProgress(partialTick) >= 0) return null;
            float swing = entity.getAttackAnim(partialTick);
            if (swing <= 0 || entity.swingingArm != InteractionHand.MAIN_HAND) return null;
            if (!WeaponMeleeProfile.isSample(id)) swing = normalProgress(swing, entity.getCurrentSwingDuration());
            result = new Action("dragontooth_shiv".equals(id)
                    ? DragontoothShivBreathClientState.isActive(entity) ? SHIV_EMPOWERED : SHIV_SWING
                    : "dragontooth_club".equals(id) ? "dragontooth_club_swing" : "rapier".equals(id) ? "rapier_swing"
                    : "the_slammer".equals(id) ? "the_slammer_swing" : "dwarf_hammer".equals(id) ? "dwarf_hammer_swing"
                    : "lead_rod".equals(id) ? "lead_rod_swing" : "kudgel".equals(id) ? "kudgel_swing"
                    : "wood_club".equals(id) ? "wood_club_swing" : "wood_mallet".equals(id) ? "wood_mallet_swing"
                    : "galaxy_hammer".equals(id) ? "galaxy_hammer_swing" : "infinity_gavel".equals(id) ? "infinity_gavel_swing"
                    : "cutlass".equals(id) ? CUTLASS_SWING : "steel_falchion".equals(id) ? FALCHION_SWING
                    : "rusty_sword".equals(id) ? RUST_SWING : "wooden_blade".equals(id) ? WOOD_SWING
                    : "steel_smallsword".equals(id) ? LIGHT_SWING : "iron_edge".equals(id) ? SPINE_SWING
                    : "pirate_sword".equals(id) ? PIRATE_SWING : "silver_saber".equals(id) ? SILVER_SWING
                    : "iron_dirk".equals(id) ? IRON_SWING : "wind_spire".equals(id) ? WIND_SWING
                    : "bone_sword".equals(id) ? BONE_SWORD_SWING : "claymore".equals(id) ? CLAYMORE_SWING
                    : "dwarf_sword".equals(id) ? DWARF_SWORD_SWING : "dwarf_dagger".equals(id) ? DWARF_DAGGER_SWING
                    : "iridium_needle".equals(id) ? NEEDLE_SWING : "burglars_shank".equals(id) ? BURGLAR_SWING
                    : "shadow_dagger".equals(id) ? SHADOW_SWING : "insect_head".equals(id) ? INSECT_SWING
                    : "crystal_dagger".equals(id) ? CRYSTAL_SWING : "wicked_kris".equals(id) ? VENOM_SWING
                    : "dark_sword".equals(id) ? DARK_SWING : "tempered_broadsword".equals(id) ? FORGE_SWING
                    : "obsidian_edge".equals(id) ? OBSIDIAN_SWING : "ossified_blade".equals(id) ? OSSIFIED_SWING
                    : "meowmere".equals(id) ? MEOW_SWING : "holy_blade".equals(id) ? HOLY_SWING : "templars_blade".equals(id) ? TEMPLAR_SWING
                    : "elf_blade".equals(id) ? ELF_SWING : normalAction(profile.type()),
                    mc.level.getGameTime() - Math.max(0, entity.swingTime), swing);
        }
        double now = mc.level.getGameTime() + partialTick;
        if (entity == mc.player && activeLevel == mc.level && freeze != null
                && result.startTick == freeze.startTick && result.skillId.equals(freeze.skillId)
                && now >= freeze.from && now < freeze.until) {
            return new Action(result.skillId, result.startTick, freeze.progress);
        }
        return result;
    }

    public static String normalAction(WeaponType type) {
        return switch (type) {
            case SWORD -> SWORD_SWING;
            case DAGGER -> CARVING_SWING;
            case CLUB -> FEMUR_SWING;
            default -> "";
        };
    }

    /** Speed changes recovery; immediate server hits still have a fast visual contact frame. */
    static float normalProgress(float swing, int duration) {
        float ticks = Math.max(1, duration);
        float strikeTicks = Math.min(1, ticks * 0.4f);
        float elapsed = Mth.clamp(swing, 0, 1) * ticks;
        return elapsed <= strikeTicks ? elapsed / strikeTicks * 0.185f
                : 0.185f + (elapsed - strikeTicks) / (ticks - strikeTicks) * 0.815f;
    }

    private static boolean supportsSkill(String weapon, String skill) {
        if(com.stardew.craft.combat.skill.handler.DragonRapierRules.isWeapon(weapon)) return com.stardew.craft.combat.skill.handler.DragonRapierRules.supports(weapon,skill);
        if(com.stardew.craft.combat.skill.handler.SlammerDwarfRules.isWeapon(weapon)) return com.stardew.craft.combat.skill.handler.SlammerDwarfRules.supports(weapon,skill);
        if(com.stardew.craft.combat.skill.handler.IronClubRules.isWeapon(weapon)) return com.stardew.craft.combat.skill.handler.IronClubRules.supports(weapon,skill);
        if(com.stardew.craft.combat.skill.handler.WoodWeaponRules.isWeapon(weapon)) return com.stardew.craft.combat.skill.handler.WoodWeaponRules.supports(weapon,skill);
        if (com.stardew.craft.combat.skill.handler.HeavyHammerRules.isWeapon(weapon))
            return com.stardew.craft.combat.skill.handler.HeavyHammerRules.supports(weapon, skill);
        if ("cutlass".equals(weapon)) return CRESCENT_SLASH.equals(skill);
        if ("steel_falchion".equals(weapon)) return FALCHION_LINE.equals(skill)||FALCHION_TRACE.equals(skill);
        if ("rusty_sword".equals(weapon)) return RUST_STRIKE.equals(skill);
        if ("wooden_blade".equals(weapon)) return WOOD_BLESS.equals(skill);
        if ("steel_smallsword".equals(weapon)) return LIGHT_GUARD.equals(skill)||LIGHT_COUNTER.equals(skill);
        if ("iron_edge".equals(weapon)) return SPINE_ENTER.equals(skill)||SPINE_STRIKE.equals(skill)||SPINE_WEAK.equals(skill);
        if ("pirate_sword".equals(weapon)) return PIRATE_PLUNDER.equals(skill);
        if ("silver_saber".equals(weapon)) return SILVER_OUT.equals(skill) || SILVER_RETURN.equals(skill) || SILVER_STAY.equals(skill) || SILVER_EMPTY.equals(skill);
        if ("iron_dirk".equals(weapon)) return IRON_THRUST.equals(skill);
        if ("wind_spire".equals(weapon)) return WIND_THRUST.equals(skill);
        if ("bone_sword".equals(weapon)) return BONE_FRACTURE.equals(skill);
        if ("claymore".equals(weapon)) return CLAYMORE_OUT.equals(skill) || CLAYMORE_RETURN.equals(skill);
        if ("dwarf_sword".equals(weapon)) return DWARF_GUARD.equals(skill) || DWARF_FORTRESS.equals(skill);
        if ("dwarf_dagger".equals(weapon)) return DWARF_THRUST.equals(skill) || DWARF_RUSH.equals(skill);
        if ("iridium_needle".equals(weapon)) return NEEDLE_READY.equals(skill) || NEEDLE_STRIKE.equals(skill) || NEEDLE_FINAL.equals(skill) || NEEDLE_FRENZY.equals(skill);
        if ("burglars_shank".equals(weapon)) return BURGLAR_STRIKE.equals(skill);
        if ("shadow_dagger".equals(weapon)) return SHADOW_EXECUTE.equals(skill);
        if ("insect_head".equals(weapon)) return INSECT_STANCE.equals(skill) || INSECT_DASH.equals(skill);
        if ("crystal_dagger".equals(weapon)) return CRYSTAL_LAYER.equals(skill);
        if ("wicked_kris".equals(weapon)) return VENOM_RIPPLE.equals(skill) || VENOM_NEST.equals(skill);
        if ("dark_sword".equals(weapon)) return DARK_DEBT.equals(skill) || DARK_MOON.equals(skill);
        if ("tempered_broadsword".equals(weapon)) return FORGE_QUENCH.equals(skill) || FORGE_BILLET.equals(skill);
        if ("obsidian_edge".equals(weapon)) return OBSIDIAN_CRACK.equals(skill);
        if ("ossified_blade".equals(weapon)) return OSSIFIED_MARK.equals(skill) || OSSIFIED_EXECUTION.equals(skill);
        if ("meowmere".equals(weapon)) return MEOW_SHOT.equals(skill) || MEOW_FAN.equals(skill);
        if ("holy_blade".equals(weapon)) return HOLY_SMITE.equals(skill) || HOLY_DOMAIN.equals(skill);
        if ("templars_blade".equals(weapon)) return TEMPLAR_VOW.equals(skill) || TEMPLAR_STRIKE.equals(skill)
                || TEMPLAR_END.equals(skill) || TEMPLAR_JUDGEMENT.equals(skill);
        if ("forest_sword".equals(weapon)) return FOREST_READY.equals(skill) || FOREST_RELEASE.equals(skill);
        if ("elf_blade".equals(weapon)) return ELF_CAST.equals(skill);
        if ("neptunes_glaive".equals(weapon)) return TIDE_MARK.equals(skill) || TIDE_ANCHOR.equals(skill);
        if ("broken_trident".equals(weapon)) return TIDE_READY.equals(skill) || TIDE_STAB.equals(skill) || TIDE_REEL.equals(skill);
        if ("dragontooth_shiv".equals(weapon)) return SHIV_STAB.equals(skill) || SHIV_BREATH.equals(skill);
        if ("galaxy_sword".equals(weapon)) return GALAXY_RIFT.equals(skill) || GALAXY_JUDGEMENT.equals(skill);
        if ("galaxy_dagger".equals(weapon)) return GALAXY_READY.equals(skill) || GALAXY_STAB.equals(skill) || GALAXY_LEAP.equals(skill);
        if ("infinity_blade".equals(weapon)) return INFINITY_EVOLVE.equals(skill) || INFINITY_RELEASE.equals(skill) || INFINITY_COLLAPSE.equals(skill);
        if ("infinity_dagger".equals(weapon)) return INFINITY_READY.equals(skill) || INFINITY_STAB.equals(skill) || INFINITY_BACK.equals(skill);
        if ("yeti_tooth".equals(weapon)) return YETI_MARK.equals(skill) || YETI_SPINE.equals(skill);
        if ("dragontooth_cutlass".equals(weapon)) return DRAGON_PIERCE.equals(skill) || DRAGON_JUDGEMENT.equals(skill);
        return "femur".equals(weapon) ? FEMUR_SLAM.equals(skill)
                : "carving_knife".equals(weapon) && (CARVING_READY.equals(skill) || CARVING_STRIKE.equals(skill) || CARVING_BONUS.equals(skill));
    }

    public static boolean startSkill(WeaponSkillAnimPayload payload) {
        if(com.stardew.craft.combat.skill.handler.DragonRapierRules.supports(payload.weaponId(),payload.skillId())) {DragonRapierVisuals.start(payload);return true;}
        if(com.stardew.craft.combat.skill.handler.SlammerDwarfRules.supports(payload.weaponId(),payload.skillId())) {SlammerDwarfVisuals.start(payload);return true;}
        if(com.stardew.craft.combat.skill.handler.IronClubRules.supports(payload.weaponId(),payload.skillId())) {IronClubVisuals.start(payload);return true;}
        if(com.stardew.craft.combat.skill.handler.WoodWeaponRules.supports(payload.weaponId(),payload.skillId())) {WoodWeaponVisuals.start(payload);return true;}
        if (com.stardew.craft.combat.skill.handler.HeavyHammerRules.supports(payload.weaponId(),payload.skillId())) { HeavyHammerVisuals.start(payload); return true; }
        if("steel_falchion".equals(payload.weaponId())&&supportsSkill(payload.weaponId(),payload.skillId()))return true;
        if(("rusty_sword".equals(payload.weaponId())||"wooden_blade".equals(payload.weaponId()))&&supportsSkill(payload.weaponId(),payload.skillId())){RustWoodVisuals.start(payload);return true;}
        if (("steel_smallsword".equals(payload.weaponId())||"iron_edge".equals(payload.weaponId()))&&supportsSkill(payload.weaponId(),payload.skillId())) {GuardSpineVisuals.start(payload);return true;}
        if (("pirate_sword".equals(payload.weaponId()) || "silver_saber".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { PirateSilverVisuals.start(payload); return true; }
        if (("iron_dirk".equals(payload.weaponId()) || "wind_spire".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { IronWindVisuals.start(payload); return true; }
        if (("bone_sword".equals(payload.weaponId()) || "claymore".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { BoneClaymoreVisuals.start(payload); return true; }
        if (("dwarf_sword".equals(payload.weaponId()) || "dwarf_dagger".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { DwarfWeaponVisuals.start(payload); return true; }
        if (("iridium_needle".equals(payload.weaponId()) || "burglars_shank".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { NeedleBurglarVisuals.start(payload); return true; }
        if (("shadow_dagger".equals(payload.weaponId()) || "insect_head".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) { ShadowInsectVisuals.start(payload); return true; }
        if (("crystal_dagger".equals(payload.weaponId()) || "wicked_kris".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(),payload.skillId())) {CrystalVenomVisuals.start(payload);return true;}
        if (("dark_sword".equals(payload.weaponId()) || "tempered_broadsword".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            BloodForgeVisuals.start(payload); return true;
        }
        if ("obsidian_edge".equals(payload.weaponId()) && "obsidian_resonance".equals(payload.skillId())) return true;
        if (("obsidian_edge".equals(payload.weaponId()) || "ossified_blade".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) return true;
        if ("meowmere".equals(payload.weaponId()) && supportsSkill(payload.weaponId(), payload.skillId())) {
            var mc = Minecraft.getInstance();
            Vec3 origin = new Vec3(payload.originX(), payload.originY() + 1, payload.originZ());
            if (mc.level != null && mc.player != null && mc.player.distanceToSqr(origin) <= RANGE_SQR)
                mc.level.playLocalSound(origin.x, origin.y, origin.z, SoundEvents.NOTE_BLOCK_CHIME.value(),
                        SoundSource.PLAYERS, 0.45f, MEOW_FAN.equals(payload.skillId()) ? 1.15f : 1.6f, false);
            return true;
        }
        if (("holy_blade".equals(payload.weaponId()) || "templars_blade".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            SacredWeaponVisuals.start(payload);
            return true;
        }
        if (("forest_sword".equals(payload.weaponId()) || "elf_blade".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            GroveWeaponVisuals.start(payload);
            return true;
        }
        if (("neptunes_glaive".equals(payload.weaponId()) || "broken_trident".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            TideWeaponVisuals.start(payload);
            return true;
        }
        if (("infinity_blade".equals(payload.weaponId()) || "infinity_dagger".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            InfinityWeaponVisuals.start(payload);
            return true;
        }
        if (("galaxy_sword".equals(payload.weaponId()) || "galaxy_dagger".equals(payload.weaponId()))
                && supportsSkill(payload.weaponId(), payload.skillId())) {
            GalaxyWeaponVisuals.start(payload);
            return true;
        }
        if ("dragontooth_shiv".equals(payload.weaponId()) && supportsSkill(payload.weaponId(), payload.skillId())) {
            DragontoothShivVisuals.start(payload);
            return true;
        }
        if ("yeti_tooth".equals(payload.weaponId()) && supportsSkill(payload.weaponId(), payload.skillId())) {
            YetiToothVisuals.start(payload);
            return true;
        }
        if ("dragontooth_cutlass".equals(payload.weaponId()) && supportsSkill(payload.weaponId(), payload.skillId())) {
            DragonCutlassVisuals.start(payload);
            return true;
        }
        if (!("femur".equals(payload.weaponId()) || "carving_knife".equals(payload.weaponId()))
                || !supportsSkill(payload.weaponId(), payload.skillId())) return false;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || CARVING_READY.equals(payload.skillId())) return true;
        playRelease(new Vec3(payload.originX(), payload.originY() + 1, payload.originZ()), payload.skillId());
        return true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        IMPACTS.removeIf(impact -> now - impact.start > 8);
        GROUND.removeIf(pulse -> now - pulse.start > 11);
        RELEASES.entrySet().removeIf(entry -> mc.level.getEntity(entry.getKey()) == null
                || now - entry.getValue().startTick > 40);
        HIT_SOUNDS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        CHARGE_SPARKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (var player : mc.level.players()) {
            if (player.distanceToSqr(mc.player) > RANGE_SQR) continue;
            Action current = action(player, 1);
            if (current == null || !(SWORD_SWING.equals(current.skillId) || CARVING_SWING.equals(current.skillId)
                    || PIRATE_SWING.equals(current.skillId) || SILVER_SWING.equals(current.skillId) || IRON_SWING.equals(current.skillId) || WIND_SWING.equals(current.skillId) || BONE_SWORD_SWING.equals(current.skillId) || CLAYMORE_SWING.equals(current.skillId) || DWARF_SWORD_SWING.equals(current.skillId) || DWARF_DAGGER_SWING.equals(current.skillId) || NEEDLE_SWING.equals(current.skillId) || BURGLAR_SWING.equals(current.skillId) || SHADOW_SWING.equals(current.skillId) || INSECT_SWING.equals(current.skillId) || CRYSTAL_SWING.equals(current.skillId) || VENOM_SWING.equals(current.skillId) || DARK_SWING.equals(current.skillId) || FORGE_SWING.equals(current.skillId) || OBSIDIAN_SWING.equals(current.skillId) || OSSIFIED_SWING.equals(current.skillId) || MEOW_SWING.equals(current.skillId) || HOLY_SWING.equals(current.skillId) || TEMPLAR_SWING.equals(current.skillId)
                    || "dragontooth_club_swing".equals(current.skillId) || "rapier_swing".equals(current.skillId) || "the_slammer_swing".equals(current.skillId) || "dwarf_hammer_swing".equals(current.skillId) || "lead_rod_swing".equals(current.skillId) || "kudgel_swing".equals(current.skillId) || "wood_club_swing".equals(current.skillId) || "wood_mallet_swing".equals(current.skillId) || "galaxy_hammer_swing".equals(current.skillId) || "infinity_gavel_swing".equals(current.skillId)
                    || ELF_SWING.equals(current.skillId) || FEMUR_SWING.equals(current.skillId) || FEMUR_CHARGE.equals(current.skillId))) continue;
            Action prior = RELEASES.put(player.getId(), current);
            if (prior == null || !prior.skillId.equals(current.skillId) || prior.startTick != current.startTick) {
                playRelease(player.position().add(0, 1, 0), current.skillId);
            }
        }
    }

    private static void playRelease(Vec3 point, String skill) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(point) > RANGE_SQR) return;
        boolean bone = "dragontooth_club_swing".equals(skill) || "the_slammer_swing".equals(skill) || "dwarf_hammer_swing".equals(skill) || "lead_rod_swing".equals(skill) || "kudgel_swing".equals(skill) || "wood_club_swing".equals(skill) || "wood_mallet_swing".equals(skill) || skill.startsWith("femur_") || "galaxy_hammer_swing".equals(skill) || "infinity_gavel_swing".equals(skill);
        boolean charge = FEMUR_CHARGE.equals(skill);
        boolean sword = "rapier_swing".equals(skill) || CUTLASS_SWING.equals(skill) || FALCHION_SWING.equals(skill) || RUST_SWING.equals(skill) || WOOD_SWING.equals(skill) || LIGHT_SWING.equals(skill) || SPINE_SWING.equals(skill) || PIRATE_SWING.equals(skill) || SILVER_SWING.equals(skill) || BONE_SWORD_SWING.equals(skill) || CLAYMORE_SWING.equals(skill) || DWARF_SWORD_SWING.equals(skill) || INSECT_SWING.equals(skill) || VENOM_SWING.equals(skill) || DARK_SWING.equals(skill) || FORGE_SWING.equals(skill) || OBSIDIAN_SWING.equals(skill) || OSSIFIED_SWING.equals(skill) || MEOW_SWING.equals(skill) || SWORD_SWING.equals(skill) || HOLY_SWING.equals(skill) || TEMPLAR_SWING.equals(skill);
        mc.level.playLocalSound(point.x, point.y, point.z,
                charge ? SoundEvents.BONE_BLOCK_STEP : (bone || sword) ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, charge ? 0.42f : bone ? 0.48f : sword ? 0.32f : 0.22f,
                charge ? 0.55f : bone ? 0.58f : CLAYMORE_SWING.equals(skill) ? .78f : sword ? 1.05f : CARVING_BONUS.equals(skill) ? 1.45f : 1.85f, false);
    }

    public static void chargeTip(int entityId, Action action, Vec3 tip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || !FEMUR_CHARGE.equals(action.skillId)
                || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()
                || tip.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 24 * 24) return;
        ensureLevel(mc.level);
        long now = mc.level.getGameTime();
        Long prior = CHARGE_SPARKS.get(entityId);
        if (prior != null && now - prior < (action.progress > 0.7f ? 1 : 3)) return;
        CHARGE_SPARKS.put(entityId, now);
        mc.level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, 0.012, 0);
    }

    public static void impact(MeleeImpactPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ensureLevel(mc.level);
        Vec3 point = new Vec3(payload.x(), payload.y(), payload.z());
        if (mc.player.distanceToSqr(point) > RANGE_SQR) return;
        if (payload.kind() == Kind.GROUND) {
            if (Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) addGroundPulse(point);
            return;
        }
        if(payload.kind()==Kind.CRESCENT_SLASH||payload.kind()==Kind.FALCHION_DOT||payload.kind()==Kind.FALCHION_BURST){
            var style=payload.kind()==Kind.CRESCENT_SLASH?WeaponTargetImpactClient.Style.CRESCENT_SLASH:payload.kind()==Kind.FALCHION_DOT?WeaponTargetImpactClient.Style.FALCHION_DOT:WeaponTargetImpactClient.Style.FALCHION_BURST;
            WeaponTargetImpactClient.show(payload.casterId(),payload.targetId(),payload.gameTick(),point,style);
            if(payload.kind()==Kind.CRESCENT_SLASH&&!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(),payload.gameTick()))&&payload.casterId()==mc.player.getId())freezeHit(payload,false,true);
            return;
        }
        if(payload.kind()==Kind.RUST_STRIKE||payload.kind()==Kind.WOOD_BLESS){
            WeaponTargetImpactClient.show(payload.casterId(),payload.targetId(),payload.gameTick(),point,payload.kind()==Kind.RUST_STRIKE?WeaponTargetImpactClient.Style.RUST_STRIKE:WeaponTargetImpactClient.Style.WOOD_BLESS);
            if(!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(),payload.gameTick()))&&payload.casterId()==mc.player.getId())freezeHit(payload,false,true);
            return;
        }
        if (payload.kind()==Kind.LIGHT_COUNTER||payload.kind()==Kind.SPINE_STRIKE||payload.kind()==Kind.SPINE_WEAK) {
            var style=payload.kind()==Kind.LIGHT_COUNTER?WeaponTargetImpactClient.Style.LIGHT_COUNTER:payload.kind()==Kind.SPINE_STRIKE?WeaponTargetImpactClient.Style.SPINE_STRIKE:WeaponTargetImpactClient.Style.SPINE_WEAK;
            WeaponTargetImpactClient.show(payload.casterId(),payload.targetId(),payload.gameTick(),point,style);
            if(!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(),payload.gameTick()))&&payload.casterId()==mc.player.getId())freezeHit(payload,false,true);
            return;
        }
        if (payload.kind() == Kind.PIRATE_PLUNDER || payload.kind() == Kind.SILVER_HIT) {
            var action = WeaponSkillAnimationClient.getWorldAction(payload.casterId());
            String phase = action != null && Math.abs(action.startGameTick() - payload.gameTick()) <= 2 ? action.skillId() : "";
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.PIRATE_PLUNDER ? WeaponTargetImpactClient.Style.PIRATE_PLUNDER : silverImpactStyle(phase));
            if (!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.IRON_THRUST || payload.kind() == Kind.WIND_THRUST) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.IRON_THRUST ? WeaponTargetImpactClient.Style.IRON_THRUST : WeaponTargetImpactClient.Style.WIND_THRUST);
            if (!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.BONE_FRACTURE || payload.kind() == Kind.CLAYMORE_OUT || payload.kind() == Kind.CLAYMORE_RETURN) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case BONE_FRACTURE -> WeaponTargetImpactClient.Style.BONE_FRACTURE;
                        case CLAYMORE_OUT -> WeaponTargetImpactClient.Style.CLAYMORE_OUT;
                        default -> WeaponTargetImpactClient.Style.CLAYMORE_RETURN;
                    });
            if (!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.DWARF_GUARD || payload.kind() == Kind.DWARF_SHOCK || payload.kind() == Kind.DWARF_THRUST) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case DWARF_GUARD -> WeaponTargetImpactClient.Style.DWARF_GUARD;
                        case DWARF_THRUST -> WeaponTargetImpactClient.Style.DWARF_THRUST;
                        default -> WeaponTargetImpactClient.Style.DWARF_SHOCK;
                    });
            if (payload.kind() == Kind.DWARF_GUARD
                    && !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.NEEDLE_STRIKE || payload.kind() == Kind.NEEDLE_FINAL
                || payload.kind() == Kind.NEEDLE_FRENZY || payload.kind() == Kind.BURGLAR_STRIKE) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case NEEDLE_STRIKE -> WeaponTargetImpactClient.Style.NEEDLE_STRIKE;
                        case NEEDLE_FINAL -> WeaponTargetImpactClient.Style.NEEDLE_FINAL;
                        case NEEDLE_FRENZY -> WeaponTargetImpactClient.Style.NEEDLE_FRENZY;
                        default -> WeaponTargetImpactClient.Style.BURGLAR_STRIKE;
                    });
            if (!Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, payload.kind() != Kind.NEEDLE_STRIKE);
            return;
        }
        if (payload.kind() == Kind.SHADOW_EXECUTE || payload.kind() == Kind.SHADOW_FINISH
                || payload.kind() == Kind.INSECT_EYE || payload.kind() == Kind.INSECT_DASH) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case SHADOW_EXECUTE -> WeaponTargetImpactClient.Style.SHADOW_EXECUTE;
                        case SHADOW_FINISH -> WeaponTargetImpactClient.Style.SHADOW_FINISH;
                        case INSECT_EYE -> WeaponTargetImpactClient.Style.INSECT_EYE;
                        default -> WeaponTargetImpactClient.Style.INSECT_DASH;
                    });
            if (payload.kind() != Kind.SHADOW_FINISH
                    && !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()))
                    && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.CRYSTAL_LAYER || payload.kind() == Kind.CRYSTAL_BURST || payload.kind() == Kind.VENOM_RIPPLE
                || payload.kind() == Kind.VENOM_NEST || payload.kind() == Kind.VENOM_DOT || payload.kind() == Kind.VENOM_BURST) {
            WeaponTargetImpactClient.show(payload.casterId(),payload.targetId(),payload.gameTick(),point,switch(payload.kind()) {
                case CRYSTAL_LAYER -> WeaponTargetImpactClient.Style.CRYSTAL_LAYER;
                case CRYSTAL_BURST -> WeaponTargetImpactClient.Style.CRYSTAL_BURST;
                case VENOM_RIPPLE -> WeaponTargetImpactClient.Style.VENOM_RIPPLE;
                case VENOM_NEST -> WeaponTargetImpactClient.Style.VENOM_NEST;
                case VENOM_DOT -> WeaponTargetImpactClient.Style.VENOM_DOT;
                default -> WeaponTargetImpactClient.Style.VENOM_BURST;
            });
            boolean direct=payload.kind()==Kind.CRYSTAL_LAYER || payload.kind()==Kind.VENOM_NEST;
            if(direct && !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(),payload.gameTick()))
                    && payload.casterId()==mc.player.getId()) freezeHit(payload,false,true);
            return;
        }
        if (payload.kind() == Kind.DARK_DEBT || payload.kind() == Kind.DARK_BURST || payload.kind() == Kind.FORGE_QUENCH
                || payload.kind() == Kind.FORGE_BLAST || payload.kind() == Kind.FORGE_BILLET || payload.kind() == Kind.FORGE_RING) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case DARK_DEBT -> WeaponTargetImpactClient.Style.DARK_DEBT;
                        case DARK_BURST -> WeaponTargetImpactClient.Style.DARK_BURST;
                        case FORGE_QUENCH -> WeaponTargetImpactClient.Style.FORGE_QUENCH;
                        case FORGE_BLAST -> WeaponTargetImpactClient.Style.FORGE_BLAST;
                        case FORGE_BILLET -> WeaponTargetImpactClient.Style.FORGE_BILLET;
                        default -> WeaponTargetImpactClient.Style.FORGE_RING;
                    });
            boolean direct = payload.kind() == Kind.DARK_DEBT || payload.kind() == Kind.FORGE_QUENCH;
            boolean first = direct && !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (direct && first && payload.casterId() == mc.player.getId()) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.OBSIDIAN_RESONANCE || payload.kind() == Kind.OBSIDIAN_CRACK
                || payload.kind() == Kind.OSSIFIED_BONUS || payload.kind() == Kind.OSSIFIED_PULSE) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case OBSIDIAN_RESONANCE -> WeaponTargetImpactClient.Style.OBSIDIAN_RESONANCE;
                        case OBSIDIAN_CRACK -> WeaponTargetImpactClient.Style.OBSIDIAN_CRACK;
                        case OSSIFIED_BONUS -> WeaponTargetImpactClient.Style.OSSIFIED_BONUS;
                        default -> WeaponTargetImpactClient.Style.OSSIFIED_PULSE;
                    });
            // Delayed fissures and field pulses must not stop a later weapon swing.
            return;
        }
        if (payload.kind() == Kind.MEOW_PROJECTILE) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point, WeaponTargetImpactClient.Style.MEOW_PROJECTILE);
            return;
        }
        if (payload.kind() == Kind.SHIV_HIT || payload.kind() == Kind.SHIV_STAB || payload.kind() == Kind.SHIV_BREATH_HIT) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.SHIV_STAB ? WeaponTargetImpactClient.Style.SHIV_STAB
                            : payload.kind() == Kind.SHIV_BREATH_HIT ? WeaponTargetImpactClient.Style.SHIV_BREATH
                            : WeaponTargetImpactClient.Style.SHIV_HIT);
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId()) freezeHit(payload, false, payload.kind() != Kind.SHIV_HIT);
            return;
        }
        if (payload.kind() == Kind.GALAXY_RIFT || payload.kind() == Kind.GALAXY_JUDGEMENT
                || payload.kind() == Kind.GALAXY_STAB || payload.kind() == Kind.GALAXY_LEAP) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case GALAXY_RIFT -> WeaponTargetImpactClient.Style.GALAXY_RIFT;
                        case GALAXY_JUDGEMENT -> WeaponTargetImpactClient.Style.GALAXY_JUDGEMENT;
                        case GALAXY_LEAP -> WeaponTargetImpactClient.Style.GALAXY_LEAP;
                        default -> WeaponTargetImpactClient.Style.GALAXY_STAB;
                    });
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId()) freezeHit(payload, false, payload.kind() != Kind.GALAXY_STAB);
            return;
        }
        if ((payload.kind() == Kind.SWORD || payload.kind() == Kind.DAGGER) && normalImpactStyle(payload.weaponId()) != null) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    normalImpactStyle(payload.weaponId()));
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId()) freezeHit(payload, false, payload.critical());
            return;
        }
        if (payload.kind() == Kind.HOLY_SMITE || payload.kind() == Kind.HOLY_PULSE || payload.kind() == Kind.TEMPLAR_STRIKE) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.HOLY_SMITE ? WeaponTargetImpactClient.Style.HOLY_SMITE
                            : payload.kind() == Kind.HOLY_PULSE ? WeaponTargetImpactClient.Style.HOLY_PULSE : WeaponTargetImpactClient.Style.TEMPLAR_STRIKE);
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId() && payload.kind() != Kind.HOLY_PULSE)
                freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.FOREST_RELEASE || payload.kind() == Kind.ELF_STAB || payload.kind() == Kind.ELF_LEAF) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.FOREST_RELEASE ? WeaponTargetImpactClient.Style.FOREST_RELEASE
                            : payload.kind() == Kind.ELF_STAB ? WeaponTargetImpactClient.Style.ELF_STAB : WeaponTargetImpactClient.Style.ELF_LEAF);
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId() && payload.kind() != Kind.ELF_LEAF)
                freezeHit(payload, false, payload.kind() == Kind.FOREST_RELEASE);
            return;
        }
        if (payload.kind() == Kind.TIDE_ANCHOR || payload.kind() == Kind.TIDE_BONUS
                || payload.kind() == Kind.TIDE_STAB || payload.kind() == Kind.TIDE_REEL) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case TIDE_ANCHOR -> WeaponTargetImpactClient.Style.TIDE_ANCHOR;
                        case TIDE_BONUS -> WeaponTargetImpactClient.Style.TIDE_BONUS;
                        case TIDE_REEL -> WeaponTargetImpactClient.Style.TIDE_REEL;
                        default -> WeaponTargetImpactClient.Style.TIDE_STAB;
                    });
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId()
                    && (payload.kind() == Kind.TIDE_STAB || payload.kind() == Kind.TIDE_REEL))
                freezeHit(payload, false, payload.kind() == Kind.TIDE_REEL);
            return;
        }
        if (payload.kind() == Kind.INFINITY_EVOLVE || payload.kind() == Kind.INFINITY_COLLAPSE
                || payload.kind() == Kind.INFINITY_STAB || payload.kind() == Kind.INFINITY_BACK || payload.kind() == Kind.INFINITY_RIFT) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    switch (payload.kind()) {
                        case INFINITY_EVOLVE -> WeaponTargetImpactClient.Style.INFINITY_EVOLVE;
                        case INFINITY_COLLAPSE -> WeaponTargetImpactClient.Style.INFINITY_COLLAPSE;
                        case INFINITY_STAB -> WeaponTargetImpactClient.Style.INFINITY_STAB;
                        case INFINITY_BACK -> WeaponTargetImpactClient.Style.INFINITY_BACK;
                        default -> WeaponTargetImpactClient.Style.INFINITY_RIFT;
                    });
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId() && payload.kind() != Kind.INFINITY_COLLAPSE && payload.kind() != Kind.INFINITY_RIFT)
                freezeHit(payload, false, payload.kind() != Kind.INFINITY_STAB);
            return;
        }
        if (payload.kind() == Kind.YETI_MARK || payload.kind() == Kind.YETI_SPINE) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.YETI_MARK ? WeaponTargetImpactClient.Style.FROST_MARK
                            : WeaponTargetImpactClient.Style.FROST_SPINE);
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId() && payload.kind() == Kind.YETI_MARK) freezeHit(payload, false, true);
            return;
        }
        if (payload.kind() == Kind.DRAGON_PIERCE || payload.kind() == Kind.DRAGON_JUDGEMENT) {
            WeaponTargetImpactClient.show(payload.casterId(), payload.targetId(), payload.gameTick(), point,
                    payload.kind() == Kind.DRAGON_JUDGEMENT ? WeaponTargetImpactClient.Style.DRAGON_JUDGEMENT
                            : WeaponTargetImpactClient.Style.DRAGON_PIERCE);
            boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
            if (first && payload.casterId() == mc.player.getId()) freezeHit(payload, true, payload.kind() == Kind.DRAGON_JUDGEMENT);
            return;
        }
        boolean bone = payload.kind() == Kind.CLUB || payload.kind() == Kind.SLAM;
        boolean sword = payload.kind() == Kind.SWORD;
        var profile = WeaponMeleeProfile.get(payload.weaponId());
        Material material = profile == null ? Material.METAL : profile.material();
        boolean strong = payload.critical() || payload.kind() == Kind.SLAM || payload.kind() == Kind.BONUS_THRUST;
        boolean first = !Long.valueOf(payload.gameTick()).equals(HIT_SOUNDS.put(payload.casterId(), payload.gameTick()));
        if (first) {
            mc.level.playLocalSound(point.x, point.y, point.z,
                    switch (material) {
                        case BONE -> SoundEvents.BONE_BLOCK_BREAK;
                        case WOOD -> SoundEvents.WOOD_HIT;
                        case FROST, VOID, HOLY, PRISM -> SoundEvents.AMETHYST_BLOCK_HIT;
                        default -> bone ? SoundEvents.STONE_HIT : SoundEvents.TRIDENT_HIT;
                    }, SoundSource.PLAYERS,
                    bone ? 0.7f : 0.42f, bone ? 0.62f : sword ? 1.1f : strong ? 1.25f : 1.75f, false);
            if (strong) mc.level.playLocalSound(point.x, point.y, point.z,
                    bone ? SoundEvents.STONE_BREAK : SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, bone ? 0.5f : 0.32f, bone ? 0.65f : 1.5f, false);
            if (payload.casterId() == mc.player.getId()) freezeHit(payload, bone, strong);
        }
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        if (IMPACTS.size() >= 48) com.stardew.craft.port.PortJava.removeFirst(IMPACTS);
        IMPACTS.add(new Impact(point, mc.level.getGameTime(), bone, strong, sword, material));
        Vec3 direction = mc.level.getEntity(payload.casterId()) instanceof LivingEntity caster
                ? point.subtract(caster.getEyePosition()).normalize() : new Vec3(0, 0, 1);
        RandomSource random = RandomSource.create(payload.gameTick() * 31 + payload.targetId());
        var boneParticle = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE));
        for (int i = 0; i < (bone ? 10 : strong ? 9 : 5); i++) {
            Vec3 velocity = direction.scale(0.12 + random.nextDouble() * 0.2).add(
                    (random.nextDouble() - 0.5) * 0.14, random.nextDouble() * 0.17,
                    (random.nextDouble() - 0.5) * 0.14);
            mc.level.addParticle(i % 3 != 0 ? ParticleTypes.CRIT : switch (material) {
                        case BONE -> boneParticle;
                        case WOOD -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState());
                        case FROST -> ParticleTypes.SNOWFLAKE;
                        case VOID, HOLY, PRISM -> ParticleTypes.END_ROD;
                        default -> ParticleTypes.CRIT;
                    },
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    static void heavyHammerContact(com.stardew.craft.combat.network.HeavyHammerFxPayload payload) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !(mc.player.getMainHandItem().getItem() instanceof IStardewWeapon held)
                || !com.stardew.craft.combat.skill.handler.HeavyHammerRules.isWeapon(held.getWeaponId()))return;
        boolean pound=com.stardew.craft.combat.skill.handler.HeavyHammerRules.POUND.equals(payload.skill());
        boolean finalHit=payload.skill().endsWith("_final");
        CameraShakeState.kick(finalHit?.17f:pound?.045f:.11f,pound?2:3,finalHit?.25f:0);
        float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
        Action current=action(mc.player,partial);
        // Automatic echoes must not freeze a later swing or another skill.
        if(current==null || !current.skillId.equals(payload.skill()))return;
        var playback=WeaponSkillAnimationClient.getWorldAction(mc.player.getId());
        int duration=playback!=null?playback.actionDurationTicks():com.stardew.craft.combat.skill.handler.HeavyHammerRules.animationTicks(current.skillId);
        float contact=pound?2f/7:payload.skill().endsWith("_sweep")?5f/12:payload.skill().endsWith("_quake")?7f/16:8f/15;
        double from=mc.level.getGameTime()+partial+Math.max(0,contact-current.progress)*duration;
        freeze=new Freeze(current.skillId,current.startTick,Math.max(contact,current.progress),from,from+(pound?.35:.8));
    }

    /** A short local hold after real contact, without rewinding the pose or pausing the world. */
    static void authoredHeavyContact(String damageId,float holdTicks) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
        Action current=action(mc.player,partial);
        if(current==null||current.progress>=.94f
                || !(damageId.equals(current.skillId)||damageId.startsWith(current.skillId+"_")))return;
        double now=mc.level.getGameTime()+partial;
        freeze=new Freeze(current.skillId,current.startTick,current.progress,now,now+holdTicks);
    }

    private static void freezeHit(MeleeImpactPayload payload, boolean bone, boolean strong) {
        Minecraft mc = Minecraft.getInstance();
        float partial = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
        Action current = action(mc.player, partial);
        if (current == null || !(mc.player.getMainHandItem().getItem() instanceof IStardewWeapon held)
                || !held.getWeaponId().equals(payload.weaponId()) || !matchesAction(payload.kind(), current.skillId)
                || !contactMatchesTick(current.skillId,current.startTick,payload.gameTick())) return;
        boolean dragon = CRESCENT_SLASH.equals(current.skillId) || RUST_STRIKE.equals(current.skillId) || WOOD_BLESS.equals(current.skillId) || LIGHT_COUNTER.equals(current.skillId) || SPINE_STRIKE.equals(current.skillId) || SPINE_WEAK.equals(current.skillId) || PIRATE_PLUNDER.equals(current.skillId) || SILVER_OUT.equals(current.skillId) || SILVER_RETURN.equals(current.skillId) || SILVER_STAY.equals(current.skillId) || IRON_THRUST.equals(current.skillId) || WIND_THRUST.equals(current.skillId) || BONE_FRACTURE.equals(current.skillId) || CLAYMORE_OUT.equals(current.skillId) || CLAYMORE_RETURN.equals(current.skillId) || DWARF_GUARD.equals(current.skillId) || NEEDLE_STRIKE.equals(current.skillId) || NEEDLE_FINAL.equals(current.skillId) || BURGLAR_STRIKE.equals(current.skillId) || SHADOW_EXECUTE.equals(current.skillId) || INSECT_DASH.equals(current.skillId) || CRYSTAL_LAYER.equals(current.skillId) || VENOM_NEST.equals(current.skillId) || DARK_DEBT.equals(current.skillId) || FORGE_QUENCH.equals(current.skillId) || HOLY_SMITE.equals(current.skillId) || TEMPLAR_STRIKE.equals(current.skillId) || FOREST_RELEASE.equals(current.skillId) || TideWeaponVisuals.isAction(current.skillId) || InfinityWeaponVisuals.isAction(current.skillId) || GalaxyWeaponVisuals.isAction(current.skillId) || SHIV_STAB.equals(current.skillId) || DRAGON_PIERCE.equals(current.skillId) || DRAGON_JUDGEMENT.equals(current.skillId)
                || YETI_MARK.equals(current.skillId) || YETI_SPINE.equals(current.skillId);
        float contact = CRESCENT_SLASH.equals(current.skillId) ? .375f : FEMUR_SLAM.equals(current.skillId) ? 0.082f : 0.185f;
        float duration = FEMUR_SLAM.equals(current.skillId) ? 8 : CARVING_STRIKE.equals(current.skillId) ? 3
                : CARVING_BONUS.equals(current.skillId) ? 4 : mc.player.getCurrentSwingDuration();
        if (dragon) duration = GALAXY_JUDGEMENT.equals(current.skillId) ? 12 : (GALAXY_STAB.equals(current.skillId) || INFINITY_STAB.equals(current.skillId)) ? 4 : 8;
        if (NEEDLE_STRIKE.equals(current.skillId) || NEEDLE_FINAL.equals(current.skillId)) duration = 3;
        if (FORGE_QUENCH.equals(current.skillId)) duration = 10;
        if (TIDE_STAB.equals(current.skillId)) duration = 6;
        if (TIDE_REEL.equals(current.skillId) || CLAYMORE_OUT.equals(current.skillId) || CLAYMORE_RETURN.equals(current.skillId)) duration = 12;
        if (!dragon && !WeaponMeleeProfile.isSample(held.getWeaponId())) duration = Math.min(1, duration * 0.4f) / 0.185f;
        double from = mc.level.getGameTime() + partial + Math.max(0, contact - current.progress) * duration;
        freeze = new Freeze(current.skillId, current.startTick, Math.max(contact, current.progress), from,
                from + (bone ? strong ? 1.2 : 0.85 : strong ? 0.45 : 0.25));
        CameraShakeState.kick(bone ? strong ? 0.22f : 0.12f : strong ? 0.075f : 0.035f,
                bone ? 3 : 2, bone && strong ? 0.75f : 0.12f);
    }

    static WeaponTargetImpactClient.Style silverImpactStyle(String phase) {
        return SILVER_RETURN.equals(phase) || SILVER_STAY.equals(phase) ? WeaponTargetImpactClient.Style.SILVER_RETURN : WeaponTargetImpactClient.Style.SILVER_OUT;
    }

    static WeaponTargetImpactClient.Style normalImpactStyle(String weapon) {
        return switch (weapon) {
            case "cutlass" -> WeaponTargetImpactClient.Style.CUTLASS_CUT;
            case "steel_falchion" -> WeaponTargetImpactClient.Style.FALCHION_CUT;
            case "rusty_sword" -> WeaponTargetImpactClient.Style.RUST_CUT;
            case "wooden_blade" -> WeaponTargetImpactClient.Style.WOOD_CUT;
            case "steel_smallsword" -> WeaponTargetImpactClient.Style.LIGHT_CUT;
            case "iron_edge" -> WeaponTargetImpactClient.Style.SPINE_CUT;
            case "pirate_sword" -> WeaponTargetImpactClient.Style.PIRATE_CUT;
            case "silver_saber" -> WeaponTargetImpactClient.Style.SILVER_CUT;
            case "iron_dirk" -> WeaponTargetImpactClient.Style.IRON_CUT;
            case "wind_spire" -> WeaponTargetImpactClient.Style.WIND_CUT;
            case "bone_sword" -> WeaponTargetImpactClient.Style.BONE_SWORD_CUT;
            case "claymore" -> WeaponTargetImpactClient.Style.CLAYMORE_CUT;
            case "dwarf_sword" -> WeaponTargetImpactClient.Style.DWARF_SWORD_CUT;
            case "dwarf_dagger" -> WeaponTargetImpactClient.Style.DWARF_DAGGER_CUT;
            case "iridium_needle" -> WeaponTargetImpactClient.Style.NEEDLE_CUT;
            case "burglars_shank" -> WeaponTargetImpactClient.Style.BURGLAR_CUT;
            case "shadow_dagger" -> WeaponTargetImpactClient.Style.SHADOW_CUT;
            case "insect_head" -> WeaponTargetImpactClient.Style.INSECT_CUT;
            case "crystal_dagger" -> WeaponTargetImpactClient.Style.CRYSTAL_CUT;
            case "wicked_kris" -> WeaponTargetImpactClient.Style.VENOM_CUT;
            case "dark_sword" -> WeaponTargetImpactClient.Style.DARK_CUT;
            case "tempered_broadsword" -> WeaponTargetImpactClient.Style.FORGE_CUT;
            case "obsidian_edge" -> WeaponTargetImpactClient.Style.OBSIDIAN_CUT;
            case "ossified_blade" -> WeaponTargetImpactClient.Style.OSSIFIED_CUT;
            case "meowmere" -> WeaponTargetImpactClient.Style.MEOW_CUT;
            case "holy_blade" -> WeaponTargetImpactClient.Style.HOLY_CUT;
            case "templars_blade" -> WeaponTargetImpactClient.Style.TEMPLAR_CUT;
            case "forest_sword" -> WeaponTargetImpactClient.Style.FOREST_CUT;
            case "yeti_tooth" -> WeaponTargetImpactClient.Style.FROST_CUT;
            case "dragontooth_cutlass" -> WeaponTargetImpactClient.Style.DRAGON_CUT;
            case "galaxy_sword", "galaxy_dagger" -> WeaponTargetImpactClient.Style.GALAXY_CUT;
            case "infinity_blade", "infinity_dagger" -> WeaponTargetImpactClient.Style.INFINITY_CUT;
            case "neptunes_glaive", "broken_trident" -> WeaponTargetImpactClient.Style.TIDE_CUT;
            default -> null;
        };
    }

    static boolean contactMatchesTick(String action,long start,long hit){return Math.abs(hit-start-(CRESCENT_SLASH.equals(action)?3:0))<=2;}

    static boolean matchesAction(Kind kind, String action) {
        return switch (kind) {
            case PIRATE_PLUNDER -> PIRATE_PLUNDER.equals(action);
            case CRESCENT_SLASH -> CRESCENT_SLASH.equals(action);
            case FALCHION_DOT,FALCHION_BURST -> false;
            case RUST_STRIKE -> RUST_STRIKE.equals(action);
            case WOOD_BLESS -> WOOD_BLESS.equals(action);
            case LIGHT_COUNTER -> LIGHT_COUNTER.equals(action);
            case SPINE_STRIKE -> SPINE_STRIKE.equals(action);
            case SPINE_WEAK -> SPINE_WEAK.equals(action);
            case SILVER_HIT -> SILVER_OUT.equals(action) || SILVER_RETURN.equals(action) || SILVER_STAY.equals(action);
            case IRON_THRUST -> IRON_THRUST.equals(action);
            case WIND_THRUST -> WIND_THRUST.equals(action);
            case BONE_FRACTURE -> BONE_FRACTURE.equals(action);
            case CLAYMORE_OUT -> CLAYMORE_OUT.equals(action);
            case CLAYMORE_RETURN -> CLAYMORE_RETURN.equals(action);
            case DWARF_GUARD -> DWARF_GUARD.equals(action);
            case DWARF_SHOCK, DWARF_THRUST -> false;
            case NEEDLE_STRIKE -> NEEDLE_STRIKE.equals(action);
            case NEEDLE_FINAL -> NEEDLE_FINAL.equals(action);
            case NEEDLE_FRENZY -> NEEDLE_SWING.equals(action);
            case BURGLAR_STRIKE -> BURGLAR_STRIKE.equals(action);
            case SHADOW_EXECUTE -> SHADOW_EXECUTE.equals(action);
            case SHADOW_FINISH -> false;
            case INSECT_EYE -> INSECT_SWING.equals(action);
            case INSECT_DASH -> INSECT_DASH.equals(action);
            case CRYSTAL_LAYER -> CRYSTAL_LAYER.equals(action);
            case VENOM_NEST -> VENOM_NEST.equals(action);
            case CRYSTAL_BURST, VENOM_RIPPLE, VENOM_DOT, VENOM_BURST -> false;
            case DARK_DEBT -> DARK_DEBT.equals(action);
            case FORGE_QUENCH -> FORGE_QUENCH.equals(action);
            case DARK_BURST, FORGE_BLAST, FORGE_BILLET, FORGE_RING -> false;
            case OBSIDIAN_RESONANCE, OBSIDIAN_CRACK, OSSIFIED_BONUS, OSSIFIED_PULSE, MEOW_PROJECTILE -> false;
            case SWORD -> "rapier_swing".equals(action) || CUTLASS_SWING.equals(action) || FALCHION_SWING.equals(action) || RUST_SWING.equals(action) || WOOD_SWING.equals(action) || LIGHT_SWING.equals(action) || SPINE_SWING.equals(action) || PIRATE_SWING.equals(action) || SILVER_SWING.equals(action) || BONE_SWORD_SWING.equals(action) || CLAYMORE_SWING.equals(action) || DWARF_SWORD_SWING.equals(action) || INSECT_SWING.equals(action) || DARK_SWING.equals(action) || FORGE_SWING.equals(action) || OBSIDIAN_SWING.equals(action) || OSSIFIED_SWING.equals(action) || MEOW_SWING.equals(action) || SWORD_SWING.equals(action) || HOLY_SWING.equals(action) || TEMPLAR_SWING.equals(action);
            case HOLY_SMITE -> HOLY_SMITE.equals(action);
            case TEMPLAR_STRIKE -> TEMPLAR_STRIKE.equals(action);
            case HOLY_PULSE -> false;
            case DAGGER -> IRON_SWING.equals(action) || WIND_SWING.equals(action) || DWARF_DAGGER_SWING.equals(action) || NEEDLE_SWING.equals(action) || BURGLAR_SWING.equals(action) || SHADOW_SWING.equals(action) || CRYSTAL_SWING.equals(action) || VENOM_SWING.equals(action) || CARVING_SWING.equals(action);
            case THRUST -> CARVING_STRIKE.equals(action);
            case BONUS_THRUST -> CARVING_BONUS.equals(action);
            case CLUB -> "dragontooth_club_swing".equals(action) || "the_slammer_swing".equals(action) || "dwarf_hammer_swing".equals(action) || "lead_rod_swing".equals(action) || "kudgel_swing".equals(action) || "wood_club_swing".equals(action) || "wood_mallet_swing".equals(action) || FEMUR_SWING.equals(action) || "galaxy_hammer_swing".equals(action) || "infinity_gavel_swing".equals(action);
            case SLAM -> FEMUR_SLAM.equals(action);
            case DRAGON_PIERCE -> MeleeWeaponVisuals.DRAGON_PIERCE.equals(action);
            case DRAGON_JUDGEMENT -> MeleeWeaponVisuals.DRAGON_JUDGEMENT.equals(action);
            case YETI_MARK -> MeleeWeaponVisuals.YETI_MARK.equals(action);
            case YETI_SPINE -> MeleeWeaponVisuals.YETI_SPINE.equals(action);
            case SHIV_STAB -> SHIV_STAB.equals(action);
            case SHIV_HIT, SHIV_BREATH_HIT -> SHIV_SWING.equals(action) || SHIV_EMPOWERED.equals(action);
            case GALAXY_RIFT -> GALAXY_RIFT.equals(action);
            case GALAXY_JUDGEMENT -> GALAXY_JUDGEMENT.equals(action);
            case GALAXY_STAB -> GALAXY_STAB.equals(action);
            case GALAXY_LEAP -> GALAXY_LEAP.equals(action);
            case INFINITY_EVOLVE -> INFINITY_RELEASE.equals(action);
            case INFINITY_STAB -> INFINITY_STAB.equals(action);
            case INFINITY_BACK -> INFINITY_BACK.equals(action);
            case FOREST_RELEASE -> FOREST_RELEASE.equals(action);
            case ELF_STAB -> ELF_SWING.equals(action);
            case ELF_LEAF -> false;
            case TIDE_STAB -> TIDE_STAB.equals(action);
            case TIDE_REEL -> TIDE_REEL.equals(action);
            case TIDE_ANCHOR, TIDE_BONUS -> false;
            case INFINITY_COLLAPSE, INFINITY_RIFT -> false;
            case GROUND -> false;
        };
    }

    private static void addGroundPulse(Vec3 center) {
        Minecraft mc = Minecraft.getInstance();
        List<GroundSegment> segments = new ArrayList<>();
        for (double radius : new double[]{0.45, 1.0, 1.65, 2.3}) {
            Vec3[] points = new Vec3[24];
            for (int i = 0; i < points.length; i++) {
                double angle = i * Math.PI * 2 / points.length;
                var contact = WeaponGroundContact.find(mc.level, mc.player,
                        center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
                if (contact != null) points[i] = contact.getLocation().add(0, 0.025, 0);
            }
            for (int i = 0; i < points.length; i++) {
                Vec3 a = points[i], b = points[(i + 1) % points.length];
                if (a != null && b != null && Math.abs(a.y - b.y) < 0.26) {
                    segments.add(new GroundSegment(a, b, (float) radius));
                }
            }
        }
        if (GROUND.size() >= 6) com.stardew.craft.port.PortJava.removeFirst(GROUND);
        GROUND.add(new GroundPulse(mc.level.getGameTime(), segments));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()
                || (IMPACTS.isEmpty() && GROUND.isEmpty())) return;
        var stack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        stack.pushPose();
        stack.translate(-camera.x, -camera.y, -camera.z);
        var matrix = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        double now = mc.level.getGameTime() + event.getPartialTick();
        for (Impact impact : IMPACTS) {
            float age = (float) (now - impact.start);
            if (age < 0 || age > 8 || impact.point.distanceToSqr(camera) > RANGE_SQR) continue;
            Vec3 normal = camera.subtract(impact.point).normalize();
            Vec3 right = normal.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(normal).normalize();
            float fade = (float) Math.pow(1 - age / (impact.bone ? 8 : 6), 2);
            if (!impact.bone && age > 6) continue;
            float size = impact.strong ? 0.9f : impact.bone ? 0.6f : impact.sword ? 0.62f : 0.42f;
            Vec3 axis = impact.bone ? up : right.add(up.scale(impact.sword ? 0.7 : 0.35)).normalize();
            WeaponContactGeometry.blade(out, matrix, impact.point.subtract(axis.scale(size)),
                    impact.point.add(axis.scale(size)), normal, impact.bone ? 0.24 : impact.sword ? 0.19 : 0.095,
                    fade, false, impact.material.red(), impact.material.green(), impact.material.blue());
            if (impact.bone || impact.strong) WeaponGlowGeometry.ring(out, matrix, impact.point, right, up,
                    size * (0.25 + age * 0.12), 0.05 * fade,
                    impact.material.red(), impact.material.green(), impact.material.blue(), (int) (165 * fade));
        }
        for (GroundPulse pulse : GROUND) {
            for (GroundSegment segment : pulse.segments) {
                float age = (float) (now - pulse.start) - segment.radius * 1.5f;
                if (age < 0 || age > 6 || segment.from.distanceToSqr(camera) > RANGE_SQR) continue;
                float fade = 1 - age / 6;
                Vec3 width = segment.to.subtract(segment.from).cross(new Vec3(0, 1, 0)).normalize().scale(0.065 * fade);
                WeaponGlowGeometry.strip(out, matrix, segment.from, segment.to, width,
                        239, 207, 149, (int) (155 * fade * fade));
            }
        }
        stack.popPose();
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }

    private static void ensureLevel(@Nullable ClientLevel level) {
        if (activeLevel == level) return;
        IMPACTS.clear(); GROUND.clear(); RELEASES.clear(); HIT_SOUNDS.clear(); CHARGE_SPARKS.clear();
        freeze = null;
        activeLevel = level;
    }

    private record Impact(Vec3 point, long start, boolean bone, boolean strong, boolean sword, Material material) {}
    private record GroundSegment(Vec3 from, Vec3 to, float radius) {}
    private record GroundPulse(long start, List<GroundSegment> segments) {}
}
