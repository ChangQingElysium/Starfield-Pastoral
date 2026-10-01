package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.weapon.MeleeWeaponVisuals;
import com.stardew.craft.combat.WeaponMeleeProfile;
import com.stardew.craft.item.weapon.IStardewWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.animation.WeaponSkillKeyframeTimeline.Easing.*;

/** Item and body samples share action clocks, including each server-authored thrust. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("removal")
public final class MeleeWeaponAnimations {
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillPose READY = new WeaponSkillPose(-102, -78, -12, 2.4f, 2.7f, 0.4f);
    // Keep the bone upright beside the shoulder; a large backward rotation reads as an inverted grip in first person.
    private static final WeaponSkillPose RAISED = new WeaponSkillPose(-35, -78, -8, 1.4f, 4.8f, -0.8f);
    private static final WeaponSkillPose DOWN = new WeaponSkillPose(-12, -65, 48, -2.6f, -1.8f, -5.6f);
    private static final Vector3f GRIP = new Vector3f(0, -0.375f, 0);
    private static final WeaponSkillKeyframeTimeline THRUST = new WeaponSkillKeyframeTimeline(3, REST, GRIP,
            key(0, REST, LINEAR), key(0.18f, READY, EASE_OUT_CUBIC),
            key(0.55f, new WeaponSkillPose(-110, -87, 6, 0.8f, 2.1f, -9.4f), EASE_IN_QUAD),
            key(1.1f, new WeaponSkillPose(-106, -82, 13, 1.7f, 2.5f, -6.3f), EASE_OUT_CUBIC),
            key(2.1f, READY, EASE_OUT_CUBIC), key(3, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline CLUB = new WeaponSkillKeyframeTimeline(6, REST, GRIP,
            key(0, REST, LINEAR),
            key(0.35f, new WeaponSkillPose(-121, -25, -48, 4.0f, 5.2f, 1.0f), SMOOTH),
            key(1.0f, DOWN, EASE_IN_QUAD), key(1.7f, DOWN, LINEAR),
            key(3.1f, new WeaponSkillPose(-43, -70, 36, -0.3f, 0.7f, -2.6f), EASE_OUT_CUBIC),
            key(6, REST, SMOOTH));
    private static final WeaponSkillKeyframeTimeline SLAM = new WeaponSkillKeyframeTimeline(8, REST, GRIP,
            key(0, RAISED, LINEAR), key(0.65f, DOWN, EASE_IN_QUAD),
            key(1.3f, DOWN, LINEAR),
            key(2.8f, new WeaponSkillPose(-67, -54, 19, 1.6f, 1.7f, -1.6f), EASE_OUT_CUBIC),
            key(4.0f, new WeaponSkillPose(-32, -74, 33, 0.0f, 1.0f, -2.0f), SMOOTH),
            key(8, REST, SMOOTH));

    private MeleeWeaponAnimations() {}

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(PoseStack stack, LocalPlayer player, HumanoidArm arm,
                                                   ItemStack item, float partialTick, float equip, float swing) {
                if (arm != player.getMainArm() || item.getItem() != player.getMainHandItem().getItem()) return false;
                var action = MeleeWeaponVisuals.action(player, partialTick);
                if (action == null) return false;
                stack.translate(arm == HumanoidArm.RIGHT ? 0.56f : -0.56f, -0.52f - equip * 0.6f, -0.72f);
                WeaponSkillPose pose = sample(action.skillId(), action.progress());
                WeaponSkillAnimationMath.applyDeltaFromBaseDisplayWithPivot(stack, GRIP,
                        arm == HumanoidArm.RIGHT ? REST : WeaponSkillPose.mirrorRightToLeft(REST),
                        arm == HumanoidArm.RIGHT ? pose : WeaponSkillPose.mirrorRightToLeft(pose));
                return true;
            }
        }, BuiltInRegistries.ITEM.stream().filter(item -> item instanceof IStardewWeapon weapon
                && WeaponMeleeProfile.get(weapon.getWeaponId()) != null
                && !"lava_katana".equals(weapon.getWeaponId()) && !event.isItemRegistered(item)).toArray(Item[]::new));
    }

    static WeaponSkillPose sample(String skill, float progress) {
        float t = Mth.clamp(progress, 0, 1);
        if (DragonRapierAnimation.supports(skill)) return DragonRapierAnimation.sample(skill, t);
        if (SlammerDwarfAnimation.supports(skill)) return SlammerDwarfAnimation.sample(skill, t);
        if (IronClubAnimation.supports(skill)) return IronClubAnimation.sample(skill, t);
        if (WoodWeaponAnimation.supports(skill)) return WoodWeaponAnimation.sample(skill, t);
        if (HeavyHammerAnimation.supports(skill)) return HeavyHammerAnimation.sample(skill, t);
        return switch (skill) {
            case CUTLASS_SWING,CRESCENT_SLASH,FALCHION_SWING,FALCHION_LINE,FALCHION_TRACE -> CrescentFalchionAnimation.sample(skill,t);
            case RUST_SWING,RUST_STRIKE,WOOD_SWING,WOOD_BLESS -> RustWoodAnimation.sample(skill,t);
            case LIGHT_SWING,LIGHT_GUARD,LIGHT_COUNTER,SPINE_SWING,SPINE_ENTER,SPINE_STRIKE,SPINE_WEAK -> GuardSpineAnimation.sample(skill,t);
            case PIRATE_SWING, PIRATE_PLUNDER, SILVER_SWING, SILVER_OUT, SILVER_RETURN, SILVER_STAY, SILVER_EMPTY -> PirateSilverAnimation.sample(skill, t);
            case IRON_SWING, IRON_THRUST, WIND_SWING, WIND_THRUST -> IronWindAnimation.sample(skill, t);
            case BONE_SWORD_SWING, BONE_FRACTURE, CLAYMORE_SWING, CLAYMORE_OUT, CLAYMORE_RETURN -> BoneClaymoreAnimation.sample(skill, t);
            case DWARF_SWORD_SWING, DWARF_DAGGER_SWING, DWARF_GUARD, DWARF_FORTRESS, DWARF_THRUST, DWARF_RUSH -> DwarfWeaponAnimation.sample(skill, t);
            case NEEDLE_SWING, NEEDLE_READY, NEEDLE_STRIKE, NEEDLE_FINAL, NEEDLE_FRENZY, BURGLAR_SWING, BURGLAR_STRIKE -> NeedleBurglarAnimation.sample(skill, t);
            case SHADOW_SWING, SHADOW_EXECUTE, INSECT_SWING, INSECT_STANCE, INSECT_DASH -> ShadowInsectAnimation.sample(skill, t);
            case CRYSTAL_SWING, CRYSTAL_LAYER, VENOM_SWING, VENOM_RIPPLE, VENOM_NEST -> CrystalVenomAnimation.sample(skill,t);
            case DARK_SWING, DARK_DEBT, DARK_MOON, FORGE_SWING, FORGE_QUENCH, FORGE_BILLET -> BloodForgeAnimation.sample(skill, t);
            case OBSIDIAN_SWING, OSSIFIED_SWING, OBSIDIAN_CRACK, OSSIFIED_MARK, OSSIFIED_EXECUTION -> MineralWeaponAnimation.sample(skill, t);
            case HOLY_SWING, TEMPLAR_SWING, HOLY_SMITE, HOLY_DOMAIN, TEMPLAR_VOW, TEMPLAR_STRIKE, TEMPLAR_END, TEMPLAR_JUDGEMENT -> SacredWeaponAnimation.sample(skill, t);
            case SHIV_SWING, SHIV_EMPOWERED, SHIV_STAB, SHIV_BREATH -> DragontoothShivAnimation.sample(skill, t);
            case GALAXY_RIFT, GALAXY_JUDGEMENT, GALAXY_READY, GALAXY_STAB, GALAXY_LEAP -> GalaxyWeaponAnimation.sample(skill, t);
            case FOREST_READY, FOREST_RELEASE, ELF_CAST, ELF_SWING -> GroveWeaponAnimation.sample(skill, t);
            case TIDE_MARK, TIDE_ANCHOR, TIDE_READY, TIDE_STAB, TIDE_REEL -> TideWeaponAnimation.sample(skill, t);
            case INFINITY_EVOLVE, INFINITY_RELEASE, INFINITY_COLLAPSE, INFINITY_READY, INFINITY_STAB, INFINITY_BACK -> InfinityWeaponAnimation.sample(skill, t);
            case YETI_MARK, YETI_SPINE -> YetiToothAnimation.sample(skill, t);
            case DRAGON_PIERCE, DRAGON_JUDGEMENT -> DragonCutlassAnimation.sample(skill, t);
            case MEOW_SHOT, MEOW_FAN -> MeowmereAnimation.sample(MEOW_FAN.equals(skill), t);
            case MEOW_SWING, SWORD_SWING -> LavaKatanaSlashAnimation.sample(t);
            case FEMUR_CHARGE -> {
                WeaponSkillPose raised = WeaponSkillPose.lerp(REST, RAISED, smooth(t / 0.55f));
                float shake = Mth.sin(t * 42) * t * t;
                yield new WeaponSkillPose(raised.rx(), raised.ry() + shake * 1.1f,
                        raised.rz() + shake * 1.7f, raised.tx(), raised.ty(), raised.tz());
            }
            case FEMUR_SLAM -> SLAM.sampleRight(t);
            case FEMUR_SWING -> CLUB.sampleRight(t);
            // Preparing the sequence does not invent three future strikes.
            case CARVING_READY -> WeaponSkillPose.lerp(REST, READY,
                    smooth(t / 0.05f) * (1 - smooth((t - 0.1f) / 0.2f)));
            default -> THRUST.sampleRight(t);
        };
    }

    public static boolean applyThirdPerson(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
        var action = MeleeWeaponVisuals.action(entity, partialTick);
        if (action == null) return false;
        if (DragonRapierAnimation.supports(action.skillId())) {DragonRapierAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (SlammerDwarfAnimation.supports(action.skillId())) {SlammerDwarfAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (IronClubAnimation.supports(action.skillId())) {IronClubAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (WoodWeaponAnimation.supports(action.skillId())) {WoodWeaponAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (HeavyHammerAnimation.supports(action.skillId())) {HeavyHammerAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (CrescentFalchionAnimation.supports(action.skillId())) {CrescentFalchionAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (RustWoodAnimation.supports(action.skillId())) {RustWoodAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (GuardSpineAnimation.supports(action.skillId())) {GuardSpineAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (PirateSilverAnimation.supports(action.skillId())) {
            PirateSilverAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (IronWindAnimation.supports(action.skillId())) {
            IronWindAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (BoneClaymoreAnimation.supports(action.skillId())) {
            BoneClaymoreAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (DwarfWeaponAnimation.supports(action.skillId())) {
            DwarfWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (NeedleBurglarAnimation.supports(action.skillId())) {
            NeedleBurglarAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (ShadowInsectAnimation.supports(action.skillId())) {
            ShadowInsectAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (CrystalVenomAnimation.supports(action.skillId())) {CrystalVenomAnimation.applyBodyPose(model,entity,action.skillId(),action.progress());return true;}
        if (BloodForgeAnimation.supports(action.skillId())) {
            BloodForgeAnimation.applyBodyPose(model, entity, action.skillId(), action.progress()); return true;
        }
        if (MineralWeaponAnimation.supports(action.skillId())) {
            MineralWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (com.stardew.craft.client.weapon.SacredWeaponVisuals.isAction(action.skillId())) {
            SacredWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (com.stardew.craft.client.weapon.GroveWeaponVisuals.isAction(action.skillId())) {
            GroveWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (com.stardew.craft.client.weapon.TideWeaponVisuals.isAction(action.skillId())) {
            TideWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (DragontoothShivAnimation.supports(action.skillId())) {
            DragontoothShivAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (com.stardew.craft.client.weapon.GalaxyWeaponVisuals.isAction(action.skillId())) {
            GalaxyWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (com.stardew.craft.client.weapon.InfinityWeaponVisuals.isAction(action.skillId())) {
            InfinityWeaponAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (YETI_MARK.equals(action.skillId()) || YETI_SPINE.equals(action.skillId())) {
            YetiToothAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (DRAGON_PIERCE.equals(action.skillId()) || DRAGON_JUDGEMENT.equals(action.skillId())) {
            DragonCutlassAnimation.applyBodyPose(model, entity, action.skillId(), action.progress());
            return true;
        }
        if (MEOW_SHOT.equals(action.skillId()) || MEOW_FAN.equals(action.skillId())) {
            MeowmereAnimation.applyBodyPose(model, entity, action.progress());
            return true;
        }
        if (MEOW_SWING.equals(action.skillId()) || SWORD_SWING.equals(action.skillId())) {
            LavaKatanaSlashAnimation.applyBodyPose(model, entity, action.progress());
            return true;
        }
        float t = action.progress();
        boolean bone = action.skillId().startsWith("femur_");
        boolean charge = FEMUR_CHARGE.equals(action.skillId());
        boolean slam = FEMUR_SLAM.equals(action.skillId());
        float weight = charge ? smooth(t / 0.45f)
                : (slam ? 1 : smooth(t / 0.06f)) * (1 - smooth((t - 0.48f) / 0.52f));
        float hit = charge ? 0 : smooth(t / (bone ? 0.17f : 0.19f));
        float mirror = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        var main = mirror > 0 ? model.rightArm : model.leftArm;
        var off = mirror > 0 ? model.leftArm : model.rightArm;
        main.xRot = Mth.lerp(weight, main.xRot, bone ? Mth.lerp(hit, -2.8f, -0.38f) : -1.55f);
        main.yRot = Mth.lerp(weight, main.yRot, mirror * (bone ? -0.18f : -0.07f));
        main.zRot = Mth.lerp(weight, main.zRot, mirror * (bone ? -0.28f : 0.08f));
        if (!bone) {
            main.z -= weight * hit * 2.8f;
            off.xRot = Mth.lerp(weight * 0.6f, off.xRot, -0.8f);
            model.body.yRot += mirror * weight * 0.12f;
        } else {
            off.xRot = Mth.lerp(weight * 0.85f, off.xRot, Mth.lerp(hit, -2.3f, -0.55f));
            off.yRot += mirror * weight * 0.32f;
            model.body.xRot += weight * Mth.lerp(hit, -0.09f, 0.26f);
            model.rightLeg.xRot += weight * 0.16f;
            model.leftLeg.xRot -= weight * 0.16f;
        }
        return true;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static WeaponSkillKeyframeTimeline.Keyframe key(float tick, WeaponSkillPose pose,
                                                            WeaponSkillKeyframeTimeline.Easing easing) {
        return new WeaponSkillKeyframeTimeline.Keyframe(tick, pose, easing);
    }
}
