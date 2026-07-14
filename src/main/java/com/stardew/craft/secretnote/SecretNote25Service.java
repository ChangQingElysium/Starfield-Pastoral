package com.stardew.craft.secretnote;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Source-faithful server rules for Secret Note 25 and Caroline's necklace. */
public final class SecretNote25Service {
    public static final String NOTE_ID = "stardewcraft:25";
    public static final String NECKLACE_FOUND_FLAG = "carolinesNecklace";

    private static final BlockPos SPA_POOL_MIN = new BlockPos(-16, 84, -179);
    private static final BlockPos SPA_POOL_MAX = new BlockPos(-11, 86, -174);

    private SecretNote25Service() {
    }

    /**
     * Mirrors {@code Railroad.getFish}: after note 25, the first catch from the
     * bath-house pool is the necklace and activates both mutually exclusive quests.
     */
    public static Optional<ItemStack> tryCreateNecklaceCatch(
            ServerPlayer player, ServerLevel level, BlockPos hookPos) {
        if (player == null || level == null || hookPos == null
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || !isSpaPoolHookPosition(hookPos)) {
            return Optional.empty();
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        QuestManager quests = QuestManager.of(player);
        if (quests == null
                || !data.hasSeenSecretNote(NOTE_ID)
                || data.hasMailFlag(NECKLACE_FOUND_FLAG)) {
            return Optional.empty();
        }

        data.addMailFlag(NECKLACE_FOUND_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);
        quests.acceptQuest("128", player);
        quests.acceptQuest("129", player);
        return Optional.of(new ItemStack(ModItems.ORNATE_NECKLACE.get()));
    }

    static boolean isSpaPoolHookPosition(BlockPos pos) {
        return pos.getX() >= SPA_POOL_MIN.getX() && pos.getX() <= SPA_POOL_MAX.getX()
                && pos.getY() >= SPA_POOL_MIN.getY() && pos.getY() <= SPA_POOL_MAX.getY()
                && pos.getZ() >= SPA_POOL_MIN.getZ() && pos.getZ() <= SPA_POOL_MAX.getZ();
    }
}
