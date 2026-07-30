package com.stardew.craft.world.interaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionActions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.OpenMailPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.player.PlayerDisplayName;
import com.stardew.craft.qi.MrQiQuestAnchor;
import com.stardew.craft.qi.MrQiQuestInteractionService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** StardewCraft-owned action types used by built-in map definitions. */
public final class BuiltinMapInteractionActions {
    private static boolean bootstrapped;

    private BuiltinMapInteractionActions() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        StardewMapInteractionActions.register(
                id("mr_qi_anchor"),
                MrQiAnchorAction.CODEC,
                (context, data) -> {
                    MrQiQuestInteractionService.interact(
                            context.player(), data.anchor());
                    return InteractionResult.SUCCESS;
                });
        StardewMapInteractionActions.register(
                id("open_letter"),
                OpenLetterAction.CODEC,
                (context, data) -> {
                    String definitionId = context.definitionId() == null
                            ? "unknown"
                            : context.definitionId().toString();
                    PacketDistributor.sendToPlayer(
                            context.player(),
                            new OpenMailPayload(
                                    "map_interaction:" + definitionId,
                                    data.text(),
                                    "",
                                    data.background(),
                                    data.textColor(),
                                    List.of(),
                                    0,
                                    "",
                                    "",
                                    false,
                                    0));
                    return InteractionResult.SUCCESS;
                });
        StardewMapInteractionActions.register(
                id("npc_message"),
                NpcMessageAction.CODEC,
                (context, data) -> {
                    StardewNpcEntity npc = findNearbyNpc(
                            context.level(),
                            context.player().blockPosition(),
                            data);
                    if (npc == null) {
                        ObjectDialogueService.show(
                                context.player(),
                                data.fallback().component());
                        return InteractionResult.SUCCESS;
                    }

                    if (data.announceSnooping()) {
                        context.level().getServer().getPlayerList()
                                .broadcastSystemMessage(
                                        Component.translatable(
                                                "stardewcraft.strings_ui.chat_caught_snooping",
                                                PlayerDisplayName.get(
                                                        context.player()),
                                                npc.getName()),
                                        false);
                    }
                    int friendshipPoints = NpcFriendshipDataManager
                            .get(context.level())
                            .getPointsForNpc(
                                    context.player().getUUID(),
                                    data.npc());
                    PacketDistributor.sendToPlayer(
                            context.player(),
                            new OpenNpcDialogueScreenPayload(
                                    data.npc(),
                                    data.nearby().npcPayloadText(
                                            context.level()),
                                    friendshipPoints));
                    return InteractionResult.SUCCESS;
                });
        bootstrapped = true;
    }

    private static StardewNpcEntity findNearbyNpc(
            ServerLevel level,
            net.minecraft.core.BlockPos playerPos,
            NpcMessageAction data
    ) {
        double radius = data.radius();
        int verticalRadius = data.verticalRadius();
        AABB search = new AABB(
                playerPos.getX() - radius,
                playerPos.getY() - verticalRadius,
                playerPos.getZ() - radius,
                playerPos.getX() + radius + 1.0,
                playerPos.getY() + verticalRadius + 1.0,
                playerPos.getZ() + radius + 1.0);
        return level.getEntitiesOfClass(
                        StardewNpcEntity.class,
                        search,
                        npc -> data.npc().equalsIgnoreCase(npc.getNpcId())
                                && Math.abs(
                                        npc.blockPosition().getY()
                                                - playerPos.getY())
                                        <= verticalRadius
                                && horizontalDistanceSqr(npc, playerPos)
                                        <= radius * radius)
                .stream()
                .min(Comparator.comparingDouble(
                        npc -> horizontalDistanceSqr(npc, playerPos)))
                .orElse(null);
    }

    private static double horizontalDistanceSqr(
            StardewNpcEntity npc,
            net.minecraft.core.BlockPos playerPos
    ) {
        double dx = npc.getX() - (playerPos.getX() + 0.5);
        double dz = npc.getZ() - (playerPos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }

    public record MrQiAnchorAction(MrQiQuestAnchor anchor) {
        public static final Codec<MrQiAnchorAction> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.comapFlatMap(
                                        MrQiAnchorAction::decodeAnchor,
                                        value -> value.name()
                                                .toLowerCase(Locale.ROOT))
                                .fieldOf("anchor")
                                .forGetter(
                                        MrQiAnchorAction::anchor)
                ).apply(instance, MrQiAnchorAction::new));

        private static DataResult<MrQiQuestAnchor> decodeAnchor(
                String value
        ) {
            try {
                return DataResult.success(
                        MrQiQuestAnchor.valueOf(
                                value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() ->
                        "Unknown Mr Qi map anchor: " + value);
            }
        }
    }

    /**
     * Opens the existing Stardew-style letter viewer from a data-pack map
     * interaction. {@code text} may be either literal letter text or a client
     * translation key.
     */
    public record OpenLetterAction(
            String text,
            int background,
            String textColor
    ) {
        public static final Codec<OpenLetterAction> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("text")
                                .forGetter(OpenLetterAction::text),
                        Codec.intRange(0, 7)
                                .optionalFieldOf("background", 0)
                                .forGetter(OpenLetterAction::background),
                        Codec.STRING.optionalFieldOf("text_color", "")
                                .forGetter(OpenLetterAction::textColor)
                ).apply(instance, OpenLetterAction::new));

        public OpenLetterAction {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "open_letter text must not be blank");
            }
            textColor = textColor == null ? "" : textColor;
        }
    }

    /**
     * Original {@code NPCMessage}: show portrait dialogue when the named NPC
     * is nearby on the same 3D interior layer, otherwise show object text.
     */
    public record NpcMessageAction(
            String npc,
            LocalizedText nearby,
            LocalizedText fallback,
            double radius,
            int verticalRadius,
            boolean announceSnooping
    ) {
        private static final Codec<NpcMessageAction> RAW_CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.fieldOf("npc")
                                .forGetter(NpcMessageAction::npc),
                        LocalizedText.CODEC.fieldOf("nearby")
                                .forGetter(NpcMessageAction::nearby),
                        LocalizedText.CODEC.fieldOf("fallback")
                                .forGetter(NpcMessageAction::fallback),
                        Codec.doubleRange(0.0, 128.0)
                                .optionalFieldOf("radius", 14.0)
                                .forGetter(NpcMessageAction::radius),
                        Codec.intRange(0, 32)
                                .optionalFieldOf("vertical_radius", 4)
                                .forGetter(
                                        NpcMessageAction::verticalRadius),
                        Codec.BOOL.optionalFieldOf(
                                        "announce_snooping", false)
                                .forGetter(
                                        NpcMessageAction::announceSnooping)
                ).apply(instance, NpcMessageAction::new));
        public static final Codec<NpcMessageAction> CODEC =
                RAW_CODEC.validate(NpcMessageAction::validate);

        private static DataResult<NpcMessageAction> validate(
                NpcMessageAction value
        ) {
            String normalized = value.npc() == null
                    ? ""
                    : value.npc().trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_.-]+")) {
                return DataResult.error(() ->
                        "npc_message npc must be a non-empty logical NPC id");
            }
            return DataResult.success(new NpcMessageAction(
                    normalized,
                    value.nearby(),
                    value.fallback(),
                    value.radius(),
                    value.verticalRadius(),
                    value.announceSnooping()));
        }
    }

    /** Literal or client-localized text used by built-in typed actions. */
    public record LocalizedText(
            String translationKey,
            String fallback,
            String literal
    ) {
        private static final Codec<LocalizedText> RAW_CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.STRING.optionalFieldOf("translate")
                                .forGetter(value -> Optional.ofNullable(
                                        value.translationKey())),
                        Codec.STRING.optionalFieldOf("fallback")
                                .forGetter(value -> Optional.ofNullable(
                                        value.fallback())),
                        Codec.STRING.optionalFieldOf("literal")
                                .forGetter(value -> Optional.ofNullable(
                                        value.literal()))
                ).apply(instance, (translate, fallback, literal) ->
                        new LocalizedText(
                                translate.orElse(null),
                                fallback.orElse(null),
                                literal.orElse(null))));
        public static final Codec<LocalizedText> CODEC =
                RAW_CODEC.validate(LocalizedText::validate);

        private static DataResult<LocalizedText> validate(
                LocalizedText value
        ) {
            String translation = blankToNull(value.translationKey());
            String literal = blankToNull(value.literal());
            String fallback = value.fallback() == null
                    ? null
                    : value.fallback();
            boolean hasTranslation = translation != null;
            boolean hasLiteral = literal != null;
            if (hasTranslation == hasLiteral) {
                return DataResult.error(() ->
                        "text needs exactly one of translate or literal");
            }
            if (hasLiteral && fallback != null) {
                return DataResult.error(() ->
                        "literal text cannot declare fallback");
            }
            return DataResult.success(
                    new LocalizedText(translation, fallback, literal));
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }

        public Component component() {
            if (literal != null) {
                return Component.literal(literal);
            }
            return fallback == null || fallback.isEmpty()
                    ? Component.translatable(translationKey)
                    : Component.translatableWithFallback(
                            translationKey, fallback);
        }

        public String npcPayloadText(ServerLevel level) {
            return Component.Serializer.toJson(
                    component(), level.registryAccess());
        }
    }
}
