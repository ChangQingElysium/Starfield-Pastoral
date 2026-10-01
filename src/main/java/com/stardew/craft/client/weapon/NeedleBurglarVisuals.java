package com.stardew.craft.client.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import static com.stardew.craft.client.weapon.MeleeWeaponVisuals.*;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class NeedleBurglarVisuals {
    private record Key(int caster, String skill, long tick) {}
    private static final Map<Key, Long> CASTS = new LinkedHashMap<>();
    private static ClientLevel level;
    private static long hitTick = Long.MIN_VALUE, lootTick = Long.MIN_VALUE;
    private NeedleBurglarVisuals() {}
    public static void ensureLevel() {
        ClientLevel next = Minecraft.getInstance().level;
        if (next != level) { level = next; CASTS.clear(); hitTick = lootTick = Long.MIN_VALUE; IridiumNeedleFrenzyClientState.clear(); }
    }
    public static void strikeConfirmed() { ensureLevel(); if (level != null) hitTick = level.getGameTime(); }
    public static float hitAge(float partial) { return age(hitTick, partial); }
    public static float lootAge(float partial) { return age(lootTick, partial); }
    private static float age(long tick, float partial) { return level == null || tick == Long.MIN_VALUE ? -1 : level.getGameTime() - tick + partial; }
    public static void lootConfirmed() {
        ensureLevel(); var mc = Minecraft.getInstance();
        if (level == null || mc.player == null) return;
        lootTick = level.getGameTime();
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .35f, 1.45f);
    }
    static boolean keepsCurrentStrike(String current, String incoming) {
        return NEEDLE_FRENZY.equals(incoming) && (NEEDLE_READY.equals(current) || NEEDLE_STRIKE.equals(current) || NEEDLE_FINAL.equals(current));
    }
    public static void start(WeaponSkillAnimPayload p) {
        ensureLevel(); var mc = Minecraft.getInstance();
        Vec3 origin = new Vec3(p.originX(), p.originY(), p.originZ());
        if (level == null || mc.player == null || mc.player.distanceToSqr(origin) > 48 * 48 || NEEDLE_READY.equals(p.skillId())) return;
        Key key = new Key(p.casterEntityId(), p.skillId(), p.startGameTick());
        if (CASTS.putIfAbsent(key, level.getGameTime()) != null) return;
        while (CASTS.size() > 64) CASTS.remove(CASTS.keySet().iterator().next());
        var sound = NEEDLE_FRENZY.equals(p.skillId()) ? SoundEvents.AMETHYST_BLOCK_CHIME
                : BURGLAR_STRIKE.equals(p.skillId()) ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.TRIDENT_THROW.value();
        level.playLocalSound(origin.x, origin.y + 1, origin.z, sound, SoundSource.PLAYERS,
                NEEDLE_FRENZY.equals(p.skillId()) ? .35f : .22f, NEEDLE_FINAL.equals(p.skillId()) ? 1.4f : 1.75f, false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ensureLevel(); if (level == null || Minecraft.getInstance().isPaused()) return;
        CASTS.values().removeIf(tick -> level.getGameTime() - tick > 24);
    }
}
