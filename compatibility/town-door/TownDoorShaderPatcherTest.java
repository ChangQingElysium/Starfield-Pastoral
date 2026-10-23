package com.stardew.craft.client.interior;

import com.mojang.blaze3d.shaders.Program;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TownDoorShaderPatcherTest {
    private static final String HEADER = "#version 150\nuniform mat4 u_ModelViewMatrix;\n"
            + "vec3 position;\nvec4 getVertexPosition() { return vec4(1.0); }\n";

    @Test void entityClippingUsesItsPoseInViewSpaceRatherThanRawModelCoordinates() {
        String source = "#version 150\nin vec3 Position;\nuniform mat4 ModelViewMat;\nvoid main() {}";
        String patched = TownDoorShaderPatcher.transformVanilla(Program.Type.VERTEX, "rendertype_entity_cutout", source);
        assertTrue(patched.contains("ModelViewMat * vec4(Position.xyz, 1.0)"));
        assertSame(source, TownDoorShaderPatcher.transformVanilla(Program.Type.FRAGMENT, "rendertype_entity_cutout", source));
    }

    @Test void clipAssignmentBelongsToMainEvenWithTrailingHelperFunctions() {
        String source = HEADER + "void main() { gl_Position = getVertexPosition(); }\n"
                + "float helper() { return 1.0; }\n";
        String patched = TownDoorShaderPatcher.transformSodiumVertex(source);
        assertNotEquals(source, patched);
        assertTrue(patched.indexOf("gl_ClipDistance") < patched.indexOf("float helper"), patched);
        assertTrue(patched.endsWith("float helper() { return 1.0; }\n"));
    }

    @Test void mainAcceptsVoidAndIgnoresCommentedOutEntryPointsAndBraces() {
        String comments = "// void main() { fake(); }\n/* } void main() { broken(); } */\n";
        String source = HEADER + comments
                + "void main(void) { if (true) { gl_Position = getVertexPosition(); } /* } */ }\n"
                + "void after() {}\n";
        String patched = TownDoorShaderPatcher.transformSodiumVertex(source);
        assertNotEquals(source, patched);
        assertTrue(patched.contains(comments));
        assertTrue(patched.indexOf("gl_ClipDistance") > patched.indexOf("/* } */"));
        assertTrue(patched.indexOf("gl_ClipDistance") < patched.indexOf("void after"));
    }

    @Test void transformationIsIdempotentAndLeavesUnrelatedProgramsAlone() {
        String once = TownDoorShaderPatcher.transformSodiumVertex(HEADER + "void main() { gl_Position = getVertexPosition(); }");
        assertEquals(once, TownDoorShaderPatcher.transformSodiumVertex(once));
        String composite = "#version 150\nvoid main() { gl_Position = vec4(1.0); }";
        assertSame(composite, TownDoorShaderPatcher.transformSodiumVertex(composite));
    }

    @Test void irisFragmentClipsBeforeEarlyReturnsAndDoesNotRequireVertexConventions() {
        String source = "#version 330\nout vec4 color;\n// void main() { }\n"
                + "void main(void) { if (gl_FragCoord.x < 1.0) return; color = vec4(1.0); }\n"
                + "float helper() { return 2.0; }\n";
        String patched = TownDoorShaderPatcher.transformIrisFragment(source);
        assertTrue(patched.startsWith("#version 330\n"));
        assertTrue(patched.indexOf("discard;") < patched.indexOf("return;"));
        assertTrue(patched.endsWith("float helper() { return 2.0; }\n"));
        assertFalse(patched.contains("gl_ClipDistance"));
        assertFalse(patched.contains("getVertexPosition"));
        assertEquals(patched, TownDoorShaderPatcher.transformIrisFragment(patched));
    }
}
