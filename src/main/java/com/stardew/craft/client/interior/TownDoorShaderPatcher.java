package com.stardew.craft.client.interior;

import com.mojang.blaze3d.shaders.Program;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shader-source transformation adapted from Immersive Portals (Apache-2.0). */
public final class TownDoorShaderPatcher {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{");
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

    private TownDoorShaderPatcher() {}

    public static boolean patchesVanilla(String name) {
        String normalized = normalize(name);
        return VANILLA_TERRAIN.contains(normalized) || VANILLA_MODEL.contains(normalized);
    }

    public static String transformVanilla(Program.Type type, String name, String source) {
        if (type != Program.Type.VERTEX) return source;
        String normalized = normalize(name);
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
        return injectAtMainEnd(source, "(u_ModelViewMatrix * vec4(position, 1.0)).xyz");
    }

    /** Screen-space plane evaluation also covers geometry/tessellation and displaced water. */
    public static String transformIrisFragment(String source) {
        if (source.contains(TownDoorClipping.SCREEN_UNIFORM)) return source;
        Matcher main = MAIN.matcher(codeOnly(source));
        if (!main.find()) return source;
        String uniforms = "uniform vec4 " + TownDoorClipping.SCREEN_UNIFORM + ";\n"
                + "uniform vec4 " + TownDoorClipping.VIEWPORT_UNIFORM + ";\n";
        String clip = "\n    if (dot(vec4((gl_FragCoord.xy - " + TownDoorClipping.VIEWPORT_UNIFORM
                + ".xy) / " + TownDoorClipping.VIEWPORT_UNIFORM + ".zw * 2.0 - 1.0, "
                + "gl_FragCoord.z * 2.0 - 1.0, 1.0), " + TownDoorClipping.SCREEN_UNIFORM + ") < 0.0) discard;\n";
        return source.substring(0, main.start()) + uniforms + source.substring(main.start(), main.end())
                + clip + source.substring(main.end());
    }

    private static String inject(String source, String position) {
        if (source.contains(TownDoorClipping.UNIFORM)) return source;
        Matcher matcher = MAIN.matcher(codeOnly(source));
        if (!matcher.find()) return source;
        String replacement = "uniform vec4 " + TownDoorClipping.UNIFORM + ";\nvoid main() {\n"
                + "    gl_ClipDistance[0] = dot(" + position + ", " + TownDoorClipping.UNIFORM
                + ".xyz) + " + TownDoorClipping.UNIFORM + ".w;";
        return source.substring(0, matcher.start()) + replacement + source.substring(matcher.end());
    }

    /** Sodium calculates its final camera-space vertex inside main, so clip after that value exists. */
    private static String injectAtMainEnd(String source, String position) {
        if (source.contains(TownDoorClipping.UNIFORM)) return source;
        String code = codeOnly(source);
        Matcher matcher = MAIN.matcher(code);
        if (!matcher.find()) return source;
        int depth = 1;
        int closingBrace = -1;
        for (int i = matcher.end(); i < code.length(); i++) {
            if (code.charAt(i) == '{') depth++;
            else if (code.charAt(i) == '}' && --depth == 0) {
                closingBrace = i;
                break;
            }
        }
        if (closingBrace < 0) return source;
        String assignment = "\n    gl_ClipDistance[0] = dot(" + position + ", "
                + TownDoorClipping.UNIFORM + ".xyz) + " + TownDoorClipping.UNIFORM + ".w;\n";
        return source.substring(0, matcher.start()) + "uniform vec4 " + TownDoorClipping.UNIFORM + ";\n"
                + source.substring(matcher.start(), closingBrace) + assignment + source.substring(closingBrace);
    }

    /** Preserve offsets and newlines while hiding comments from the entry-point/brace search. */
    private static String codeOnly(String source) {
        char[] code = source.toCharArray();
        for (int i = 0; i + 1 < code.length; i++) {
            if (code[i] != '/') continue;
            if (code[i + 1] == '/') {
                code[i++] = ' ';
                while (i < code.length && code[i] != '\n') code[i++] = ' ';
            } else if (code[i + 1] == '*') {
                code[i++] = ' ';
                code[i++] = ' ';
                while (i < code.length) {
                    if (i + 1 < code.length && code[i] == '*' && code[i + 1] == '/') {
                        code[i] = code[i + 1] = ' ';
                        i++;
                        break;
                    }
                    if (code[i] != '\n' && code[i] != '\r') code[i] = ' ';
                    i++;
                }
            }
        }
        return new String(code);
    }
}
