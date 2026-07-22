package com.stardew.craft.warp;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.WizardBuildingKind;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.network.payload.MagicWarpFlashPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative implementation of {@code Building.PerformObeliskWarp}. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ObeliskWarpService {
    private static final String PENDING_START = "stardewcraft_obelisk_warp_start";
    private static final String PENDING_KIND = "stardewcraft_obelisk_warp_kind";
    private static final String PREVIOUS_INVISIBLE = "stardewcraft_obelisk_previous_invisible";
    private static final String PREVIOUS_INVULNERABLE = "stardewcraft_obelisk_previous_invulnerable";
    private static final int START_FADE_AT = 20;
    private static final int FADE_TICKS = 10;
    private static final int WARP_AT = START_FADE_AT + FADE_TICKS;

    private ObeliskWarpService() {
    }

    public static boolean begin(ServerPlayer player, WizardBuildingKind kind) {
        if (!player.isAlive() || player.getPersistentData().contains(PENDING_START)) {
            return false;
        }
        if (kind == WizardBuildingKind.ISLAND_OBELISK) {
            player.displayClientMessage(Component.translatable(
                    "message.stardewcraft.wizard_building.island_unavailable"), true);
            return false;
        }
        if (destination(kind) == null) {
            return false;
        }
        // Building.cs returns after dismounting; a second interaction starts the warp.
        if (kind == WizardBuildingKind.DESERT_OBELISK && player.isPassenger()) {
            player.stopRiding();
            return true;
        }

        player.closeContainer();
        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        player.getPersistentData().putLong(PENDING_START, player.serverLevel().getGameTime());
        player.getPersistentData().putInt(PENDING_KIND, kind.ordinal());
        player.getPersistentData().putBoolean(PREVIOUS_INVISIBLE, player.isInvisible());
        player.getPersistentData().putBoolean(PREVIOUS_INVULNERABLE, player.isInvulnerable());
        player.setInvisible(true);
        player.setInvulnerable(true);

        spawnBurst(player.serverLevel(), player.position());
        player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        PacketDistributor.sendToPlayer(player, new MagicWarpFlashPayload((byte) 0));
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.getPersistentData().contains(PENDING_START)) {
            return;
        }
        long elapsed = player.serverLevel().getGameTime()
                - player.getPersistentData().getLong(PENDING_START);
        if (elapsed < 0 || elapsed > 200) {
            clear(player);
            return;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        if (elapsed >= 0 && elapsed <= 8) {
            spawnSweepStep(player.serverLevel(), player.position(), (int) elapsed);
        }
        if (elapsed == START_FADE_AT) {
            PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 0, FADE_TICKS));
        } else if (elapsed >= WARP_AT) {
            performWarp(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    private static void performWarp(ServerPlayer player) {
        int ordinal = player.getPersistentData().getInt(PENDING_KIND);
        WizardBuildingKind[] kinds = WizardBuildingKind.values();
        if (ordinal < 0 || ordinal >= kinds.length) {
            clear(player);
            return;
        }
        Vec3 destination = destination(kinds[ordinal]);
        ServerLevel target = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (destination != null && target != null) {
            ModTeleport.to(player, target, destination.x, destination.y, destination.z,
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
        }
        restorePlayerState(player);
        PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 1, FADE_TICKS));
        clearKeys(player);
    }

    private static void spawnBurst(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ModParticles.MAGIC_WARP_BURST.get(),
                    center.x + level.random.nextDouble() * 7.0D - 4.0D,
                    center.y + 0.25D + level.random.nextDouble() * 2.5D,
                    center.z + level.random.nextDouble() * 7.0D - 4.0D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnSweepStep(ServerLevel level, Vec3 center, int step) {
        int firstIndex = step * 2;
        for (int offset = 0; offset < 2; offset++) {
            int index = firstIndex + offset;
            if (index > 16) {
                return;
            }
            level.sendParticles(ModParticles.MAGIC_WARP_SWEEP.get(),
                    center.x + 8.0D - index, center.y + 0.5D, center.z,
                    1, 0.0D, 0.0D, 0.0D, -0.00390625D);
        }
    }

    private static void clear(ServerPlayer player) {
        restorePlayerState(player);
        clearKeys(player);
    }

    private static void restorePlayerState(ServerPlayer player) {
        if (player.getPersistentData().contains(PREVIOUS_INVISIBLE)) {
            player.setInvisible(player.getPersistentData().getBoolean(PREVIOUS_INVISIBLE));
        }
        if (player.getPersistentData().contains(PREVIOUS_INVULNERABLE)) {
            player.setInvulnerable(player.getPersistentData().getBoolean(PREVIOUS_INVULNERABLE));
        }
    }

    private static void clearKeys(ServerPlayer player) {
        player.getPersistentData().remove(PENDING_START);
        player.getPersistentData().remove(PENDING_KIND);
        player.getPersistentData().remove(PREVIOUS_INVISIBLE);
        player.getPersistentData().remove(PREVIOUS_INVULNERABLE);
    }

    private static Vec3 destination(WizardBuildingKind kind) {
        return switch (kind) {
            // Keep the exact landing centers used by the original warp-wand destinations.
            case EARTH_OBELISK -> new Vec3(75.5D, 81.0D, -104.5D);
            case WATER_OBELISK -> new Vec3(44.5D, 60.0D, 94.5D);
            case DESERT_OBELISK -> new Vec3(-202.5D, 64.0D, -156.5D);
            case JUNIMO_HUT, ISLAND_OBELISK, GOLD_CLOCK -> null;
        };
    }
}
