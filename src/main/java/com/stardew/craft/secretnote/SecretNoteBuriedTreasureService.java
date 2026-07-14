package com.stardew.craft.secretnote;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Source-parity one-time hoe rewards for vanilla Secret Notes 16, 17 and 18. */
public final class SecretNoteBuriedTreasureService {
    private SecretNoteBuriedTreasureService() {}

    public static boolean isConfiguredDigSurface(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null
                || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        BuriedTreasure treasure = treasureAt(pos);
        if (treasure == null) return false;
        return treasure.vanillaNumber() == 18
                ? state.is(Blocks.SAND)
                : state.is(ModBlocks.YELLOW_DIRT.get());
    }

    public static boolean canDig(ServerPlayer player, BlockPos pos, BlockState state) {
        if (player == null || !isConfiguredDigSurface(player.level(), pos, state)) return false;
        BuriedTreasure treasure = treasureAt(pos);
        if (treasure == null) return false;
        ResourceLocation noteId = SecretNoteRegistry.byVanillaNumber(treasure.vanillaNumber());
        if (noteId == null) return false;
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasSeenSecretNote(noteId.toString()) && !data.hasMailFlag(treasure.completionFlag());
    }

    /** Returns true only when this strike consumed an eligible one-time secret-note reward. */
    public static boolean tryDig(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (level == null || player == null || !canDig(player, pos, state)) return false;
        BuriedTreasure treasure = treasureAt(pos);
        if (treasure == null) return false;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addMailFlag(treasure.completionFlag());
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);

        ItemStack reward = switch (treasure.vanillaNumber()) {
            case 16 -> new ItemStack(ModItems.TREASURE_CHEST.get());
            case 17 -> new ItemStack(ModItems.STRANGE_DOLL_GREEN.get());
            case 18 -> new ItemStack(ModItems.STRANGE_DOLL_YELLOW.get());
            default -> ItemStack.EMPTY;
        };
        if (!reward.isEmpty()) {
            Block.popResource(level, pos.above(), reward);
        }
        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    static BuriedTreasure treasureAt(BlockPos pos) {
        if (inside(pos, -35, 84, -210, -25, 84, -204)) {
            return new BuriedTreasure(16, "SecretNote16_done");
        }
        if (inside(pos, 101, 63, -74, 103, 63, -70)) {
            return new BuriedTreasure(17, "SecretNote17_done");
        }
        if (inside(pos, -192, 63, -147, -178, 63, -140)) {
            return new BuriedTreasure(18, "SecretNote18_done");
        }
        return null;
    }

    private static boolean inside(BlockPos pos, int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
        return pos != null
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    record BuriedTreasure(int vanillaNumber, String completionFlag) {}
}
