package com.stardew.craft.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerGenderTextTest {
    @Test
    void resolvesEveryInlineTokenForMaleProfile() {
        assertEquals("Dear farmer, welcome",
                PlayerGenderText.resolve("${Dear^Dear}$ farmer, ${welcome^welcome}$", true));
    }

    @Test
    void resolvesEveryInlineTokenForFemaleProfile() {
        assertEquals("Царица полей и Поселянка",
                PlayerGenderText.resolve("${Царь полей^Царица полей}$ и ${Поселянин^Поселянка}$", false));
    }

    @Test
    void preservesMalformedTokenInsteadOfDroppingText() {
        assertEquals("before ${male^female after",
                PlayerGenderText.resolve("before ${male^female after", true));
    }

    @Test
    void supportsVanillaAlternateSeparators() {
        assertEquals("Madam", PlayerGenderText.preprocess("Sir¦Madam", false));
        assertEquals("Madam", PlayerGenderText.resolve("${Sir¦Madam}$", false));
    }
}
