package com.stardew.craft.api.v1.agriculture;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Registry of addon animal icons consumed by the built-in purchase screen. */
public final class StardewAnimalPurchaseDisplays {
    private static final Map<String, StardewAnimalPurchaseDisplay> DISPLAYS = new HashMap<>();
    private static final Map<ResourceLocation, String> REGISTRATION_IDS = new HashMap<>();
    private static volatile Map<String, StardewAnimalPurchaseDisplay> snapshot = Map.of();

    private StardewAnimalPurchaseDisplays() {
    }

    public static synchronized void register(StardewAnimalPurchaseDisplay display) {
        Objects.requireNonNull(display, "display");
        if (DISPLAYS.containsKey(display.animalTypeId())) {
            throw new IllegalStateException(
                    "Stardew animal purchase display already registered: "
                            + display.animalTypeId());
        }
        if (REGISTRATION_IDS.containsKey(display.registrationId())) {
            throw new IllegalStateException(
                    "Stardew animal purchase display registration ID already registered: "
                            + display.registrationId());
        }
        DISPLAYS.put(display.animalTypeId(), display);
        REGISTRATION_IDS.put(display.registrationId(), display.animalTypeId());
        snapshot = Map.copyOf(DISPLAYS);
    }

    @Nullable
    public static StardewAnimalPurchaseDisplay display(String animalTypeId) {
        if (animalTypeId == null) {
            return null;
        }
        return snapshot.get(animalTypeId.trim().toLowerCase(Locale.ROOT));
    }
}
