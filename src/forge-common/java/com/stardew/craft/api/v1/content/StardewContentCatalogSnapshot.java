package com.stardew.craft.api.v1.content;

import java.util.List;

/** Immutable result of one cross-system projection and resolution pass. */
public record StardewContentCatalogSnapshot(
        List<StardewContentNodeSnapshot> nodes,
        List<StardewContentIssue> issues
) {
    public StardewContentCatalogSnapshot {
        nodes = List.copyOf(nodes);
        issues = List.copyOf(issues);
    }

    public boolean healthy() {
        return issues.stream().noneMatch(issue ->
                issue.severity() == StardewContentIssue.Severity.ERROR)
                && nodes.stream().allMatch(
                        StardewContentNodeSnapshot::healthy);
    }

    public List<StardewContentNodeSnapshot> unhealthyNodes() {
        return nodes.stream()
                .filter(node -> !node.healthy())
                .toList();
    }

    public List<StardewContentReferenceSnapshot> unresolvedReferences() {
        return nodes.stream()
                .flatMap(node -> node.references().stream())
                .filter(reference -> !reference.resolved())
                .toList();
    }
}
