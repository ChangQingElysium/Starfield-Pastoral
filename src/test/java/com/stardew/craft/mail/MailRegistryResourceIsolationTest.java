package com.stardew.craft.mail;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailRegistryResourceIsolationTest {
    @Test
    void invalidResourceDoesNotDiscardValidNamespacedMail() {
        ResourceLocation validSource = id("example_stardew_addon", "apple_club");
        ResourceLocation invalidSource = id("broken_addon", "missing_text");
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(invalidSource, json("{\"background\": 1}"));
        resources.put(validSource, json("{\"text\": \"Welcome.\"}"));

        MailRegistry.Candidate candidate = MailRegistry.parseCandidate(resources);

        assertEquals("Welcome.", candidate.definitions().get(validSource).text());
        assertFalse(candidate.definitions().containsKey(invalidSource));
        assertTrue(candidate.diagnostics().stream().allMatch(
                diagnostic -> diagnostic.severity() == DefinitionDiagnostic.Severity.WARNING));
        assertTrue(candidate.diagnostics().stream().anyMatch(
                diagnostic -> invalidSource.equals(diagnostic.source())
                        && diagnostic.message().contains("Ignored invalid mail resource")));
    }

    @Test
    void malformedIdTypeIsQuarantinedInsteadOfAbortingParsing() {
        ResourceLocation validSource = id("a_addon", "valid");
        ResourceLocation invalidSource = id("b_addon", "invalid");
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(validSource, json("{\"text\": \"Valid.\"}"));
        resources.put(invalidSource, json("{\"id\": {}, \"text\": \"Invalid.\"}"));

        MailRegistry.Candidate candidate = MailRegistry.parseCandidate(resources);

        assertEquals(1, candidate.definitions().size());
        assertTrue(candidate.definitions().containsKey(validSource));
        assertTrue(candidate.diagnostics().stream().anyMatch(
                diagnostic -> invalidSource.equals(diagnostic.source())));
    }

    @Test
    void duplicateIdsUseDeterministicResourceOrderAndQuarantineLaterFile() {
        ResourceLocation firstSource = id("a_addon", "first");
        ResourceLocation laterSource = id("z_addon", "later");
        ResourceLocation sharedId = id("shared_addon", "welcome");
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(laterSource, json(
                "{\"id\": \"shared_addon:welcome\", \"text\": \"Later.\"}"));
        resources.put(firstSource, json(
                "{\"id\": \"shared_addon:welcome\", \"text\": \"First.\"}"));

        MailRegistry.Candidate candidate = MailRegistry.parseCandidate(resources);

        assertEquals("First.", candidate.definitions().get(sharedId).text());
        assertTrue(candidate.diagnostics().stream().anyMatch(
                diagnostic -> laterSource.equals(diagnostic.source())
                        && sharedId.equals(diagnostic.definitionId())
                        && diagnostic.message().contains("already defined by " + firstSource)));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
