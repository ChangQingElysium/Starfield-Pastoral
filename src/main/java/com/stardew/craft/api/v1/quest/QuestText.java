package com.stardew.craft.api.v1.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Localizable text stored in a quest definition. */
public record QuestText(String translate, String literal, List<String> args) {
    private static final Codec<QuestText> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "translate", "").forGetter(QuestText::translate),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "literal", "").forGetter(QuestText::literal),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING.listOf(), "args", List.of()).forGetter(QuestText::args)
    ).apply(instance, QuestText::new));

    public static final Codec<QuestText> CODEC = com.stardew.craft.port.PortCodecs.withAlternative(
            com.stardew.craft.port.PortCodecs.validate(OBJECT_CODEC, QuestText::validate),
            Codec.STRING.xmap(QuestText::translated, QuestText::translate)
    );

    public QuestText {
        translate = translate == null ? "" : translate;
        literal = literal == null ? "" : literal;
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static QuestText translated(String key) {
        return new QuestText(key, "", List.of());
    }

    public static QuestText empty() {
        return new QuestText("", "", List.of());
    }

    public Component component() {
        if (!translate.isBlank()) {
            return Component.translatable(translate, args.toArray());
        }
        return Component.literal(literal);
    }

    public boolean isEmpty() {
        return translate.isBlank() && literal.isBlank();
    }

    private static DataResult<QuestText> validate(QuestText text) {
        if (!text.translate().isBlank() && !text.literal().isBlank()) {
            return DataResult.error(() -> "Quest text cannot define both translate and literal");
        }
        return DataResult.success(text);
    }
}
