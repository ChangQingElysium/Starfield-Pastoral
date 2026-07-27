package com.stardew.craft.api.v1.internal.content;

import com.stardew.craft.api.v1.content.StardewContentAlias;
import com.stardew.craft.api.v1.content.StardewContentAliasSnapshot;
import com.stardew.craft.api.v1.content.StardewContentIssue;
import com.stardew.craft.api.v1.content.StardewContentKey;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Deterministic validation and transitive resolution for content aliases. */
final class StardewContentAliasResolver {
    static final int MAX_CHAIN_LENGTH = 256;
    private static final Comparator<StardewContentKey> KEY_ORDER =
            Comparator.comparing((StardewContentKey key) ->
                            key.type().toString())
                    .thenComparing(key -> key.id().toString());

    private StardewContentAliasResolver() {
    }

    static Result resolve(
            List<Declaration> declarations,
            Predicate<StardewContentKey> canonicalExists
    ) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(canonicalExists, "canonicalExists");
        LinkedHashMap<StardewContentKey, Declaration> accepted =
                new LinkedHashMap<>();
        HashMap<StardewContentKey, List<String>> issuesByAlias =
                new HashMap<>();
        ArrayList<StardewContentIssue> catalogIssues =
                new ArrayList<>();

        for (Declaration declaration : declarations) {
            StardewContentAlias alias = declaration.alias();
            StardewContentKey aliasKey = alias.alias();
            Declaration previous = accepted.putIfAbsent(
                    aliasKey, declaration);
            if (previous != null) {
                catalogIssues.add(issue(
                        declaration.source(),
                        aliasKey,
                        "Duplicate content alias; first declared by "
                                + previous.source()));
                continue;
            }
            if (!aliasKey.type().equals(alias.target().type())) {
                addIssue(
                        issuesByAlias,
                        aliasKey,
                        "Alias and target content types differ");
            }
            if (canonicalExists.test(aliasKey)) {
                addIssue(
                        issuesByAlias,
                        aliasKey,
                        "Alias conflicts with canonical content");
            }
        }

        HashMap<StardewContentKey, StardewContentKey> canonical =
                new HashMap<>();
        for (StardewContentKey alias : accepted.keySet()) {
            resolveOne(
                    alias,
                    accepted,
                    canonicalExists,
                    canonical,
                    issuesByAlias,
                    new LinkedHashSet<>());
        }

        ArrayList<StardewContentAliasSnapshot> snapshots =
                new ArrayList<>(accepted.size());
        accepted.values().stream()
                .sorted(Comparator.comparing(
                        declaration -> declaration.alias().alias(),
                        KEY_ORDER))
                .forEach(declaration -> {
                    StardewContentKey alias =
                            declaration.alias().alias();
                    List<String> aliasIssues = List.copyOf(
                            issuesByAlias.getOrDefault(
                                    alias, List.of()));
                    aliasIssues.forEach(message ->
                            catalogIssues.add(issue(
                                    declaration.source(),
                                    alias,
                                    message)));
                    snapshots.add(new StardewContentAliasSnapshot(
                            alias,
                            declaration.alias().target(),
                            canonical.get(alias),
                            declaration.source(),
                            aliasIssues));
                });
        return new Result(canonical, snapshots, catalogIssues);
    }

    private static StardewContentKey resolveOne(
            StardewContentKey alias,
            Map<StardewContentKey, Declaration> declarations,
            Predicate<StardewContentKey> canonicalExists,
            Map<StardewContentKey, StardewContentKey> canonical,
            Map<StardewContentKey, List<String>> issues,
            LinkedHashSet<StardewContentKey> path
    ) {
        if (canonical.containsKey(alias)) {
            return canonical.get(alias);
        }
        if (!issues.getOrDefault(alias, List.of()).isEmpty()) {
            return null;
        }
        if (path.size() >= MAX_CHAIN_LENGTH) {
            addIssue(
                    issues,
                    alias,
                    "Content alias chain exceeds "
                            + MAX_CHAIN_LENGTH + " entries");
            return null;
        }
        if (!path.add(alias)) {
            markCycle(alias, path, issues);
            return null;
        }

        StardewContentKey target =
                declarations.get(alias).alias().target();
        StardewContentKey resolved;
        if (canonicalExists.test(target)) {
            resolved = target;
        } else if (declarations.containsKey(target)) {
            resolved = resolveOne(
                    target,
                    declarations,
                    canonicalExists,
                    canonical,
                    issues,
                    path);
            if (resolved == null
                    && issues.getOrDefault(alias, List.of()).isEmpty()) {
                addIssue(
                        issues,
                        alias,
                        "Alias target does not resolve: " + target);
            }
        } else {
            resolved = null;
            addIssue(
                    issues,
                    alias,
                    "Alias target does not exist: " + target);
        }
        path.remove(alias);
        if (resolved != null
                && issues.getOrDefault(alias, List.of()).isEmpty()) {
            canonical.put(alias, resolved);
        }
        return resolved;
    }

    private static void markCycle(
            StardewContentKey repeated,
            Set<StardewContentKey> path,
            Map<StardewContentKey, List<String>> issues
    ) {
        boolean inCycle = false;
        for (StardewContentKey key : path) {
            if (key.equals(repeated)) {
                inCycle = true;
            }
            if (inCycle) {
                addIssue(issues, key, "Content alias cycle");
            }
        }
    }

    private static void addIssue(
            Map<StardewContentKey, List<String>> issues,
            StardewContentKey alias,
            String message
    ) {
        List<String> current = issues.computeIfAbsent(
                alias, ignored -> new ArrayList<>());
        if (!current.contains(message)) {
            current.add(message);
        }
    }

    private static StardewContentIssue issue(
            ResourceLocation source,
            StardewContentKey alias,
            String message
    ) {
        return new StardewContentIssue(
                StardewContentIssue.Severity.ERROR,
                source,
                alias,
                message);
    }

    record Declaration(
            ResourceLocation source,
            StardewContentAlias alias
    ) {
        Declaration {
            source = Objects.requireNonNull(source, "source");
            alias = Objects.requireNonNull(alias, "alias");
        }
    }

    record Result(
            Map<StardewContentKey, StardewContentKey> canonical,
            List<StardewContentAliasSnapshot> snapshots,
            List<StardewContentIssue> issues
    ) {
        Result {
            canonical = Map.copyOf(canonical);
            snapshots = List.copyOf(snapshots);
            issues = List.copyOf(issues);
        }
    }
}
