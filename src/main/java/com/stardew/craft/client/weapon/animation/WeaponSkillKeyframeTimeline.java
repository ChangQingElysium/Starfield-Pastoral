package com.stardew.craft.client.weapon.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * Tick-authored held-item animation. Keyframes describe the right hand and are
 * mirrored for the left hand so the action, hit frame and trail all share one
 * deterministic timeline.
 */
final class WeaponSkillKeyframeTimeline {
    private final float totalTicks;
    private final WeaponSkillPose baseRight;
    private final Vector3f pivot;
    private final Keyframe[] keyframes;

    WeaponSkillKeyframeTimeline(
            float totalTicks,
            WeaponSkillPose baseRight,
            Vector3f pivot,
            Keyframe... keyframes
    ) {
        if (totalTicks <= 0.0f) {
            throw new IllegalArgumentException("totalTicks must be positive");
        }
        if (keyframes.length < 2) {
            throw new IllegalArgumentException("at least two keyframes are required");
        }
        this.totalTicks = totalTicks;
        this.baseRight = baseRight;
        this.pivot = new Vector3f(pivot);
        this.keyframes = Arrays.copyOf(keyframes, keyframes.length);
        validateKeyframes();
    }

    boolean apply(PoseStack poseStack, HumanoidArm arm, float progress) {
        WeaponSkillPose targetRight = sampleRight(progress);
        WeaponSkillPose base = arm == HumanoidArm.RIGHT
                ? baseRight
                : WeaponSkillPose.mirrorRightToLeft(baseRight);
        WeaponSkillPose target = arm == HumanoidArm.RIGHT
                ? targetRight
                : WeaponSkillPose.mirrorRightToLeft(targetRight);
        WeaponSkillAnimationMath.applyDeltaFromBaseDisplayWithPivot(
                poseStack,
                mirroredPivot(arm),
                base,
                target
        );
        return true;
    }

    WeaponSkillPose sampleRight(float progress) {
        float tick = Mth.clamp(progress, 0.0f, 1.0f) * totalTicks;
        if (tick <= keyframes[0].tick()) {
            return keyframes[0].poseRight();
        }
        if (tick >= keyframes[keyframes.length - 1].tick()) {
            return keyframes[keyframes.length - 1].poseRight();
        }
        Keyframe from = keyframes[0];
        Keyframe to = keyframes[keyframes.length - 1];
        for (int index = 1; index < keyframes.length; index++) {
            to = keyframes[index];
            if (tick <= to.tick()) {
                break;
            }
            from = to;
        }

        float span = Math.max(1.0E-4f, to.tick() - from.tick());
        float amount = to.easing().apply((tick - from.tick()) / span);
        return WeaponSkillPose.lerp(
                from.poseRight(),
                to.poseRight(),
                amount
        );
    }

    private Vector3f mirroredPivot(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT
                ? new Vector3f(pivot)
                : new Vector3f(-pivot.x, pivot.y, pivot.z);
    }

    private void validateKeyframes() {
        float previous = -1.0f;
        for (Keyframe keyframe : keyframes) {
            if (keyframe.tick() < previous
                    || keyframe.tick() < 0.0f
                    || keyframe.tick() > totalTicks) {
                throw new IllegalArgumentException("keyframes must be ordered inside the timeline");
            }
            previous = keyframe.tick();
        }
        if (keyframes[0].tick() != 0.0f
                || keyframes[keyframes.length - 1].tick() != totalTicks) {
            throw new IllegalArgumentException("timeline must start at 0 and end at totalTicks");
        }
    }

    record Keyframe(float tick, WeaponSkillPose poseRight, Easing easing) {
    }

    enum Easing {
        LINEAR {
            @Override
            float apply(float value) {
                return clamp(value);
            }
        },
        SMOOTH {
            @Override
            float apply(float value) {
                float t = clamp(value);
                return t * t * (3.0f - 2.0f * t);
            }
        },
        EASE_IN_QUAD {
            @Override
            float apply(float value) {
                float t = clamp(value);
                return t * t;
            }
        },
        EASE_OUT_CUBIC {
            @Override
            float apply(float value) {
                float t = clamp(value);
                float inverse = 1.0f - t;
                return 1.0f - inverse * inverse * inverse;
            }
        };

        abstract float apply(float value);

        static float clamp(float value) {
            return Mth.clamp(value, 0.0f, 1.0f);
        }
    }
}
