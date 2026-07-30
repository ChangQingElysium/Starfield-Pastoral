package com.stardew.craft.client.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.PassOutOverlayScreen;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.payload.PassOutAckPayload;
import com.stardew.craft.network.payload.PassOutPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Client-only presentation and input lock for combat HP=0.
 *
 * <p>The server remains authoritative for penalties, teleportation and rescue selection. This
 * state only sends the transaction ACK after the complete eight-second collapse, then holds full
 * black until the destination-load handshake confirms that the rescue location is ready.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class CombatCollapseClientState {
    private static final int OVERLAY_Z = 30_000;

    private static final CombatCollapseTransactionGate TRANSACTIONS =
        new CombatCollapseTransactionGate();

    private static int elapsedTicks;
    private static UUID collapsingPlayerId;
    private static CameraType previousCameraType;
    private static PassOutOverlayScreen screen;
    private static PassOutPayload deferredPayload;

    private CombatCollapseClientState() {
    }

    public static void begin(PassOutPayload payload) {
        if (payload == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            deferredPayload = payload;
            return;
        }
        deferredPayload = null;
        long requestedTransaction = payload.transactionId();
        CombatCollapseTransactionGate.StartDecision decision =
            TRANSACTIONS.begin(requestedTransaction);
        if (decision == CombatCollapseTransactionGate.StartDecision.DUPLICATE_ACTIVE
                || decision == CombatCollapseTransactionGate.StartDecision.ALREADY_ACKNOWLEDGED) {
            return;
        }
        if (decision == CombatCollapseTransactionGate.StartDecision.REPLACED_ACTIVE) {
            clearVisual(false);
        }

        elapsedTicks = 0;
        collapsingPlayerId = minecraft.player.getUUID();
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        screen = new PassOutOverlayScreen(requestedTransaction);
        minecraft.setScreen(screen);
        lockPlayer(minecraft);
        StardewMusicManager.stopForCutsceneSilence();

        StardewCraft.LOGGER.info(
            "[COMBAT_COLLAPSE_CLIENT] Started transaction={} type={}",
            requestedTransaction, payload.passOutType());
    }

    public static boolean isActive() {
        return TRANSACTIONS.isActive();
    }

    public static boolean isCollapsing(AbstractClientPlayer player) {
        return TRANSACTIONS.isActive()
            && collapsingPlayerId != null
            && player != null
            && collapsingPlayerId.equals(player.getUUID());
    }

    public static float bodyRotationDegrees(AbstractClientPlayer player, float partialTick) {
        if (!isCollapsing(player)) {
            return 0.0F;
        }
        return 90.0F * CombatCollapseTimeline.bodyFallProgress(elapsedTicks, partialTick);
    }

    public static float bodyJitterX(AbstractClientPlayer player, float partialTick) {
        if (!isCollapsing(player)) {
            return 0.0F;
        }
        float strength = CombatCollapseTimeline.jitterStrength(elapsedTicks, partialTick);
        float time = elapsedTicks + partialTick;
        return (float) Math.sin(time * 2.71F) * 0.028F * strength;
    }

    public static float bodyJitterZ(AbstractClientPlayer player, float partialTick) {
        if (!isCollapsing(player)) {
            return 0.0F;
        }
        float strength = CombatCollapseTimeline.jitterStrength(elapsedTicks, partialTick);
        float time = elapsedTicks + partialTick;
        return (float) Math.cos(time * 3.17F) * 0.022F * strength;
    }

    public static float cameraYawJitter(float partialTick) {
        if (!TRANSACTIONS.isActive()) {
            return 0.0F;
        }
        float strength = CombatCollapseTimeline.jitterStrength(elapsedTicks, partialTick);
        return (float) Math.sin((elapsedTicks + partialTick) * 1.83F) * 0.75F * strength;
    }

    public static float cameraPitchJitter(float partialTick) {
        if (!TRANSACTIONS.isActive()) {
            return 0.0F;
        }
        float strength = CombatCollapseTimeline.jitterStrength(elapsedTicks, partialTick);
        return (float) Math.cos((elapsedTicks + partialTick) * 2.11F) * 0.5F * strength;
    }

    public static void renderOverlay(GuiGraphics graphics, int width, int height, float partialTick) {
        if (!TRANSACTIONS.isActive()) {
            return;
        }
        fillOverlay(
            graphics,
            width,
            height,
            argb(CombatCollapseTimeline.redAlpha(elapsedTicks, partialTick), 0xFF0000)
        );
        fillOverlay(
            graphics,
            width,
            height,
            argb(CombatCollapseTimeline.blackAlpha(elapsedTicks, partialTick), 0x000000)
        );
    }

    /**
     * Called only after the client has the authored rescue dimension/chunk and is standing in its
     * target chunk. The coordinator's ready packet is sent immediately after this call.
     */
    public static void destinationReady() {
        if (!TRANSACTIONS.isAcknowledged()) {
            return;
        }
        StardewCraft.LOGGER.info(
            "[COMBAT_COLLAPSE_CLIENT] Destination ready for transaction={}",
            TRANSACTIONS.activeTransaction());
    }

    /**
     * Hands the already-black combat overlay to the cutscene fade layer after
     * EventPlayer has successfully taken ownership.
     */
    public static void handoffToCutscene() {
        if (!TRANSACTIONS.isAcknowledged()) {
            return;
        }
        com.stardew.craft.cutscene.runtime.EventScreenFade.holdBlack();
        clearVisual(false);
        TRANSACTIONS.finish();
    }

    /**
     * Releases silence for non-cutscene/failure outcomes. A normal rescue cutscene owns and
     * releases the same music override through EventPlayer.
     */
    public static void outcomeReady() {
        if (TRANSACTIONS.isActive()) {
            clearVisual(false);
            TRANSACTIONS.finish();
        }
        StardewMusicManager.releaseCutsceneOverride();
    }

    public static void resetSession() {
        clearVisual(true);
        deferredPayload = null;
        TRANSACTIONS.resetSession();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!TRANSACTIONS.isActive() && deferredPayload != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null) {
                PassOutPayload readyPayload = deferredPayload;
                deferredPayload = null;
                begin(readyPayload);
            }
        }
        if (!TRANSACTIONS.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            resetSession();
            return;
        }

        lockPlayer(minecraft);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (minecraft.screen != screen) {
            minecraft.setScreen(screen);
        }
        if (elapsedTicks < CombatCollapseTimeline.TOTAL_TICKS) {
            elapsedTicks++;
        }
        if (CombatCollapseTimeline.shouldAcknowledge(elapsedTicks)
                && minecraft.getConnection() == null) {
            resetSession();
            return;
        }
        if (CombatCollapseTimeline.shouldAcknowledge(elapsedTicks)
                && TRANSACTIONS.acknowledgeOnce()) {
            long acknowledgedTransaction = TRANSACTIONS.activeTransaction();
            PacketDistributor.sendToServer(new PassOutAckPayload(acknowledgedTransaction));
            StardewCraft.LOGGER.info(
                "[COMBAT_COLLAPSE_CLIENT] Sent collapse ACK transaction={}",
                acknowledgedTransaction);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!TRANSACTIONS.isActive()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (TRANSACTIONS.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (TRANSACTIONS.isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
    }

    private static void clearVisual(boolean releaseSilence) {
        Minecraft minecraft = Minecraft.getInstance();
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }
        if (minecraft.screen == screen) {
            minecraft.setScreen(null);
        }
        if (releaseSilence) {
            StardewMusicManager.releaseCutsceneOverride();
        }

        elapsedTicks = 0;
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
        minecraft.player.stopUsingItem();

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

    private static void drain(KeyMapping mapping) {
        while (mapping.consumeClick()) {
            // Consume input accumulated before or during the knockout.
        }
    }

    private static void fillOverlay(GuiGraphics graphics, int width, int height, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        graphics.fill(0, 0, width, height, OVERLAY_Z, color);
    }

    private static int argb(float alpha, int rgb) {
        int channel = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return channel << 24 | rgb & 0xFFFFFF;
    }
}
