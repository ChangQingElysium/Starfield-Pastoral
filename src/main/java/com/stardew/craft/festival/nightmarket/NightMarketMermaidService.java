package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenNightMarketMermaidPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class NightMarketMermaidService {
    public static final String TARGET_ID = "night_market_mermaid_show";
    public static final String MARKER_TAG = "sdv_festival_marker:night_market_mermaid_show";
    public static final BlockPos INTERACTION_BOTTOM_POS = new BlockPos(105, 60, 148);
    public static final BlockPos INTERACTION_TOP_POS = new BlockPos(105, 61, 148);

    private static final String GOT_PEARL_FLAG = "gotPearl";
    private static final int OPEN_TIME = 1700;
    private static final int CLOSE_TIME = 2430;
    private static final long CLAM_UNLOCK_NANOS = 71_000_000_000L;
    private static final long REWARD_DELAY_NANOS = 3_500_000_000L;
    private static final long SESSION_TIMEOUT_NANOS = 15L * 60L * 1_000_000_000L;
    private static final int[] CORRECT_SEQUENCE = {0, 4, 3, 1, 2};
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private NightMarketMermaidService() {
    }

    public static void install(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        installInteractionBlock(level, INTERACTION_BOTTOM_POS);
        installInteractionBlock(level, INTERACTION_TOP_POS);
    }

    public static void cleanup(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        removeInteractionBlock(level, INTERACTION_BOTTOM_POS);
        removeInteractionBlock(level, INTERACTION_TOP_POS);
        SESSIONS.clear();
    }

    public static void open(ServerPlayer player) {
        if (player == null || !isStardewLevel(player.serverLevel())) {
            return;
        }
        int time = FestivalService.currentTimeOfDay();
        if (!FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)
                || time < OPEN_TIME || time >= CLOSE_TIME) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.mermaid.closed");
            return;
        }

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        SESSIONS.put(player.getUUID(), new Session(System.nanoTime()));
        PacketDistributor.sendToPlayer(player, new OpenNightMarketMermaidPayload(
            data.hasMailFlag(GOT_PEARL_FLAG)
        ));
    }

    public static void handleClam(ServerPlayer player, int clamIndex) {
        if (player == null || clamIndex < 0 || clamIndex > 4
                || !FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        long now = System.nanoTime();
        if (session == null || now - session.openedAtNanos < CLAM_UNLOCK_NANOS) {
            return;
        }

        session.lastFive.addLast(clamIndex);
        while (session.lastFive.size() > CORRECT_SEQUENCE.length) {
            session.lastFive.removeFirst();
        }
        if (session.rewardAtNanos != 0L || !matchesCorrectSequence(session.lastFive)) {
            return;
        }

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (!data.hasMailFlag(GOT_PEARL_FLAG)) {
            session.rewardAtNanos = now + REWARD_DELAY_NANOS;
        }
    }

    public static void close(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.rewardAtNanos == 0L) {
            SESSIONS.remove(player.getUUID());
        }
    }

    public static void tick(ServerLevel level) {
        if (!isStardewLevel(level) || SESSIONS.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry = iterator.next();
            Session session = entry.getValue();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                if (now - session.openedAtNanos >= SESSION_TIMEOUT_NANOS) {
                    iterator.remove();
                }
                continue;
            }
            if (session.rewardAtNanos != 0L && now >= session.rewardAtNanos) {
                grantPearl(player);
                iterator.remove();
            } else if (now - session.openedAtNanos >= SESSION_TIMEOUT_NANOS) {
                iterator.remove();
            }
        }
    }

    private static void grantPearl(ServerPlayer player) {
        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        if (data.hasMailFlag(GOT_PEARL_FLAG)) {
            return;
        }
        data.addMailFlag(GOT_PEARL_FLAG);
        PlayerDataEventHandler.syncPlayerData(player, data);

        ItemStack pearl = new ItemStack(ModItems.PEARL.get());
        if (!player.addItem(pearl.copy())) {
            player.drop(pearl.copy(), false);
        }
        HoldUpItemPayload.sendTo(player, pearl);
        ItemPickupHudPacket.sendTo(player, pearl, 1, false);
    }

    private static boolean matchesCorrectSequence(ArrayDeque<Integer> values) {
        if (values.size() != CORRECT_SEQUENCE.length) {
            return false;
        }
        int index = 0;
        for (int value : values) {
            if (value != CORRECT_SEQUENCE[index++]) {
                return false;
            }
        }
        return true;
    }

    private static void installInteractionBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && TARGET_ID.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(TARGET_ID, MARKER_TAG);
        }
    }

    private static void removeInteractionBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && TARGET_ID.equals(blockEntity.getTargetId())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static boolean isStardewLevel(ServerLevel level) {
        return level != null && ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }

    private static final class Session {
        private final long openedAtNanos;
        private final ArrayDeque<Integer> lastFive = new ArrayDeque<>(5);
        private long rewardAtNanos;

        private Session(long openedAtNanos) {
            this.openedAtNanos = openedAtNanos;
        }
    }
}
