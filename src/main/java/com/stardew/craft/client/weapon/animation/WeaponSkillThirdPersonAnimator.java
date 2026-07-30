package com.stardew.craft.client.weapon.animation;

import com.stardew.craft.client.weapon.WeaponSkillAnimationClient;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Full-body third-person poses for authored weapon actions. The model is sampled
 * after vanilla locomotion, so the skill can add weight transfer without replacing
 * walking, crouching or head tracking.
 */
public final class WeaponSkillThirdPersonAnimator {
    private static final float ACTION_TICKS = 8.0f;

    private WeaponSkillThirdPersonAnimator() {}

    public static void apply(
            HumanoidModel<?> model,
            LivingEntity entity,
            float partialTick
    ) {
        float progress = WeaponSkillAnimationClient.getWorldActionProgress(
                entity.getId(),
                partialTick
        );
        if (progress < 0.0f) {
            return;
        }
        WeaponSkillAnimPayload action =
                WeaponSkillAnimationClient.getWorldAction(entity.getId());
        if (action == null) {
            return;
        }

        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        float tick = progress * ACTION_TICKS;
        ModelBaseline baseline = ModelBaseline.capture(model);
        BodyPose pose = switch (action.skillId()) {
            case "crescent_slash" -> sampleCrescent(baseline, rightHanded, tick);
            case "forest_blessing" -> sampleForest(baseline, rightHanded, tick);
            default -> null;
        };
        if (pose == null) {
            return;
        }
        pose.apply(model, baseline, rightHanded);
    }

    private static BodyPose sampleCrescent(
            ModelBaseline base,
            boolean rightHanded,
            float tick
    ) {
        float mirror = rightHanded ? 1.0f : -1.0f;
        BodyPose rest = BodyPose.rest(base, rightHanded);
        BodyPose wind = new BodyPose(
                new ArmPose(-1.78f, -0.78f * mirror, 0.68f * mirror),
                new ArmPose(-0.52f, 0.24f * mirror, -0.20f * mirror),
                0.04f, -0.24f * mirror,
                -0.12f, 0.045f * mirror,
                0.12f, -0.045f * mirror
        );
        BodyPose compressed = new BodyPose(
                new ArmPose(-1.90f, -0.88f * mirror, 0.76f * mirror),
                new ArmPose(-0.58f, 0.30f * mirror, -0.24f * mirror),
                0.055f, -0.28f * mirror,
                -0.15f, 0.055f * mirror,
                0.15f, -0.055f * mirror
        );
        BodyPose contact = new BodyPose(
                new ArmPose(-0.94f, 0.32f * mirror, -0.30f * mirror),
                new ArmPose(-0.78f, -0.20f * mirror, 0.18f * mirror),
                0.025f, 0.10f * mirror,
                0.08f, -0.025f * mirror,
                -0.08f, 0.025f * mirror
        );
        BodyPose follow = new BodyPose(
                new ArmPose(-0.58f, 0.92f * mirror, -0.82f * mirror),
                new ArmPose(-0.86f, -0.34f * mirror, 0.28f * mirror),
                0.035f, 0.27f * mirror,
                0.14f, -0.06f * mirror,
                -0.14f, 0.06f * mirror
        );
        BodyPose settle = new BodyPose(
                new ArmPose(-0.64f, 0.78f * mirror, -0.70f * mirror),
                new ArmPose(-0.78f, -0.26f * mirror, 0.22f * mirror),
                0.025f, 0.22f * mirror,
                0.10f, -0.045f * mirror,
                -0.10f, 0.045f * mirror
        );
        return samplePhases(tick, rest, wind, compressed, contact, follow, settle);
    }

    private static BodyPose sampleForest(
            ModelBaseline base,
            boolean rightHanded,
            float tick
    ) {
        float mirror = rightHanded ? 1.0f : -1.0f;
        BodyPose rest = BodyPose.rest(base, rightHanded);
        BodyPose gather = new BodyPose(
                new ArmPose(-1.42f, 0.50f * mirror, -0.56f * mirror),
                new ArmPose(-0.82f, -0.16f * mirror, -0.28f * mirror),
                0.10f, 0.14f * mirror,
                -0.16f, 0.035f * mirror,
                0.16f, -0.035f * mirror
        );
        BodyPose compressed = new BodyPose(
                new ArmPose(-1.55f, 0.58f * mirror, -0.64f * mirror),
                new ArmPose(-0.92f, -0.18f * mirror, -0.32f * mirror),
                0.13f, 0.18f * mirror,
                -0.20f, 0.045f * mirror,
                0.20f, -0.045f * mirror
        );
        BodyPose contact = new BodyPose(
                new ArmPose(-0.84f, -0.24f * mirror, 0.28f * mirror),
                new ArmPose(-0.96f, 0.10f * mirror, -0.18f * mirror),
                -0.04f, -0.08f * mirror,
                0.06f, -0.02f * mirror,
                -0.06f, 0.02f * mirror
        );
        BodyPose release = new BodyPose(
                new ArmPose(-1.18f, -0.58f * mirror, 0.52f * mirror),
                new ArmPose(-1.02f, 0.14f * mirror, -0.24f * mirror),
                -0.10f, -0.17f * mirror,
                0.14f, -0.04f * mirror,
                -0.14f, 0.04f * mirror
        );
        BodyPose settle = new BodyPose(
                new ArmPose(-1.12f, -0.48f * mirror, 0.44f * mirror),
                new ArmPose(-0.94f, 0.10f * mirror, -0.20f * mirror),
                -0.07f, -0.13f * mirror,
                0.10f, -0.03f * mirror,
                -0.10f, 0.03f * mirror
        );
        return samplePhases(tick, rest, gather, compressed, contact, release, settle);
    }

    private static BodyPose samplePhases(
            float tick,
            BodyPose rest,
            BodyPose wind,
            BodyPose compressed,
            BodyPose contact,
            BodyPose follow,
            BodyPose settle
    ) {
        if (tick <= 1.25f) {
            return BodyPose.lerp(rest, wind, easeOutCubic(tick / 1.25f));
        }
        if (tick <= 2.0f) {
            return BodyPose.lerp(
                    wind,
                    compressed,
                    smooth((tick - 1.25f) / 0.75f)
            );
        }
        if (tick <= 3.0f) {
            return BodyPose.lerp(
                    compressed,
                    contact,
                    easeInQuad(tick - 2.0f)
            );
        }
        if (tick <= 4.25f) {
            return BodyPose.lerp(
                    contact,
                    follow,
                    easeOutCubic((tick - 3.0f) / 1.25f)
            );
        }
        if (tick <= 5.0f) {
            return BodyPose.lerp(
                    follow,
                    settle,
                    smooth((tick - 4.25f) / 0.75f)
            );
        }
        return BodyPose.lerp(settle, rest, smooth((tick - 5.0f) / 3.0f));
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float easeInQuad(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t;
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inverse = 1.0f - t;
        return 1.0f - inverse * inverse * inverse;
    }

    private record ArmPose(float x, float y, float z) {
        private static ArmPose capture(ModelPart part) {
            return new ArmPose(part.xRot, part.yRot, part.zRot);
        }

        private static ArmPose lerp(ArmPose from, ArmPose to, float amount) {
            return new ArmPose(
                    Mth.lerp(amount, from.x, to.x),
                    Mth.lerp(amount, from.y, to.y),
                    Mth.lerp(amount, from.z, to.z)
            );
        }

        private void apply(ModelPart part) {
            part.xRot = x;
            part.yRot = y;
            part.zRot = z;
        }
    }

    private record BodyPose(
            ArmPose weaponArm,
            ArmPose balanceArm,
            float bodyX,
            float bodyY,
            float weaponLegX,
            float weaponLegZ,
            float balanceLegX,
            float balanceLegZ
    ) {
        private static BodyPose rest(ModelBaseline baseline, boolean rightHanded) {
            return new BodyPose(
                    rightHanded ? baseline.rightArm : baseline.leftArm,
                    rightHanded ? baseline.leftArm : baseline.rightArm,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f
            );
        }

        private static BodyPose lerp(BodyPose from, BodyPose to, float amount) {
            return new BodyPose(
                    ArmPose.lerp(from.weaponArm, to.weaponArm, amount),
                    ArmPose.lerp(from.balanceArm, to.balanceArm, amount),
                    Mth.lerp(amount, from.bodyX, to.bodyX),
                    Mth.lerp(amount, from.bodyY, to.bodyY),
                    Mth.lerp(amount, from.weaponLegX, to.weaponLegX),
                    Mth.lerp(amount, from.weaponLegZ, to.weaponLegZ),
                    Mth.lerp(amount, from.balanceLegX, to.balanceLegX),
                    Mth.lerp(amount, from.balanceLegZ, to.balanceLegZ)
            );
        }

        private void apply(
                HumanoidModel<?> model,
                ModelBaseline base,
                boolean rightHanded
        ) {
            ModelPart mainArm = rightHanded ? model.rightArm : model.leftArm;
            ModelPart offArm = rightHanded ? model.leftArm : model.rightArm;
            ModelPart mainLeg = rightHanded ? model.rightLeg : model.leftLeg;
            ModelPart offLeg = rightHanded ? model.leftLeg : model.rightLeg;
            float baseMainLegX = rightHanded ? base.rightLegX : base.leftLegX;
            float baseMainLegZ = rightHanded ? base.rightLegZ : base.leftLegZ;
            float baseOffLegX = rightHanded ? base.leftLegX : base.rightLegX;
            float baseOffLegZ = rightHanded ? base.leftLegZ : base.rightLegZ;

            weaponArm.apply(mainArm);
            balanceArm.apply(offArm);
            model.body.xRot = base.bodyX + bodyX;
            model.body.yRot = base.bodyY + bodyY;
            model.head.yRot = base.headY - bodyY * 0.55f;
            model.hat.copyFrom(model.head);

            mainLeg.xRot = baseMainLegX + weaponLegX;
            mainLeg.zRot = baseMainLegZ + weaponLegZ;
            offLeg.xRot = baseOffLegX + balanceLegX;
            offLeg.zRot = baseOffLegZ + balanceLegZ;
        }
    }

    private record ModelBaseline(
            ArmPose rightArm,
            ArmPose leftArm,
            float bodyX,
            float bodyY,
            float headY,
            float rightLegX,
            float rightLegZ,
            float leftLegX,
            float leftLegZ
    ) {
        private static ModelBaseline capture(HumanoidModel<?> model) {
            return new ModelBaseline(
                    ArmPose.capture(model.rightArm),
                    ArmPose.capture(model.leftArm),
                    model.body.xRot,
                    model.body.yRot,
                    model.head.yRot,
                    model.rightLeg.xRot,
                    model.rightLeg.zRot,
                    model.leftLeg.xRot,
                    model.leftLeg.zRot
            );
        }
    }
}
