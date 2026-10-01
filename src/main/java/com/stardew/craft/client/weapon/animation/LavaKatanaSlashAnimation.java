package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.weapon.LavaKatanaVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Fast diagonal cut followed by a weighted wrist recovery; the existing hit remains immediate. */
public final class LavaKatanaSlashAnimation implements WeaponSkillAnimation {
    public static final LavaKatanaSlashAnimation INSTANCE = new LavaKatanaSlashAnimation();
    private static final WeaponSkillPose REST = new WeaponSkillPose(0, -90, 25, 1.13f, 3.2f, 1.13f);
    private static final WeaponSkillKeyframeTimeline TIMELINE = new WeaponSkillKeyframeTimeline(
            6, REST, new Vector3f(0, -0.375f, 0),
            key(0, REST, WeaponSkillKeyframeTimeline.Easing.LINEAR),
            key(0.35f, new WeaponSkillPose(-72, -30, -45, 4.0f, 4.1f, -0.5f),
                    WeaponSkillKeyframeTimeline.Easing.EASE_OUT_CUBIC),
            key(1.1f, new WeaponSkillPose(-88, -8, 48, -2.6f, 1.8f, -4.8f),
                    WeaponSkillKeyframeTimeline.Easing.EASE_IN_QUAD),
            key(2.2f, new WeaponSkillPose(-69, 18, 116, -8.0f, 0.9f, -1.4f),
                    WeaponSkillKeyframeTimeline.Easing.EASE_OUT_CUBIC),
            key(3.3f, new WeaponSkillPose(-62, 12, 96, -6.2f, 1.7f, -0.1f),
                    WeaponSkillKeyframeTimeline.Easing.SMOOTH),
            key(6, REST, WeaponSkillKeyframeTimeline.Easing.SMOOTH));

    private static WeaponSkillKeyframeTimeline.Keyframe key(
            float tick, WeaponSkillPose pose, WeaponSkillKeyframeTimeline.Easing easing) {
        return new WeaponSkillKeyframeTimeline.Keyframe(tick, pose, easing);
    }

    @Override
    public boolean apply(PoseStack stack, HumanoidArm arm, float progress) {
        var minecraft = Minecraft.getInstance();
        var action = minecraft.player == null ? null : LavaKatanaVisuals.action(
                minecraft.player, com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
        if (action != null && LavaKatanaVisuals.REVERB.equals(action.skillId())) {
            return LavaKatanaReverbAnimation.INSTANCE.apply(stack, arm, action.progress());
        }
        return TIMELINE.apply(stack, arm, action == null ? progress : action.progress());
    }

    public static boolean applyThirdPerson(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
        var action = LavaKatanaVisuals.action(entity, partialTick);
        if (action == null) {
            return false;
        }
        if (LavaKatanaVisuals.REVERB.equals(action.skillId())) {
            LavaKatanaReverbAnimation.applyBodyPose(model, entity, action.progress());
        } else {
            applyBodyPose(model, entity, action.progress());
        }
        return true;
    }

    static void applyBodyPose(HumanoidModel<?> model, LivingEntity entity, float t) {
        float weight = Mth.clamp(t / 0.06f, 0, 1) * (1 - smooth((t - 0.57f) / 0.43f));
        float cut = smooth((t - 0.06f) / 0.30f);
        float mirror = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        ModelPart main = mirror > 0 ? model.rightArm : model.leftArm;
        ModelPart off = mirror > 0 ? model.leftArm : model.rightArm;
        main.xRot = Mth.lerp(weight, main.xRot, Mth.lerp(cut, -1.9f, -0.62f));
        main.yRot = Mth.lerp(weight, main.yRot, Mth.lerp(cut, -0.72f, 0.92f) * mirror);
        main.zRot = Mth.lerp(weight, main.zRot, Mth.lerp(cut, 0.65f, -0.95f) * mirror);
        off.xRot = Mth.lerp(weight * 0.7f, off.xRot, -0.65f);
        off.zRot += weight * 0.22f * mirror;
        float twist = Mth.lerp(cut, -0.28f, 0.32f) * weight * mirror;
        model.body.yRot += twist;
        model.body.xRot += weight * 0.065f;
        model.head.yRot -= twist * 0.55f;
        model.hat.copyFrom(model.head);
        model.rightLeg.xRot += weight * 0.12f * mirror;
        model.leftLeg.xRot -= weight * 0.12f * mirror;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static WeaponSkillPose sample(float progress) {
        return TIMELINE.sampleRight(progress);
    }
}
