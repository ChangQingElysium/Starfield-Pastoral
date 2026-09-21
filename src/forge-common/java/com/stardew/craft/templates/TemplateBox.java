package com.stardew.craft.templates;

/** Coordinates use the vanilla model range of 0..16. */
public record TemplateBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public TemplateBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Template box minimum exceeds maximum");
        }
    }
}
