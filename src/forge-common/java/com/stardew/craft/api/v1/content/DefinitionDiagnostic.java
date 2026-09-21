package com.stardew.craft.api.v1.content;

import net.minecraft.resources.ResourceLocation;

/** A structured diagnostic produced while building a reloadable definition snapshot. */
public record DefinitionDiagnostic(
        Severity severity,
        ResourceLocation source,
        ResourceLocation definitionId,
        String message
) {
    public enum Severity {
        WARNING,
        ERROR
    }

    public static DefinitionDiagnostic error(
            ResourceLocation source,
            ResourceLocation definitionId,
            String message
    ) {
        return new DefinitionDiagnostic(Severity.ERROR, source, definitionId, message);
    }

    public static DefinitionDiagnostic warning(
            ResourceLocation source,
            ResourceLocation definitionId,
            String message
    ) {
        return new DefinitionDiagnostic(Severity.WARNING, source, definitionId, message);
    }
}
