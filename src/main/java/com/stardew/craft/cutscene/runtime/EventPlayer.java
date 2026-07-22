package com.stardew.craft.cutscene.runtime;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.stardew.craft.cutscene.command.EventCommand;
import com.stardew.craft.cutscene.command.EventCommandFactory;
import com.stardew.craft.cutscene.data.EventData;
import com.stardew.craft.cutscene.network.MarkEventSeenPayload;
import com.stardew.craft.client.sound.StardewMusicManager;
import com.stardew.craft.network.payload.ClientNpcVisibilityState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.*;

/**
 * Client-side event playback engine.
 * Drives a tick-based state machine that executes event commands sequentially.
 *
 * Singleton — only one event can play at a time.
 */
@OnlyIn(Dist.CLIENT)
public final class EventPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EventPlayer INSTANCE = new EventPlayer();

    // ─── state ───
    private EventData currentEvent = null;
    private List<EventCommand> commands = List.of();
    private List<Integer> commandTokens = List.of();
    private int commandIndex = -1;
    private long sessionId = 0L;
    private boolean running = false;
    private boolean skippable = false;
    private boolean playerFrozen = false;
    private boolean previousHideGui = false;
    private boolean realPlayerMovedByServer = false;
    private PlayerSnapshot playerSnapshot = null;

    /** Dimension the cutscene was started in; if the player leaves it, abort. */
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> startDimension = null;

    // Actor and NPC management
    private final Map<String, Mob> actors = new HashMap<>();
    private final Set<String> hiddenNpcs = new HashSet<>();

    private EventPlayer() {}

    private record PlayerSnapshot(double x, double y, double z, float yRot, float xRot, float yHeadRot) {}

    public static EventPlayer get() {
        return INSTANCE;
    }

    // ─── public API ───

    /**
     * Start playing an event. Call from client thread only.
     */
    public boolean start(EventData event, long authorizedSessionId) {
        if (running) {
            LOGGER.warn("Tried to start event {} while {} is playing", event.id(), currentEvent.id());
            return false;
        }

        LOGGER.info("Starting cutscene event: {}", event.id());
        currentEvent = event;
        sessionId = authorizedSessionId;
        skippable = event.skippableAtStart();
        realPlayerMovedByServer = false;
        capturePlayerSnapshot();

        if (event.trigger() != null && "wake_up".equals(event.trigger().type())
                && CutsceneAnchorRegistry.get("farm_spawn") == null) {
            Minecraft mc = Minecraft.getInstance();
            var localPlayer = mc.player;
            if (localPlayer != null) {
                CutsceneAnchorRegistry.set("farm_spawn", localPlayer.getX(), localPlayer.getY(), localPlayer.getZ());
                LOGGER.warn("Wake-up cutscene {} started without farm_spawn anchor; using current player position as fallback", event.id());
            }
        }

        // NOTE: Do NOT clear the anchor registry here.
        // Server-pushed anchors (e.g. farm_spawn for wake_up events) arrive BEFORE
        // the TriggerEventPayload, so wiping at start would discard them. Anchors
        // are cleared at event end / abort instead (see finish() / abort()).

        // Parse commands
        List<EventCommand> parsed = new ArrayList<>();
        List<Integer> parsedTokens = new ArrayList<>();
        for (int rawIndex = 0; rawIndex < event.rawCommands().size(); rawIndex++) {
            JsonObject obj = event.rawCommands().get(rawIndex);
            try {
                EventCommand cmd = EventCommandFactory.create(obj);
                if (cmd != null) {
                    parsed.add(cmd);
                    parsedTokens.add(rawIndex);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse cutscene command: {} — {}", obj, e.getMessage());
            }
        }
        commands = parsed;
        commandTokens = parsedTokens;
        commandIndex = 0;
        running = true;

        // Capture starting dimension so we can abort if the player teleports out.
        net.minecraft.client.multiplayer.ClientLevel cap = Minecraft.getInstance().level;
        if (cap != null) {
            startDimension = cap.dimension();
        } else {
            startDimension = null;
        }

        // Hide all GUI (like spectator)
        Minecraft mc = Minecraft.getInstance();
        previousHideGui = mc.options.hideGui;
        mc.options.hideGui = true;

        // Start first command
        if (!commands.isEmpty()) {
            commands.get(0).start(this);
        }
        return true;
    }

    /**
     * Called every client tick from ModClientEvents.
     */
    public void tick() {
        if (!running || commands.isEmpty()) return;

        // Abort if the player has changed dimension since the cutscene started.
        // Otherwise camera/actor commands keep playing in a world the player is
        // no longer in (e.g. after a portal teleport mid-cutscene).
        if (startDimension != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.level.dimension() != startDimension) {
                LOGGER.warn("Cutscene {} aborted: player left starting dimension {}",
                        currentEvent != null ? currentEvent.id() : "?", startDimension);
                endEvent();
                return;
            }
        }

        if (commandIndex >= commands.size()) {
            // All commands exhausted without explicit 'end'
            endEvent();
            return;
        }

        EventCommand current = commands.get(commandIndex);
        current.tick(this);

        if (current.isComplete()) {
            commandIndex++;
            if (commandIndex < commands.size()) {
                commands.get(commandIndex).start(this);
            }
        }
    }

    /**
     * Try to skip the current event (called on ESC press).
     */
    public void trySkip() {
        if (!running || !skippable) return;

        LOGGER.info("Skipping event: {}", currentEvent.id());

        if (commandIndex >= 0 && commandIndex < commands.size()) {
            EventCommand active = commands.get(commandIndex);
            if (!active.isStateCommand()) {
                active.onSkip(this);
            }
        }

        // Execute all remaining state commands
        for (int i = commandIndex; i < commands.size(); i++) {
            commandIndex = i;
            EventCommand cmd = commands.get(i);
            if (cmd.isStateCommand()) {
                cmd.onSkip(this);
            }
        }

        endEvent();
    }

    /**
     * Called by EndCommand or internally to finish the event.
     */
    public void endEvent() {
        if (!running) return;

        String eventId = currentEvent.id();
        LOGGER.info("Ending cutscene event: {}", eventId);

        // Restore player
        setPlayerFrozen(false);
        restorePlayerSnapshotIfNeeded();

        // Restore GUI. A skip can happen while a blocking cutscene dialogue is still open;
        // close only screens owned by cutscene commands so the finished event cannot leave one
        // behind, while preserving unrelated menus that may have replaced it.
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.stardew.craft.client.gui.common.StardewNpcDialogueScreen
                || mc.screen instanceof com.stardew.craft.client.gui.common.StardewObjectDialogueScreen
                || mc.screen instanceof com.stardew.craft.client.gui.common.StardewConfirmDialogScreen) {
            mc.setScreen(null);
        }
        mc.options.hideGui = previousHideGui;

        // Remove all actors
        for (Mob actor : actors.values()) {
            actor.discard();
        }
        actors.clear();

        // Restore hidden NPCs
        for (String npcId : hiddenNpcs) {
            ClientNpcVisibilityState.show(npcId);
        }
        hiddenNpcs.clear();

        // Release camera
        EventCameraController.release();

        // Clear fade
        EventScreenFade.clear();
        com.stardew.craft.cutscene.command.TemporaryBlockCommand.restoreAll();
        com.stardew.craft.cutscene.command.GroundItemCommand.clearAll();
        com.stardew.craft.client.render.CutsceneTextAboveHeadRenderer.clear();

        StardewMusicManager.releaseCutsceneOverride();

        // Mark as seen on server if the client is still connected.
        // Disconnect-time aborts can reach here after Minecraft has already cleared
        // its connection; PacketDistributor.sendToServer requires a live connection.
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new MarkEventSeenPayload(eventId, sessionId));
        }

        // Clean up
        currentEvent = null;
        commands = List.of();
        commandTokens = List.of();
        commandIndex = -1;
        sessionId = 0L;
        running = false;
        skippable = false;
        startDimension = null;
        realPlayerMovedByServer = false;
        playerSnapshot = null;
        CutsceneAnchorRegistry.clear();
    }

    // ─── state accessors used by commands ───

    public boolean isRunning() {
        return running;
    }

    public boolean isSkippable() {
        return running && skippable;
    }

    public EventData currentEvent() {
        return currentEvent;
    }

    public void sendServerAction(String action, String value) {
        if (!running || currentEvent == null || sessionId == 0L
                || commandIndex < 0 || commandIndex >= commandTokens.size()) {
            LOGGER.warn("Ignored cutscene server action '{}' without an authorized command", action);
            return;
        }
        PacketDistributor.sendToServer(new com.stardew.craft.cutscene.network.CutsceneServerActionPayload(
                currentEvent.id(), sessionId, commandTokens.get(commandIndex), action, value));
    }

    public void setSkippable(boolean skippable) {
        this.skippable = skippable;
    }

    public boolean isPlayerFrozen() {
        return playerFrozen;
    }

    public void setPlayerFrozen(boolean frozen) {
        this.playerFrozen = frozen;
        // Actual movement suppression is handled in ModClientEvents.onClientTick
    }

    public void markRealPlayerMovedByServer() {
        this.realPlayerMovedByServer = true;
    }

    private void capturePlayerSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) {
            playerSnapshot = null;
            return;
        }
        playerSnapshot = new PlayerSnapshot(
                localPlayer.getX(), localPlayer.getY(), localPlayer.getZ(),
                localPlayer.getYRot(), localPlayer.getXRot(), localPlayer.getYHeadRot());
    }

    private void restorePlayerSnapshotIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null || playerSnapshot == null || realPlayerMovedByServer) {
            clearLocalPlayerMotion(localPlayer);
            return;
        }
        if (startDimension != null && (mc.level == null || mc.level.dimension() != startDimension)) {
            clearLocalPlayerMotion(localPlayer);
            return;
        }

        localPlayer.setPos(playerSnapshot.x(), playerSnapshot.y(), playerSnapshot.z());
        localPlayer.setYRot(playerSnapshot.yRot());
        localPlayer.setXRot(playerSnapshot.xRot());
        localPlayer.setYHeadRot(playerSnapshot.yHeadRot());
        clearLocalPlayerMotion(localPlayer);
    }

    private static void clearLocalPlayerMotion(LocalPlayer localPlayer) {
        if (localPlayer == null) {
            return;
        }
        localPlayer.setDeltaMovement(Vec3.ZERO);
        localPlayer.fallDistance = 0.0F;
    }

    // ─── Actor management ───

    public void registerActor(String tag, Mob actor) {
        actors.put(tag, actor);
    }

    public Mob getActor(String tag) {
        return actors.get(tag);
    }

    public void removeActor(String tag) {
        Mob actor = actors.remove(tag);
        if (actor != null) {
            actor.discard();
        }
    }

    // ─── NPC visibility management ───

    public void trackHiddenNpc(String npcId) {
        hiddenNpcs.add(npcId);
    }

    /**
     * Reset all state (e.g. on disconnect).
     */
    public void reset() {
        if (running) {
            setPlayerFrozen(false);
            restorePlayerSnapshotIfNeeded();
            Minecraft.getInstance().options.hideGui = previousHideGui;
            for (Mob actor : actors.values()) {
                actor.discard();
            }
            actors.clear();
            for (String npcId : hiddenNpcs) {
                ClientNpcVisibilityState.show(npcId);
            }
            hiddenNpcs.clear();
            EventCameraController.release();
            EventScreenFade.clear();
            com.stardew.craft.cutscene.command.TemporaryBlockCommand.restoreAll();
            com.stardew.craft.cutscene.command.GroundItemCommand.clearAll();
            com.stardew.craft.client.render.CutsceneTextAboveHeadRenderer.clear();
            StardewMusicManager.releaseCutsceneOverride();
        }
        currentEvent = null;
        commands = List.of();
        commandTokens = List.of();
        commandIndex = -1;
        sessionId = 0L;
        running = false;
        skippable = false;
        playerFrozen = false;
        startDimension = null;
        realPlayerMovedByServer = false;
        playerSnapshot = null;
        CutsceneAnchorRegistry.clear();
    }
}
