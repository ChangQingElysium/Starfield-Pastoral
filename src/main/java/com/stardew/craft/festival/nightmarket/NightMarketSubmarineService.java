package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class NightMarketSubmarineService {
    public static final String ENTRANCE_TARGET_ID = "night_market_submarine_enter";
    public static final String EXIT_TARGET_ID = "night_market_submarine_exit";
    public static final String QUESTION_CONTEXT = "night_market_submarine";

    public static final BlockPos ENTRANCE_BOTTOM_POS = new BlockPos(26, 60, 158);
    public static final BlockPos ENTRANCE_TOP_POS = new BlockPos(26, 61, 158);
    public static final BlockPos SUBMARINE_ARRIVAL_POS = new BlockPos(28, 44, 161);
    public static final BlockPos EXIT_BOTTOM_POS = new BlockPos(28, 44, 162);
    public static final BlockPos EXIT_TOP_POS = new BlockPos(28, 45, 162);
    public static final BlockPos BEACH_RETURN_POS = new BlockPos(26, 60, 159);

    private static final BlockPos SUBMARINE_CORNER_A = new BlockPos(13, 52, 142);
    private static final BlockPos SUBMARINE_CORNER_B = new BlockPos(42, 41, 167);
    private static final AABB SUBMARINE_BOUNDS = inclusiveBox(SUBMARINE_CORNER_A, SUBMARINE_CORNER_B);
    private static final String ENTRANCE_MARKER_TAG = "sdv_festival_marker:night_market_submarine_enter";
    private static final String EXIT_MARKER_TAG = "sdv_festival_marker:night_market_submarine_exit";
    private static final int TOUR_PRICE = 1000;
    private static final float NORTH_YAW = 180.0F;
    private static final float SOUTH_YAW = 0.0F;
    private static final String CHOICE_YES = "yes";

    private NightMarketSubmarineService() {
    }

    public static void install(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        installInteractionBlock(level, ENTRANCE_BOTTOM_POS, ENTRANCE_TARGET_ID, ENTRANCE_MARKER_TAG);
        installInteractionBlock(level, ENTRANCE_TOP_POS, ENTRANCE_TARGET_ID, ENTRANCE_MARKER_TAG);
        installInteractionBlock(level, EXIT_BOTTOM_POS, EXIT_TARGET_ID, EXIT_MARKER_TAG);
        installInteractionBlock(level, EXIT_TOP_POS, EXIT_TARGET_ID, EXIT_MARKER_TAG);
    }

    public static void cleanup(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (isInsideSubmarineBounds(player.blockPosition())) {
                teleportToBeach(player);
            }
        }
        removeInteractionBlock(level, ENTRANCE_BOTTOM_POS, ENTRANCE_TARGET_ID);
        removeInteractionBlock(level, ENTRANCE_TOP_POS, ENTRANCE_TARGET_ID);
        removeInteractionBlock(level, EXIT_BOTTOM_POS, EXIT_TARGET_ID);
        removeInteractionBlock(level, EXIT_TOP_POS, EXIT_TARGET_ID);
    }

    public static void openEntrance(ServerPlayer player) {
        if (player == null || !isStardewLevel(player.serverLevel())
                || !FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)) {
            return;
        }
        Component question = Component.translatable("stardewcraft.night_market.submarine.question");
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
            QUESTION_CONTEXT,
            0,
            "",
            Component.Serializer.toJson(question, player.registryAccess()),
            List.of(
                response(CHOICE_YES, Component.translatable("stardewcraft.dialog.yes"), player),
                response("no", Component.translatable("stardewcraft.dialog.no"), player)
            )
        ));
    }

    public static void handleQuestionResponse(ServerPlayer player, String choiceId) {
        if (player == null || !CHOICE_YES.equals(choiceId)
                || !FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)) {
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, TOUR_PRICE)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.submarine.no_money");
            return;
        }
        ModTeleport.to(player, player.serverLevel(), SUBMARINE_ARRIVAL_POS, NORTH_YAW, 0.0F);
        player.fallDistance = 0.0F;
    }

    public static void openExit(ServerPlayer player) {
        if (player == null || !isStardewLevel(player.serverLevel())) {
            return;
        }
        teleportToBeach(player);
    }

    public static boolean isInsideSubmarineBounds(BlockPos pos) {
        return pos != null
            && pos.getX() >= (int) SUBMARINE_BOUNDS.minX && pos.getX() < (int) SUBMARINE_BOUNDS.maxX
            && pos.getY() >= (int) SUBMARINE_BOUNDS.minY && pos.getY() < (int) SUBMARINE_BOUNDS.maxY
            && pos.getZ() >= (int) SUBMARINE_BOUNDS.minZ && pos.getZ() < (int) SUBMARINE_BOUNDS.maxZ;
    }

    private static void teleportToBeach(ServerPlayer player) {
        ModTeleport.to(player, player.serverLevel(), BEACH_RETURN_POS, SOUTH_YAW, 0.0F);
        player.fallDistance = 0.0F;
    }

    private static void installInteractionBlock(
            ServerLevel level,
            BlockPos pos,
            String targetId,
            String markerTag) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(targetId, markerTag);
        }
    }

    private static void removeInteractionBlock(ServerLevel level, BlockPos pos, String targetId) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(
            String id,
            Component label,
            ServerPlayer player) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
    }

    private static AABB inclusiveBox(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static boolean isStardewLevel(ServerLevel level) {
        return level != null && ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }
}
