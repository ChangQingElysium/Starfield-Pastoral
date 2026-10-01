package com.stardew.craft.pet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.stardew.craft.api.v1.pet.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The twelve existing breeds only. Fan pets belong to independently installed addons. */
public final class PetDefaults {
    private record Graph(Map<String, Double> clips, Map<String, StardewPetBehavior.State> states) {}
    private PetDefaults() {}
    public static void register() {
        try (var behaviorReader = new InputStreamReader(PetDefaults.class.getResourceAsStream("/data/stardewcraft/pet/behaviors.json"), StandardCharsets.UTF_8);
             var feedbackReader = new InputStreamReader(PetDefaults.class.getResourceAsStream("/data/stardewcraft/pet/feedback.json"), StandardCharsets.UTF_8);
             var giftReader = new InputStreamReader(PetDefaults.class.getResourceAsStream("/data/stardewcraft/pet/gifts.json"), StandardCharsets.UTF_8)) {
            Map<String, Graph> graphs = new Gson().fromJson(behaviorReader, new TypeToken<Map<String, Graph>>() {}.getType());
            Map<String, StardewPetFeedback> feedback = new Gson().fromJson(feedbackReader, new TypeToken<Map<String, StardewPetFeedback>>() {}.getType());
            Map<String, List<StardewPetSpeciesDefinition.Gift>> gifts = new Gson().fromJson(giftReader, new TypeToken<Map<String, List<StardewPetSpeciesDefinition.Gift>>>() {}.getType());
            for (String species : List.of("cat", "dog", "turtle")) {
                boolean cat = species.equals("cat"), dog = species.equals("dog"), turtle = species.equals("turtle");
                var graph = graphs.get(species); var exits = new HashMap<String, String>();
                for (var state : graph.states().keySet()) {
                    if (turtle && state.equals("SitDown")) exits.put(state, "emerge");
                    else if (cat && state.equals("Flop")) exits.put(state, "lie_up");
                    else if (state.startsWith("SitSide") || state.equals("BeginSitSide")) exits.put(state, "stand_side_up");
                    else if (!turtle && (state.startsWith("SitDown") || state.equals("BeginSitDown"))) exits.put(state, "stand_up");
                }
                StardewPets.registerSpecies(new StardewPetSpeciesDefinition(id(species), cat ? .6f : dog ? .7f : .8f, cat ? .9f : dog ? 1.05f : .44f,
                        !turtle, cat ? .75 : dog ? .05 : 0, new StardewPetBehavior(graph.clips(), graph.states(), exits), .2, gifts.get(species),
                        turtle ? null : id(cat ? "pet_cat" : "dog_bark"),
                        id(cat ? "pet_cat" : dog ? "dog_pant" : "turtle_pet"), cat ? .6f : .7f, 1, feedback.get(species)));
                for (int i = 0; i < (turtle ? 2 : 5); i++) {
                    String name = species + i; double scale = dog ? switch (i) { case 1 -> 1.1; case 2 -> .8; case 3 -> .9; case 4 -> 1.04; default -> 1; } : 1;
                    StardewPets.registerBreed(new StardewPetBreedDefinition(id(name), id(species), "pet.stardewcraft.variant." + name,
                            id("pet_native/" + name + ".json.gz"), id("textures/entity/pet/" + name + ".png"), id("textures/gui/pet/" + name + ".png"),
                            !turtle, true, turtle ? i == 0 ? 60000 : 500000 : 40000, (turtle ? 1.5 : 4) * scale, (turtle ? 1.5 : dog ? 48.0 / 7 : 4) * scale));
                }
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot load built-in pet definitions", error); }
    }
    public static int order(ResourceLocation id) {
        if (!id.getNamespace().equals("stardewcraft")) return 100;
        return switch (id.getPath()) { case "cat0" -> 0; case "cat1" -> 1; case "cat2" -> 2; case "cat3" -> 3; case "cat4" -> 4;
            case "dog0" -> 5; case "dog1" -> 6; case "dog2" -> 7; case "dog3" -> 8; case "dog4" -> 9; case "turtle0" -> 10; case "turtle1" -> 11; default -> 100; };
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("stardewcraft", path); }
}
