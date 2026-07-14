package com.stardew.craft.api.v1.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Localizable text stored in a quest definition. */
public record QuestText(String translate, String literal, List<String> args) {
    private static final Codec<QuestText> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("translate", "").forGetter(QuestText::translate),
            Codec.STRING.optionalFieldOf("literal", "").forGetter(QuestText::literal),
            Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(QuestText::args)
    ).apply(instance, QuestText::new));

    public static final Codec<QuestText> CODEC = Codec.withAlternative(
            OBJECT_CODEC.validate(QuestText::validate),
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
