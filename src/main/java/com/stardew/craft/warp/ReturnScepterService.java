package com.stardew.craft.warp;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.network.payload.MagicWarpFlashPayload;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.weather.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative SDV Return Scepter sequence. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ReturnScepterService {
    private static final String PENDING_START = "stardewcraft_return_scepter_start";
    private static final String PREVIOUS_INVISIBLE = "stardewcraft_return_scepter_previous_invisible";
    private static final String PREVIOUS_INVULNERABLE = "stardewcraft_return_scepter_previous_invulnerable";

    private static final int FADE_OUT_AT = 20;
    private static final int WARP_AT = 40;
    private static final int FADE_IN_TICKS = 10;
    private static final int FINISH_AT = WARP_AT + FADE_IN_TICKS;

    private ReturnScepterService() {
    }

    /** Begin the same fixed-home warp used by {@code StardewValley.Tools.Wand}. */
    public static boolean begin(ServerPlayer player) {
        if (!player.isAlive() || player.getPersistentData().contains(PENDING_START)) {
            return false;
        }

        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.stardewcraft.totem_no_farm"), true);
            return false;
        }

        player.closeContainer();
        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(0.0F); // SDV faceDirection(2): south.
        player.setYHeadRot(0.0F);

        player.getPersistentData().putLong(PENDING_START, player.serverLevel().getGameTime());
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
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    private static void tick(ServerPlayer player) {
        if (!player.getPersistentData().contains(PENDING_START)) {
            return;
        }

        long elapsed = player.serverLevel().getGameTime()
                - player.getPersistentData().getLong(PENDING_START);
        if (elapsed < 0L || elapsed > 200L) {
            clear(player);
            return;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        if (elapsed >= 0L && elapsed <= 8L) {
            spawnSweepStep(player.serverLevel(), player.position(), (int) elapsed);
        }

        if (elapsed == FADE_OUT_AT) {
            PacketDistributor.sendToPlayer(player,
                    new DesertBusFadePayload((byte) 0, WARP_AT - FADE_OUT_AT));
        } else if (elapsed == WARP_AT) {
            warpHome(player);
            PacketDistributor.sendToPlayer(player,
                    new DesertBusFadePayload((byte) 1, FADE_IN_TICKS));
        } else if (elapsed >= FINISH_AT) {
            clear(player);
        }
    }

    private static void warpHome(ServerPlayer player) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        ServerLevel targetLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (farm == null || targetLevel == null) {
            clear(player);
            return;
        }

        BlockPos frontDoor = farm.getSpawnPoint();
        // Wand.DoFunction faces direction 2 (south), and warpFarmer(..., flip:false)
        // preserves that facing through the warp.
        ModTeleport.to(player, targetLevel, frontDoor, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        restorePlayerState(player);
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
        player.getPersistentData().remove(PENDING_START);
        player.getPersistentData().remove(PREVIOUS_INVISIBLE);
        player.getPersistentData().remove(PREVIOUS_INVULNERABLE);
    }

    private static void restorePlayerState(ServerPlayer player) {
        if (player.getPersistentData().contains(PREVIOUS_INVISIBLE)) {
            player.setInvisible(player.getPersistentData().getBoolean(PREVIOUS_INVISIBLE));
        }
        if (player.getPersistentData().contains(PREVIOUS_INVULNERABLE)) {
            player.setInvulnerable(player.getPersistentData().getBoolean(PREVIOUS_INVULNERABLE));
        }
    }
}
