package com.stardew.craft.platform.fabric.client.decoration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

/** The same state set saved by Neo's ItemDecoratorHandler; all snapshots are local to one invocation. */
final class DecorationGlState {
    static final FabricItemDecorations.StateAccess ACCESS = new FabricItemDecorations.StateAccess() {
        @Override public Runnable backup() { RenderSystem.assertOnRenderThread(); var snapshot = Snapshot.capture(); return snapshot::restore; }
        @Override public void reset() { RenderSystem.enableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
    };
    private DecorationGlState() {}
    private record Snapshot(boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
            boolean depth, boolean depthMask, int depthFunc, boolean cull, boolean polygonFill, boolean polygonLine, float factor, float units,
            boolean logic, int logicOp, int stencilFunc, int stencilRef, int stencilValueMask, int stencilWriteMask, int stencilFail, int stencilDepthFail,
            int stencilPass, boolean scissor, boolean red, boolean green, boolean blue, boolean alpha) {
        static Snapshot capture() {
            try (var memory = MemoryStack.stackPush()) {
                var colorMask = memory.malloc(4); GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
                return new Snapshot(GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                        GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                        GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                        GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL), GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_LINE), GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),
                        GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS), GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP), GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE),
                        GL11.glGetInteger(GL11.GL_STENCIL_FUNC), GL11.glGetInteger(GL11.GL_STENCIL_REF), GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK),
                        GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK), GL11.glGetInteger(GL11.GL_STENCIL_FAIL), GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL),
                        GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
            }
        }
        void restore() {
            if (blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (depth) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
            GlStateManager._depthMask(depthMask); GlStateManager._depthFunc(depthFunc);
            if (cull) GlStateManager._enableCull(); else GlStateManager._disableCull();
            if (polygonFill) GlStateManager._enablePolygonOffset(); else GlStateManager._disablePolygonOffset();
            // Native GlStateManager has no public polygon-offset-line mutator and never changes its
            // unused line cache. Restore this raw capability alongside the ordinary cached setters.
            if (polygonLine) GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE); else GL11.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
            GlStateManager._polygonOffset(factor, units);
            if (logic) GlStateManager._enableColorLogicOp(); else GlStateManager._disableColorLogicOp();
            GlStateManager._logicOp(logicOp); GlStateManager._stencilFunc(stencilFunc, stencilRef, stencilValueMask);
            GlStateManager._stencilMask(stencilWriteMask); GlStateManager._stencilOp(stencilFail, stencilDepthFail, stencilPass);
            if (scissor) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
            GlStateManager._colorMask(red, green, blue, alpha);
        }
    }
}
