package com.stardew.craft.mail;

import java.util.regex.Pattern;

/** Pure parser for the display-only parts of vanilla SDV mail strings. */
public final class MailTextSyntax {
    private static final String TITLE_SEPARATOR = "[#]";
    private static final Pattern NON_BODY_COMMAND = Pattern.compile(
            "(?s)(?:%item|%action)\\b.*?%%"
    );

    private MailTextSyntax() {
    }

    public static String body(String localizedText) {
        if (localizedText == null || localizedText.isEmpty()) return "";
        int title = localizedText.indexOf(TITLE_SEPARATOR);
        String body = title >= 0 ? localizedText.substring(0, title) : localizedText;
        return NON_BODY_COMMAND.matcher(body).replaceAll("").trim();
    }

    public static String title(String localizedText) {
        if (localizedText == null || localizedText.isEmpty()) return "???";
        int separator = localizedText.indexOf(TITLE_SEPARATOR);
        if (separator < 0) return "???";
        String title = localizedText.substring(separator + TITLE_SEPARATOR.length()).trim();
        return title.isEmpty() ? "???" : title;
    }
}
