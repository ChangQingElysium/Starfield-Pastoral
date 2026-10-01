package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Short-lived crystal ridges sampled from the actual, server-positioned ground wave. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class YetiToothVisuals {
    private record Ridge(int entityId, Vec3 point, long tick) {}
    private static final List<Ridge> RIDGES = new ArrayList<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();
    private static ClientLevel activeLevel;
    private YetiToothVisuals() {}

    public static void start(WeaponSkillAnimPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 point = new Vec3(payload.originX(), payload.originY() + 0.8, payload.originZ());
        if (mc.player.distanceToSqr(point) > 48 * 48) return;
        boolean spine = MeleeWeaponVisuals.YETI_SPINE.equals(payload.skillId());
        mc.level.playLocalSound(point.x, point.y, point.z, spine ? SoundEvents.GLASS_BREAK : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, spine ? 0.65f : 0.45f, spine ? 0.65f : 1.1f, false);
        mc.level.playLocalSound(point.x, point.y, point.z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.3f, spine ? 0.7f : 1.4f, false);
    }
    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof IceSpineEffectEntity spine) || !spine.level().isClientSide || !spine.isGroundWave()) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || mc.player == null || mc.isPaused()
                || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get() || spine.distanceToSqr(mc.player) > 32 * 32) return;
        Vec3 previous = LAST.get(spine.getId());
        if (previous != null && previous.distanceToSqr(spine.position()) < 0.4 * 0.4) return;
        LAST.put(spine.getId(), spine.position());
        if (RIDGES.size() >= 80) RIDGES.removeFirst();
        RIDGES.add(new Ridge(spine.getId(), spine.position(), mc.level.getGameTime()));
    }
    static float height(float age) {
        return Mth.clamp(age / 1.5f, 0, 1) * (1 - Mth.clamp((age - 4) / 5, 0, 1));
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + event.getPartialTick();
        RIDGES.removeIf(ridge -> now - ridge.tick >= 9);
        LAST.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        if (RIDGES.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        var stack = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var pose = stack.last().pose();
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.IMPACT_EDGE);
        for (Ridge ridge : RIDGES) {
            if (ridge.point.distanceToSqr(camera) > 32 * 32) continue;
            float h = height((float) (now - ridge.tick));
            for (int side : new int[]{-1, 1}) {
                Vec3 base = ridge.point.add(side * 0.18, -0.01, 0);
                Vec3 tip = base.add(side * 0.12, (side > 0 ? 1.05 : 0.7) * h, 0.08);
                for (int face = 0; face < 4; face++) {
                    double a = face * Math.PI / 2, b = (face + 1) * Math.PI / 2;
                    Vec3 p = base.add(Math.cos(a) * 0.20, 0, Math.sin(a) * 0.20);
                    Vec3 q = base.add(Math.cos(b) * 0.20, 0, Math.sin(b) * 0.20);
                    vertex(out, pose, p, 56, 147, 202, 180);
                    vertex(out, pose, q, 92, 193, 228, 185);
                    vertex(out, pose, tip, 224, 252, 255, 230);
                    vertex(out, pose, tip, 224, 252, 255, 230);
                }
            }
        }
        buffers.endBatch(WeaponEffectRenderTypes.IMPACT_EDGE);
        stack.popPose();
    }
    private static void ensureLevel(ClientLevel level) {
        if (activeLevel == level) return;
        activeLevel = level; RIDGES.clear(); LAST.clear();
    }
}
