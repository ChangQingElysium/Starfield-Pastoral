package com.stardew.craft.client.interior;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.interior.door.TownDoorNetwork;
import com.stardew.craft.mixin.TownDoorGameRendererAccessor;
import com.stardew.craft.mixin.TownDoorLevelRendererAccessor;
import com.stardew.craft.mixin.TownDoorMinecraftAccessor;
import com.stardew.craft.mixin.TownDoorSectionRenderDispatcherAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import com.stardew.craft.port.net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL32;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-layer stencil renderer for the nearest open authored town doorway.
 *
 * <p>The mask -> portal depth clear -> nested world -> portal depth restore sequence is adapted from
 * Immersive Portals' {@code RendererUsingStencil} and {@code ViewAreaRenderer} (Apache-2.0). It uses
 * no portal entity, registry entry, renderer-mod dependency, or full-resolution color framebuffer.</p>
 */
final class TownDoorRenderer implements AutoCloseable {
    private static final boolean PROFILE = Boolean.getBoolean("stardewcraft.profileTownDoor");
    private static final double VIEW_RANGE = 20.0;
    private static final double PREPARE_RANGE = 32.0;
    private static final double NEAR_PLANE_FRUSTUM_BYPASS = 0.1;
    private RenderBuffers portalBuffers;
    private final Map<Integer, ObjectArrayList<ChunkRenderDispatcher.RenderChunk>> portalVisibleSections = new HashMap<>();
    private final ViewCamera virtualCamera = new ViewCamera();
    private final SodiumContext sodiumContext = new SodiumContext();
    private final IrisContext irisContext = new IrisContext();
    private boolean framePrepared;
    private TownDoorNetwork.DoorState frameDoor;
    private boolean failed;
    private long frames;
    private long cpuNanos;

    /**
     * Promotes the already-rendered destination visibility state on the crossing frame. The old
     * main state becomes the reverse doorway cache, so neither view starts from the wrong side.
     */
    void onClientTeleport(int doorId) {
        if (failed || TownDoorRenderContext.isRendering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean promoted = false;
        try {
            var levelRenderer = (TownDoorLevelRendererAccessor) mc.levelRenderer;
            ObjectArrayList<ChunkRenderDispatcher.RenderChunk> cached = portalVisibleSections.get(doorId);
            if (cached != null && !cached.isEmpty()) {
                var mainVisibleSections = levelRenderer.stardewcraft$getVisibleSections();
                levelRenderer.stardewcraft$setVisibleSections(cached);
                portalVisibleSections.put(doorId, mainVisibleSections);
                promoted = true;
            }
            promoted |= sodiumContext.promotePortalView(mc.levelRenderer, doorId);
        } catch (Throwable error) {
            StardewCraft.LOGGER.warn("[TOWN-DOOR] Could not promote cached destination visibility", error);
        }
        // A promoted cache is already the correct destination visibility graph. Invalidating it here
        // forces the crossing frame to rebuild from nothing and produces the visible one-frame flash.
        if (!promoted) mc.levelRenderer.needsUpdate();
    }

    /** Drops the destination visibility graph after a server-side door transition. */
    void invalidateDoor(int doorId) {
        portalVisibleSections.remove(doorId);
        sodiumContext.invalidate(doorId);
        if (frameDoor != null && frameDoor.id() == doorId) {
            frameDoor = null;
            framePrepared = false;
        }
    }

    /** Must run at GameRenderer.renderLevel HEAD, before vanilla clears and draws the outer world. */
    void prepareFrame(List<TownDoorNetwork.DoorState> doors) {
        framePrepared = false;
        frameDoor = null;
        if (TownDoorRenderContext.isRendering() || failed || doors.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double nearest = Double.POSITIVE_INFINITY;
        for (TownDoorNetwork.DoorState door : doors) {
            var link = door.connection();
            double distance = Math.min(camera.distanceToSqr(link.outside()), camera.distanceToSqr(link.inside()));
            nearest = Math.min(nearest, distance);
            boolean onVisibleSide = (camera.distanceToSqr(link.outside()) <= VIEW_RANGE * VIEW_RANGE
                    && link.distance(camera, true) > .00001)
                    || (camera.distanceToSqr(link.inside()) <= VIEW_RANGE * VIEW_RANGE
                    && link.distance(camera, false) > .00001);
            if (door.open() && onVisibleSide && distance <= VIEW_RANGE * VIEW_RANGE
                    && (frameDoor == null || distance < distanceTo(camera, frameDoor))) frameDoor = door;
        }
        if (nearest > PREPARE_RANGE * PREPARE_RANGE) return;

        try {
            var target = mc.getMainRenderTarget();
            // Allocate the stencil attachment while approaching a closed door, outside the first opened frame.
            if (!target.isStencilEnabled()) target.enableStencil();
            if (frameDoor == null) return;
            target.bindWrite(false);
            GL11.glClearStencil(0);
            GL11.glStencilMask(0xFF);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            framePrepared = true;
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** Called at the same pre-translucent point used by Immersive Portals. */
    void render(DeltaTracker delta, Camera camera, Matrix4f view, Matrix4f projection) {
        if (TownDoorRenderContext.isRendering() || failed || !framePrepared || frameDoor == null) return;
        framePrepared = false;
        TownDoorNetwork.DoorState state = frameDoor;
        frameDoor = null;

        try {
            Vec3 realCamera = camera.getPosition();
            var link = state.connection();
            for (int side = 0; side < 2; side++) {
                boolean entering = side == 0;
                Vec3 origin = link.origin(entering);
                double planeDistance = link.distance(realCamera, entering);
                if (planeDistance <= .00001
                        || realCamera.distanceToSqr(origin) > VIEW_RANGE * VIEW_RANGE) continue;
                AABB aperture = new AABB(origin.x - link.width() * .5, origin.y - 1, origin.z - .01,
                        origin.x + link.width() * .5, origin.y + 1, origin.z + .01);
                var frustum = ((TownDoorLevelRendererAccessor) Minecraft.getInstance().levelRenderer)
                        .stardewcraft$getCullingFrustum();
                // Vanilla's frustum becomes unreliable while the camera intersects the portal
                // plane. Immersive Portals deliberately skips this early test at close range too.
                if (planeDistance > NEAR_PLANE_FRUSTUM_BYPASS
                        && frustum != null && !frustum.isVisible(aperture)) continue;
                long start = PROFILE ? System.nanoTime() : 0;
                draw(delta, camera, state, entering, view, projection);
                if (PROFILE) {
                    cpuNanos += System.nanoTime() - start;
                    frames++;
                    if (frames % 600 == 0) {
                        StardewCraft.LOGGER.info("[TOWN-DOOR-PERF] frames={} meanCpuMs={} stencil-main-target",
                                frames, cpuNanos / (frames * 1_000_000.0));
                    }
                }
                break;
            }
        } catch (Throwable error) {
            fail(error);
        } finally {
            TownDoorClipping.disable();
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
    }

    private static double distanceTo(Vec3 camera, TownDoorNetwork.DoorState door) {
        return Math.min(camera.distanceToSqr(door.connection().outside()), camera.distanceToSqr(door.connection().inside()));
    }

    private void draw(DeltaTracker delta, Camera camera, TownDoorNetwork.DoorState state, boolean entering,
                      Matrix4f view, Matrix4f projection) throws Exception {
        try {
            renderDoorArea(state, entering, camera.getPosition(), view, projection, true);

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            clearPortalDepth();

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            renderContent(delta, camera, state, entering, view, projection);

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            try {
                renderDoorArea(state, entering, camera.getPosition(), view, projection, false);
            } finally {
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }
        } finally {
            restoreWorldRenderState();
        }
    }

    /** Writes the aperture into stencil first, then restores its physical depth after the nested render. */
    private static void renderDoorArea(TownDoorNetwork.DoorState state, boolean entering, Vec3 camera,
                                       Matrix4f view, Matrix4f projection, boolean writeStencil) {
        TownDoorClipping.disable();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.disableCull();
        if (writeStencil) {
            GL11.glStencilFunc(GL11.GL_EQUAL, 0, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
            GL11.glStencilMask(0xFF);
        } else {
            GL11.glStencilMask(0);
        }

        GL11.glEnable(GL32.GL_DEPTH_CLAMP);
        try {
            ShaderInstance shader = GameRenderer.getPositionColorShader();
            if (shader == null) throw new IllegalStateException("Minecraft position-color shader is unavailable");
            shader.MODEL_VIEW_MATRIX.set(view);
            shader.PROJECTION_MATRIX.set(projection);
            shader.apply();
            try {
                Vec3 point = state.connection().origin(entering).subtract(camera);
                float half = (float) state.connection().width() * .5f;
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                        DefaultVertexFormat.POSITION_COLOR);
                add(builder, point.x - half, point.y + 1, point.z);
                add(builder, point.x - half, point.y - 1, point.z);
                add(builder, point.x + half, point.y - 1, point.z);
                add(builder, point.x + half, point.y - 1, point.z);
                add(builder, point.x + half, point.y + 1, point.z);
                add(builder, point.x - half, point.y + 1, point.z);
                BufferUploader.draw(builder.buildOrThrow());
            } finally {
                shader.clear();
            }
        } finally {
            GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            GL11.glStencilMask(0xFF);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }

    private static void add(BufferBuilder builder, double x, double y, double z) {
        builder.addVertex((float) x, (float) y, (float) z).setColor(255, 255, 255, 255);
    }

    private static void clearPortalDepth() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        GL11.glDepthRange(1, 1);
        try {
            renderScreenQuad();
        } finally {
            GL11.glDepthRange(0, 1);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    /** Full-screen depth writer copied in structure from Immersive Portals' MyRenderHelper. */
    private static void renderScreenQuad() {
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) throw new IllegalStateException("Minecraft position-color shader is unavailable");
        Matrix4f identity = new Matrix4f();
        shader.MODEL_VIEW_MATRIX.set(identity);
        shader.PROJECTION_MATRIX.set(identity);
        shader.apply();
        try {
            BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR);
            add(builder, 1, -1, 0); add(builder, 1, 1, 0); add(builder, -1, 1, 0);
            add(builder, -1, 1, 0); add(builder, -1, -1, 0); add(builder, 1, -1, 0);
            BufferUploader.draw(builder.buildOrThrow());
        } finally {
            shader.clear();
        }
    }

    private void renderContent(DeltaTracker delta, Camera camera, TownDoorNetwork.DoorState state, boolean entering,
                               Matrix4f view, Matrix4f projection) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Vec3 remoteCamera = camera.getPosition().add(state.connection().translation(entering));
        Vec3 destination = state.connection().destination(entering);
        Vec3 normal = state.connection().direction(entering);
        if (portalBuffers == null) portalBuffers = new RenderBuffers(0);
        virtualCamera.copy(camera, remoteCamera, delta.getGameTimeDeltaPartialTick(false));

        var minecraft = (TownDoorMinecraftAccessor) mc;
        var gameRenderer = (TownDoorGameRendererAccessor) mc.gameRenderer;
        var levelRenderer = (TownDoorLevelRendererAccessor) mc.levelRenderer;
        var dispatcher = (TownDoorSectionRenderDispatcherAccessor) mc.levelRenderer.getSectionRenderDispatcher();
        RenderBuffers oldClientBuffers = mc.renderBuffers();
        RenderBuffers oldLevelBuffers = levelRenderer.stardewcraft$getRenderBuffers();
        var oldFixedBuffers = dispatcher.stardewcraft$getFixedBuffers();
        var oldVisibleSections = levelRenderer.stardewcraft$getVisibleSections();
        var oldFrustum = levelRenderer.stardewcraft$getCullingFrustum();
        var oldTransparency = levelRenderer.stardewcraft$getTransparencyChain();
        Camera oldCamera = mc.gameRenderer.getMainCamera();
        boolean oldRenderHand = gameRenderer.stardewcraft$rendersHand();
        boolean oldSmartCull = mc.smartCull;
        HitResult oldHit = mc.hitResult;
        var oldCrosshairEntity = mc.crosshairPickEntity;
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        SodiumContext.Token sodium = SodiumContext.Token.NONE;
        IrisContext.Token iris = IrisContext.Token.NONE;
        boolean contextStarted = false;
        try {
            mc.smartCull = false;
            minecraft.stardewcraft$setRenderBuffers(portalBuffers);
            levelRenderer.stardewcraft$setRenderBuffers(portalBuffers);
            dispatcher.stardewcraft$setFixedBuffers(portalBuffers.fixedBufferPack());
            levelRenderer.stardewcraft$setVisibleSections(portalVisibleSections.computeIfAbsent(
                    state.id(), ignored -> new ObjectArrayList<>(256)));
            levelRenderer.stardewcraft$setTransparencyChain(null);
            gameRenderer.stardewcraft$setMainCamera(virtualCamera);
            mc.gameRenderer.setRenderHand(false);
            mc.hitResult = null;
            TownDoorRenderContext.begin(remoteCamera, destination, normal, state.connection().width());
            contextStarted = true;
            // Sodium schedules its portal render-list rebuild while swapping views. The target-space
            // culling context must already be active when that work is requested.
            sodium = sodiumContext.swapIn(mc.levelRenderer, state.id());
            TownDoorRenderContext.rendererManagesTerrain(sodium != SodiumContext.Token.NONE);
            iris = irisContext.swapOut(mc.levelRenderer);
            TownDoorClipping.enable(destination, normal, remoteCamera, view);
            GL11.glEnable(GL32.GL_DEPTH_CLAMP);
            mc.getMainRenderTarget().bindWrite(true);
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilMask(0);
            mc.levelRenderer.prepareCullFrustum(remoteCamera, view, projection);
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            mc.getProfiler().push("town_door_content");
            try {
                mc.levelRenderer.renderLevel(delta, false, virtualCamera, mc.gameRenderer,
                        mc.gameRenderer.lightTexture(), view, projection);
            } finally {
                mc.getProfiler().pop();
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            portalVisibleSections.put(state.id(), levelRenderer.stardewcraft$getVisibleSections());
        } finally {
            TownDoorClipping.disable();
            if (contextStarted) TownDoorRenderContext.end();
            Exception rendererRestoreFailure = null;
            try {
                irisContext.restore(iris);
            } catch (Exception error) {
                rendererRestoreFailure = error;
            }
            try {
                sodiumContext.restore(sodium);
            } catch (Exception error) {
                if (rendererRestoreFailure == null) rendererRestoreFailure = error;
                else rendererRestoreFailure.addSuppressed(error);
            }
            levelRenderer.stardewcraft$setTransparencyChain(oldTransparency);
            levelRenderer.stardewcraft$setCullingFrustum(oldFrustum);
            levelRenderer.stardewcraft$setVisibleSections(oldVisibleSections);
            dispatcher.stardewcraft$setFixedBuffers(oldFixedBuffers);
            levelRenderer.stardewcraft$setRenderBuffers(oldLevelBuffers);
            minecraft.stardewcraft$setRenderBuffers(oldClientBuffers);
            gameRenderer.stardewcraft$setMainCamera(oldCamera);
            mc.gameRenderer.setRenderHand(oldRenderHand);
            mc.smartCull = oldSmartCull;
            mc.hitResult = oldHit;
            mc.levelRenderer.getSectionRenderDispatcher().setCamera(oldCamera.getPosition());
            mc.getEntityRenderDispatcher().prepare(mc.level, oldCamera, oldCrosshairEntity);
            mc.getBlockEntityRenderDispatcher().prepare(mc.level, oldCamera, oldHit);
            gameRenderer.stardewcraft$resetProjectionMatrix(oldProjection);
            restoreWorldRenderState();
            restoreOuterFog(delta, oldCamera);
            if (rendererRestoreFailure != null) throw rendererRestoreFailure;
        }
    }

    /**
     * Nested level rendering has a known exit state at this hook. Restoring it explicitly avoids the
     * synchronous OpenGL state reads which previously stalled the render thread every portal frame.
     */
    private static void restoreWorldRenderState() {
        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, target.viewWidth, target.viewHeight);
        RenderSystem.disableScissor();
        TownDoorClipping.disable();
        // Sodium/Iris may leave their terrain shader bound after the nested view.  Reset the
        // actual uniform as well as the Java-side flag so the outer terrain cannot remain clipped.
        TownDoorClipping.resetActiveShaderUniform();
        GL11.glDisable(GL32.GL_DEPTH_CLAMP);
        GL11.glDepthRange(0, 1);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilMask(0);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        // LevelRenderer changes both fixed-function diffuse lights and texture unit 2 while it
        // renders a world.  Immersive Portals restores these explicitly before resuming the
        // caller's view; otherwise the outer side can become black after looking through a door.
        if (mc.level != null && mc.level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    private static void restoreOuterFog(DeltaTracker delta, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        FogRenderer.setupColor(camera, partial, mc.level, mc.options.getEffectiveRenderDistance(),
                mc.gameRenderer.getDarkenWorldAmount(partial));
        Vec3 pos = camera.getPosition();
        boolean foggy = mc.level.effects().isFoggyAt(Mth.floor(pos.x), Mth.floor(pos.y))
                || mc.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupFog(camera, FogRenderer.FogMode.FOG_TERRAIN,
                Math.max(mc.gameRenderer.getRenderDistance(), 32.0F), foggy, partial);
        // This is the post-portal order used by Immersive Portals: setup the outer fog range,
        // then restore the world fog color that the nested camera replaced.
        FogRenderer.levelFogColor();
    }

    private void fail(Throwable error) {
        failed = true;
        framePrepared = false;
        TownDoorClipping.disable();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        StardewCraft.LOGGER.error("[TOWN-DOOR] Immersive stencil view failed; disabled until world reload", error);
    }

    @Override
    public void close() {
        framePrepared = false;
        frameDoor = null;
        failed = false;
        portalVisibleSections.clear();
        sodiumContext.clear();
    }

    private static final class ViewCamera extends Camera {
        void copy(Camera source, Vec3 position, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            setup(mc.level, mc.getCameraEntity(), source.isDetached(),
                    mc.options.getCameraType().isMirrored(), partialTick);
            setPosition(position);
            // setRotation updates both the quaternion and Camera's scalar yaw/pitch fields.
            // Copying only the quaternion makes frustum setup and world rendering disagree.
            setRotation(source.getYRot(), source.getXRot());
        }
    }

    /** Swaps Sodium's per-view render list while reusing already compiled chunk meshes. */
    private static final class SodiumContext {
        private boolean initialized;
        private boolean present;
        private Method getWorldRenderer;
        private Method scheduleUpdate;
        private Method emptyLists;
        private Field sectionManager;
        private Field renderLists;
        private final Map<Integer, Object> portalLists = new HashMap<>();

        void clear() {
            portalLists.clear();
        }

        void invalidate(int doorId) {
            portalLists.remove(doorId);
        }

        Token swapIn(net.minecraft.client.renderer.LevelRenderer renderer, int doorId) throws Exception {
            initialize(renderer);
            if (!present) return Token.NONE;
            Object sodiumRenderer = getWorldRenderer.invoke(renderer);
            Object manager = sectionManager.get(sodiumRenderer);
            Object outerLists = renderLists.get(manager);
            Object cachedLists = portalLists.computeIfAbsent(doorId, ignored -> {
                try {
                    return emptyLists.invoke(null);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
            });
            boolean swapped = false;
            try {
                scheduleUpdate.invoke(sodiumRenderer);
                renderLists.set(manager, cachedLists);
                swapped = true;
                scheduleUpdate.invoke(sodiumRenderer);
                return new Token(doorId, sodiumRenderer, manager, outerLists);
            } catch (Exception error) {
                if (swapped) renderLists.set(manager, outerLists);
                throw error;
            }
        }

        void restore(Token token) throws Exception {
            if (token == Token.NONE) return;
            portalLists.put(token.doorId, renderLists.get(token.manager));
            renderLists.set(token.manager, token.outerLists);
            scheduleUpdate.invoke(token.renderer);
        }

        boolean promotePortalView(net.minecraft.client.renderer.LevelRenderer renderer, int doorId) throws Exception {
            initialize(renderer);
            Object cachedLists = portalLists.get(doorId);
            if (!present || cachedLists == null) return false;
            Object sodiumRenderer = getWorldRenderer.invoke(renderer);
            Object manager = sectionManager.get(sodiumRenderer);
            Object mainLists = renderLists.get(manager);
            scheduleUpdate.invoke(sodiumRenderer);
            renderLists.set(manager, cachedLists);
            portalLists.put(doorId, mainLists);
            scheduleUpdate.invoke(sodiumRenderer);
            return true;
        }

        private void initialize(net.minecraft.client.renderer.LevelRenderer renderer) throws Exception {
            if (initialized) return;
            initialized = true;
            try {
                ClassLoader loader = renderer.getClass().getClassLoader();
                Class<?> extension = Class.forName("net.caffeinemc.mods.sodium.client.world.LevelRendererExtension", false, loader);
                Class<?> sodiumRenderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", false, loader);
                Class<?> manager = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", false, loader);
                Class<?> lists = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists", false, loader);
                getWorldRenderer = extension.getMethod("sodium$getWorldRenderer");
                scheduleUpdate = sodiumRenderer.getMethod("scheduleTerrainUpdate");
                emptyLists = lists.getMethod("empty");
                sectionManager = sodiumRenderer.getDeclaredField("renderSectionManager");
                renderLists = manager.getDeclaredField("renderLists");
                sectionManager.setAccessible(true);
                renderLists.setAccessible(true);
                present = true;
            } catch (ClassNotFoundException ignored) {
                present = false;
            }
        }

        private record Token(int doorId, Object renderer, Object manager, Object outerLists) {
            private static final Token NONE = new Token(-1, null, null, null);
        }
    }

    /** Iris stores this optional pipeline on LevelRenderer; nested vanilla drawing needs a separate lazy state. */
    private static final class IrisContext {
        private boolean initialized;
        private Field pipeline;

        Token swapOut(net.minecraft.client.renderer.LevelRenderer renderer) throws IllegalAccessException {
            initialize(renderer);
            if (pipeline == null) return Token.NONE;
            Object old = pipeline.get(renderer);
            pipeline.set(renderer, null);
            return new Token(renderer, old);
        }

        void restore(Token token) throws IllegalAccessException {
            if (token != Token.NONE) pipeline.set(token.renderer, token.pipeline);
        }

        private void initialize(net.minecraft.client.renderer.LevelRenderer renderer) {
            if (initialized) return;
            initialized = true;
            try {
                pipeline = renderer.getClass().getDeclaredField("pipeline");
                pipeline.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                pipeline = null;
            }
        }

        private record Token(Object renderer, Object pipeline) {
            private static final Token NONE = new Token(null, null);
        }
    }

}
