package com.stardew.craft.weather;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherTypeNormalizerTest {
    @ParameterizedTest
    @CsvSource({
            "sunny, Sun",
            "sun, Sun",
            "rainy, Rain",
            "rain, Rain",
            "stormy, Storm",
            "lightning, Storm",
            "snowy, Snow",
            "windy, WindSpring",
            "windfall, WindFall",
            "festival, Festival"
    })
    void canonicalizesDatapackAliases(String input, String expected) {
        assertEquals(expected, WeatherTypeNormalizer.normalize(input));
    }
}
