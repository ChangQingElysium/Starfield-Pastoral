package com.stardew.craft.client.gui.common;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

public record StardewQuestionDialogSpec(
    Component question,
    List<Component> responses,
    IntConsumer onAnswer,
    int defaultSelectedIndex,
    int dialogWidth,
    int dialogBaseHeight,
    int dialogBottomMargin
) {
    public static final int DEFAULT_DIALOG_WIDTH = 1200;
    public static final int DEFAULT_DIALOG_BASE_HEIGHT = 384;
    public static final int DEFAULT_DIALOG_BOTTOM_MARGIN = 64;

    public StardewQuestionDialogSpec {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(responses, "responses");
        Objects.requireNonNull(onAnswer, "onAnswer");
        if (responses.isEmpty()) {
            throw new IllegalArgumentException("responses must not be empty");
        }
        if (defaultSelectedIndex < -1 || defaultSelectedIndex >= responses.size()) {
            throw new IllegalArgumentException("defaultSelectedIndex out of range");
        }
        if (dialogWidth <= 0 || dialogBaseHeight <= 0 || dialogBottomMargin < 0) {
            throw new IllegalArgumentException("invalid dialog dimensions");
        }
    }

    public static StardewQuestionDialogSpec of(Component question, List<Component> responses, IntConsumer onAnswer, int defaultSelectedIndex) {
        return new StardewQuestionDialogSpec(
            question,
            responses,
            onAnswer,
            defaultSelectedIndex,
            DEFAULT_DIALOG_WIDTH,
            DEFAULT_DIALOG_BASE_HEIGHT,
            DEFAULT_DIALOG_BOTTOM_MARGIN
        );
    }
}
