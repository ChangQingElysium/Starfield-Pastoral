package com.stardew.craft.api.v1.internal.content;

import com.stardew.craft.api.v1.content.StardewContentAlias;
import com.stardew.craft.api.v1.content.StardewContentKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewContentAliasResolverTest {
    @Test
    void resolvesDirectAndChainedAliasesDeterministically() {
        StardewContentKey canonical = key("feature", "current");
        StardewContentKey old = key("feature", "old");
        StardewContentKey oldest = key("feature", "oldest");

        var result = StardewContentAliasResolver.resolve(
                List.of(
                        declaration("second", oldest, old),
                        declaration("first", old, canonical)),
                Set.of(canonical)::contains);

        assertEquals(canonical, result.canonical().get(old));
        assertEquals(canonical, result.canonical().get(oldest));
        assertEquals(List.of(old, oldest),
                result.snapshots().stream()
                        .map(snapshot -> snapshot.alias())
                        .toList());
        assertTrue(result.snapshots().stream()
                .allMatch(snapshot -> snapshot.resolved()));
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void rejectsCyclesMissingTargetsTypeChangesAndCanonicalConflicts() {
        StardewContentKey canonical = key("feature", "current");
        StardewContentKey first = key("feature", "first");
        StardewContentKey second = key("feature", "second");
        StardewContentKey missingAlias = key("feature", "missing_alias");
        StardewContentKey wrongType = key("other", "wrong_type");

        var result = StardewContentAliasResolver.resolve(
                List.of(
                        declaration("cycle_a", first, second),
                        declaration("cycle_b", second, first),
                        declaration(
                                "missing",
                                missingAlias,
                                key("feature", "absent")),
                        declaration("wrong", wrongType, canonical),
                        declaration(
                                "conflict",
                                canonical,
                                key("feature", "replacement"))),
                Set.of(canonical)::contains);

        assertTrue(result.canonical().isEmpty());
        assertFalse(result.snapshots().stream()
                .anyMatch(snapshot -> snapshot.resolved()));
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.message()
                        .contains("cycle")));
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.message()
                        .contains("does not exist")));
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.message()
                        .contains("types differ")));
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.message()
                        .contains("conflicts with canonical")));
    }

    @Test
    void duplicateAliasKeepsTheFirstDeclaration() {
        StardewContentKey canonical = key("feature", "current");
        StardewContentKey other = key("feature", "other");
        StardewContentKey old = key("feature", "old");

        var result = StardewContentAliasResolver.resolve(
                List.of(
                        declaration("first", old, canonical),
                        declaration("second", old, other)),
                Set.of(canonical, other)::contains);

        assertEquals(canonical, result.canonical().get(old));
        assertEquals(id("first"),
                result.snapshots().getFirst().source());
        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.source().equals(id("second"))
                        && issue.message().contains("Duplicate")));
    }

    @Test
    void rejectsAliasChainsAboveTheTraversalBound() {
        ArrayList<StardewContentAliasResolver.Declaration>
                declarations = new ArrayList<>();
        StardewContentKey canonical = key("feature", "canonical");
        for (int index = 0;
             index <= StardewContentAliasResolver.MAX_CHAIN_LENGTH;
             index++) {
            StardewContentKey alias =
                    key("feature", "alias_" + index);
            StardewContentKey target = index
                    == StardewContentAliasResolver.MAX_CHAIN_LENGTH
                    ? canonical
                    : key("feature", "alias_" + (index + 1));
            declarations.add(declaration(
                    "source_" + index, alias, target));
        }

        var result = StardewContentAliasResolver.resolve(
                declarations,
                Set.of(canonical)::contains);

        assertTrue(result.issues().stream()
                .anyMatch(issue -> issue.message()
                        .contains("chain exceeds")));
    }

    private static StardewContentAliasResolver.Declaration declaration(
            String source,
            StardewContentKey alias,
            StardewContentKey target
    ) {
        return new StardewContentAliasResolver.Declaration(
                id(source),
                new StardewContentAlias(alias, target));
    }

    private static StardewContentKey key(String type, String path) {
        return new StardewContentKey(id(type), id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_test", path);
    }
}
