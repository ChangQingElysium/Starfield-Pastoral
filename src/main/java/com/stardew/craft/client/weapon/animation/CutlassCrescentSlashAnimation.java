package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

public final class CutlassCrescentSlashAnimation implements WeaponSkillAnimation {

    private static final WeaponSkillPose BASE_RIGHT = new WeaponSkillPose(0f, -90f, 25f, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP_PIVOT = new Vector3f(0.0f, -6.0f / 16.0f, 0.0f);
    private static final WeaponSkillPose WIND_UP =
            new WeaponSkillPose(-82f, -26f, -36f, 4.25f, 3.55f, 0.40f);
    private static final WeaponSkillPose COMPRESSED =
            new WeaponSkillPose(-88f, -18f, -22f, 4.85f, 3.15f, -1.10f);
    private static final WeaponSkillPose CONTACT =
            new WeaponSkillPose(-84f, -4f, 48f, -1.60f, 2.55f, -3.25f);
    private static final WeaponSkillPose FOLLOW_THROUGH =
            new WeaponSkillPose(-76f, 14f, 104f, -7.80f, 3.05f, -1.20f);
    private static final WeaponSkillPose SETTLE =
            new WeaponSkillPose(-78f, 12f, 92f, -6.90f, 3.20f, -0.70f);
    private static final WeaponSkillKeyframeTimeline TIMELINE =
            new WeaponSkillKeyframeTimeline(
                    8.0f,
                    BASE_RIGHT,
                    GRIP_PIVOT,
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            0.0f,
                            BASE_RIGHT,
                            WeaponSkillKeyframeTimeline.Easing.LINEAR
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            1.25f,
                            WIND_UP,
                            WeaponSkillKeyframeTimeline.Easing.EASE_OUT_CUBIC
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            2.0f,
                            COMPRESSED,
                            WeaponSkillKeyframeTimeline.Easing.SMOOTH
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            3.0f,
                            CONTACT,
                            WeaponSkillKeyframeTimeline.Easing.EASE_IN_QUAD
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            4.25f,
                            FOLLOW_THROUGH,
                            WeaponSkillKeyframeTimeline.Easing.EASE_OUT_CUBIC
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            5.0f,
                            SETTLE,
                            WeaponSkillKeyframeTimeline.Easing.SMOOTH
                    ),
                    new WeaponSkillKeyframeTimeline.Keyframe(
                            8.0f,
                            BASE_RIGHT,
                            WeaponSkillKeyframeTimeline.Easing.SMOOTH
                    )
            );

    @Override
    public boolean apply(PoseStack poseStack, HumanoidArm arm, float progress) {
        return TIMELINE.apply(poseStack, arm, progress);
    }

    static WeaponSkillPose sampleRightAtTick(float tick) {
        return TIMELINE.sampleRight(tick / 8.0f);
    }
}
