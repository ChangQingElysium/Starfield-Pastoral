package com.stardew.craft.item.weapon;

import com.stardew.craft.combat.WeaponType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 武器数据注册表
 * 存储所有武器的数据定义
 */
public class WeaponRegistry {
    
    private static final Map<String, WeaponData> WEAPONS = new HashMap<>();
    
    static {
        registerAllWeapons();
    }
    
    private static void registerAllWeapons() {
        // ============== 剑类武器 ==============
        
        // Lv.1 - 新手级
        register(WeaponData.builder("rusty_sword")
                .type(WeaponType.SWORD)
                .level(1)
                .damage(2, 5)
                .critChance(0.02)
                .skill1(WeaponSkillData.builder("tetanus_strike")
                .nameKey("stardewcraft.weapon.skill.tetanus_strike")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tetanus_strike.desc.1",
                    "stardewcraft.weapon.skill.tetanus_strike.desc.2")
                        .damage(100)
                .effectKey("stardewcraft.weapon.skill.tetanus_strike.effect.1")
                        .cooldown(5)
                        .icon(WeaponIcons.SKILL_RUSTY_SWORD_1)
                        .build())
            .loreKey("stardewcraft.weapon.rusty_sword.lore")
                .build());

        register(WeaponData.builder("steel_smallsword")
            .type(WeaponType.SWORD)
            .level(1)
            .damage(4, 8)
            .speed(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("light_counter")
                .nameKey("stardewcraft.weapon.skill.light_counter")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.light_counter.desc.1",
                    "stardewcraft.weapon.skill.light_counter.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.light_counter.effect.1")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_STEEL_SMALLSWORD_1)
                .build())
            .build());

        register(WeaponData.builder("wooden_blade")
            .type(WeaponType.SWORD)
            .level(1)
            .damage(3, 7)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("tree_blessing")
                .nameKey("stardewcraft.weapon.skill.tree_blessing")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tree_blessing.desc.1",
                    "stardewcraft.weapon.skill.tree_blessing.desc.2")
                .damage(110)
                .effectKey("stardewcraft.weapon.skill.tree_blessing.effect.1")
                .cooldown(5)
                .icon(WeaponIcons.SKILL_WOODEN_BLADE_1)
                .build())
            .build());

        // Lv.2 - 初级
        register(WeaponData.builder("pirate_sword")
            .type(WeaponType.SWORD)
            .level(2)
            .damage(8, 14)
            .speed(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("desperate_plunder")
                .nameKey("stardewcraft.weapon.skill.desperate_plunder")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.desperate_plunder.desc.1",
                    "stardewcraft.weapon.skill.desperate_plunder.desc.2")
                .damage(140)
                .effectKey("stardewcraft.weapon.skill.desperate_plunder.effect.1")
                .effectKey("stardewcraft.weapon.skill.desperate_plunder.effect.2")
                .effectKey("stardewcraft.weapon.skill.desperate_plunder.effect.3")
                .cooldown(5)
                .icon(WeaponIcons.SKILL_PIRATE_SWORD_1)
                .build())
            .loreKey("stardewcraft.weapon.pirate_sword.lore")
            .build());

        register(WeaponData.builder("silver_saber")
            .type(WeaponType.SWORD)
            .level(2)
            .damage(8, 15)
            .defense(1)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("silver_foldback")
                .nameKey("stardewcraft.weapon.skill.silver_foldback")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.silver_foldback.desc.1",
                    "stardewcraft.weapon.skill.silver_foldback.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.silver_foldback.effect.1")
                .effectKey("stardewcraft.weapon.skill.silver_foldback.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_SILVER_SABER_1)
                .build())
            .build());

        register(WeaponData.builder("cutlass")
            .type(WeaponType.SWORD)
            .level(3)
            .damage(9, 17)
            .speed(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("crescent_slash")
                .nameKey("stardewcraft.weapon.skill.crescent_slash")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.crescent_slash.desc.1",
                    "stardewcraft.weapon.skill.crescent_slash.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.crescent_slash.effect.1")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_CUTLASS_1)
                .build())
            .build());

        register(WeaponData.builder("forest_sword")
            .type(WeaponType.SWORD)
            .level(3)
            .damage(8, 18)
            .speed(2)
            .defense(1)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("forest_blessing")
                .nameKey("stardewcraft.weapon.skill.forest_blessing")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.forest_blessing.desc.1",
                    "stardewcraft.weapon.skill.forest_blessing.desc.2")
                .damage(100)
                .effectKey("stardewcraft.weapon.skill.forest_blessing.effect.1")
                .effectKey("stardewcraft.weapon.skill.forest_blessing.effect.2")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_FOREST_SWORD_1)
                .build())
            .build());

        register(WeaponData.builder("iron_edge")
            .type(WeaponType.SWORD)
            .level(3)
            .damage(12, 25)
            .speed(-2)
            .defense(1)
            .weight(3)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("steel_spine_fury")
                .nameKey("stardewcraft.weapon.skill.steel_spine_fury")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.steel_spine_fury.desc.1",
                    "stardewcraft.weapon.skill.steel_spine_fury.desc.2")
                .damage(100)
                .effectKey("stardewcraft.weapon.skill.steel_spine_fury.effect.1")
                .effectKey("stardewcraft.weapon.skill.steel_spine_fury.effect.2")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_IRON_EDGE_1)
                .build())
            .build());
        
        // Lv.4 - 中级
        register(WeaponData.builder("meowmere")
            .type(WeaponType.SWORD)
            .level(4)
            .damage(20, 20) // 极度稳定
            .speed(4)
            .weight(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("meowmere_shot")
                .nameKey("stardewcraft.weapon.skill.meowmere_shot")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.meowmere_shot.desc.1",
                    "stardewcraft.weapon.skill.meowmere_shot.desc.2")
                .damage(100)
                .effectKey("stardewcraft.weapon.skill.meowmere_shot.effect.1")
                .effectKey("stardewcraft.weapon.skill.meowmere_shot.effect.2")
                .cooldown(4) // 4秒冷却
                .icon(WeaponIcons.SKILL_MEOWMERE_1)
                .build())
            .skill2(WeaponSkillData.builder("meowmere_symphony")
                .nameKey("stardewcraft.weapon.skill.meowmere_symphony")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.meowmere_symphony.desc.1",
                    "stardewcraft.weapon.skill.meowmere_symphony.desc.2")
                .damage(80) // 每一发80%
                .effectKey("stardewcraft.weapon.skill.meowmere_symphony.effect.1") // 消耗能量
                .effectKey("stardewcraft.weapon.skill.meowmere_symphony.effect.2") // 5发齐射
                .cooldown(15) // 15秒冷却
                .icon(WeaponIcons.SKILL_MEOWMERE_2)
                .build())
            .loreKey("stardewcraft.weapon.meowmere.lore")
            .build());

        // Lv.5 - 中级
        register(WeaponData.builder("bone_sword")
            .type(WeaponType.SWORD)
            .level(5)
            .damage(20, 30)
            .speed(4)
            .weight(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("bone_fracture")
                .nameKey("stardewcraft.weapon.skill.bone_fracture")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.bone_fracture.desc.1",
                    "stardewcraft.weapon.skill.bone_fracture.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.bone_fracture.effect.1")
                .effectKey("stardewcraft.weapon.skill.bone_fracture.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_BONE_SWORD_1)
                .build())
            .build());

        register(WeaponData.builder("claymore")
            .type(WeaponType.SWORD)
            .level(5)
            .damage(20, 32)
            .speed(-4)
            .defense(2)
            .weight(3)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("claymore_foldback")
                .nameKey("stardewcraft.weapon.skill.claymore_foldback")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.claymore_foldback.desc.1",
                    "stardewcraft.weapon.skill.claymore_foldback.desc.2")
                .damage(70)
                .effectKey("stardewcraft.weapon.skill.claymore_foldback.effect.1")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_CLAYMORE_1)
                .build())
            .build());

        register(WeaponData.builder("neptunes_glaive")
            .type(WeaponType.SWORD)
            .level(5)
            .damage(18, 35)
            .speed(-1)
            .defense(2)
            .weight(4)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("tide_mark")
                .nameKey("stardewcraft.weapon.skill.tide_mark")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tide_mark.desc.1",
                    "stardewcraft.weapon.skill.tide_mark.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.tide_mark.effect.1")
                .effectKey("stardewcraft.weapon.skill.tide_mark.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_NEPTUNE_GLAIVE_1)
                .build())
            .skill2(WeaponSkillData.builder("tide_anchor")
                .nameKey("stardewcraft.weapon.skill.tide_anchor")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tide_anchor.desc.1",
                    "stardewcraft.weapon.skill.tide_anchor.desc.2")
                .damage(150)
                .effectKey("stardewcraft.weapon.skill.tide_anchor.effect.1")
                .effectKey("stardewcraft.weapon.skill.tide_anchor.effect.2")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_NEPTUNE_GLAIVE_2)
                .build())
            .build());

        register(WeaponData.builder("templars_blade")
            .type(WeaponType.SWORD)
            .level(5)
            .damage(22, 29)
            .defense(1)
            .critChance(0.00)
            .skill1(WeaponSkillData.builder("templar_vow")
                .nameKey("stardewcraft.weapon.skill.templar_vow")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.templar_vow.desc.1",
                    "stardewcraft.weapon.skill.templar_vow.desc.2")
                .damage(110)
                .effectKey("stardewcraft.weapon.skill.templar_vow.effect.1")
                .effectKey("stardewcraft.weapon.skill.templar_vow.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_TEMPLARS_BLADE_1)
                .build())
            .skill2(WeaponSkillData.builder("templar_judgement")
                .nameKey("stardewcraft.weapon.skill.templar_judgement")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.templar_judgement.desc.1",
                    "stardewcraft.weapon.skill.templar_judgement.desc.2")
                .damage(160)
                .effectKey("stardewcraft.weapon.skill.templar_judgement.effect.1")
                .effectKey("stardewcraft.weapon.skill.templar_judgement.effect.2")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_TEMPLARS_BLADE_2)
                .build())
            .build());

        // Lv.6 - 中高级
        register(WeaponData.builder("insect_head")
            .type(WeaponType.SWORD)
            .level(6)
            .damage(20, 30)
            .speed(2)
            .critChance(0.04)
            .skill1(WeaponSkillData.builder("insect_eye_stance")
                .nameKey("stardewcraft.weapon.skill.insect_eye_stance")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.insect_eye_stance.desc.1",
                    "stardewcraft.weapon.skill.insect_eye_stance.desc.2")
                .damage(105)
                .effectKey("stardewcraft.weapon.skill.insect_eye_stance.effect.1")
                .effectKey("stardewcraft.weapon.skill.insect_eye_stance.effect.2")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_INSECT_HEAD_1)
                .build())
            .skill2(WeaponSkillData.builder("insect_dash")
                .nameKey("stardewcraft.weapon.skill.insect_dash")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.insect_dash.desc.1",
                    "stardewcraft.weapon.skill.insect_dash.desc.2")
                .damage(80)
                .effectKey("stardewcraft.weapon.skill.insect_dash.effect.1")
                .effectKey("stardewcraft.weapon.skill.insect_dash.effect.2")
                .effectKey("stardewcraft.weapon.skill.insect_dash.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_INSECT_HEAD_2)
                .build())
            .build());

        register(WeaponData.builder("obsidian_edge")
            .type(WeaponType.SWORD)
            .level(6)
            .damage(30, 45)
            .speed(-1)
            .critChance(0.02)
            .critPower(1.1)
            .skill1(WeaponSkillData.builder("obsidian_resonance")
                .nameKey("stardewcraft.weapon.skill.obsidian_resonance")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.obsidian_resonance.desc.1",
                    "stardewcraft.weapon.skill.obsidian_resonance.desc.2")
                .damage(70)
                .effectKey("stardewcraft.weapon.skill.obsidian_resonance.effect.1")
                .effectKey("stardewcraft.weapon.skill.obsidian_resonance.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_OBSIDIAN_EDGE_1)
                .build())
            .skill2(WeaponSkillData.builder("obsidian_crack")
                .nameKey("stardewcraft.weapon.skill.obsidian_crack")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.obsidian_crack.desc.1",
                    "stardewcraft.weapon.skill.obsidian_crack.desc.2")
                .damage(160)
                .effectKey("stardewcraft.weapon.skill.obsidian_crack.effect.1")
                .effectKey("stardewcraft.weapon.skill.obsidian_crack.effect.2")
                .effectKey("stardewcraft.weapon.skill.obsidian_crack.effect.3")
                .cooldown(10)
                .icon(WeaponIcons.SKILL_OBSIDIAN_EDGE_2)
                .build())
            .build());

        register(WeaponData.builder("ossified_blade")
            .type(WeaponType.SWORD)
            .level(6)
            .damage(26, 42)
            .speed(-2)
            .defense(1)
            .weight(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("ossified_mark")
                .nameKey("stardewcraft.weapon.skill.ossified_mark")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.ossified_mark.desc.1",
                    "stardewcraft.weapon.skill.ossified_mark.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.ossified_mark.effect.1")
                .effectKey("stardewcraft.weapon.skill.ossified_mark.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_OSSIFIED_BLADE_1)
                .build())
            .skill2(WeaponSkillData.builder("ossified_execution")
                .nameKey("stardewcraft.weapon.skill.ossified_execution")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.ossified_execution.desc.1",
                    "stardewcraft.weapon.skill.ossified_execution.desc.2")
                .damage(50)
                .effectKey("stardewcraft.weapon.skill.ossified_execution.effect.1")
                .effectKey("stardewcraft.weapon.skill.ossified_execution.effect.2")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_OSSIFIED_BLADE_2)
                .build())
            .build());

        // Lv.7 - 高级
        register(WeaponData.builder("holy_blade")
            .type(WeaponType.SWORD)
            .level(7)
            .damage(20, 27)
            .speed(4)
            .defense(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("holy_smite")
                .nameKey("stardewcraft.weapon.skill.holy_smite")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.holy_smite.desc.1",
                    "stardewcraft.weapon.skill.holy_smite.desc.2")
                .damage(90)
                .effectKey("stardewcraft.weapon.skill.holy_smite.effect.1")
                .effectKey("stardewcraft.weapon.skill.holy_smite.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_HOLY_BLADE_1)
                .build())
            .skill2(WeaponSkillData.builder("holy_domain")
                .nameKey("stardewcraft.weapon.skill.holy_domain")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.holy_domain.desc.1",
                    "stardewcraft.weapon.skill.holy_domain.desc.2")
                .damage(75)
                .effectKey("stardewcraft.weapon.skill.holy_domain.effect.1")
                .effectKey("stardewcraft.weapon.skill.holy_domain.effect.2")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_HOLY_BLADE_2)
                .build())
            .build());

        register(WeaponData.builder("tempered_broadsword")
            .type(WeaponType.SWORD)
            .level(7)
            .damage(29, 44)
            .speed(-3)
            .defense(3)
            .weight(3)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("tempered_quench")
                .nameKey("stardewcraft.weapon.skill.tempered_quench")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tempered_quench.desc.1",
                    "stardewcraft.weapon.skill.tempered_quench.desc.2")
                .damage(105)
                .effectKey("stardewcraft.weapon.skill.tempered_quench.effect.1")
                .effectKey("stardewcraft.weapon.skill.tempered_quench.effect.2")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_TEMPERED_BROADSWORD_1)
                .build())
            .skill2(WeaponSkillData.builder("tempered_billet")
                .nameKey("stardewcraft.weapon.skill.tempered_billet")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tempered_billet.desc.1",
                    "stardewcraft.weapon.skill.tempered_billet.desc.2")
                .damage(100)
                .effectKey("stardewcraft.weapon.skill.tempered_billet.effect.1")
                .effectKey("stardewcraft.weapon.skill.tempered_billet.effect.2")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_TEMPERED_BROADSWORD_2)
                .build())
            .build());

        register(WeaponData.builder("yeti_tooth")
            .type(WeaponType.SWORD)
            .level(7)
            .damage(26, 42)
            .defense(4)
            .critChance(0.02)
            .critPower(1.1)
            .skill1(WeaponSkillData.builder("yeti_tooth_mark")
                .nameKey("stardewcraft.weapon.skill.yeti_tooth_mark")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.yeti_tooth_mark.desc.1",
                    "stardewcraft.weapon.skill.yeti_tooth_mark.desc.2")
                .damage(110)
                .effectKey("stardewcraft.weapon.skill.yeti_tooth_mark.effect.1")
                .effectKey("stardewcraft.weapon.skill.yeti_tooth_mark.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_YETI_TOOTH_1)
                .build())
            .skill2(WeaponSkillData.builder("yeti_tooth_spine")
                .nameKey("stardewcraft.weapon.skill.yeti_tooth_spine")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.yeti_tooth_spine.desc.1",
                    "stardewcraft.weapon.skill.yeti_tooth_spine.desc.2")
                .damage(180)
                .effectKey("stardewcraft.weapon.skill.yeti_tooth_spine.effect.1")
                .effectKey("stardewcraft.weapon.skill.yeti_tooth_spine.effect.2")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_YETI_TOOTH_2)
                .build())
            .build());

        register(WeaponData.builder("steel_falchion")
            .type(WeaponType.SWORD)
            .level(8)
            .damage(28, 46)
            .speed(4)
            .critChance(0.02)
            .critPower(1.2)
            .skill1(WeaponSkillData.builder("steel_falchion_line")
                .nameKey("stardewcraft.weapon.skill.steel_falchion_line")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.steel_falchion_line.desc.1",
                    "stardewcraft.weapon.skill.steel_falchion_line.desc.2")
                .damage(30)
                .effectKey("stardewcraft.weapon.skill.steel_falchion_line.effect.1")
                .effectKey("stardewcraft.weapon.skill.steel_falchion_line.effect.2")
                .cooldown(10)
                .icon(WeaponIcons.SKILL_STEEL_FALCHION_1)
                .build())
            .skill2(WeaponSkillData.builder("steel_falchion_trace")
                .nameKey("stardewcraft.weapon.skill.steel_falchion_trace")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.steel_falchion_trace.desc.1",
                    "stardewcraft.weapon.skill.steel_falchion_trace.desc.2")
                .damage(50)
                .effectKey("stardewcraft.weapon.skill.steel_falchion_trace.effect.1")
                .effectKey("stardewcraft.weapon.skill.steel_falchion_trace.effect.2")
                .effectKey("stardewcraft.weapon.skill.steel_falchion_trace.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_STEEL_FALCHION_2)
                .build())
            .build());

        register(WeaponData.builder("dark_sword")
            .type(WeaponType.SWORD)
            .level(9)
            .damage(30, 45)
            .speed(-5)
            .weight(5)
            .critChance(0.04)
            .skill1(WeaponSkillData.builder("dark_sword_blood_debt")
                .nameKey("stardewcraft.weapon.skill.dark_sword_blood_debt")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dark_sword_blood_debt.desc.1",
                    "stardewcraft.weapon.skill.dark_sword_blood_debt.desc.2")
                .damage(140)
                .effectKey("stardewcraft.weapon.skill.dark_sword_blood_debt.effect.1")
                .effectKey("stardewcraft.weapon.skill.dark_sword_blood_debt.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_DARK_SWORD_1)
                .build())
            .skill2(WeaponSkillData.builder("dark_sword_blood_moon")
                .nameKey("stardewcraft.weapon.skill.dark_sword_blood_moon")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dark_sword_blood_moon.desc.1",
                    "stardewcraft.weapon.skill.dark_sword_blood_moon.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.dark_sword_blood_moon.effect.1")
                .effectKey("stardewcraft.weapon.skill.dark_sword_blood_moon.effect.2")
                .effectKey("stardewcraft.weapon.skill.dark_sword_blood_moon.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_DARK_SWORD_2)
                .build())
            .build());

        register(WeaponData.builder("lava_katana")
            .type(WeaponType.SWORD)
            .level(10)
            .damage(55, 64)
            .defense(3)
            .weight(3)
            .critChance(0.015)
            .critPower(1.25)
            .skill1(WeaponSkillData.builder("lava_katana_brand")
                .nameKey("stardewcraft.weapon.skill.lava_katana_brand")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.lava_katana_brand.desc.1",
                    "stardewcraft.weapon.skill.lava_katana_brand.desc.2")
                .damage(110)
                .effectKey("stardewcraft.weapon.skill.lava_katana_brand.effect.1")
                .effectKey("stardewcraft.weapon.skill.lava_katana_brand.effect.2")
                .effectKey("stardewcraft.weapon.skill.lava_katana_brand.effect.3")
                .cooldown(12)
                .icon(WeaponIcons.SKILL_LAVA_KATANA_1)
                .build())
            .skill2(WeaponSkillData.builder("lava_katana_reverb")
                .nameKey("stardewcraft.weapon.skill.lava_katana_reverb")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.lava_katana_reverb.desc.1",
                    "stardewcraft.weapon.skill.lava_katana_reverb.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.lava_katana_reverb.effect.1")
                .effectKey("stardewcraft.weapon.skill.lava_katana_reverb.effect.2")
                .effectKey("stardewcraft.weapon.skill.lava_katana_reverb.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_LAVA_KATANA_2)
                .build())
            .build());

        register(WeaponData.builder("dragontooth_cutlass")
            .type(WeaponType.SWORD)
            .level(13)
            .damage(75, 90)
            .critChance(0.02)
            .critPower(1.50)
            .skill1(WeaponSkillData.builder("dragon_breath_thrust")
                .nameKey("stardewcraft.weapon.skill.dragon_breath_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dragon_breath_thrust.desc.1",
                    "stardewcraft.weapon.skill.dragon_breath_thrust.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.dragon_breath_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.dragon_breath_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.dragon_breath_thrust.effect.3")
                .cooldown(8)
                .icon(WeaponIcons.SKILL_DRAGONTOOTH_CUTLASS_1)
                .build())
            .skill2(WeaponSkillData.builder("dragon_breath_judgement")
                .nameKey("stardewcraft.weapon.skill.dragon_breath_judgement")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.1",
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.2",
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.3",
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.4",
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.5",
                    "stardewcraft.weapon.skill.dragon_breath_judgement.desc.6")
                .damage(260)
                .effectKey("stardewcraft.weapon.skill.dragon_breath_judgement.effect.1")
                .effectKey("stardewcraft.weapon.skill.dragon_breath_judgement.effect.2")
                .cooldown(0)
                .icon(WeaponIcons.SKILL_DRAGONTOOTH_CUTLASS_2)
                .build())
            .build());

        register(WeaponData.builder("dwarf_sword")
            .type(WeaponType.SWORD)
            .level(13)
            .damage(65, 75)
            .speed(2)
            .defense(4)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("dwarf_rune_guard")
                .nameKey("stardewcraft.weapon.skill.dwarf_rune_guard")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dwarf_rune_guard.desc.1",
                    "stardewcraft.weapon.skill.dwarf_rune_guard.desc.2")
                .damage(110)
                .effectKey("stardewcraft.weapon.skill.dwarf_rune_guard.effect.1")
                .effectKey("stardewcraft.weapon.skill.dwarf_rune_guard.effect.2")
                .effectKey("stardewcraft.weapon.skill.dwarf_rune_guard.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_DWARF_SWORD_1)
                .build())
            .skill2(WeaponSkillData.builder("dwarf_fortress")
                .nameKey("stardewcraft.weapon.skill.dwarf_fortress")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dwarf_fortress.desc.1",
                    "stardewcraft.weapon.skill.dwarf_fortress.desc.2")
                .damage(220)
                .effectKey("stardewcraft.weapon.skill.dwarf_fortress.effect.1")
                .effectKey("stardewcraft.weapon.skill.dwarf_fortress.effect.2")
                .effectKey("stardewcraft.weapon.skill.dwarf_fortress.effect.3")
                .effectKey("stardewcraft.weapon.skill.dwarf_fortress.effect.4")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_DWARF_SWORD_2)
                .build())
            .build());

        register(WeaponData.builder("galaxy_sword")
            .type(WeaponType.SWORD)
            .level(13)
            .damage(60, 80)
            .speed(4)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("startrail_rift")
                .nameKey("stardewcraft.weapon.skill.startrail_rift")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.startrail_rift.desc.1",
                    "stardewcraft.weapon.skill.startrail_rift.desc.2")
                .damage(140)
                .effectKey("stardewcraft.weapon.skill.startrail_rift.effect.1")
                .effectKey("stardewcraft.weapon.skill.startrail_rift.effect.2")
                .effectKey("stardewcraft.weapon.skill.startrail_rift.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_GALAXY_SWORD_1)
                .build())
            .skill2(WeaponSkillData.builder("galaxy_judgement")
                .nameKey("stardewcraft.weapon.skill.galaxy_judgement")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.galaxy_judgement.desc.1",
                    "stardewcraft.weapon.skill.galaxy_judgement.desc.2",
                    "stardewcraft.weapon.skill.galaxy_judgement.desc.3")
                .damage(220)
                .effectKey("stardewcraft.weapon.skill.galaxy_judgement.effect.1")
                .effectKey("stardewcraft.weapon.skill.galaxy_judgement.effect.2")
                .effectKey("stardewcraft.weapon.skill.galaxy_judgement.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_GALAXY_SWORD_2)
                .build())
            .build());

        register(WeaponData.builder("infinity_blade")
            .type(WeaponType.SWORD)
            .level(17)
            .damage(80, 100)
            .speed(4)
            .defense(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("singularity_evolve")
                .nameKey("stardewcraft.weapon.skill.singularity_evolve")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.singularity_evolve.desc.1",
                    "stardewcraft.weapon.skill.singularity_evolve.desc.2",
                    "stardewcraft.weapon.skill.singularity_evolve.desc.3")
                .damage(160)
                .effectKey("stardewcraft.weapon.skill.singularity_evolve.effect.1")
                .effectKey("stardewcraft.weapon.skill.singularity_evolve.effect.2")
                .effectKey("stardewcraft.weapon.skill.singularity_evolve.effect.3")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_INFINITY_BLADE_1)
                .build())
            .skill2(WeaponSkillData.builder("eternal_collapse")
                .nameKey("stardewcraft.weapon.skill.eternal_collapse")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.eternal_collapse.desc.1",
                    "stardewcraft.weapon.skill.eternal_collapse.desc.2",
                    "stardewcraft.weapon.skill.eternal_collapse.desc.3")
                .damage(80)
                .effectKey("stardewcraft.weapon.skill.eternal_collapse.effect.1")
                .effectKey("stardewcraft.weapon.skill.eternal_collapse.effect.2")
                .effectKey("stardewcraft.weapon.skill.eternal_collapse.effect.3")
                .cooldown(25)
                .icon(WeaponIcons.SKILL_INFINITY_BLADE_2)
                .build())
            .build());

        // ============== 匕首武器 ==============
        // Lv.1 - 新手级
        register(WeaponData.builder("carving_knife")
            .type(WeaponType.DAGGER)
            .level(1)
            .damage(1, 3)
            .critChance(0.04)
            .critPower(1.02)
            .skill1(WeaponSkillData.builder("carving_thrust")
                .nameKey("stardewcraft.weapon.skill.carving_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.carving_thrust.desc.1",
                    "stardewcraft.weapon.skill.carving_thrust.desc.2")
                .damage(45)
                .effectKey("stardewcraft.weapon.skill.carving_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.carving_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.carving_thrust.effect.3")
                .cooldown(5)
                .icon(WeaponIcons.SKILL_CARVING_KNIFE_1)
                .build())
            .build());

        register(WeaponData.builder("iron_dirk")
            .type(WeaponType.DAGGER)
            .level(1)
            .damage(2, 4)
            .critChance(0.03)
            .critPower(1.02)
            .skill1(WeaponSkillData.builder("iron_dirk_thrust")
                .nameKey("stardewcraft.weapon.skill.iron_dirk_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.iron_dirk_thrust.desc.1",
                    "stardewcraft.weapon.skill.iron_dirk_thrust.desc.2")
                .damage(150)
                .effectKey("stardewcraft.weapon.skill.iron_dirk_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.iron_dirk_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.iron_dirk_thrust.effect.3")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_IRON_DIRK_1)
                .build())
            .build());

        register(WeaponData.builder("wind_spire")
            .type(WeaponType.DAGGER)
            .level(1)
            .damage(1, 5)
            .critChance(0.02)
            .critPower(1.10)
            .weight(5)
            .skill1(WeaponSkillData.builder("wind_spire_thrust")
                .nameKey("stardewcraft.weapon.skill.wind_spire_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.wind_spire_thrust.desc.1",
                    "stardewcraft.weapon.skill.wind_spire_thrust.desc.2")
                .damage(150)
                .effectKey("stardewcraft.weapon.skill.wind_spire_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.wind_spire_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.wind_spire_thrust.effect.3")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_WIND_SPIRE_1)
                .build())
            .build());

        register(WeaponData.builder("elf_blade")
            .type(WeaponType.DAGGER)
            .level(2)
            .damage(3, 5)
            .critChance(0.04)
            .critPower(1.02)
            .skill1(WeaponSkillData.builder("elf_blade_leaf")
                .nameKey("stardewcraft.weapon.skill.elf_blade_leaf")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.elf_blade_leaf.desc.1",
                    "stardewcraft.weapon.skill.elf_blade_leaf.desc.2")
                .damage(50)
                .effectKey("stardewcraft.weapon.skill.elf_blade_leaf.effect.1")
                .effectKey("stardewcraft.weapon.skill.elf_blade_leaf.effect.2")
                .effectKey("stardewcraft.weapon.skill.elf_blade_leaf.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_ELF_BLADE_1)
                .build())
            .build());

        register(WeaponData.builder("burglars_shank")
            .type(WeaponType.DAGGER)
            .level(4)
            .damage(7, 12)
            .critChance(0.04)
            .critPower(1.25)
            .skill1(WeaponSkillData.builder("burglar_shank")
                .nameKey("stardewcraft.weapon.skill.burglar_shank")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.burglar_shank.desc.1",
                    "stardewcraft.weapon.skill.burglar_shank.desc.2")
                .damage(150)
                .effectKey("stardewcraft.weapon.skill.burglar_shank.effect.1")
                .effectKey("stardewcraft.weapon.skill.burglar_shank.effect.2")
                .effectKey("stardewcraft.weapon.skill.burglar_shank.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_BURGLARS_SHANK_1)
                .build())
            .build());

        register(WeaponData.builder("crystal_dagger")
            .type(WeaponType.DAGGER)
            .level(4)
            .damage(4, 10)
            .critChance(0.03)
            .critPower(1.50)
            .weight(5)
            .skill1(WeaponSkillData.builder("crystal_dagger_layer")
                .nameKey("stardewcraft.weapon.skill.crystal_dagger_layer")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.crystal_dagger_layer.desc.1",
                    "stardewcraft.weapon.skill.crystal_dagger_layer.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.crystal_dagger_layer.effect.1")
                .effectKey("stardewcraft.weapon.skill.crystal_dagger_layer.effect.2")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_CRYSTAL_DAGGER_1)
                .build())
            .build());

        register(WeaponData.builder("shadow_dagger")
            .type(WeaponType.DAGGER)
            .level(4)
            .damage(10, 20)
            .critChance(0.04)
            .critPower(1.25)
            .skill1(WeaponSkillData.builder("shadow_dagger_execute")
                .nameKey("stardewcraft.weapon.skill.shadow_dagger_execute")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.shadow_dagger_execute.desc.1",
                    "stardewcraft.weapon.skill.shadow_dagger_execute.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.shadow_dagger_execute.effect.1")
                .effectKey("stardewcraft.weapon.skill.shadow_dagger_execute.effect.2")
                .effectKey("stardewcraft.weapon.skill.shadow_dagger_execute.effect.3")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_SHADOW_DAGGER_1)
                .build())
            .build());

        register(WeaponData.builder("wicked_kris")
            .type(WeaponType.DAGGER)
            .level(8)
            .damage(24, 30)
            .critChance(0.06)
            .critPower(1.40)
            .skill1(WeaponSkillData.builder("wicked_kris_venom_ripple")
                .nameKey("stardewcraft.weapon.skill.wicked_kris_venom_ripple")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.wicked_kris_venom_ripple.desc.1",
                    "stardewcraft.weapon.skill.wicked_kris_venom_ripple.desc.2")
                .damage(60)
                .effectKey("stardewcraft.weapon.skill.wicked_kris_venom_ripple.effect.1")
                .effectKey("stardewcraft.weapon.skill.wicked_kris_venom_ripple.effect.2")
                .effectKey("stardewcraft.weapon.skill.wicked_kris_venom_ripple.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_WICKED_KRIS_1)
                .build())
            .skill2(WeaponSkillData.builder("wicked_kris_nest_burst")
                .nameKey("stardewcraft.weapon.skill.wicked_kris_nest_burst")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.wicked_kris_nest_burst.desc.1",
                    "stardewcraft.weapon.skill.wicked_kris_nest_burst.desc.2")
                .damage(140)
                .effectKey("stardewcraft.weapon.skill.wicked_kris_nest_burst.effect.1")
                .effectKey("stardewcraft.weapon.skill.wicked_kris_nest_burst.effect.2")
                .effectKey("stardewcraft.weapon.skill.wicked_kris_nest_burst.effect.3")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_WICKED_KRIS_2)
                .build())
            .build());

        register(WeaponData.builder("galaxy_dagger")
            .type(WeaponType.DAGGER)
            .level(8)
            .damage(30, 40)
            .speed(1)
            .weight(5)
            .critChance(0.02)
            .critPower(1.40)
            .skill1(WeaponSkillData.builder("galaxy_dagger_starstab")
                .nameKey("stardewcraft.weapon.skill.galaxy_dagger_starstab")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.galaxy_dagger_starstab.desc.1",
                    "stardewcraft.weapon.skill.galaxy_dagger_starstab.desc.2")
                .damage(50)
                .effectKey("stardewcraft.weapon.skill.galaxy_dagger_starstab.effect.1")
                .effectKey("stardewcraft.weapon.skill.galaxy_dagger_starstab.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_GALAXY_DAGGER_1)
                .build())
            .skill2(WeaponSkillData.builder("galaxy_dagger_starleap")
                .nameKey("stardewcraft.weapon.skill.galaxy_dagger_starleap")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.galaxy_dagger_starleap.desc.1",
                    "stardewcraft.weapon.skill.galaxy_dagger_starleap.desc.2")
                .damage(140)
                .effectKey("stardewcraft.weapon.skill.galaxy_dagger_starleap.effect.1")
                .effectKey("stardewcraft.weapon.skill.galaxy_dagger_starleap.effect.2")
                .effectKey("stardewcraft.weapon.skill.galaxy_dagger_starleap.effect.3")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_GALAXY_DAGGER_2)
                .build())
            .build());

        register(WeaponData.builder("dwarf_dagger")
            .type(WeaponType.DAGGER)
            .level(11)
            .damage(32, 38)
            .speed(1)
            .defense(6)
            .weight(5)
            .critChance(0.03)
            .critPower(1.25)
            .skill1(WeaponSkillData.builder("dwarf_dagger_thrust")
                .nameKey("stardewcraft.weapon.skill.dwarf_dagger_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dwarf_dagger_thrust.desc.1",
                    "stardewcraft.weapon.skill.dwarf_dagger_thrust.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_thrust.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_DWARF_DAGGER_1)
                .build())
            .skill2(WeaponSkillData.builder("dwarf_dagger_rush")
                .nameKey("stardewcraft.weapon.skill.dwarf_dagger_rush")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dwarf_dagger_rush.desc.1",
                    "stardewcraft.weapon.skill.dwarf_dagger_rush.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_rush.effect.1")
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_rush.effect.2")
                .effectKey("stardewcraft.weapon.skill.dwarf_dagger_rush.effect.3")
                .cooldown(15)
                .icon(WeaponIcons.SKILL_DWARF_DAGGER_2)
                .build())
            .build());

        register(WeaponData.builder("iridium_needle")
            .type(WeaponType.DAGGER)
            .level(12)
            .damage(20, 35)
            .critChance(0.10)
            .critPower(3.00)
            .skill1(WeaponSkillData.builder("iridium_needle_thrust")
                .nameKey("stardewcraft.weapon.skill.iridium_needle_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.iridium_needle_thrust.desc.1",
                    "stardewcraft.weapon.skill.iridium_needle_thrust.desc.2")
                .damage(40)
                .effectKey("stardewcraft.weapon.skill.iridium_needle_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.iridium_needle_thrust.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_IRIDIUM_NEEDLE_1)
                .build())
            .skill2(WeaponSkillData.builder("iridium_needle_frenzy")
                .nameKey("stardewcraft.weapon.skill.iridium_needle_frenzy")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.iridium_needle_frenzy.desc.1",
                    "stardewcraft.weapon.skill.iridium_needle_frenzy.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.iridium_needle_frenzy.effect.1")
                .effectKey("stardewcraft.weapon.skill.iridium_needle_frenzy.effect.2")
                .effectKey("stardewcraft.weapon.skill.iridium_needle_frenzy.effect.3")
                .effectKey("stardewcraft.weapon.skill.iridium_needle_frenzy.effect.4")
                .effectKey("stardewcraft.weapon.skill.iridium_needle_frenzy.effect.5")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_IRIDIUM_NEEDLE_2)
                .build())
            .build());

        register(WeaponData.builder("dragontooth_shiv")
            .type(WeaponType.DAGGER)
            .level(12)
            .damage(40, 50)
            .critChance(0.05)
            .critPower(2.00)
            .weight(5)
            .skill1(WeaponSkillData.builder("dragontooth_shiv_stab")
                .nameKey("stardewcraft.weapon.skill.dragontooth_shiv_stab")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dragontooth_shiv_stab.desc.1",
                    "stardewcraft.weapon.skill.dragontooth_shiv_stab.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_stab.effect.1")
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_stab.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_DRAGONTOOTH_SHIV_1)
                .build())
            .skill2(WeaponSkillData.builder("dragontooth_shiv_breath")
                .nameKey("stardewcraft.weapon.skill.dragontooth_shiv_breath")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.dragontooth_shiv_breath.desc.1",
                    "stardewcraft.weapon.skill.dragontooth_shiv_breath.desc.2")
                .damage(0)
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_breath.effect.1")
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_breath.effect.2")
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_breath.effect.3")
                .effectKey("stardewcraft.weapon.skill.dragontooth_shiv_breath.effect.4")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_DRAGONTOOTH_SHIV_2)
                .build())
            .build());

        register(WeaponData.builder("infinity_dagger")
            .type(WeaponType.DAGGER)
            .level(16)
            .damage(50, 70)
            .speed(1)
            .defense(3)
            .weight(5)
            .critChance(0.06)
            .critPower(2.00)
            .skill1(WeaponSkillData.builder("infinity_dagger_singularity_stab")
                .nameKey("stardewcraft.weapon.skill.infinity_dagger_singularity_stab")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.infinity_dagger_singularity_stab.desc.1",
                    "stardewcraft.weapon.skill.infinity_dagger_singularity_stab.desc.2")
                .damage(22)
                .effectKey("stardewcraft.weapon.skill.infinity_dagger_singularity_stab.effect.1")
                .effectKey("stardewcraft.weapon.skill.infinity_dagger_singularity_stab.effect.2")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_INFINITY_DAGGER_1)
                .build())
            .skill2(WeaponSkillData.builder("infinity_dagger_singularity_backstab")
                .nameKey("stardewcraft.weapon.skill.infinity_dagger_singularity_backstab")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.infinity_dagger_singularity_backstab.desc.1",
                    "stardewcraft.weapon.skill.infinity_dagger_singularity_backstab.desc.2")
                .damage(70)
                .effectKey("stardewcraft.weapon.skill.infinity_dagger_singularity_backstab.effect.1")
                .effectKey("stardewcraft.weapon.skill.infinity_dagger_singularity_backstab.effect.2")
                .effectKey("stardewcraft.weapon.skill.infinity_dagger_singularity_backstab.effect.3")
                .cooldown(20)
                .icon(WeaponIcons.SKILL_INFINITY_DAGGER_2)
                .build())
            .build());

        register(WeaponData.builder("broken_trident")
            .type(WeaponType.DAGGER)
            .level(5)
            .damage(15, 26)
            .critChance(0.02)
            .critPower(1.10)
            .skill1(WeaponSkillData.builder("fishcatch_thrust")
                .nameKey("stardewcraft.weapon.skill.fishcatch_thrust")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.fishcatch_thrust.desc.1",
                    "stardewcraft.weapon.skill.fishcatch_thrust.desc.2")
                .damage(40)
                .effectKey("stardewcraft.weapon.skill.fishcatch_thrust.effect.1")
                .effectKey("stardewcraft.weapon.skill.fishcatch_thrust.effect.2")
                .effectKey("stardewcraft.weapon.skill.fishcatch_thrust.effect.3")
                .cooldown(6)
                .icon(WeaponIcons.SKILL_BROKEN_TRIDENT_1)
                .build())
            .skill2(WeaponSkillData.builder("tide_reel")
                .nameKey("stardewcraft.weapon.skill.tide_reel")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.tide_reel.desc.1",
                    "stardewcraft.weapon.skill.tide_reel.desc.2")
                .damage(200)
                .effectKey("stardewcraft.weapon.skill.tide_reel.effect.1")
                .effectKey("stardewcraft.weapon.skill.tide_reel.effect.2")
                .effectKey("stardewcraft.weapon.skill.tide_reel.effect.3")
                .cooldown(18)
                .icon(WeaponIcons.SKILL_BROKEN_TRIDENT_2)
                .build())
            .build());

        register(WeaponData.builder("elliotts_pencil")
            .type(WeaponType.DAGGER)
            .level(12)
            .damage(24, 30)
            .weight(0.5)
            .critChance(0.06)
            .critPower(3.0)
            .build());

        register(WeaponData.builder("abbys_planchette")
            .type(WeaponType.DAGGER)
            .level(12)
            .damage(24, 30)
            .weight(0.5)
            .critChance(0.06)
            .critPower(3.0)
            .build());

        // ============== 棍棒武器 ==============
        // Lv.2 - 初级
        register(WeaponData.builder("femur")
            .type(WeaponType.CLUB)
            .level(2)
            .damage(6, 11)
            .speed(2)
            .critChance(0.02)
            .skill1(WeaponSkillData.builder("femur_slam")
                .nameKey("stardewcraft.weapon.skill.femur_slam")
                .descriptionKeys(
                    "stardewcraft.weapon.skill.femur_slam.desc.1",
                    "stardewcraft.weapon.skill.femur_slam.desc.2")
                .damage(120)
                .effectKey("stardewcraft.weapon.skill.femur_slam.effect.1")
                .effectKey("stardewcraft.weapon.skill.femur_slam.effect.2")
                .effectKey("stardewcraft.weapon.skill.femur_slam.effect.3")
                .cooldown(7)
                .icon(WeaponIcons.SKILL_FEMUR_1)
                .build())
            .build());
        register(WeaponData.builder("alexs_bat")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());
        register(WeaponData.builder("sams_old_guitar")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());
        register(WeaponData.builder("marus_wrench")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());
        register(WeaponData.builder("harveys_mallet")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());
        register(WeaponData.builder("pennys_fryer")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());
        register(WeaponData.builder("sebs_lost_mace")
            .type(WeaponType.CLUB)
            .level(12)
            .damage(40, 55)
            .speed(-12)
            .weight(1.5)
            .critChance(0.02)
            .critPower(3.0)
            .build());

        register(WeaponData.builder("galaxy_hammer")
            .type(WeaponType.CLUB)
            .level(13)
            .damage(70, 90)
            .speed(-2)
            .critChance(0.02)
            .build());

        register(WeaponData.builder("infinity_gavel")
            .type(WeaponType.CLUB)
            .level(17)
            .damage(100, 120)
            .speed(-2)
            .defense(1)
            .critChance(0.02)
            .build());
        register(WeaponData.builder("leahs_whittler")
            .type(WeaponType.SWORD)
            .level(12)
            .damage(30, 45)
            .speed(-2)
            .weight(1.0)
            .critChance(0.02)
            .critPower(3.2)
            .build());
        register(WeaponData.builder("haleys_iron")
            .type(WeaponType.SWORD)
            .level(12)
            .damage(30, 45)
            .speed(-2)
            .weight(1.0)
            .critChance(0.02)
            .critPower(3.2)
            .build());
        
        // TODO: 后续添加更多武器
    }
    
    private static void register(WeaponData data) {
        WeaponData previous = WEAPONS.putIfAbsent(data.getId(), data);
        if (previous != null) {
            throw new IllegalStateException("Duplicate weapon id: " + data.getId());
        }
    }
    
    /**
     * 根据ID获取武器数据
     */
    public static WeaponData get(String id) {
        return WEAPONS.get(id);
    }
    
    /**
     * 检查武器是否存在
     */
    public static boolean exists(String id) {
        return WEAPONS.containsKey(id);
    }
    
    /**
     * 获取所有武器ID
     */
    public static Collection<String> getAllIds() {
        return Collections.unmodifiableSet(WEAPONS.keySet());
    }

    /**
     * Immutable view used by validation, tooling and data generation.
     */
    public static Collection<WeaponData> getAll() {
        return Collections.unmodifiableCollection(WEAPONS.values());
    }
}
