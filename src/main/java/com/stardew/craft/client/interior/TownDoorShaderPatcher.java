package com.stardew.craft.client.interior;

import com.mojang.blaze3d.shaders.Program;
import com.stardew.craft.StardewCraft;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shader-source transformation adapted from Immersive Portals (Apache-2.0). */
public final class TownDoorShaderPatcher {
    private static final Pattern MAIN = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{");
    private static final Set<String> VANILLA_TERRAIN = Set.of(
            "rendertype_solid", "rendertype_cutout", "rendertype_cutout_mipped", "rendertype_translucent"
    );
    private static final Set<String> VANILLA_MODEL = Set.of(
            "rendertype_entity_solid", "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset", "rendertype_item_entity_translucent_cull",
            "rendertype_entity_translucent_cull", "rendertype_entity_translucent", "rendertype_entity_smooth_cutout",
            "rendertype_entity_translucent_emissive", "rendertype_armor_cutout_no_cull", "rendertype_eyes",
            "rendertype_leash", "rendertype_crumbling", "particle"
    );
    private static boolean loggedSodiumPatch;

    private TownDoorShaderPatcher() {}

    public static boolean patchesVanilla(String name) {
        String normalized = normalize(name);
        return VANILLA_TERRAIN.contains(normalized) || VANILLA_MODEL.contains(normalized);
    }

    public static String transformVanilla(Program.Type type, String name, String source) {
        if (type != Program.Type.VERTEX) return source;
        String normalized = normalize(name);
        // PORT(1.20.1): 1.21 vanilla vertices are camera-relative world positions for every patched program, but
        // 1.20.1 entity/block-entity poses carry the view rotation (ModelViewMat = identity) while terrain and
        // particles use ModelViewMat = view. Clipping the view-space position against the view-space plane gives
        // the same clip distance as 1.21 for all of them.
        if (VANILLA_TERRAIN.contains(normalized)) {
            return inject(source, "(ModelViewMat * vec4(Position.xyz + ChunkOffset, 1.0)).xyz");
        }
        if (VANILLA_MODEL.contains(normalized)) {
            return inject(source, "(ModelViewMat * vec4(Position.xyz, 1.0)).xyz");
        }
        return source;
    }

    private static String normalize(String name) {
        return name.indexOf(':') >= 0 ? name.substring(name.indexOf(':') + 1) : name;
    }

    public static String transformSodiumVertex(String source) {
        if (!source.contains("u_ModelViewMatrix") || !source.contains("vec3 position")) return source;
        String transformed = injectAtMainEnd(source, "(u_ModelViewMatrix * vec4(position, 1.0)).xyz");
        if (transformed != source && !loggedSodiumPatch) {
            loggedSodiumPatch = true;
            StardewCraft.LOGGER.info("[TOWN-DOOR] Sodium terrain front-clipping shader installed");
        }
        return transformed;
    }

    public static String transformIrisVertex(String source) {
        if (!source.contains("iris_ModelViewMatrix")) return source;
        if (source.contains("getVertexPosition")) {
            return injectAtMainEnd(source, "(iris_ModelViewMatrix * getVertexPosition()).xyz");
        }
        // PORT(1.20.1): Oculus SodiumCoreTransformer expands vaPosition directly instead of
        // declaring getVertexPosition(). These are the same audited camera-relative coordinates.
        if (source.contains("_vert_position") && source.contains("_get_draw_translation(_draw_id)")
                && source.contains("u_RegionOffset")) {
            return injectAtMainEnd(source, "(iris_ModelViewMatrix * vec4(_vert_position + u_RegionOffset + _get_draw_translation(_draw_id), 1.0)).xyz");
        }
        return source;
    }

    private static String inject(String source, String position) {
        if (source.contains(TownDoorClipping.UNIFORM)) return source;
        Matcher matcher = MAIN.matcher(source);
        if (!matcher.find()) return source;
        String replacement = "uniform vec4 " + TownDoorClipping.UNIFORM + ";\nvoid main() {\n"
                + "    gl_ClipDistance[0] = dot(" + position + ", " + TownDoorClipping.UNIFORM
                + ".xyz) + " + TownDoorClipping.UNIFORM + ".w;";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    /** Sodium/Iris calculate their final camera-space vertex inside main, so clip only after that value exists. */
    private static String injectAtMainEnd(String source, String position) {
        if (source.contains(TownDoorClipping.UNIFORM)) return source;
        Matcher matcher = MAIN.matcher(source);
        if (!matcher.find()) return source;
        String withUniform = matcher.replaceFirst(Matcher.quoteReplacement(
                "uniform vec4 " + TownDoorClipping.UNIFORM + ";\nvoid main() {"));
        Matcher main = MAIN.matcher(withUniform);
        if (!main.find()) return source;
        int closingBrace = mainClosingBrace(withUniform, main.end() - 1);
        if (closingBrace < 0) return source;
        String assignment = "\n    gl_ClipDistance[0] = dot(" + position + ", "
                + TownDoorClipping.UNIFORM + ".xyz) + " + TownDoorClipping.UNIFORM + ".w;\n";
        return withUniform.substring(0, closingBrace) + assignment + withUniform.substring(closingBrace);
    }

    /** main need not be the last function; braces in shader comments are not scope delimiters. */
    private static int mainClosingBrace(String source, int openingBrace) {
        int depth = 0;
        boolean lineComment = false, blockComment = false;
        for (int i = openingBrace; i < source.length(); i++) {
            char c = source.charAt(i), next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (c == '/' && next == '/') { lineComment = true; i++; continue; }
            if (c == '/' && next == '*') { blockComment = true; i++; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }
}
