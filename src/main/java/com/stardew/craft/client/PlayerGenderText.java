package com.stardew.craft.client;

/** Resolves Stardew Valley player-gender tokens such as {@code ${sir^madam}$}. */
public final class PlayerGenderText {
    private PlayerGenderText() {
    }

    /** Resolves tokens using the gender selected in the local player profile. */
    public static String resolve(String text) {
        return resolve(text, ClientPlayerDataCache.isPlayerMale());
    }

    /**
     * Applies the gender preprocessing Stardew Valley performs whenever it loads localized text:
     * inline blocks first, followed by the alternate top-level {@code ¦} split.
     */
    public static String preprocess(String text) {
        return preprocess(text, ClientPlayerDataCache.isPlayerMale());
    }

    public static String preprocess(String text, boolean male) {
        String resolved = resolve(text, male);
        if (resolved == null) {
            return null;
        }
        int separator = resolved.indexOf('¦');
        return separator < 0
                ? resolved
                : male ? resolved.substring(0, separator) : resolved.substring(separator + 1);
    }

    /** Resolves every {@code ${male^female}$} token in the supplied text. */
    public static String resolve(String text, boolean male) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        StringBuilder resolved = new StringBuilder(text.length());
        int position = 0;
        while (position < text.length()) {
            int start = text.indexOf("${", position);
            if (start < 0) {
                resolved.append(text, position, text.length());
                break;
            }

            resolved.append(text, position, start);
            int end = text.indexOf("}$", start + 2);
            if (end < 0) {
                resolved.append(text, start, text.length());
                break;
            }

            String alternatives = text.substring(start + 2, end);
            int separator = alternatives.indexOf('¦');
            if (separator < 0) {
                separator = alternatives.indexOf('^');
            }
            if (separator >= 0) {
                resolved.append(male
                        ? alternatives.substring(0, separator)
                        : alternatives.substring(separator + 1));
            } else {
                resolved.append(alternatives);
            }
            position = end + 2;
        }
        return resolved.toString();
    }
}
