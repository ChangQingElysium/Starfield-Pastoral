package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.UUID;

/**
 * Client-side state machine for 2AM/stamina collapse.
 *
 * <p>The final settlement is retained until the full vanilla animation-293 collapse has finished. A duplicate
 * start or settlement packet for the same day is harmless. Session state is discarded on
 * disconnect so reconnecting can never inherit a black screen.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class OvernightCollapseClientState {
    private static boolean active;
    private static boolean restingAtBed;
    private static boolean returnToBedRequested;
    private static int settlementDay = Integer.MIN_VALUE;
    private static int elapsedTicks;
    private static UUID collapsingPlayerId;
    private static OvernightCollapseStartPayload.Cause cause = OvernightCollapseStartPayload.Cause.TWO_AM;
    private static OvernightSettlementPayload pendingSettlement;
    private static int lastDeliveredSettlementDay = Integer.MIN_VALUE;
    private static CameraType previousCameraType;
    private static OvernightCollapseScreen screen;
    private static int deferredSettlementDay = Integer.MIN_VALUE;
    private static OvernightCollapseStartPayload.Cause deferredCause;

    private OvernightCollapseClientState() {
    }

    public static void begin(int requestedSettlementDay, OvernightCollapseStartPayload.Cause requestedCause) {
        int normalizedDay = Math.max(1, requestedSettlementDay);
        if (normalizedDay <= lastDeliveredSettlementDay) {
            return;
        }
        if ((active || restingAtBed) && normalizedDay == settlementDay) {
            return;
        }

        if (active || restingAtBed) {
            endVisual(false);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            deferredSettlementDay = normalizedDay;
            deferredCause = requestedCause;
            return;
        }
        deferredSettlementDay = Integer.MIN_VALUE;
        deferredCause = null;

        active = true;
        settlementDay = normalizedDay;
        elapsedTicks = 0;
        cause = requestedCause == null
            ? OvernightCollapseStartPayload.Cause.TWO_AM
            : requestedCause;
        collapsingPlayerId = minecraft.player.getUUID();
        pendingSettlement = null;
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        screen = new OvernightCollapseScreen();
        minecraft.setScreen(screen);
        lockPlayer(minecraft);

        StardewCraft.LOGGER.info(
            "[OVERNIGHT_COLLAPSE_CLIENT] Started day={} cause={}", settlementDay, cause);
    }

    /**
     * Receives the authoritative end-of-night result. A pass-out settlement without a preceding
     * start packet uses a complete local fallback sequence (important for packet ordering and
     * reconnect races).
     */
    public static void acceptSettlement(OvernightSettlementPayload payload) {
        int payloadDay = settlementOrdinal(payload.context());
        if (payloadDay <= lastDeliveredSettlementDay) {
            StardewCraft.LOGGER.debug(
                "[OVERNIGHT_COLLAPSE_CLIENT] Ignored duplicate settlement day={}", payloadDay);
            return;
        }

        if (restingAtBed) {
            if (payloadDay < settlementDay) {
                return;
            }
            leaveBedRest();
            deliverSettlement(payload, payloadDay);
            return;
        }

        if (!active && payload.hasPassOut()) {
            begin(payloadDay, causeFromPassOutType(payload.passOutType()));
        }

        if (!active) {
            deliverSettlement(payload, payloadDay);
            return;
        }

        if (payloadDay < settlementDay) {
            StardewCraft.LOGGER.warn(
                "[OVERNIGHT_COLLAPSE_CLIENT] Ignored stale settlement day={} while waiting for day={}",
                payloadDay, settlementDay);
            return;
        }
        if (payloadDay > settlementDay) {
            StardewCraft.LOGGER.warn(
                "[OVERNIGHT_COLLAPSE_CLIENT] Replaced stale collapse day={} with settlement day={}",
                settlementDay, payloadDay);
            endVisual(false);
            begin(payloadDay, causeFromPassOutType(payload.passOutType()));
        }

        if (pendingSettlement != null) {
            StardewCraft.LOGGER.debug(
                "[OVERNIGHT_COLLAPSE_CLIENT] Ignored repeated pending settlement day={}", payloadDay);
            return;
        }
        pendingSettlement = payload;
        StardewCraft.LOGGER.info(
            "[OVERNIGHT_COLLAPSE_CLIENT] Settlement ready day={} at collapseTick={}",
            payloadDay, elapsedTicks);
        tryDeliverPending();
    }

    public static boolean isActive() {
        return active || restingAtBed;
    }

    public static boolean isCollapsing(AbstractClientPlayer player) {
        return (active || restingAtBed)
            && collapsingPlayerId != null
            && player != null
            && collapsingPlayerId.equals(player.getUUID());
    }

    public static float collapseRotationDegrees(AbstractClientPlayer player, float partialTick) {
        if (!isCollapsing(player)) {
            return 0.0F;
        }
        return OvernightCollapseTimeline.drowsyNodDegrees(elapsedTicks, partialTick)
            + 90.0F * OvernightCollapseTimeline.collapseProgress(elapsedTicks, partialTick);
    }

    static float blackAlpha(float partialTick) {
        if (!active) {
            return 0.0F;
        }
        return OvernightCollapseTimeline.blackAlpha(elapsedTicks, partialTick);
    }

    /**
     * The server has moved an early multiplayer stamina collapse back to bed
     * without advancing the shared day. This packet normally arrives before
     * animation 293 ends, so visual release is deferred until tick 174.
     */
    public static void returnedToBed(int requestedSettlementDay) {
        if (!active || requestedSettlementDay != settlementDay) {
            return;
        }
        returnToBedRequested = true;
        tryEnterBedRest();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active && !restingAtBed && deferredSettlementDay != Integer.MIN_VALUE) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                int readyDay = deferredSettlementDay;
                OvernightCollapseStartPayload.Cause readyCause = deferredCause;
                deferredSettlementDay = Integer.MIN_VALUE;
                deferredCause = null;
                begin(readyDay, readyCause);
            }
        }
        if (!active && !restingAtBed) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetSession();
            return;
        }

        lockPlayer(minecraft);
        if (restingAtBed) {
            return;
        }
        if (minecraft.screen != screen) {
            minecraft.setScreen(screen);
        }
        if (elapsedTicks < OvernightCollapseTimeline.COLLAPSE_TICKS) {
            elapsedTicks++;
        }
        tryDeliverPending();
        tryEnterBedRest();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active && !restingAtBed) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (active || restingAtBed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (active || restingAtBed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
        ClientOvernightHandler.resetSession();
    }

    public static void resetSession() {
        endVisual(false);
        settlementDay = Integer.MIN_VALUE;
        lastDeliveredSettlementDay = Integer.MIN_VALUE;
        pendingSettlement = null;
        restingAtBed = false;
        returnToBedRequested = false;
        deferredSettlementDay = Integer.MIN_VALUE;
        deferredCause = null;
    }

    /** Releases all collapse state after an authoritative settlement failure. */
    public static void cancel() {
        endVisual(false);
        settlementDay = Integer.MIN_VALUE;
        pendingSettlement = null;
        restingAtBed = false;
        returnToBedRequested = false;
        deferredSettlementDay = Integer.MIN_VALUE;
        deferredCause = null;
        ClientOvernightHandler.resetSession();
    }

    private static void tryDeliverPending() {
        if (!OvernightCollapseTimeline.canOpenSettlement(
                elapsedTicks, pendingSettlement != null)) {
            return;
        }
        OvernightSettlementPayload payload = pendingSettlement;
        int payloadDay = settlementOrdinal(payload.context());
        pendingSettlement = null;
        endVisual(true);
        deliverSettlement(payload, payloadDay);
    }

    private static void tryEnterBedRest() {
        if (!active
                || !returnToBedRequested
                || pendingSettlement != null
                || elapsedTicks < OvernightCollapseTimeline.COLLAPSE_TICKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        if (minecraft.screen == screen) {
            minecraft.setScreen(null);
        }
        active = false;
        restingAtBed = true;
        returnToBedRequested = false;
        previousCameraType = null;
        screen = null;
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_COLLAPSE_CLIENT] Returned to bed while awaiting day={}",
                settlementDay);
    }

    private static void leaveBedRest() {
        active = false;
        restingAtBed = false;
        returnToBedRequested = false;
        elapsedTicks = 0;
        settlementDay = Integer.MIN_VALUE;
        collapsingPlayerId = null;
        previousCameraType = null;
        screen = null;
    }

    private static void deliverSettlement(OvernightSettlementPayload payload, int payloadDay) {
        lastDeliveredSettlementDay = Math.max(lastDeliveredSettlementDay, payloadDay);
        ClientOvernightHandler.startSequence(payload);
    }

    private static void endVisual(boolean settlementWillReplaceScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        if (!settlementWillReplaceScreen && minecraft.screen == screen) {
            minecraft.setScreen(null);
        }

        active = false;
        restingAtBed = false;
        returnToBedRequested = false;
        elapsedTicks = 0;
        settlementDay = Integer.MIN_VALUE;
        collapsingPlayerId = null;
        previousCameraType = null;
        screen = null;
    }

    private static void lockPlayer(Minecraft minecraft) {
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.fallDistance = 0.0F;
        minecraft.player.xxa = 0.0F;
        minecraft.player.zza = 0.0F;
        minecraft.player.setSprinting(false);

        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyShift.setDown(false);
        minecraft.options.keySprint.setDown(false);
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyPickItem.setDown(false);
        minecraft.options.keyDrop.setDown(false);
        minecraft.options.keySwapOffhand.setDown(false);
        minecraft.options.keyInventory.setDown(false);

        drain(minecraft.options.keyAttack);
        drain(minecraft.options.keyUse);
        drain(minecraft.options.keyPickItem);
        drain(minecraft.options.keyDrop);
        drain(minecraft.options.keySwapOffhand);
        drain(minecraft.options.keyInventory);
    }

    private static void drain(net.minecraft.client.KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // Consume presses accumulated before or during the collapse.
        }
    }

    private static int settlementOrdinal(OvernightSettlementPayload.OvernightContext context) {
        return ((Math.max(1, context.newYear()) - 1) * 112)
            + (Math.max(0, Math.min(3, context.newSeason())) * 28)
            + Math.max(1, Math.min(28, context.newDay()));
    }

    private static OvernightCollapseStartPayload.Cause causeFromPassOutType(int passOutType) {
        // PassOutService ids 2/3 are 2AM and stamina respectively. Keep this client class
        // independent of the server-only recovery service.
        return passOutType == 3
            ? OvernightCollapseStartPayload.Cause.STAMINA
            : OvernightCollapseStartPayload.Cause.TWO_AM;
    }
}
