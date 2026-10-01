package com.stardew.craft.client.weapon;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;

/** Replicated stance windows; rendering separately verifies that the shiv is held. */
public final class DragontoothShivBreathClientState {
    private record Window(long started, int duration) {
        long end() { return started + duration; }
    }
    private static final Map<Integer, Window> WINDOWS = new HashMap<>();
    private static ClientLevel activeLevel;
    private DragontoothShivBreathClientState() {}

    public static void apply(int casterId, boolean active, int duration, long started) {
        ensureLevel();
        Window prior = WINDOWS.get(casterId);
        if (prior != null && (prior.started > started
                || prior.started == started && prior.duration < 0 && active)) return;
        // Keep a short-lived tombstone so a delayed start cannot resurrect a cancelled window.
        WINDOWS.put(casterId, new Window(started, active ? Math.max(0, duration) : -1));
    }
    public static boolean isActive(LivingEntity player) {
        ensureLevel();
        Window w = player == null ? null : WINDOWS.get(player.getId());
        return w != null && player.level() == activeLevel && player.isAlive()
                && withinWindow(player.level().getGameTime(), w.started, w.duration);
    }
    static boolean withinWindow(long now, long started, int duration) {
        return duration > 0 && now >= started && now <= started + duration;
    }
    public static int getRemainingTicks(Player player) {
        return isActive(player) ? (int) Math.max(0, WINDOWS.get(player.getId()).end() - player.level().getGameTime()) : 0;
    }
    public static int getTotalTicks() {
        ensureLevel();
        Player player = Minecraft.getInstance().player;
        Window w = player == null ? null : WINDOWS.get(player.getId());
        return w == null ? 1 : Math.max(1, w.duration);
    }
    public static void clear() { WINDOWS.clear(); }
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureLevel();
        if (activeLevel == null || Minecraft.getInstance().isPaused()) return;
        WINDOWS.entrySet().removeIf(e -> activeLevel.getGameTime() > e.getValue().end() + 40);
    }
    public static void clearIfNoPlayer() {
        ensureLevel();
        if (Minecraft.getInstance().player == null) clear();
    }
    private static void ensureLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (activeLevel == level) return;
        activeLevel = level;
        clear();
    }
}
