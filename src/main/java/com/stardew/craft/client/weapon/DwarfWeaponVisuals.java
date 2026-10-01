package com.stardew.craft.client.weapon;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.DwarfShockPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.skill.WeaponGroundContact;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class DwarfWeaponVisuals {
    private record CastKey(int caster, String skill, long tick) {}
    private record Cast(CastKey key, long start) {}
    private record ShockKey(int caster, long tick, float radius, boolean echo) {}
    private record Path(int band, Vec3[] points) {}
    private record Shock(long start, boolean echo, Vec3 center, List<Path> paths) {}
    private record Step(long tick, Vec3 a, Vec3 b) {}
    private static final Map<CastKey, Cast> CASTS = new LinkedHashMap<>();
    private static final Map<ShockKey, Shock> SHOCKS = new LinkedHashMap<>();
    private static final Map<Integer, Vec3> PREVIOUS = new HashMap<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static ClientLevel level;
    private DwarfWeaponVisuals() {}
    public static void ensureLevel() {
        ClientLevel next = Minecraft.getInstance().level;
        if (next != level) {
            level = next; CASTS.clear(); SHOCKS.clear(); PREVIOUS.clear(); STEPS.clear();
            DwarfFortressClientState.clear(); DwarfDaggerRushClientState.clear(); DwarfDaggerThrustClientState.clear();
        }
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel(); var mc = Minecraft.getInstance();
        Vec3 origin = new Vec3(p.originX(), p.originY(), p.originZ());
        if (level == null || mc.player == null || mc.player.distanceToSqr(origin) > 48 * 48) return;
        CastKey key = new CastKey(p.casterEntityId(), p.skillId(), p.startGameTick());
        if (CASTS.putIfAbsent(key, new Cast(key, level.getGameTime())) != null) return;
        while (CASTS.size() > 64) CASTS.remove(CASTS.keySet().iterator().next());
        if (DWARF_THRUST.equals(p.skillId())) PREVIOUS.put(p.casterEntityId(), origin);
        var sound = DWARF_THRUST.equals(p.skillId()) ? SoundEvents.TRIDENT_THROW.value()
                : DWARF_GUARD.equals(p.skillId()) ? SoundEvents.SHIELD_BLOCK : SoundEvents.AMETHYST_BLOCK_CHIME;
        level.playLocalSound(origin.x, origin.y + .8, origin.z, sound, SoundSource.PLAYERS, .32f,
                DWARF_THRUST.equals(p.skillId()) ? 1.35f : .9f, false);
    }
    public static boolean guarding(int actor) { return active(actor, DWARF_GUARD, 50); }
    public static void stopLocalThrust() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        CASTS.keySet().removeIf(key -> key.caster == player.getId() && DWARF_THRUST.equals(key.skill));
        PREVIOUS.remove(player.getId());
    }
    private static boolean active(int actor, String skill, int ticks) {
        return level != null && CASTS.values().stream().anyMatch(c -> c.key.caster == actor && skill.equals(c.key.skill)
                && level.getGameTime() - c.start < ticks);
    }
    public static void shock(DwarfShockPayload p) {
        ensureLevel(); var mc = Minecraft.getInstance();
        Vec3 center = new Vec3(p.x(), p.y(), p.z());
        if (level == null || mc.player == null || !validShock(p) || mc.player.distanceToSqr(center) > 48 * 48) return;
        ShockKey key = new ShockKey(p.caster(), p.tick(), p.radius(), p.echo());
        if (SHOCKS.containsKey(key)) return;
        var actor = level.getEntity(p.caster());
        if (actor == null) actor = mc.player;
        List<Path> paths = new ArrayList<>();
        if (Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) for (int band = 0; band < 4; band++) for (int side = 0; side < 4; side++) {
            Vec3[] points = DwarfWeaponGeometry.perimeterSide(center, p.radius() * (band + 1) / 4, side);
            for (int i = 0; i < points.length; i++) {
                var hit = WeaponGroundContact.find(level, actor, points[i]);
                points[i] = hit == null ? null : hit.getLocation().add(0, .028, 0);
            }
            for (Vec3[] part : continuousGroundPaths(points)) paths.add(new Path(band, part));
        }
        SHOCKS.put(key, new Shock(level.getGameTime(), p.echo(), center, paths));
        while (SHOCKS.size() > 24) SHOCKS.remove(SHOCKS.keySet().iterator().next());
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, p.echo() ? .55f : .35f, p.echo() ? .65f : .85f, false);
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, .2f, p.echo() ? .85f : 1.15f, false);
    }
    static boolean validShock(DwarfShockPayload p) {
        return Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()) && p.radius() > 0 && p.radius() <= 4;
    }
    /** Split at sampled air and step edges rather than joining different floors. */
    static List<Vec3[]> continuousGroundPaths(Vec3[] points) {
        List<Vec3[]> result = new ArrayList<>(); List<Vec3> current = new ArrayList<>();
        for (Vec3 p : points) {
            if (p == null || (!current.isEmpty() && Math.abs(p.y - current.getLast().y) > .26)) {
                if (current.size() > 1) result.add(current.toArray(Vec3[]::new)); current.clear();
            }
            if (p != null) current.add(p);
        }
        if (current.size() > 1) result.add(current.toArray(Vec3[]::new));
        return result;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel(); var mc = Minecraft.getInstance(); if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        CASTS.values().removeIf(c -> now - c.start > 50 || !(level.getEntity(c.key.caster) instanceof LivingEntity actor) || !actor.isAlive());
        SHOCKS.values().removeIf(s -> now - s.start >= 12); STEPS.removeIf(s -> now - s.tick >= 6);
        Set<Integer> walking = new HashSet<>();
        if (Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) for (var player : level.players()) {
            if (!player.isAlive() || player.distanceToSqr(mc.player) > 32 * 32
                    || !(player.getMainHandItem().getItem() instanceof IStardewWeapon weapon) || !"dwarf_dagger".equals(weapon.getWeaponId())) continue;
            if (!active(player.getId(), DWARF_THRUST, 6) && !(player == mc.player && DwarfDaggerRushClientState.isActive(player))) continue;
            walking.add(player.getId()); Vec3 current = player.position(), prior = PREVIOUS.put(player.getId(), current);
            if (prior == null || prior.distanceToSqr(current) < .0225 || prior.distanceToSqr(current) > 9) continue;
            var a = WeaponGroundContact.find(level, player, prior); var b = WeaponGroundContact.find(level, player, current);
            if (a != null && b != null && Math.abs(a.getLocation().y - b.getLocation().y) <= .26) {
                int samples = Math.max(2, (int)Math.ceil(prior.distanceTo(current) / .15));
                boolean continuous = true;
                for (int i = 1; i < samples; i++) {
                    var middle = WeaponGroundContact.find(level, player, prior.lerp(current, i / (double)samples));
                    if (middle == null || Math.abs(middle.getLocation().y - a.getLocation().y) > .26) {
                        continuous = false; break;
                    }
                }
                if (!continuous) continue;
                STEPS.add(new Step(now, a.getLocation().add(0, .03, 0), b.getLocation().add(0, .03, 0)));
                while (STEPS.size() > 96) STEPS.removeFirst();
            }
        }
        PREVIOUS.keySet().retainAll(walking);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ensureLevel(); var mc = Minecraft.getInstance(); if (level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) return;
        Vec3 camera = event.getCamera().getPosition(); float partial = event.getPartialTick();
        var buffers = mc.renderBuffers().bufferSource(); var out = buffers.getBuffer(WeaponEffectRenderTypes.MOLTEN_GLOW);
        var pose = event.getPoseStack().last().pose();
        for (Shock s : SHOCKS.values()) {
            if (s.center.distanceToSqr(camera) > 48 * 48) continue;
            float age = level.getGameTime() - s.start + partial;
            for (Path path : s.paths) {
                float fade = DwarfWeaponGeometry.waveOpacity(age, path.band);
                if (fade <= 0) continue;
                Vec3[] local = Arrays.stream(path.points).map(p -> p.subtract(camera)).toArray(Vec3[]::new);
                DwarfWeaponGeometry.groundStroke(out, pose, local, fade, s.echo);
            }
        }
        for (Step s : STEPS) {
            float fade = 1 - (level.getGameTime() - s.tick + partial) / 6;
            if (fade <= 0 || s.a.distanceToSqr(camera) > 32 * 32) continue;
            WeaponContactGeometry.blade(out, pose, s.a.subtract(camera), s.b.subtract(camera), new Vec3(0, 1, 0), .035,
                    fade * fade * .6f, false, 156, 209, 196);
        }
        buffers.endBatch(WeaponEffectRenderTypes.MOLTEN_GLOW);
    }
}
