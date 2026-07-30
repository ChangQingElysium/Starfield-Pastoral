package com.stardew.craft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import com.stardew.craft.network.payload.MapInteractionHintPayload;
import com.stardew.craft.network.payload.MapInteractionHintRequestPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Unified crosshair-side indicator for readable points, NPCs, animals and
 * actionable blocks. The server resolves the semantic type and done state.
 */
public final class MapInteractionHintRenderer {
    private static final int CROSSHAIR_GAP = 9;
    private static final float FADE_IN_MS = 140.0F;
    private static final float FADE_OUT_MS = 180.0F;
    private static final float DONE_OPACITY = 0.45F;
    private static final double PENDING_BOB_PERIOD_MS = 1200.0D;
    private static final double PENDING_BOB_AMPLITUDE = 2.0D;
    private static final int REFRESH_TICKS = 10;

    private static Target serverTarget;
    private static Target displayedTarget;
    private static CrosshairTarget lastQueriedTarget;
    private static ResourceKey<Level> lastDimension;
    private static int refreshTicks;
    private static float fadeAlpha;
    private static long lastFadeUpdateMs;

    private MapInteractionHintRenderer() {
    }

    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            clearAll();
            return;
        }
        if (minecraft.screen != null || minecraft.options.hideGui) {
            clearTargeting();
            return;
        }

        CrosshairTarget targeted = currentTarget(minecraft);
        if (targeted == null) {
            clearTargeting();
            return;
        }

        ResourceKey<Level> dimension = minecraft.level.dimension();
        boolean changed = !targeted.equals(lastQueriedTarget)
                || !dimension.equals(lastDimension);
        if (changed || ++refreshTicks >= REFRESH_TICKS) {
            lastQueriedTarget = targeted;
            lastDimension = dimension;
            refreshTicks = 0;
            PacketDistributor.sendToServer(targeted.entityId() >= 0
                    ? new MapInteractionHintRequestPayload(
                            targeted.pos(), targeted.entityId())
                    : MapInteractionHintRequestPayload.block(
                            targeted.pos()));
        }
    }

    public static void accept(MapInteractionHintPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        CrosshairTarget current = currentTarget(minecraft);
        if (current == null || !current.matches(payload)) {
            return;
        }
        serverTarget = payload.visible()
                ? new Target(
                        current,
                        payload.identity(),
                        payload.hintType(),
                        payload.done())
                : null;
    }

    public static void onRenderGui(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean targeted = isCurrentTarget(minecraft, serverTarget);
        updateDisplayedTarget(targeted);
        updateFade(targeted);

        Target target = displayedTarget;
        if (target == null
                || fadeAlpha <= 0.01F
                || minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.screen != null
                || minecraft.player.isSpectator()
                || minecraft.screen
                        instanceof StardewHudLayoutEditorScreen) {
            return;
        }

        Icon icon = iconFor(target.type(), target.done());
        boolean pendingAttention =
                (target.type() == StardewInteractionHintType.LOOK
                        || target.type()
                                == StardewInteractionHintType.TALK)
                && !target.done();
        long now = Util.getMillis();
        int bobOffset = pendingAttention
                ? (int) Math.round(Math.sin(
                        now / PENDING_BOB_PERIOD_MS
                                * Math.PI * 2.0D)
                        * PENDING_BOB_AMPLITUDE)
                : 0;
        int x = graphics.guiWidth() / 2 + CROSSHAIR_GAP;
        int y = graphics.guiHeight() / 2
                - icon.height() / 2 + bobOffset;
        boolean completedAttention =
                (target.type() == StardewInteractionHintType.LOOK
                        || target.type()
                                == StardewInteractionHintType.TALK)
                && target.done();
        float stateOpacity = completedAttention
                ? DONE_OPACITY
                : 1.0F;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(
                1.0F, 1.0F, 1.0F,
                fadeAlpha * stateOpacity);
        graphics.blit(
                icon.texture(),
                x, y, 0, 0,
                icon.width(), icon.height(),
                icon.width(), icon.height());
        RenderSystem.setShaderColor(
                1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Icon iconFor(
            StardewInteractionHintType type,
            boolean done
    ) {
        return switch (type) {
            case GRAB -> icon("grab", 10, 10);
            case GIFT -> icon("gift", 14, 14);
            case TALK -> icon(done ? "talk_done" : "talk", 14, 14);
            case LOOK -> icon(done ? "look_done" : "look", 13, 13);
            case HARVEST -> icon("harvest", 8, 8);
        };
    }

    private static Icon icon(String name, int width, int height) {
        return new Icon(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID,
                        "textures/gui/interaction_hint/"
                                + name + ".png"),
                width,
                height);
    }

    private static void updateDisplayedTarget(boolean targeted) {
        if (!targeted || serverTarget == null) {
            return;
        }
        if (displayedTarget == null
                || !displayedTarget.sameInteraction(serverTarget)) {
            displayedTarget = serverTarget;
            fadeAlpha = 0.0F;
            lastFadeUpdateMs = Util.getMillis();
            return;
        }
        displayedTarget = serverTarget;
    }

    private static void updateFade(boolean targeted) {
        long now = Util.getMillis();
        if (lastFadeUpdateMs == 0L) {
            lastFadeUpdateMs = now;
            return;
        }
        float elapsedMs = Math.min(
                100.0F, now - lastFadeUpdateMs);
        lastFadeUpdateMs = now;
        float durationMs = targeted
                ? FADE_IN_MS
                : FADE_OUT_MS;
        float delta = elapsedMs / durationMs;
        fadeAlpha = targeted
                ? Math.min(1.0F, fadeAlpha + delta)
                : Math.max(0.0F, fadeAlpha - delta);
        if (!targeted && fadeAlpha <= 0.0F) {
            displayedTarget = null;
        }
    }

    private static boolean isCurrentTarget(
            Minecraft minecraft,
            Target target
    ) {
        if (target == null) {
            return false;
        }
        CrosshairTarget current = currentTarget(minecraft);
        return current != null && current.equals(target.target());
    }

    private static CrosshairTarget currentTarget(
            Minecraft minecraft
    ) {
        if (minecraft == null || minecraft.hitResult == null) {
            return null;
        }
        if (minecraft.hitResult
                instanceof EntityHitResult entityHit
                && entityHit.getType() == HitResult.Type.ENTITY) {
            return CrosshairTarget.entity(
                    entityHit.getEntity().getId(),
                    entityHit.getEntity().blockPosition());
        }
        if (minecraft.hitResult
                instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            return CrosshairTarget.block(blockHit.getBlockPos());
        }
        return null;
    }

    private static void clearTargeting() {
        serverTarget = null;
        lastQueriedTarget = null;
        lastDimension = null;
        refreshTicks = 0;
    }

    private static void clearAll() {
        clearTargeting();
        displayedTarget = null;
        fadeAlpha = 0.0F;
        lastFadeUpdateMs = 0L;
    }

    private record Icon(
            ResourceLocation texture,
            int width,
            int height
    ) {
    }

    private record CrosshairTarget(
            BlockPos pos,
            int entityId
    ) {
        private CrosshairTarget {
            pos = pos.immutable();
        }

        private static CrosshairTarget block(BlockPos pos) {
            return new CrosshairTarget(pos, -1);
        }

        private static CrosshairTarget entity(
                int entityId,
                BlockPos pos
        ) {
            return new CrosshairTarget(pos, entityId);
        }

        private boolean matches(MapInteractionHintPayload payload) {
            return entityId >= 0
                    ? entityId == payload.entityId()
                    : payload.entityId() < 0
                            && pos.equals(payload.pos());
        }
    }

    private record Target(
            CrosshairTarget target,
            ResourceLocation identity,
            StardewInteractionHintType type,
            boolean done
    ) {
        private boolean sameInteraction(Target other) {
            return target.equals(other.target)
                    && identity.equals(other.identity)
                    && type == other.type;
        }
    }
}
