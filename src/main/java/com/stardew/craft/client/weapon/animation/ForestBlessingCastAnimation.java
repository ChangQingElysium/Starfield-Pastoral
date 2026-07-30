package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * A grounded rising cut. The blessing is released by the completed strike
 * rather than by a disconnected casting pose.
 */
public final class ForestBlessingCastAnimation implements WeaponSkillAnimation {
    private static final WeaponSkillPose BASE_RIGHT = new WeaponSkillPose(0f, -90f, 25f, 1.13f, 3.2f, 1.13f);
    private static final Vector3f GRIP_PIVOT = new Vector3f(0.0f, -6.0f / 16.0f, 0.0f);
    private static final WeaponSkillPose GATHER =
            new WeaponSkillPose(-104f, -34f, 36f, 4.10f, 4.45f, 0.85f);
    private static final WeaponSkillPose COMPRESSED =
            new WeaponSkillPose(-112f, -28f, 28f, 4.55f, 4.75f, -0.45f);
    private static final WeaponSkillPose CONTACT =
            new WeaponSkillPose(-72f, -5f, -38f, 0.65f, 2.15f, -3.75f);
    private static final WeaponSkillPose RELEASE =
            new WeaponSkillPose(-49f, -17f, -70f, -3.35f, 1.35f, -1.30f);
    private static final WeaponSkillPose SETTLE =
            new WeaponSkillPose(-55f, -18f, -62f, -2.85f, 1.55f, -0.70f);
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
                            GATHER,
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
                            RELEASE,
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
