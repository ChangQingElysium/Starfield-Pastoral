package com.stardew.craft.api.v1.secretnote;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Data-pack definition for one discoverable secret note. */
public record StardewSecretNoteDefinition(
        int vanillaNumber,
        int displayNumber,
        int sortOrder,
        String text,
        int imageIndex,
        List<GiftReveal> giftReveals,
        boolean obtainable,
        String implementationStatus
) {
    public static final Codec<StardewSecretNoteDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "vanilla_number", -1)
                    .forGetter(StardewSecretNoteDefinition::vanillaNumber),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "display_number", -1)
                    .forGetter(StardewSecretNoteDefinition::displayNumber),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.INT, "sort_order", 0)
                    .forGetter(StardewSecretNoteDefinition::sortOrder),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "text", "")
                    .forGetter(StardewSecretNoteDefinition::text),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(-1, Integer.MAX_VALUE), "image_index", -1)
                    .forGetter(StardewSecretNoteDefinition::imageIndex),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(GiftReveal.CODEC.listOf(), "gift_reveals", List.of())
                    .forGetter(StardewSecretNoteDefinition::giftReveals),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "obtainable", true)
                    .forGetter(StardewSecretNoteDefinition::obtainable),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "implementation_status", "complete")
                    .forGetter(StardewSecretNoteDefinition::implementationStatus)
    ).apply(instance, StardewSecretNoteDefinition::new));

    public StardewSecretNoteDefinition {
        displayNumber = displayNumber < 0 ? vanillaNumber : displayNumber;
        text = text == null ? "" : text.trim();
        giftReveals = List.copyOf(giftReveals == null ? List.of() : giftReveals);
        implementationStatus = implementationStatus == null ? "complete" : implementationStatus.trim();
        if (text.isEmpty() == (imageIndex < 0)) {
            throw new IllegalArgumentException("secret note must define exactly one of text or image_index");
        }
        if (!obtainable && implementationStatus.isEmpty()) {
            throw new IllegalArgumentException("unobtainable secret note must explain its implementation_status");
        }
    }

    /** Exact SDV %revealtaste:Npc:ObjectId token. */
    public record GiftReveal(String npc, String objectId) {
        public static final Codec<GiftReveal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("npc").forGetter(GiftReveal::npc),
                Codec.STRING.fieldOf("object_id").forGetter(GiftReveal::objectId)
        ).apply(instance, GiftReveal::new));

        public GiftReveal {
            npc = npc == null ? "" : npc.trim();
            objectId = objectId == null ? "" : objectId.trim();
            if (npc.isEmpty() || objectId.isEmpty()) {
                throw new IllegalArgumentException("gift reveal npc and object_id cannot be blank");
            }
        }
    }
}
