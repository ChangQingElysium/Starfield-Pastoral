package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class NightMarketCoffeeService {
    public static final String TARGET_ID = "night_market_coffee";
    public static final String MARKER_TAG = "sdv_festival_marker:night_market_coffee";
    public static final String QUESTION_CONTEXT = "night_market_coffee";
    public static final BlockPos INTERACTION_BOTTOM_POS = new BlockPos(35, 60, 159);
    public static final BlockPos INTERACTION_TOP_POS = new BlockPos(35, 61, 159);

    private static final String CHOICE_YES = "yes";

    private NightMarketCoffeeService() {
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
    }

    public static void open(ServerPlayer player) {
        if (player == null || !isStardewLevel(player.serverLevel())) {
            return;
        }
        if (!FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.coffee.closed");
            return;
        }
        if (hasReceivedCoffee(player)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.coffee.enjoy");
            return;
        }

        Component question = Component.translatable("stardewcraft.night_market.coffee.question");
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
        if (hasReceivedCoffee(player)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.coffee.enjoy");
            return;
        }

        PlayerStardewData data = PlayerStardewDataAPI.getData(player);
        data.addMailFlag(coffeeFlag());
        PlayerDataEventHandler.syncPlayerData(player, data);

        ItemStack coffee = new ItemStack(ModItems.COFFEE.get());
        if (!player.addItem(coffee.copy())) {
            player.drop(coffee.copy(), false);
        }
        HoldUpItemPayload.sendTo(player, coffee);
        ItemPickupHudPacket.sendTo(player, coffee, 1, false);
    }

    private static boolean hasReceivedCoffee(ServerPlayer player) {
        return PlayerStardewDataAPI.getData(player).hasMailFlag(coffeeFlag());
    }

    private static String coffeeFlag() {
        StardewTimeManager time = StardewTimeManager.get();
        int festivalDay = Math.max(1, Math.min(3,
            FestivalService.getDayOfPassiveFestival(NightMarketPainterService.FESTIVAL_ID)));
        return "NightMarketYear" + time.getCurrentYear() + "Day" + festivalDay + "_freeCoffee";
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

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(
        String id,
        Component label,
        ServerPlayer player
    ) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
            id,
            Component.Serializer.toJson(label, player.registryAccess())
        );
    }

    private static boolean isStardewLevel(ServerLevel level) {
        return level != null && ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }
}
