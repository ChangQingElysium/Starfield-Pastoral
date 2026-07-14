package com.stardew.craft.api.v1.festival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Datapack-facing calendar and world metadata for one festival. */
public record StardewFestivalDefinition(
        FestivalKind type,
        String legacyId,
        String displayName,
        String legacyCondition,
        List<StardewCondition> availableWhen,
        int season,
        int startDay,
        int endDay,
        int startTime,
        int endTime,
        boolean showOnCalendar,
        boolean onlyShowStartMessageOnFirstDay,
        String startMessageKey,
        String announcementMailId,
        Presentation presentation,
        WorldSettings world
) {
    private static final Codec<Map<String, String>> STRING_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private static final Codec<StardewFestivalDefinition> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FestivalKind.CODEC.fieldOf("type").forGetter(StardewFestivalDefinition::type),
                    Codec.STRING.optionalFieldOf("legacy_id", "").forGetter(StardewFestivalDefinition::legacyId),
                    Codec.STRING.fieldOf("display_name").forGetter(StardewFestivalDefinition::displayName),
                    Codec.STRING.optionalFieldOf("legacy_condition", "").forGetter(StardewFestivalDefinition::legacyCondition),
                    StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                            .forGetter(StardewFestivalDefinition::availableWhen),
                    Codec.intRange(0, 3).fieldOf("season").forGetter(StardewFestivalDefinition::season),
                    Codec.intRange(1, 28).fieldOf("start_day").forGetter(StardewFestivalDefinition::startDay),
                    Codec.intRange(1, 28).fieldOf("end_day").forGetter(StardewFestivalDefinition::endDay),
                    Codec.intRange(0, 2600).fieldOf("start_time").forGetter(StardewFestivalDefinition::startTime),
                    Codec.intRange(0, 2600).fieldOf("end_time").forGetter(StardewFestivalDefinition::endTime),
                    Codec.BOOL.optionalFieldOf("show_on_calendar", true)
                            .forGetter(StardewFestivalDefinition::showOnCalendar),
                    Codec.BOOL.optionalFieldOf("only_show_start_message_on_first_day", false)
                            .forGetter(StardewFestivalDefinition::onlyShowStartMessageOnFirstDay),
                    Codec.STRING.optionalFieldOf("start_message_key", "")
                            .forGetter(StardewFestivalDefinition::startMessageKey),
                    Codec.STRING.optionalFieldOf("announcement_mail_id", "")
                            .forGetter(StardewFestivalDefinition::announcementMailId),
                    Presentation.CODEC.optionalFieldOf("presentation", Presentation.EMPTY)
                            .forGetter(StardewFestivalDefinition::presentation),
                    WorldSettings.CODEC.optionalFieldOf("world", WorldSettings.EMPTY)
                            .forGetter(StardewFestivalDefinition::world)
            ).apply(instance, StardewFestivalDefinition::new));

    public static final Codec<StardewFestivalDefinition> CODEC = RAW_CODEC.validate(definition -> {
        if (definition.displayName().isBlank()) {
            return DataResult.error(() -> "festival display_name must not be blank");
        }
        if (definition.endDay() < definition.startDay()) {
            return DataResult.error(() -> "festival end_day must not be before start_day");
        }
        if (definition.endTime() <= definition.startTime()) {
            return DataResult.error(() -> "festival end_time must be after start_time");
        }
        return DataResult.success(definition);
    });

    public StardewFestivalDefinition {
        legacyId = legacyId == null ? "" : legacyId.trim();
        displayName = displayName == null ? "" : displayName.trim();
        legacyCondition = legacyCondition == null ? "" : legacyCondition;
        availableWhen = List.copyOf(availableWhen == null ? List.of() : availableWhen);
        startMessageKey = startMessageKey == null ? "" : startMessageKey;
        announcementMailId = announcementMailId == null ? "" : announcementMailId;
        presentation = presentation == null ? Presentation.EMPTY : presentation;
        world = world == null ? WorldSettings.EMPTY : world;
    }

    public record Presentation(String displayToken, String startMessageToken) {
        public static final Presentation EMPTY = new Presentation("", "");
        public static final Codec<Presentation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("display_token", "").forGetter(Presentation::displayToken),
                Codec.STRING.optionalFieldOf("start_message_token", "").forGetter(Presentation::startMessageToken)
        ).apply(instance, Presentation::new));

        public Presentation {
            displayToken = displayToken == null ? "" : displayToken;
            startMessageToken = startMessageToken == null ? "" : startMessageToken;
        }
    }

    public record WorldSettings(
            String location,
            String mapOverlay,
            Map<String, String> mapReplacements,
            List<String> shops,
            String mechanicId
    ) {
        public static final WorldSettings EMPTY = new WorldSettings("", "", Map.of(), List.of(), "");
        public static final Codec<WorldSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("location", "").forGetter(WorldSettings::location),
                Codec.STRING.optionalFieldOf("map_overlay", "").forGetter(WorldSettings::mapOverlay),
                STRING_MAP_CODEC.optionalFieldOf("map_replacements", Map.of()).forGetter(WorldSettings::mapReplacements),
                Codec.STRING.listOf().optionalFieldOf("shops", List.of()).forGetter(WorldSettings::shops),
                Codec.STRING.optionalFieldOf("mechanic_id", "").forGetter(WorldSettings::mechanicId)
        ).apply(instance, WorldSettings::new));

        public WorldSettings {
            location = location == null ? "" : location;
            mapOverlay = mapOverlay == null ? "" : mapOverlay;
            mapReplacements = Map.copyOf(mapReplacements == null ? Map.of() : mapReplacements);
            shops = List.copyOf(shops == null ? List.of() : shops);
            mechanicId = mechanicId == null ? "" : mechanicId.trim();
        }
    }

    public enum FestivalKind {
        ACTIVE,
        PASSIVE;

        public static final Codec<FestivalKind> CODEC = Codec.STRING.comapFlatMap(
                value -> {
                    try {
                        return DataResult.success(FestivalKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException exception) {
                        return DataResult.error(() -> "unknown festival type: " + value);
                    }
                },
                value -> value.name().toLowerCase(Locale.ROOT)
        );
    }
}
