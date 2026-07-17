package com.stardew.craft.manager;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.DarkTalismanSealBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.payload.DesertBusFadePayload;
import com.stardew.craft.network.payload.MagicWarpFlashPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.weather.ModParticles;
import com.stardew.craft.world.PlayerAreaEvictionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Runtime placement and per-player behavior for the two Witch Warp Cave circles and seal. */
public final class WitchWarpCaveService {
    public static final BlockPos SEAL_POS = new BlockPos(52, 85, -213);
    public static final BlockPos UPPER_CIRCLE_POS = new BlockPos(52, 85, -221);
    public static final BlockPos LOWER_CIRCLE_POS = new BlockPos(51, 48, -223);

    private static final String DARK_TALISMAN_FLAG = "HasDarkTalisman";
    private static final String SEAL_GATE_ID = "dark_talisman_seal";
    private static final AABB SEALED_AREA = new AABB(50.0D, 84.0D, -223.0D, 55.0D, 89.0D, -212.0D);
    private static final Vec3 SEAL_EXIT = new Vec3(52.5D, 85.0D, -211.75D);

    private static final String PENDING_START = "stardewcraft_magic_warp_start";
    private static final String PENDING_ROUTE = "stardewcraft_magic_warp_route";
    private static final String PENDING_WAS_INVISIBLE = "stardewcraft_magic_warp_was_invisible";
    private static final String LAST_WARP_TICK = "stardewcraft_magic_warp_last";
    private static final byte ROUTE_DOWN = 1;
    private static final byte ROUTE_UP = 2;
    private static final int FADE_OUT_AT = 20;
    private static final int WARP_AT = 28;
    private static final int FADE_IN_AT = 30;
    private static final int TRANSITION_END_AT = 44;

    private WitchWarpCaveService() {
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            clearPendingWarp(player);
            return;
        }

        if (player.tickCount % 40 == 0) {
            ensurePlaced(player.serverLevel());
        }
        if (player.tickCount % 20 == 0) {
            syncSealForPlayer(player);
        }

        boolean hasDarkTalisman = PlayerDataManager.getPlayerData(player).hasMailFlag(DARK_TALISMAN_FLAG);
        boolean evicted = PlayerAreaEvictionService.enforce(
                player,
                SEAL_GATE_ID,
                !hasDarkTalisman && SEALED_AREA.intersects(player.getBoundingBox()),
                SEAL_EXIT,
                null
        );
        if (evicted || tickPendingWarp(player)) {
            return;
        }

        long now = player.serverLevel().getGameTime();
        long lastWarp = player.getPersistentData().getLong(LAST_WARP_TICK);
        if (now - lastWarp < 8L) {
            return;
        }
        if (isStandingOn(player, UPPER_CIRCLE_POS)) {
            beginWarp(player, ROUTE_DOWN, now);
        } else if (isStandingOn(player, LOWER_CIRCLE_POS)) {
            beginWarp(player, ROUTE_UP, now);
        }
    }

    public static void ensurePlaced(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        if (level.hasChunkAt(SEAL_POS)) {
            BlockState seal = ModBlocks.DARK_TALISMAN_SEAL.get().defaultBlockState()
                    .setValue(DarkTalismanSealBlock.FACING, Direction.SOUTH);
            if (!level.getBlockState(SEAL_POS).equals(seal)) {
                level.setBlock(SEAL_POS, seal, Block.UPDATE_ALL);
            }
        }
        ensureCircle(level, UPPER_CIRCLE_POS);
        ensureCircle(level, LOWER_CIRCLE_POS);
    }

    public static void syncSealForPlayer(ServerPlayer player) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY || !player.serverLevel().hasChunkAt(SEAL_POS)) {
            return;
        }
        boolean hidden = PlayerDataManager.getPlayerData(player).hasMailFlag(DARK_TALISMAN_FLAG);
        BlockState state = hidden ? Blocks.AIR.defaultBlockState() : player.serverLevel().getBlockState(SEAL_POS);
        player.connection.send(new ClientboundBlockUpdatePacket(SEAL_POS, state));
    }

    public static void onPlayerLogout(ServerPlayer player) {
        clearPendingWarp(player);
        PlayerAreaEvictionService.clearPlayer(player);
    }

    private static void ensureCircle(ServerLevel level, BlockPos pos) {
        if (level.hasChunkAt(pos) && !level.getBlockState(pos).is(ModBlocks.MAGIC_WARP_CIRCLE.get())) {
            level.setBlock(pos, ModBlocks.MAGIC_WARP_CIRCLE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean isStandingOn(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().equals(pos)
                && player.serverLevel().getBlockState(pos).is(ModBlocks.MAGIC_WARP_CIRCLE.get());
    }

    private static void beginWarp(ServerPlayer player, byte route, long now) {
        player.closeContainer();
        player.stopUsingItem();
        player.setDeltaMovement(Vec3.ZERO);
        player.getPersistentData().putLong(PENDING_START, now);
        player.getPersistentData().putByte(PENDING_ROUTE, route);
        player.getPersistentData().putBoolean(PENDING_WAS_INVISIBLE, player.isInvisible());
        player.getPersistentData().putLong(LAST_WARP_TICK, now);
        player.setInvisible(true);

        spawnBurst(player.serverLevel(), player.position());
        player.serverLevel().playSound(null, player.blockPosition(),
                ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        PacketDistributor.sendToPlayer(player, new MagicWarpFlashPayload((byte) 0));
    }

    private static boolean tickPendingWarp(ServerPlayer player) {
        if (!player.getPersistentData().contains(PENDING_START)) {
            return false;
        }

        long elapsed = player.serverLevel().getGameTime() - player.getPersistentData().getLong(PENDING_START);
        byte route = player.getPersistentData().getByte(PENDING_ROUTE);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;

        if (elapsed >= 0L && elapsed <= 8L) {
            spawnSweepStep(player.serverLevel(), route == ROUTE_DOWN ? UPPER_CIRCLE_POS : LOWER_CIRCLE_POS,
                    (int) elapsed);
        }
        if (elapsed == FADE_OUT_AT) {
            PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 0, WARP_AT - FADE_OUT_AT));
        } else if (elapsed == WARP_AT) {
            performWarp(player, route);
        } else if (elapsed == FADE_IN_AT) {
            PacketDistributor.sendToPlayer(player, new DesertBusFadePayload((byte) 1,
                    TRANSITION_END_AT - FADE_IN_AT));
        } else if (elapsed >= TRANSITION_END_AT) {
            clearPendingWarp(player);
        }
        return true;
    }

    private static void performWarp(ServerPlayer player, byte route) {
        BlockPos destination = route == ROUTE_DOWN
                ? new BlockPos(51, 48, -224)
                : new BlockPos(52, 85, -220);
        Direction facing = route == ROUTE_DOWN ? Direction.NORTH : Direction.SOUTH;
        player.teleportTo(player.serverLevel(),
                destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                facing.toYRot(), 0.0F);
        restoreVisibility(player);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
    }

    private static void spawnBurst(ServerLevel level, Vec3 center) {
        for (int i = 0; i < 12; i++) {
            double x = center.x + level.random.nextDouble() * 7.0D - 4.0D;
            double y = center.y + 0.25D + level.random.nextDouble() * 2.5D;
            double z = center.z + level.random.nextDouble() * 7.0D - 4.0D;
            level.sendParticles(ModParticles.MAGIC_WARP_BURST.get(), x, y, z, 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void spawnSweepStep(ServerLevel level, BlockPos source, int step) {
        int firstIndex = step * 2;
        for (int offset = 0; offset < 2; offset++) {
            int index = firstIndex + offset;
            if (index > 16) {
                return;
            }
            level.sendParticles(ModParticles.MAGIC_WARP_SWEEP.get(),
                    source.getX() + 8.5D - index,
                    source.getY() + 0.5D,
                    source.getZ() + 0.5D,
                    1, 0.0D, 0.0D, 0.0D, -0.00390625D);
        }
    }

    private static void clearPendingWarp(ServerPlayer player) {
        restoreVisibility(player);
        player.getPersistentData().remove(PENDING_START);
        player.getPersistentData().remove(PENDING_ROUTE);
        player.getPersistentData().remove(PENDING_WAS_INVISIBLE);
    }

    private static void restoreVisibility(ServerPlayer player) {
        if (player.getPersistentData().contains(PENDING_WAS_INVISIBLE)) {
            player.setInvisible(player.getPersistentData().getBoolean(PENDING_WAS_INVISIBLE));
        }
    }
}
