package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class NightMarketPainterService {
    public static final String FESTIVAL_ID = "NightMarket";
    public static final String TARGET_ID = "night_market_painter";
    public static final String MARKER_TAG = "sdv_festival_marker:night_market_painter";
    public static final String QUESTION_CONTEXT = "night_market_painter";

    public static final BlockPos INTERACTION_BOTTOM_POS = new BlockPos(81, 60, 153);
    public static final BlockPos INTERACTION_TOP_POS = new BlockPos(81, 61, 153);
    public static final BlockPos PAINTING_MAIN_POS = new BlockPos(82, 61, 153);
    public static final BlockPos PAINTING_EXTENSION_POS = new BlockPos(83, 61, 153);

    private static final int PAINTING_PRICE = 1200;
    private static final String CHOICE_YES = "yes";

    private NightMarketPainterService() {
    }

    public static void install(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        installInteractionBlock(level, INTERACTION_BOTTOM_POS);
        installInteractionBlock(level, INTERACTION_TOP_POS);
        if (isPaintingSold()) {
            removePaintingDisplay(level);
        } else {
            installPaintingDisplay(level);
        }
    }

    public static void cleanup(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        removeInteractionBlock(level, INTERACTION_BOTTOM_POS);
        removeInteractionBlock(level, INTERACTION_TOP_POS);
        removePaintingDisplay(level);
    }

    public static void open(ServerPlayer player) {
        if (player == null || !isStardewLevel(player.serverLevel())) {
            return;
        }
        if (!FestivalService.isPassiveFestivalOpen(FESTIVAL_ID)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.painter.closed");
            return;
        }
        if (isPaintingSold()) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.painter.sold");
            return;
        }

        Component question = Component.translatable("stardewcraft.night_market.painter.question");
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
        if (player == null || !CHOICE_YES.equals(choiceId) || !FestivalService.isPassiveFestivalOpen(FESTIVAL_ID)) {
            return;
        }
        if (isPaintingSold()) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.painter.sold");
            return;
        }
        if (!PlayerStardewDataAPI.removeMoney(player, PAINTING_PRICE)) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.painter.no_money");
            return;
        }

        ItemStack painting = new ItemStack(currentPaintingItem());
        markPaintingSoldForParty(player);
        removePaintingDisplay(player.serverLevel());

        if (!player.addItem(painting.copy())) {
            player.drop(painting.copy(), false);
        }
        ItemPickupHudPacket.sendTo(player, painting, 1, false);
        player.playNotifySound(ModSounds.PURCHASE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.server.getPlayerList().broadcastSystemMessage(
            Component.translatable("stardewcraft.night_market.painter.announcement", player.getName()),
            false
        );
    }

    public static boolean isProtectedDisplayPosition(ServerLevel level, BlockPos pos) {
        if (!isStardewLevel(level) || !FestivalService.isPassiveFestivalOpen(FESTIVAL_ID) || pos == null) {
            return false;
        }
        return (pos.equals(PAINTING_MAIN_POS) || pos.equals(PAINTING_EXTENSION_POS))
            && isNightMarketPainting(level.getBlockState(pos).getBlock());
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

    private static void installPaintingDisplay(ServerLevel level) {
        Block paintingBlock = currentPaintingBlock();
        BlockState expectedMain = paintingBlock.defaultBlockState()
            .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
            .setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
        BlockState expectedExtension = expectedMain.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION);
        if (level.getBlockState(PAINTING_MAIN_POS).equals(expectedMain)
            && level.getBlockState(PAINTING_EXTENSION_POS).equals(expectedExtension)) {
            return;
        }

        removePaintingDisplay(level);
        level.setBlock(PAINTING_MAIN_POS, expectedMain, Block.UPDATE_ALL);
        if (paintingBlock instanceof MapDecorStaticBlock decorBlock) {
            decorBlock.setPlacedBy(level, PAINTING_MAIN_POS, expectedMain, null, ItemStack.EMPTY);
        }
    }

    private static void removePaintingDisplay(ServerLevel level) {
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            if (isNightMarketPainting(level.getBlockState(PAINTING_MAIN_POS).getBlock())) {
                level.setBlock(PAINTING_MAIN_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (isNightMarketPainting(level.getBlockState(PAINTING_EXTENSION_POS).getBlock())) {
                level.setBlock(PAINTING_EXTENSION_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    private static boolean isPaintingSold() {
        String flag = paintingSoldFlag();
        return PlayerDataManager.get().getAllPlayerData().values().stream().anyMatch(data -> data.hasMailFlag(flag));
    }

    private static void markPaintingSoldForParty(ServerPlayer purchaser) {
        PlayerDataManager manager = PlayerDataManager.get();
        manager.getOrCreateData(purchaser.getUUID());
        for (ServerPlayer online : purchaser.server.getPlayerList().getPlayers()) {
            manager.getOrCreateData(online.getUUID());
        }
        String flag = paintingSoldFlag();
        for (PlayerStardewData data : manager.getAllPlayerData().values()) {
            data.addMailFlag(flag);
        }
        manager.setDirty();
        for (ServerPlayer online : purchaser.server.getPlayerList().getPlayers()) {
            PlayerDataEventHandler.syncPlayerData(online, manager.getData(online.getUUID()));
        }
    }

    private static String paintingSoldFlag() {
        StardewTimeManager time = StardewTimeManager.get();
        return "NightMarketYear" + time.getCurrentYear() + "Day" + currentFestivalDay() + "_paintingSold";
    }

    private static int currentPaintingIndex() {
        int yearCycle = Math.floorMod(StardewTimeManager.get().getCurrentYear() - 1, 3);
        return yearCycle * 3 + currentFestivalDay() - 1;
    }

    private static int currentFestivalDay() {
        int day = FestivalService.getDayOfPassiveFestival(FESTIVAL_ID);
        return Math.max(1, Math.min(3, day));
    }

    private static Block currentPaintingBlock() {
        return switch (currentPaintingIndex()) {
            case 1 -> ModBlocks.PORTRAIT_OF_A_MERMAID.get();
            case 2 -> ModBlocks.SOLAR_KINGDOM.get();
            case 3 -> ModBlocks.CLOUDS.get();
            case 4 -> ModBlocks.THOUSAND_YEARS_FROM_NOW.get();
            case 5 -> ModBlocks.THREE_TREES.get();
            case 6 -> ModBlocks.THE_SERPENT.get();
            case 7 -> ModBlocks.TROPICAL_FISH_173.get();
            case 8 -> ModBlocks.LAND_OF_CLAY.get();
            default -> ModBlocks.RED_EAGLE.get();
        };
    }

    private static Item currentPaintingItem() {
        return switch (currentPaintingIndex()) {
            case 1 -> ModItems.PORTRAIT_OF_A_MERMAID.get();
            case 2 -> ModItems.SOLAR_KINGDOM.get();
            case 3 -> ModItems.CLOUDS.get();
            case 4 -> ModItems.THOUSAND_YEARS_FROM_NOW.get();
            case 5 -> ModItems.THREE_TREES.get();
            case 6 -> ModItems.THE_SERPENT.get();
            case 7 -> ModItems.TROPICAL_FISH_173.get();
            case 8 -> ModItems.LAND_OF_CLAY.get();
            default -> ModItems.RED_EAGLE.get();
        };
    }

    private static boolean isNightMarketPainting(Block block) {
        return block == ModBlocks.RED_EAGLE.get()
            || block == ModBlocks.PORTRAIT_OF_A_MERMAID.get()
            || block == ModBlocks.SOLAR_KINGDOM.get()
            || block == ModBlocks.CLOUDS.get()
            || block == ModBlocks.THOUSAND_YEARS_FROM_NOW.get()
            || block == ModBlocks.THREE_TREES.get()
            || block == ModBlocks.THE_SERPENT.get()
            || block == ModBlocks.TROPICAL_FISH_173.get()
            || block == ModBlocks.LAND_OF_CLAY.get();
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
