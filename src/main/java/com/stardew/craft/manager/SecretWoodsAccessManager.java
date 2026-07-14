package com.stardew.craft.manager;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.decor.ResourceClumpBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

import javax.annotation.Nullable;

public final class SecretWoodsAccessManager {
    public static final String UNLOCK_FLAG = "stardewcraft.secret_woods_open";
    public static final BlockPos ENTRANCE_LOG_POS = new BlockPos(-185, 64, 15);

    private static final Direction ENTRANCE_LOG_FACING = Direction.NORTH;
    private static final int ENTRY_OPENING_MIN_X = -186;
    private static final int ENTRY_OPENING_MAX_X = -183;
    private static final int ENTRY_OPENING_MIN_Y = 64;
    private static final int ENTRY_OPENING_MAX_Y = 70;
    private static final int ENTRY_OPENING_MIN_Z = 14;
    private static final int ENTRY_OPENING_MAX_Z = 16;
    private static final double LOCKED_EXIT_X = -182.25D;
    private static final double LOCKED_EXIT_Z = 15.5D;
    private static final double LOCKED_PUSH_SPEED = 0.28D;

    private SecretWoodsAccessManager() {
    }

    public static boolean isUnlocked(ServerPlayer player) {
        return PlayerDataManager.getPlayerData(player).hasMailFlag(UNLOCK_FLAG);
    }

    public static boolean shouldIgnoreEntranceLogCollision(BlockGetter level, BlockPos pos, BlockState state, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext)) {
            return false;
        }
        Entity entity = entityContext.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        return isUnlocked(player) && isEntranceLog(level, pos, state);
    }

    public static boolean isEntranceLog(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(level instanceof Level world) || world.dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        if (!state.is(ModBlocks.HOLLOW_LOG.get()) || !(state.getBlock() instanceof ResourceClumpBlock clump)) {
            return false;
        }
        BlockPos mainPos = clump.findMainPos(level, pos, state);
        return ENTRANCE_LOG_POS.equals(mainPos);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (player.tickCount % 40 == 0) {
            ensureEntranceReady(level);
        }
        if (player.tickCount % 20 == 0) {
            syncEntranceForPlayer(player);
        }

        boolean inSecretWoods = CoalForestArea.containsColumn(player.blockPosition());
        if (isUnlocked(player) && inSecretWoods) {
            SecretWoodsSlimeSpawnService.ensureTodaySpawned(level);
        }

        if (!isUnlocked(player) && inSecretWoods) {
            player.displayClientMessage(Component.translatable("stardewcraft.secret_woods.blocked"), true);
            pushPlayerOutOfSecretWoods(player);
        }
    }

    private static void pushPlayerOutOfSecretWoods(ServerPlayer player) {
        double dx = LOCKED_EXIT_X - player.getX();
        double dz = LOCKED_EXIT_Z - player.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001D) {
            dx = 1.0D;
            dz = 0.0D;
            length = 1.0D;
        }

        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(dx / length * LOCKED_PUSH_SPEED, current.y, dz / length * LOCKED_PUSH_SPEED);
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    public static void ensureEntranceReady(ServerLevel level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        ensureEntranceOpeningClear(level);
        ensureEntranceLogPlaced(level);
    }

    public static void breakEntranceLog(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer player) {
        if (!isEntranceLog(level, pos, state)) {
            return;
        }

        BlockPos mainPos = findEntranceMain(level, pos, state);
        if (mainPos == null) {
            mainPos = ENTRANCE_LOG_POS;
        }
        level.levelEvent(2001, pos, Block.getId(state));

        if (player == null) {
            return;
        }
        if (isUnlocked(player)) {
            syncEntranceForPlayer(player);
            return;
        }

        if (!player.isCreative()) {
            Block.popResource(level, mainPos, new ItemStack(ModItems.WOOD_HARD.get(), 8));
            if (level.getRandom().nextDouble() < 0.1D) {
                Block.popResource(level, mainPos, new ItemStack(ModItems.MAHOGANY_SEED.get()));
            }
            PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, 25);
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addMailFlag(UNLOCK_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);
        syncEntranceForPlayer(player);
    }

    public static void syncEntranceForPlayer(ServerPlayer player) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        boolean unlocked = isUnlocked(player);
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : entranceLogPositions()) {
            BlockState state = unlocked ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
            player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        }
    }

    private static void ensureEntranceLogPlaced(ServerLevel level) {
        Block block = ModBlocks.HOLLOW_LOG.get();
        BlockState mainState = block.defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, ENTRANCE_LOG_FACING);
        BlockState currentMain = level.getBlockState(ENTRANCE_LOG_POS);
        if (!currentMain.is(block)
                || currentMain.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN
                || currentMain.getValue(MapDecorStaticBlock.FACING) != ENTRANCE_LOG_FACING) {
            level.setBlock(ENTRANCE_LOG_POS, mainState, 3);
        }

        boolean missingExtension = false;
        for (BlockPos pos : entranceLogPositions()) {
            if (pos.equals(ENTRANCE_LOG_POS)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(block)
                    || state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.EXTENSION
                    || state.getValue(MapDecorStaticBlock.FACING) != ENTRANCE_LOG_FACING) {
                missingExtension = true;
                break;
            }
        }
        if (missingExtension) {
            block.setPlacedBy(level, ENTRANCE_LOG_POS, mainState, null, ItemStack.EMPTY);
        }
    }

    private static void ensureEntranceOpeningClear(ServerLevel level) {
        for (int x = ENTRY_OPENING_MIN_X; x <= ENTRY_OPENING_MAX_X; x++) {
            for (int y = ENTRY_OPENING_MIN_Y; y <= ENTRY_OPENING_MAX_Y; y++) {
                for (int z = ENTRY_OPENING_MIN_Z; z <= ENTRY_OPENING_MAX_Z; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BARRIER) || state.is(ModBlocks.MINE_BARRIER.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    @Nullable
    private static BlockPos findEntranceMain(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ResourceClumpBlock clump)) {
            return null;
        }
        return clump.findMainPos(level, pos, state);
    }

    private static Iterable<BlockPos> entranceLogPositions() {
        java.util.List<BlockPos> positions = new java.util.ArrayList<>(18);
        for (int dy = 0; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    positions.add(ENTRANCE_LOG_POS.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }
}
