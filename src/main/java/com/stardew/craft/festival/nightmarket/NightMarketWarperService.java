package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.festival.FestivalFarmWarperDisplayService;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.totem.TeleportTotemItem;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class NightMarketWarperService {
    public static final String TARGET_ID = "night_market_warper";
    public static final String QUESTION_CONTEXT = "night_market_warper";
    public static final BlockPos ANCHOR_POS = new BlockPos(68, 61, 152);

    private static final String DISPLAY_MARKER_TAG = "sdv_festival_marker:night_market_warper_display";
    public static final String INTERACTION_MARKER_TAG = "sdv_festival_marker:night_market_warper";
    private static final Vec3 DISPLAY_POSITION = Vec3.atLowerCornerOf(ANCHOR_POS);
    private static final int PRICE = 250;
    private static final String CHOICE_YES = "yes";

    private NightMarketWarperService() {
    }

    public static void install(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        FestivalFarmWarperDisplayService.spawn(level, DISPLAY_MARKER_TAG, DISPLAY_POSITION);
        spawnInteraction(level);
    }

    public static void cleanup(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        FestivalFarmWarperDisplayService.remove(level, DISPLAY_MARKER_TAG, DISPLAY_POSITION);
        removeInteraction(level);
    }

    public static void open(ServerPlayer player) {
        if (player == null || !FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID)) {
            return;
        }
        Component question = Component.translatable("stardewcraft.night_market.warper.question");
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
        if (PlayerStardewDataAPI.getMoney(player) < PRICE) {
            ObjectDialogueService.show(player, "stardewcraft.night_market.warper.no_money");
            return;
        }
        if (!(ModItems.WARP_TOTEM_FARM.get() instanceof TeleportTotemItem totem)
            || totem.getTotemType() != TotemType.FARM) {
            return;
        }
        if (PlayerStardewDataAPI.removeMoney(player, PRICE)) {
            totem.performFreeWarp(player);
        }
    }

    private static void spawnInteraction(ServerLevel level) {
        if (hasInteraction(level)) {
            return;
        }
        try {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:interaction");
            tag.putFloat("width", 1.0F);
            tag.putFloat("height", 1.0F);
            tag.putBoolean("response", true);

            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(ANCHOR_POS.getX() + 0.5D));
            position.add(DoubleTag.valueOf(ANCHOR_POS.getY()));
            position.add(DoubleTag.valueOf(ANCHOR_POS.getZ() + 0.5D));
            tag.put("Pos", position);

            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(INTERACTION_MARKER_TAG));
            tag.put("Tags", tags);

            Entity entity = EntityType.loadEntityRecursive(tag, level, value -> value);
            if (entity != null) {
                level.addFreshEntity(entity);
            }
        } catch (Exception exception) {
            StardewCraft.LOGGER.error("[NIGHT_MARKET] Failed to spawn farm warper interaction", exception);
        }
    }

    private static void removeInteraction(ServerLevel level) {
        level.getEntitiesOfClass(Entity.class, interactionBounds(),
            entity -> entity.getTags().contains(INTERACTION_MARKER_TAG))
            .forEach(Entity::discard);
    }

    private static boolean hasInteraction(ServerLevel level) {
        return !level.getEntitiesOfClass(Entity.class, interactionBounds(),
            entity -> entity.getTags().contains(INTERACTION_MARKER_TAG)).isEmpty();
    }

    private static AABB interactionBounds() {
        return new AABB(ANCHOR_POS).inflate(1.0D);
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
