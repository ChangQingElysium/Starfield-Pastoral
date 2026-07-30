package com.stardew.craft.client.weapon.animation;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class WeaponSkillAnimationRegistry {

    private static final Map<String, WeaponSkillAnimation> SKILL_ANIMATIONS = new HashMap<>();
    private static final Map<String, WeaponSkillAnimation> WEAPON_ANIMATIONS = new HashMap<>();

    private static final WeaponSkillAnimation DEFAULT_ANIMATION = new RustySwordSkillAnimation();

    static {
        registerWeapon("rusty_sword", DEFAULT_ANIMATION);
        registerWeapon("pirate_sword", DEFAULT_ANIMATION);
        registerWeapon("silver_saber", DEFAULT_ANIMATION);
        registerWeapon("cutlass", DEFAULT_ANIMATION);
        registerWeapon("forest_sword", DEFAULT_ANIMATION);
        registerWeapon("bone_sword", DEFAULT_ANIMATION);
        registerWeapon("claymore", DEFAULT_ANIMATION);
        registerWeapon("neptunes_glaive", DEFAULT_ANIMATION);
        registerWeapon("templars_blade", DEFAULT_ANIMATION);
        registerWeapon("insect_head", DEFAULT_ANIMATION);
        registerWeapon("holy_blade", DEFAULT_ANIMATION);
        registerWeapon("tempered_broadsword", DEFAULT_ANIMATION);
        registerWeapon("steel_falchion", DEFAULT_ANIMATION);
        registerWeapon("dark_sword", DEFAULT_ANIMATION);
        registerWeapon("dragontooth_cutlass", DEFAULT_ANIMATION);
        registerWeapon("dwarf_sword", DEFAULT_ANIMATION);
        registerWeapon("galaxy_sword", DEFAULT_ANIMATION);
        registerWeapon("infinity_blade", DEFAULT_ANIMATION);
        registerWeapon("carving_knife", DEFAULT_ANIMATION);
        registerWeapon("iron_dirk", DEFAULT_ANIMATION);
        registerWeapon("wind_spire", DEFAULT_ANIMATION);
        registerWeapon("elf_blade", DEFAULT_ANIMATION);
        registerWeapon("burglars_shank", DEFAULT_ANIMATION);
        registerWeapon("crystal_dagger", DEFAULT_ANIMATION);
        registerWeapon("shadow_dagger", DEFAULT_ANIMATION);
        registerWeapon("broken_trident", DEFAULT_ANIMATION);
        registerWeapon("femur", DEFAULT_ANIMATION);
        registerWeapon("wicked_kris", DEFAULT_ANIMATION);
        registerWeapon("dwarf_dagger", DEFAULT_ANIMATION);
        registerWeapon("dragontooth_shiv", DEFAULT_ANIMATION);
        registerWeapon("iridium_needle", DEFAULT_ANIMATION);
        registerWeapon("galaxy_dagger", DEFAULT_ANIMATION);
        registerWeapon("infinity_dagger", DEFAULT_ANIMATION);
        registerSkill("claymore", "claymore_foldback", new ClaymoreFoldbackSlashAnimation());
        registerSkill("claymore", "claymore_foldback_return", new ClaymoreFoldbackReturnAnimation());
        registerSkill("pirate_sword", "desperate_plunder", new PirateSwordPlunderAnimation());
        registerSkill("cutlass", "crescent_slash", new CutlassCrescentSlashAnimation());
        registerSkill("forest_sword", "forest_blessing", new ForestBlessingCastAnimation());
        registerSkill("steel_smallsword", "light_counter", new SteelSmallswordGuardAnimation());
        registerSkill("steel_smallsword", "light_counter_counter", new SteelSmallswordCounterAnimation());
        registerSkill("carving_knife", "carving_thrust", new CarvingKnifeThrustAnimation());
        registerSkill("iridium_needle", "iridium_needle_thrust", new CarvingKnifeThrustAnimation());
        registerSkill("iridium_needle", "iridium_needle_frenzy", new NoOpWeaponSkillAnimation());
        registerSkill("templars_blade", "templar_vow", new SteelSmallswordGuardAnimation());
        registerSkill("insect_head", "insect_eye_stance", new NoOpWeaponSkillAnimation());
        registerSkill("holy_blade", "holy_smite", new CutlassCrescentSlashAnimation());
        registerSkill("steel_falchion", "steel_falchion_line", new CutlassCrescentSlashAnimation());
        registerSkill("steel_falchion", "steel_falchion_trace", new NoOpWeaponSkillAnimation());
        registerSkill("dark_sword", "dark_sword_blood_moon", new NoOpWeaponSkillAnimation());
        registerSkill("dragontooth_cutlass", "dragon_breath_judgement", new CutlassCrescentSlashAnimation());
        registerSkill("wicked_kris", "wicked_kris_venom_ripple", new NoOpWeaponSkillAnimation());
        registerSkill("femur", "femur_slam", new FemurSlamSkillAnimation());
        
        // Meowmere uses default animation for shooting
        registerWeapon("meowmere", DEFAULT_ANIMATION);
    }

    private WeaponSkillAnimationRegistry() {}

    public static void registerSkill(String weaponId, String skillId, WeaponSkillAnimation animation) {
        SKILL_ANIMATIONS.put(key(weaponId, skillId), animation);
    }

    public static void registerWeapon(String weaponId, WeaponSkillAnimation animation) {
        WEAPON_ANIMATIONS.put(weaponId, animation);
    }

    public static WeaponSkillAnimation getAnimation(@Nullable String weaponId, @Nullable String skillId) {
        if (weaponId != null && skillId != null) {
            WeaponSkillAnimation skillAnim = SKILL_ANIMATIONS.get(key(weaponId, skillId));
            if (skillAnim != null) {
                return skillAnim;
            }
        }
        if (weaponId != null) {
            WeaponSkillAnimation weaponAnim = WEAPON_ANIMATIONS.get(weaponId);
            if (weaponAnim != null) {
                return weaponAnim;
            }
        }
        return DEFAULT_ANIMATION;
    }

    private static String key(String weaponId, String skillId) {
        return weaponId + ":" + skillId;
    }
}
