package com.stardew.craft.client.weapon.trail;

import com.stardew.craft.client.weapon.WeaponSkillAnimationClient;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bridges the final item model transform to the shared trail runtime.
 */
public final class WeaponRenderCaptureContext {
    private static final ThreadLocal<ArrayDeque<Capture>> CAPTURES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Vector3f BLADE_BASE = new Vector3f(-0.14f, -0.14f, 0.0f);
    private static final Vector3f BLADE_TIP = new Vector3f(0.36f, 0.40f, 0.0f);

    private WeaponRenderCaptureContext() {}

    public static void begin(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {
        CAPTURES.get().addLast(new Capture(entity, stack, displayContext));
    }

    public static void end() {
        ArrayDeque<Capture> captures = CAPTURES.get();
        if (!captures.isEmpty()) {
            captures.removeLast();
        }
        if (captures.isEmpty()) {
            CAPTURES.remove();
        }
    }

    public static void capture(Matrix4f itemTransform) {
        ArrayDeque<Capture> captures = CAPTURES.get();
        Capture capture = captures.peekLast();
        if (capture == null
                || !(capture.stack.getItem() instanceof IStardewWeapon weapon)
                || !isHeldContext(capture.displayContext)) {
            return;
        }

        WeaponSkillAnimPayload action =
                WeaponSkillAnimationClient.getWorldAction(capture.entity.getId());
        if (action == null
                || !action.weaponId().equals(weapon.getWeaponId())
                || !WeaponTrailClient.supports(action.skillId())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float progress = WeaponSkillAnimationClient.getWorldActionProgress(
                capture.entity.getId(),
                partialTick
        );
        if (progress < 0.0f) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 base = transform(itemTransform, BLADE_BASE).add(camera);
        Vec3 tip = transform(itemTransform, BLADE_TIP).add(camera);
        WeaponTrailClient.capture(
                capture.entity.getId(),
                action.skillId(),
                action.startGameTick(),
                progress,
                base,
                tip,
                minecraft.level.getGameTime() + partialTick
        );
    }

    private static boolean isHeldContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static Vec3 transform(Matrix4f matrix, Vector3f point) {
        Vector3f transformed = new Vector3f(point);
        matrix.transformPosition(transformed);
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private record Capture(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {}
}
