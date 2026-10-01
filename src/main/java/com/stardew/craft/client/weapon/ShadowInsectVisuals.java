package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class ShadowInsectVisuals {
    private record Key(int caster, long tick, String skill) {}
    private record Cast(WeaponSkillAnimPayload payload, Vec3 origin, long start, Vec3 end) {}
    private static final Map<Key, Cast> CASTS = new LinkedHashMap<>();
    private static ClientLevel level;
    private ShadowInsectVisuals() {}
    public static void ensureLevel() {
        ClientLevel next = Minecraft.getInstance().level;
        if (next != level) { level = next; CASTS.clear(); InsectEyeStanceClientState.clear(); }
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel(); var mc = Minecraft.getInstance();
        Vec3 origin = new Vec3(p.originX(), p.originY(), p.originZ());
        if (level == null || mc.player == null || mc.player.distanceToSqr(origin) > 48 * 48) return;
        Key key = new Key(p.casterEntityId(), p.startGameTick(), p.skillId());
        if (CASTS.containsKey(key)) return;
        CASTS.put(key, new Cast(p, origin, level.getGameTime(), origin));
        while (CASTS.size() > 32) CASTS.remove(CASTS.keySet().iterator().next());
        var sound = INSECT_STANCE.equals(p.skillId()) ? SoundEvents.BEEHIVE_WORK
                : SHADOW_EXECUTE.equals(p.skillId()) ? SoundEvents.TRIDENT_THROW.value() : SoundEvents.PLAYER_ATTACK_SWEEP;
        level.playLocalSound(origin.x, origin.y + .9, origin.z, sound, SoundSource.PLAYERS,
                INSECT_STANCE.equals(p.skillId()) ? .22f : .3f, SHADOW_EXECUTE.equals(p.skillId()) ? 1.65f : 1.3f, false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel(); if (level == null || Minecraft.getInstance().isPaused()) return;
        CASTS.replaceAll((key, c) -> level.getGameTime() - c.start <= 5
                && level.getEntity(c.payload.casterEntityId()) instanceof LivingEntity actor
                ? new Cast(c.payload, c.origin, c.start, actor.position()) : c);
        CASTS.values().removeIf(c -> level.getGameTime() - c.start >= 9
                || !(level.getEntity(c.payload.casterEntityId()) instanceof LivingEntity actor) || !actor.isAlive()
                || !(actor.getMainHandItem().getItem() instanceof IStardewWeapon weapon)
                || !c.payload.weaponId().equals(weapon.getWeaponId()));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel(); var mc = Minecraft.getInstance();
        if (level == null || mc.player == null || CASTS.isEmpty() || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var buffers = mc.renderBuffers().bufferSource();
        var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        for (Cast cast : CASTS.values()) {
            if (!INSECT_DASH.equals(cast.payload.skillId())) continue;
            if (!(level.getEntity(cast.payload.casterEntityId()) instanceof LivingEntity actor)
                    || actor.distanceToSqr(camera) > 48 * 48) continue;
            float age = level.getGameTime() - cast.start + partial;
            // The authored dash is a straight, collision-checked five-tick segment, not a predicted trajectory.
            Vec3 end = age < 5 ? actor.getPosition(partial) : cast.end;
            if (age < 0 || age >= 8 || end.distanceToSqr(cast.origin) > 64) continue;
            Vec3 from = end.lerp(cast.origin, Math.min(1, 3 / Math.max(.001, end.distanceTo(cast.origin))));
            ShadowInsectGeometry.wake(out, event.getPoseStack().last().pose(), from.subtract(camera),
                    end.subtract(camera), Math.min(1, (8 - age) / 3));
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
}
