package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

public final class DragontoothShivVisuals {
    private DragontoothShivVisuals() {}
    public static void start(WeaponSkillAnimPayload payload) {
        var mc = Minecraft.getInstance();
        Vec3 p = new Vec3(payload.originX(), payload.originY() + 1, payload.originZ());
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(p) > 32 * 32) return;
        boolean ignite = MeleeWeaponVisuals.SHIV_BREATH.equals(payload.skillId());
        mc.level.playLocalSound(p.x, p.y, p.z, ignite ? SoundEvents.FIRECHARGE_USE : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, ignite ? 0.45f : 0.35f, ignite ? 1.3f : 1.6f, false);
        if (ignite) mc.level.playLocalSound(p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.25f, 1.3f, false);
    }

    /** Uses the captured sprite anchors and item matrix in both views, including idle stance. */
    public static void blade(Matrix4f pose, Vec3 base, Vec3 tip, MultiBufferSource buffers, double time) {
        if (!Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        Vec3 axis = tip.subtract(base).normalize();
        Vec3 side = new Vec3(-axis.y, axis.x, 0).normalize();
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 1.7);
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        // Three tapered tongues, confined to the last third of the actual blade.
        for (int i = -1; i <= 1; i++) {
            Vec3 root = base.lerp(tip, 0.73).add(side.scale(i * 0.035));
            Vec3 end = tip.add(axis.scale((i == 0 ? 0.14 : 0.08) * pulse)).add(side.scale(i * 0.055));
            Vec3 shoulder = root.lerp(end, 0.4);
            vertex(out, pose, root, 112, 40, 200, 0);
            vertex(out, pose, shoulder.add(side.scale(0.035)), 171, 81, 240, 155);
            vertex(out, pose, end, 232, 193, 255, 0);
            vertex(out, pose, shoulder.subtract(side.scale(0.035)), 171, 81, 240, 155);
        }
        strip(out, pose, base.lerp(tip, 0.82), tip, side.scale(0.009), 255, 242, 221, 205);
    }
}
