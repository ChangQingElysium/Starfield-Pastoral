package com.stardew.craft.weather;

import java.util.Locale;

/** Canonicalizes datapack weather aliases to the internal Stardew weather names. */
public final class WeatherTypeNormalizer {
    private WeatherTypeNormalizer() {
    }

    public static String normalize(String weather) {
        if (weather == null) {
            return "";
        }
        return switch (weather.toLowerCase(Locale.ROOT)) {
            case "sunny", "sun" -> "Sun";
            case "rainy", "rain" -> "Rain";
            case "stormy", "storm", "lightning" -> "Storm";
            case "snowy", "snow" -> "Snow";
            case "windy", "wind", "windspring" -> "WindSpring";
            case "windfall" -> "WindFall";
            case "festival" -> "Festival";
            default -> weather;
        };
    }
}
