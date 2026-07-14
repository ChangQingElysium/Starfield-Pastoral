package com.stardew.craft.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MailTextSyntaxTest {
    @Test
    void stripsItemCommandAndCollectionTitleFromMomLetter() {
        String raw = "亲爱的@：^\n这是给你的。 %item money 500 501 %%[#]妈妈的信 #2";

        String body = MailTextSyntax.body(raw);

        assertEquals("亲爱的@：^\n这是给你的。", body);
        assertFalse(body.contains("%item"));
        assertFalse(body.contains("[#]"));
        assertEquals("妈妈的信 #2", MailTextSyntax.title(raw));
    }

    @Test
    void stripsActionCommandsWithoutConsumingVisibleText() {
        assertEquals("before  after", MailTextSyntax.body("before %action addQuest 20 %% after[#]title"));
    }

    @Test
    void usesVanillaFallbackWhenLetterHasNoCollectionTitle() {
        assertEquals("???", MailTextSyntax.title("body only"));
    }
}
