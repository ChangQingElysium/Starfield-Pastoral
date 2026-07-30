package com.stardew.craft.client.gui.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTextWrapperTest {
    @Test
    void keepsNumberAndFollowingChineseTextOnTheSameLineWhenItFits() {
        List<String> lines = DialogueTextWrapper.wrap(
                "我10天之内就完成了这个任务",
                100,
                String::length
        );

        assertEquals(List.of("我10天之内就完成了这个任务"), lines);
    }

    @Test
    void fillsAvailableWidthWithoutBreakingAtTheNumber() {
        List<String> lines = DialogueTextWrapper.wrap(
                "我10天之内就完成了这个任务",
                6,
                String::length
        );

        assertEquals(List.of("我10天之内", "就完成了这个", "任务"), lines);
    }

    @Test
    void preservesExplicitLineBreaks() {
        List<String> lines = DialogueTextWrapper.wrap(
                "第一行\n\n第二行",
                100,
                String::length
        );

        assertEquals(List.of("第一行", "", "第二行"), lines);
    }
}
