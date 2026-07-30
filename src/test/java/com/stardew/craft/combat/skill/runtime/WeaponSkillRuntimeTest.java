package com.stardew.craft.combat.skill.runtime;

import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.combat.skill.handler.BuiltinWeaponSkillHandlers;
import com.stardew.craft.combat.skill.handler.CrescentSlashSkillHandler;
import com.stardew.craft.combat.skill.handler.BoneFractureSkillHandler;
import com.stardew.craft.combat.skill.handler.CarvingThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.ClaymoreFoldbackSkillHandler;
import com.stardew.craft.combat.skill.handler.BurglarShankSkillHandler;
import com.stardew.craft.combat.skill.handler.CrystalDaggerLayerSkillHandler;
import com.stardew.craft.combat.skill.handler.DesperatePlunderSkillHandler;
import com.stardew.craft.combat.skill.handler.DarkSwordBloodDebtSkillHandler;
import com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfDaggerRushSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfRuneGuardSkillHandler;
import com.stardew.craft.combat.skill.handler.DwarfFortressSkillHandler;
import com.stardew.craft.combat.skill.handler.DragontoothShivStabSkillHandler;
import com.stardew.craft.combat.skill.handler.DragontoothShivBreathSkillHandler;
import com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.DragonBreathJudgementSkillHandler;
import com.stardew.craft.combat.skill.handler.ElfBladeLeafSkillHandler;
import com.stardew.craft.combat.skill.handler.FishcatchThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.ForestBlessingSkillHandler;
import com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler;
import com.stardew.craft.combat.skill.handler.GalaxyDaggerStarstabSkillHandler;
import com.stardew.craft.combat.skill.handler.GalaxyJudgementSkillHandler;
import com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityStabSkillHandler;
import com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler;
import com.stardew.craft.combat.skill.handler.SingularityEvolveSkillHandler;
import com.stardew.craft.combat.skill.handler.StartrailRiftSkillHandler;
import com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler;
import com.stardew.craft.combat.skill.handler.FemurSlamSkillHandler;
import com.stardew.craft.combat.skill.handler.LightCounterSkillHandler;
import com.stardew.craft.combat.skill.handler.LavaKatanaBrandSkillHandler;
import com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler;
import com.stardew.craft.combat.skill.handler.MeowmereShotSkillHandler;
import com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler;
import com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.IridiumNeedleThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzySkillHandler;
import com.stardew.craft.combat.skill.handler.HolySmiteSkillHandler;
import com.stardew.craft.combat.skill.handler.HolyDomainSkillHandler;
import com.stardew.craft.combat.skill.handler.InsectEyeStanceSkillHandler;
import com.stardew.craft.combat.skill.handler.InsectDashSkillHandler;
import com.stardew.craft.combat.skill.handler.OssifiedExecutionSkillHandler;
import com.stardew.craft.combat.skill.handler.OssifiedMarkSkillHandler;
import com.stardew.craft.combat.skill.handler.ObsidianResonanceSkillHandler;
import com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import com.stardew.craft.combat.skill.handler.SteelFalchionTraceSkillHandler;
import com.stardew.craft.combat.skill.handler.SteelFalchionLineSkillHandler;
import com.stardew.craft.combat.skill.handler.ShadowDaggerExecuteSkillHandler;
import com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler;
import com.stardew.craft.combat.skill.handler.TetanusStrikeSkillHandler;
import com.stardew.craft.combat.skill.handler.TemperedQuenchSkillHandler;
import com.stardew.craft.combat.skill.handler.TemperedBilletSkillHandler;
import com.stardew.craft.combat.skill.handler.TemplarVowSkillHandler;
import com.stardew.craft.combat.skill.handler.TemplarJudgementSkillHandler;
import com.stardew.craft.combat.skill.handler.TreeBlessingSkillHandler;
import com.stardew.craft.combat.skill.handler.TideReelSkillHandler;
import com.stardew.craft.combat.skill.handler.TideMarkSkillHandler;
import com.stardew.craft.combat.skill.handler.TideAnchorSkillHandler;
import com.stardew.craft.combat.skill.handler.WindSpireThrustSkillHandler;
import com.stardew.craft.combat.skill.handler.WickedKrisVenomRippleSkillHandler;
import com.stardew.craft.combat.skill.handler.WickedKrisNestBurstSkillHandler;
import com.stardew.craft.combat.skill.handler.YetiToothMarkSkillHandler;
import com.stardew.craft.combat.skill.handler.YetiToothSpineSkillHandler;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillRuntimeTest {
    @Test
    void skillInstanceEnforcesLifecycleAndCopiesTargets() {
        SkillInstance instance = new SkillInstance(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                42,
                id("cutlass"),
                id("crescent_slash"),
                100L,
                new Vec3(1.0, 2.0, 3.0),
                new Vec3(0.0, 0.0, 1.0),
                99L
        );

        instance.activate();
        instance.setTargetEntityIds(List.of(7, 9));
        assertEquals(SkillInstance.Phase.ACTIVE, instance.phase());
        assertEquals(List.of(7, 9), instance.targetEntityIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> instance.targetEntityIds().add(11)
        );

        instance.beginRecovery();
        instance.finish(SkillInstance.EndReason.COMPLETED);
        assertTrue(instance.isTerminal());
        assertEquals(SkillInstance.Phase.ENDED, instance.phase());
        assertEquals(SkillInstance.EndReason.COMPLETED, instance.endReason());
        assertThrows(IllegalStateException.class, instance::activate);
        assertThrows(
                IllegalStateException.class,
                () -> instance.setTargetEntityIds(List.of(12))
        );
    }

    @Test
    void pendingSkillIdsMatchNamespacedAndPathForms() {
        ResourceLocation skillId = id("lava_katana_reverb");

        assertTrue(WeaponSkillRuntime.matchesSkillId(
                skillId,
                "lava_katana_reverb"
        ));
        assertTrue(WeaponSkillRuntime.matchesSkillId(
                skillId,
                "stardewcraft:lava_katana_reverb"
        ));
        assertFalse(WeaponSkillRuntime.matchesSkillId(
                skillId,
                "lava_katana_finisher"
        ));
    }

    @Test
    void crescentSlashRegistrationAndOriginalBehaviorConstantsRemainStable() {
        BuiltinWeaponSkillHandlers.bootstrap();

        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.CRESCENT_SLASH).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.CRESCENT_SLASH).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TETANUS_STRIKE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TETANUS_STRIKE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TREE_BLESSING).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TREE_BLESSING).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.LIGHT_COUNTER).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.LIGHT_COUNTER).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DESPERATE_PLUNDER).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DESPERATE_PLUNDER).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.BONE_FRACTURE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.BONE_FRACTURE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.CARVING_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.CARVING_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.IRON_DIRK_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.IRON_DIRK_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.WIND_SPIRE_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.WIND_SPIRE_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.BURGLAR_SHANK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.BURGLAR_SHANK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.FISHCATCH_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.FISHCATCH_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.CRYSTAL_DAGGER_LAYER).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.CRYSTAL_DAGGER_LAYER).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.SHADOW_DAGGER_EXECUTE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.SHADOW_DAGGER_EXECUTE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TIDE_REEL).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TIDE_REEL).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DWARF_DAGGER_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.HOLY_SMITE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.HOLY_SMITE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.HOLY_DOMAIN).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.HOLY_DOMAIN).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.WICKED_KRIS_VENOM_RIPPLE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.WICKED_KRIS_VENOM_RIPPLE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.WICKED_KRIS_NEST_BURST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.WICKED_KRIS_NEST_BURST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.OSSIFIED_MARK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.OSSIFIED_MARK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.OSSIFIED_EXECUTION).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.OSSIFIED_EXECUTION).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TEMPERED_QUENCH).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TEMPERED_QUENCH).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.YETI_TOOTH_MARK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.YETI_TOOTH_MARK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TEMPLAR_VOW).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TEMPLAR_VOW).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.INSECT_DASH).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.INSECT_DASH).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.OBSIDIAN_RESONANCE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.OBSIDIAN_RESONANCE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.YETI_TOOTH_SPINE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.YETI_TOOTH_SPINE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TEMPERED_BILLET).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TEMPERED_BILLET).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.OBSIDIAN_CRACK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.OBSIDIAN_CRACK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TIDE_MARK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TIDE_MARK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.TIDE_ANCHOR).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.TIDE_ANCHOR).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.SILVER_FOLDBACK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.SILVER_FOLDBACK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.CLAYMORE_FOLDBACK).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.CLAYMORE_FOLDBACK).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_DEBT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_DEBT).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.MEOWMERE_SHOT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.MEOWMERE_SHOT).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.STEEL_FALCHION_TRACE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.STEEL_FALCHION_TRACE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.MEOWMERE_SYMPHONY).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.MEOWMERE_SYMPHONY).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.STEEL_FALCHION_LINE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.STEEL_FALCHION_LINE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_MOON).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_MOON).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_STAB).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_STAB).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DWARF_RUNE_GUARD).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DWARF_RUNE_GUARD).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.LAVA_KATANA_BRAND).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.LAVA_KATANA_BRAND).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DRAGON_BREATH_THRUST).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DRAGON_BREATH_THRUST).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.LAVA_KATANA_REVERB).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.LAVA_KATANA_REVERB).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DRAGON_BREATH_JUDGEMENT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DRAGON_BREATH_JUDGEMENT).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.DWARF_FORTRESS).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.DWARF_FORTRESS).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARSTAB).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARSTAB).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARLEAP).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARLEAP).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.GALAXY_JUDGEMENT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.GALAXY_JUDGEMENT).isPresent());
        assertTrue(WeaponSkillRuntime.get(
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_STAB).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_STAB).isPresent());
        assertTrue(WeaponSkillRuntime.get(
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_BACKSTAB).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_BACKSTAB).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.SINGULARITY_EVOLVE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.SINGULARITY_EVOLVE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.STARTRAIL_RIFT).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.STARTRAIL_RIFT).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.ETERNAL_COLLAPSE).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.ETERNAL_COLLAPSE).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.FEMUR_SLAM).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.FEMUR_SLAM).isPresent());
        assertTrue(WeaponSkillRuntime.get(BuiltinWeaponSkillHandlers.FOREST_BLESSING).isPresent());
        assertTrue(StardewWeaponSkillHandlers.get(
                BuiltinWeaponSkillHandlers.FOREST_BLESSING).isPresent());
        assertEquals(64, WeaponSkillRuntime.registeredSkillIds().size());
        assertTrue(WeaponSkillRuntime.registeredSkillIds().containsAll(List.of(
                BuiltinWeaponSkillHandlers.CRESCENT_SLASH,
                BuiltinWeaponSkillHandlers.TETANUS_STRIKE,
                BuiltinWeaponSkillHandlers.TREE_BLESSING,
                BuiltinWeaponSkillHandlers.LIGHT_COUNTER,
                BuiltinWeaponSkillHandlers.DESPERATE_PLUNDER,
                BuiltinWeaponSkillHandlers.BONE_FRACTURE,
                BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY,
                BuiltinWeaponSkillHandlers.CARVING_THRUST,
                BuiltinWeaponSkillHandlers.IRON_DIRK_THRUST,
                BuiltinWeaponSkillHandlers.WIND_SPIRE_THRUST,
                BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF,
                BuiltinWeaponSkillHandlers.BURGLAR_SHANK,
                BuiltinWeaponSkillHandlers.FISHCATCH_THRUST,
                BuiltinWeaponSkillHandlers.CRYSTAL_DAGGER_LAYER,
                BuiltinWeaponSkillHandlers.SHADOW_DAGGER_EXECUTE,
                BuiltinWeaponSkillHandlers.TIDE_REEL,
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_THRUST,
                BuiltinWeaponSkillHandlers.HOLY_SMITE,
                BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH,
                BuiltinWeaponSkillHandlers.HOLY_DOMAIN,
                BuiltinWeaponSkillHandlers.WICKED_KRIS_VENOM_RIPPLE,
                BuiltinWeaponSkillHandlers.WICKED_KRIS_NEST_BURST,
                BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_THRUST,
                BuiltinWeaponSkillHandlers.OSSIFIED_MARK,
                BuiltinWeaponSkillHandlers.OSSIFIED_EXECUTION,
                BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE,
                BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY,
                BuiltinWeaponSkillHandlers.TEMPERED_QUENCH,
                BuiltinWeaponSkillHandlers.YETI_TOOTH_MARK,
                BuiltinWeaponSkillHandlers.TEMPLAR_VOW,
                BuiltinWeaponSkillHandlers.INSECT_DASH,
                BuiltinWeaponSkillHandlers.OBSIDIAN_RESONANCE,
                BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT,
                BuiltinWeaponSkillHandlers.YETI_TOOTH_SPINE,
                BuiltinWeaponSkillHandlers.TEMPERED_BILLET,
                BuiltinWeaponSkillHandlers.OBSIDIAN_CRACK,
                BuiltinWeaponSkillHandlers.TIDE_MARK,
                BuiltinWeaponSkillHandlers.TIDE_ANCHOR,
                BuiltinWeaponSkillHandlers.SILVER_FOLDBACK,
                BuiltinWeaponSkillHandlers.CLAYMORE_FOLDBACK,
                BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_DEBT,
                BuiltinWeaponSkillHandlers.MEOWMERE_SHOT,
                BuiltinWeaponSkillHandlers.STEEL_FALCHION_TRACE,
                BuiltinWeaponSkillHandlers.MEOWMERE_SYMPHONY,
                BuiltinWeaponSkillHandlers.STEEL_FALCHION_LINE,
                BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_MOON,
                BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_STAB,
                BuiltinWeaponSkillHandlers.DWARF_RUNE_GUARD,
                BuiltinWeaponSkillHandlers.LAVA_KATANA_BRAND,
                BuiltinWeaponSkillHandlers.DRAGON_BREATH_THRUST,
                BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH,
                BuiltinWeaponSkillHandlers.LAVA_KATANA_REVERB,
                BuiltinWeaponSkillHandlers.DRAGON_BREATH_JUDGEMENT,
                BuiltinWeaponSkillHandlers.DWARF_FORTRESS,
                BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARSTAB,
                BuiltinWeaponSkillHandlers.GALAXY_DAGGER_STARLEAP,
                BuiltinWeaponSkillHandlers.GALAXY_JUDGEMENT,
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_STAB,
                BuiltinWeaponSkillHandlers.INFINITY_DAGGER_SINGULARITY_BACKSTAB,
                BuiltinWeaponSkillHandlers.SINGULARITY_EVOLVE,
                BuiltinWeaponSkillHandlers.STARTRAIL_RIFT,
                BuiltinWeaponSkillHandlers.ETERNAL_COLLAPSE,
                BuiltinWeaponSkillHandlers.FEMUR_SLAM,
                BuiltinWeaponSkillHandlers.FOREST_BLESSING
        )));

        WeaponData cutlass = WeaponRegistry.get("cutlass");
        assertNotNull(cutlass);
        WeaponSkillData skill = cutlass.getSkill1();
        assertNotNull(skill);
        assertEquals("crescent_slash", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(4.5, CrescentSlashSkillHandler.TARGET_RANGE);
        assertEquals(0.2, CrescentSlashSkillHandler.MINIMUM_DIRECTION_DOT);
        assertEquals(8, CrescentSlashSkillHandler.ANIMATION_TICKS);
        assertEquals(3, CrescentSlashSkillHandler.ACTIVE_TICK_OFFSET);
        assertEquals(5, CrescentSlashSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertFalse(new CrescentSlashSkillHandler().completesImmediately());

        WeaponData forestSword = WeaponRegistry.get("forest_sword");
        assertNotNull(forestSword);
        WeaponSkillData blessing = forestSword.getSkill1();
        assertNotNull(blessing);
        assertEquals("forest_blessing", blessing.getId());
        assertEquals(100, blessing.getDamagePercent());
        assertEquals(8, blessing.getCooldown());
        assertEquals(4.0, ForestBlessingSkillHandler.TARGET_RANGE);
        assertEquals(80, ForestBlessingSkillHandler.DURATION_TICKS);
        assertEquals(10, ForestBlessingSkillHandler.HEAL_INTERVAL_TICKS);
        assertEquals(2, ForestBlessingSkillHandler.HEAL_WITH_TARGET);
        assertEquals(1, ForestBlessingSkillHandler.HEAL_WITHOUT_TARGET);
        assertEquals(3, ForestBlessingSkillHandler.ACTIVE_TICK_OFFSET);
        assertFalse(new ForestBlessingSkillHandler().completesImmediately());

        WeaponData rustySword = WeaponRegistry.get("rusty_sword");
        assertNotNull(rustySword);
        WeaponSkillData tetanus = rustySword.getSkill1();
        assertNotNull(tetanus);
        assertEquals("tetanus_strike", tetanus.getId());
        assertEquals(100, tetanus.getDamagePercent());
        assertEquals(5, tetanus.getCooldown());
        assertEquals(4.0, TetanusStrikeSkillHandler.TARGET_RANGE);
        assertEquals(60, TetanusStrikeSkillHandler.VULNERABLE_DURATION_TICKS);
        assertEquals(0, TetanusStrikeSkillHandler.VULNERABLE_AMPLIFIER);
        assertEquals(8, TetanusStrikeSkillHandler.ANIMATION_TICKS);
        assertEquals(5, TetanusStrikeSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertTrue(new TetanusStrikeSkillHandler().completesImmediately());

        WeaponData woodenBlade = WeaponRegistry.get("wooden_blade");
        assertNotNull(woodenBlade);
        WeaponSkillData treeBlessing = woodenBlade.getSkill1();
        assertNotNull(treeBlessing);
        assertEquals("tree_blessing", treeBlessing.getId());
        assertEquals(110, treeBlessing.getDamagePercent());
        assertEquals(5, treeBlessing.getCooldown());
        assertEquals(4.0, TreeBlessingSkillHandler.TARGET_RANGE);
        assertEquals(40, TreeBlessingSkillHandler.SHELTER_DURATION_TICKS);
        assertEquals(1, TreeBlessingSkillHandler.SHELTER_AMPLIFIER);
        assertEquals(8, TreeBlessingSkillHandler.ANIMATION_TICKS);
        assertEquals(5, TreeBlessingSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertTrue(new TreeBlessingSkillHandler().completesImmediately());
        assertFalse(new LightCounterSkillHandler().completesImmediately());
        assertTrue(new DesperatePlunderSkillHandler().completesImmediately());
        assertTrue(new BoneFractureSkillHandler().completesImmediately());
        assertFalse(new SteelSpineFurySkillHandler().completesImmediately());
        assertFalse(new CarvingThrustSkillHandler().completesImmediately());
        assertTrue(new IronDirkThrustSkillHandler().completesImmediately());
        assertTrue(new WindSpireThrustSkillHandler().completesImmediately());
        assertFalse(new ElfBladeLeafSkillHandler().completesImmediately());
        assertTrue(new BurglarShankSkillHandler().completesImmediately());
        assertFalse(new FishcatchThrustSkillHandler().completesImmediately());
        assertTrue(new CrystalDaggerLayerSkillHandler().completesImmediately());
        assertTrue(new ShadowDaggerExecuteSkillHandler().completesImmediately());
        assertTrue(new TideReelSkillHandler().completesImmediately());
        assertFalse(new DwarfDaggerThrustSkillHandler().completesImmediately());
        assertTrue(new HolySmiteSkillHandler().completesImmediately());
        assertFalse(new DwarfDaggerRushSkillHandler().completesImmediately());
        assertFalse(new HolyDomainSkillHandler().completesImmediately());
        assertTrue(new WickedKrisVenomRippleSkillHandler().completesImmediately());
        assertTrue(new WickedKrisNestBurstSkillHandler().completesImmediately());
        assertFalse(new IridiumNeedleThrustSkillHandler().completesImmediately());
        assertTrue(new OssifiedMarkSkillHandler().completesImmediately());
        assertFalse(new OssifiedExecutionSkillHandler().completesImmediately());
        assertFalse(new InsectEyeStanceSkillHandler().completesImmediately());
        assertFalse(new IridiumNeedleFrenzySkillHandler().completesImmediately());
        assertFalse(new TemperedQuenchSkillHandler().completesImmediately());
        assertTrue(new YetiToothMarkSkillHandler().completesImmediately());
        assertFalse(new TemplarVowSkillHandler().completesImmediately());
        assertTrue(new InsectDashSkillHandler().completesImmediately());
        assertTrue(new ObsidianResonanceSkillHandler().completesImmediately());
        assertFalse(new TemplarJudgementSkillHandler().completesImmediately());
        assertFalse(new YetiToothSpineSkillHandler().completesImmediately());
        assertTrue(new TemperedBilletSkillHandler().completesImmediately());
        assertFalse(new ObsidianCrackSkillHandler().completesImmediately());
        assertTrue(new TideMarkSkillHandler().completesImmediately());
        assertFalse(new TideAnchorSkillHandler().completesImmediately());
        assertTrue(new SilverFoldbackSkillHandler().completesImmediately());
        assertFalse(new ClaymoreFoldbackSkillHandler().completesImmediately());
        assertFalse(new DarkSwordBloodDebtSkillHandler().completesImmediately());
        assertFalse(new MeowmereShotSkillHandler().completesImmediately());
        assertFalse(new SteelFalchionTraceSkillHandler().completesImmediately());
        assertFalse(new MeowmereSymphonySkillHandler().completesImmediately());
        assertFalse(new SteelFalchionLineSkillHandler().completesImmediately());
        assertFalse(new DarkSwordBloodMoonSkillHandler().completesImmediately());
        assertTrue(new DragontoothShivStabSkillHandler().completesImmediately());
        assertFalse(new DwarfRuneGuardSkillHandler().completesImmediately());
        assertTrue(new LavaKatanaBrandSkillHandler().completesImmediately());
        assertFalse(new DragonBreathThrustSkillHandler().completesImmediately());
        assertFalse(new DragontoothShivBreathSkillHandler().completesImmediately());
        assertFalse(new LavaKatanaReverbSkillHandler().completesImmediately());
        assertTrue(new DragonBreathJudgementSkillHandler().completesImmediately());
        assertFalse(new DwarfFortressSkillHandler().completesImmediately());
        assertFalse(new GalaxyDaggerStarstabSkillHandler().completesImmediately());
        assertTrue(new GalaxyDaggerStarleapSkillHandler().completesImmediately());
        assertFalse(new GalaxyJudgementSkillHandler().completesImmediately());
        assertFalse(new InfinityDaggerSingularityStabSkillHandler().completesImmediately());
        assertTrue(new InfinityDaggerSingularityBackstabSkillHandler().completesImmediately());
        assertFalse(new SingularityEvolveSkillHandler().completesImmediately());
        assertTrue(new StartrailRiftSkillHandler().completesImmediately());
        assertFalse(new EternalCollapseSkillHandler().completesImmediately());
        assertFalse(new FemurSlamSkillHandler().completesImmediately());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stardewcraft", path);
    }
}
