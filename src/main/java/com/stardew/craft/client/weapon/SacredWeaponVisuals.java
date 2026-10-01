package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Guard geometry follows the actual replicated action, so switching/cancelling cannot leave a shield behind. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SacredWeaponVisuals {
    private SacredWeaponVisuals() {}
    public static boolean isAction(String id) {
        return HOLY_SWING.equals(id) || TEMPLAR_SWING.equals(id) || HOLY_SMITE.equals(id) || HOLY_DOMAIN.equals(id)
                || TEMPLAR_VOW.equals(id) || TEMPLAR_STRIKE.equals(id) || TEMPLAR_END.equals(id) || TEMPLAR_JUDGEMENT.equals(id);
    }
    public static void start(WeaponSkillAnimPayload p) {
        var mc = Minecraft.getInstance();
        Vec3 at = new Vec3(p.originX(), p.originY() + 1, p.originZ());
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(at) > 48 * 48 || TEMPLAR_END.equals(p.skillId())) return;
        boolean slash = HOLY_SMITE.equals(p.skillId()) || TEMPLAR_STRIKE.equals(p.skillId());
        mc.level.playLocalSound(at.x, at.y, at.z, slash ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, slash ? 0.48f : 0.38f, slash ? 0.9f : HOLY_DOMAIN.equals(p.skillId()) ? 1.2f : 1.55f, false);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var stack = event.getPoseStack(); var camera = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        var buffers = mc.renderBuffers().bufferSource();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (var player : mc.level.players()) {
            if (player.distanceToSqr(camera) > 48 * 48) continue;
            var action = MeleeWeaponVisuals.action(player, partial);
            if (action == null || !TEMPLAR_VOW.equals(action.skillId())) continue;
            float t = action.progress();
            float fade = Math.min(1, t / 0.075f) * Math.min(1, (1 - t) / 0.05f);
            Vec3 forward = Vec3.directionFromRotation(0, player.getViewYRot(partial));
            Vec3 right = new Vec3(forward.z, 0, -forward.x);
            Vec3 center = player.getPosition(partial).add(0, 1.05, 0).add(forward.scale(0.72));
            guard(out, stack.last().pose(), center, right, fade);
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW); stack.popPose();
    }
    static void guard(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, float fade) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] points = {center.add(up.scale(0.48)), center.add(right.scale(0.32)).add(up.scale(0.22)),
                center.add(right.scale(0.27)).subtract(up.scale(0.18)), center.subtract(up.scale(0.5)),
                center.subtract(right.scale(0.27)).subtract(up.scale(0.18)), center.subtract(right.scale(0.32)).add(up.scale(0.22))};
        for (int i = 0; i < points.length; i++) {
            Vec3 a = points[i], b = points[(i + 1) % points.length];
            Vec3 width = b.subtract(a).cross(right.cross(up)).normalize();
            strip(out, pose, a, b, width.scale(0.022), 135, 187, 244, Math.round(85 * fade));
            strip(out, pose, a, b, width.scale(0.007), 255, 232, 169, Math.round(190 * fade));
        }
    }
}
