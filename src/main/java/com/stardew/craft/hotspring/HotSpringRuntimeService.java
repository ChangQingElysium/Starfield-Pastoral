package com.stardew.craft.hotspring;

import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 温泉服务端运行时（Phase 1）。
 *
 * 对齐原版 Farmer.swimming：静止时每 100ms +1 stamina/+1 health（在 MC 中映射为每 2 tick +1）。
 * 移动时不恢复，按 ~800ms（MC 16 tick）节奏播放水声。
 * 进入/离开温泉播放 pullItemFromWater 反馈。
 */
public final class HotSpringRuntimeService {

    private static final int RESTORE_COOLDOWN_TICKS = 2;   // 原版 swimTimer = 100ms
    private static final int SLOSH_COOLDOWN_TICKS = 16;    // 原版 ~800ms 移动 slosh
    private static final double STILL_THRESHOLD = 0.001;   // 水平移动判定阈值（block）

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        boolean inHotSpring;
        double lastX, lastZ;
        int restoreCooldown;
        int sloshCooldown;
    }

    @EventBusSubscriber(modid = com.stardew.craft.StardewCraft.MODID)
    public static final class Events {
        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                STATES.remove(sp.getUUID());
            }
        }
    }

    private HotSpringRuntimeService() {}

    /**
     * 每个 ServerPlayer 每 tick 调用一次（PlayerTickEvent.Post 末尾）。
     */
    public static void tick(ServerPlayer player) {
        if (player.isSpectator()) {
            STATES.remove(player.getUUID());
            return;
        }

        boolean inWater = HotSpringAreaRegistry.isInHotSpringWater(player);
        State state = STATES.computeIfAbsent(player.getUUID(), k -> {
            State s = new State();
            s.lastX = player.getX();
            s.lastZ = player.getZ();
            return s;
        });

        if (!inWater) {
            if (state.inHotSpring) {
                // 离水反馈，对齐原版 PoolEntrance 同一 cue
                playPullItemFromWater(player);
                state.inHotSpring = false;
                state.restoreCooldown = 0;
                state.sloshCooldown = 0;
            }
            state.lastX = player.getX();
            state.lastZ = player.getZ();
            return;
        }

        // 入水反馈
        if (!state.inHotSpring) {
            playPullItemFromWater(player);
            spawnSplash(player, 16, 0.4);
            state.inHotSpring = true;
            state.restoreCooldown = RESTORE_COOLDOWN_TICKS;
            state.sloshCooldown = SLOSH_COOLDOWN_TICKS;
        }

        double dx = player.getX() - state.lastX;
        double dz = player.getZ() - state.lastZ;
        boolean still = (dx * dx + dz * dz) < (STILL_THRESHOLD * STILL_THRESHOLD);

        state.lastX = player.getX();
        state.lastZ = player.getZ();

        if (state.restoreCooldown > 0) state.restoreCooldown--;
        if (state.sloshCooldown > 0) state.sloshCooldown--;

        if (still) {
            if (state.restoreCooldown <= 0) {
                state.restoreCooldown = RESTORE_COOLDOWN_TICKS;
                applyRestore(player);
            }
        } else {
            if (state.sloshCooldown <= 0) {
                state.sloshCooldown = SLOSH_COOLDOWN_TICKS;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.WATER_SLOSH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                spawnSplash(player, 6, 0.25);
            }
        }
    }

    private static void applyRestore(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        boolean changed = false;

        if (data.getEnergy() < data.getMaxEnergy()) {
            data.restoreEnergy(1.0f);
            changed = true;
        }
        if (data.getHealth() < data.getMaxHealth()) {
            data.setHealth(data.getHealth() + 1);
            changed = true;
        }

        if (changed) {
            PlayerDataEventHandler.syncPlayerVitals(player, data);
        }
    }

    private static void spawnSplash(ServerPlayer player, int count, double spread) {
        if (!(player.level() instanceof ServerLevel level)) return;
        // SDV TileSheets/animations.png 64x64 splash 帧近似：白色水花，集中在水面高度。
        // Phase 2 用 MC 原生 SPLASH 粒子，色调与水面贴合。
        level.sendParticles(ParticleTypes.SPLASH,
            player.getX(), player.getY() + 0.05, player.getZ(),
            count, spread, 0.1, spread, 0.0);
    }

    private static void playPullItemFromWater(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            ModSounds.PULL_ITEM_FROM_WATER.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}
