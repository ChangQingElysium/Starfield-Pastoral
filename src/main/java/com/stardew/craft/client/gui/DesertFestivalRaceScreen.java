package com.stardew.craft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.DesertFestivalRaceSnapshot;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public class DesertFestivalRaceScreen extends Screen implements DesertFestivalRaceSnapshotScreen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/race_track_full.png");
    private static final ResourceLocation RACERS = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/desert_racers.png");
    private static final ResourceLocation RACERS_LEFT = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/desert_racers_left.png");
    private static final ResourceLocation SHADOW = new ResourceLocation(
        StardewCraft.MODID, "textures/gui/desert_festival/race/shadow.png");
    private static final int BG_W = 672;
    private static final int BG_H = 384;
    private static final int SHADOW_W = 12;
    private static final int SHADOW_H = 7;
    private static final int CROP_X = 64;
    private static final int CROP_Y = 416;
    private static final long SNAPSHOT_BLEND_NANOS = 220_000_000L;

    private DesertFestivalRaceSnapshot snapshot;
    private final Map<Integer, RenderRacer> renderRacers = new HashMap<>();
    private String lastAnnounceKey = "";
    private long lastFootstepNanos;
    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;
    private float mapScale;

    public DesertFestivalRaceScreen(DesertFestivalRaceSnapshot snapshot) {
        super(Component.translatable("stardewcraft.desert_festival.race.title"));
        this.snapshot = snapshot;
        this.lastAnnounceKey = snapshot.announceKey();
        long now = System.nanoTime();
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            renderRacers.put(racer.racerIndex(), RenderRacer.initial(racer, now));
        }
    }

    public void updateSnapshot(DesertFestivalRaceSnapshot snapshot) {
        long now = System.nanoTime();
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            RenderRacer current = renderRacers.get(racer.racerIndex());
            if (current == null || shouldSnap(current.targetState, racer)) {
                renderRacers.put(racer.racerIndex(), RenderRacer.initial(racer, now));
            } else {
                renderRacers.put(racer.racerIndex(), current.next(racer, now));
            }
        }
        if (!snapshot.announceKey().equals(lastAnnounceKey)) {
            playAnnouncementSound(snapshot.announceKey());
            lastAnnounceKey = snapshot.announceKey();
        }
        renderRacers.keySet().removeIf(racerIndex -> snapshot.racers().stream().noneMatch(racer -> racer.racerIndex() == racerIndex));
        this.snapshot = snapshot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE6000000);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        layout();
        graphics.blit(BACKGROUND, mapX, mapY, mapW, mapH, 0.0f, 0.0f, BG_W, BG_H, BG_W, BG_H);
        renderRacerShadows(graphics);
        renderRacerSprites(graphics);
        playFootsteps();
        renderOverlayText(graphics);
    }

    private void layout() {
        int maxMapW = Math.max(240, width - 16);
        int maxMapH = Math.max(180, height - 16);
        mapScale = Math.min((float) maxMapW / BG_W, (float) maxMapH / BG_H);
        mapW = Math.max(1, Math.round(BG_W * mapScale));
        mapH = Math.max(1, Math.round(BG_H * mapScale));
        mapX = width / 2 - mapW / 2;
        mapY = height / 2 - mapH / 2;
    }

    private List<DesertFestivalRaceSnapshot.RacerEntry> sortedRacers(long now) {
        return snapshot.racers().stream()
            .sorted(Comparator.comparingDouble(racer -> renderRacers.getOrDefault(racer.racerIndex(), RenderRacer.initial(racer, now)).y(now)))
            .toList();
    }

    private void renderRacerShadows(GuiGraphics graphics) {
        long now = System.nanoTime();
        List<DesertFestivalRaceSnapshot.RacerEntry> racers = sortedRacers(now);
        for (DesertFestivalRaceSnapshot.RacerEntry racer : racers) {
            renderRacerShadow(graphics, renderRacers.getOrDefault(racer.racerIndex(), RenderRacer.initial(racer, now)), now);
        }
    }

    private void renderRacerSprites(GuiGraphics graphics) {
        long now = System.nanoTime();
        List<DesertFestivalRaceSnapshot.RacerEntry> racers = sortedRacers(now);
        for (DesertFestivalRaceSnapshot.RacerEntry racer : racers) {
            renderRacerSprite(graphics, racer, renderRacers.getOrDefault(racer.racerIndex(), RenderRacer.initial(racer, now)), now);
        }
    }

    private void renderRacerShadow(GuiGraphics graphics, RenderRacer renderRacer, long now) {
        float visualHeight = renderRacer.height(now);
        float heightFade = Math.max(0.0f, Math.min(1.0f, 1.0f - visualHeight / 12.0f));
        if (heightFade <= 0.0f) return;
        float centerX = mapX + (renderRacer.x(now) / 4.0f - CROP_X) * mapScale;
        float centerY = mapY + (renderRacer.y(now) / 4.0f - CROP_Y) * mapScale;
        float shadowScale = mapScale;
        int shadowW = Math.max(1, Math.round(SHADOW_W * shadowScale));
        int shadowH = Math.max(1, Math.round(SHADOW_H * shadowScale));
        int shadowX = Math.round(centerX - shadowW / 2.0f);
        int shadowY = Math.round(centerY - shadowH / 2.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.75f * heightFade);
        graphics.blit(SHADOW, shadowX, shadowY, shadowW, shadowH, 0.0f, 0.0f, SHADOW_W, SHADOW_H, SHADOW_W, SHADOW_H);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderRacerSprite(GuiGraphics graphics, DesertFestivalRaceSnapshot.RacerEntry racer, RenderRacer renderRacer, long now) {
        int sx = switch (racer.direction()) {
            case 0 -> 0;
            case 2 -> 64;
            case 1, 3 -> 32;
            default -> 32;
        };
        if (racer.frame()) {
            sx += 16;
        }
        if (racer.tripping()) {
            sx = 96;
        }
        int sy = racer.racerIndex() * 16;
        float visualX = renderRacer.x(now);
        float visualY = renderRacer.y(now);
        float visualHeight = renderRacer.height(now);
        float sourceX = visualX / 4.0f - CROP_X;
        float sourceY = visualY / 4.0f - CROP_Y - visualHeight / 4.0f;
        int drawX = Math.round(mapX + sourceX * mapScale - 8.0f * mapScale);
        int drawY = Math.round(mapY + sourceY * mapScale - 14.0f * mapScale);
        int size = Math.max(8, Math.round(16.0f * mapScale));
        ResourceLocation texture = racer.direction() == 3 ? RACERS_LEFT : RACERS;
        graphics.blit(texture, drawX, drawY, size, size, sx, sy, 16, 16, 112, 80);
    }

    private boolean shouldSnap(DesertFestivalRaceSnapshot.RacerEntry previous, DesertFestivalRaceSnapshot.RacerEntry next) {
        if (previous.racerIndex() != next.racerIndex() || next.progress() + 0.05f < previous.progress()) {
            return true;
        }
        float dx = next.x() - previous.x();
        float dy = next.y() - previous.y();
        return dx * dx + dy * dy > 64f * 64f * 16f;
    }

    private void renderOverlayText(GuiGraphics graphics) {
        String key = snapshot.announceKey();
        if (!key.isBlank()) {
            Component text = key.equals("RESULT")
                ? Component.translatable(resultKey())
                : Component.translatable("stardewcraft.desert_festival.race.announce." + key);
            int wobble = shouldWobble(key) ? (int) ((System.nanoTime() / 60_000_000L) % 3L) - 1 : 0;
            DesertFestivalRaceUi.banner(graphics, font, text, width / 2 + wobble, mapY + 18);
        }
    }

    private boolean shouldWobble(String key) {
        return key.equals("Race_Go") || key.equals("Race_Finish") || key.startsWith("Racer_");
    }

    private void playAnnouncementSound(String key) {
        if (key.isBlank()) return;
        switch (key) {
            case "Race_Begin" -> DesertFestivalRaceUi.play(ModSounds.BIG_SELECT);
            case "Race_Ready", "Race_Set" -> DesertFestivalRaceUi.play(ModSounds.BUTTON_TAP);
            case "Race_Go" -> DesertFestivalRaceUi.play(ModSounds.WHISTLE);
            case "Race_Finish" -> DesertFestivalRaceUi.play(ModSounds.MACHINE_BELL);
            case "Race_Winner" -> DesertFestivalRaceUi.play(ModSounds.JINGLE1);
            case "RESULT" -> DesertFestivalRaceUi.play(snapshot.activeGuess() >= 0 && snapshot.activeGuess() == snapshot.lastWinner()
                ? ModSounds.REWARD : ModSounds.BIG_DESELECT);
            default -> {
                if (key.startsWith("Racer_")) {
                    DesertFestivalRaceUi.play(ModSounds.COIN);
                }
            }
        }
    }

    private void playFootsteps() {
        if (!snapshot.raceState().equals("GO")) return;
        long now = System.nanoTime();
        if (now - lastFootstepNanos < 340_000_000L) return;
        lastFootstepNanos = now;
        DesertFestivalRaceUi.play(ModSounds.WOODY_STEP);
        for (DesertFestivalRaceSnapshot.RacerEntry racer : snapshot.racers()) {
            if (racer.tripping()) {
                DesertFestivalRaceUi.play(ModSounds.THUD_STEP);
                break;
            }
        }
    }

    private String resultKey() {
        if (snapshot.activeGuess() >= 0 && snapshot.activeGuess() == snapshot.lastWinner()) {
            return "stardewcraft.desert_festival.race.announce.Race_Win";
        }
        if (snapshot.activeGuess() >= 0) {
            return "stardewcraft.desert_festival.race.announce.Race_Lose";
        }
        return "stardewcraft.desert_festival.race.announce.RESULT";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record RenderRacer(DesertFestivalRaceSnapshot.RacerEntry startState,
                               DesertFestivalRaceSnapshot.RacerEntry targetState,
                               long startTime) {
        static RenderRacer initial(DesertFestivalRaceSnapshot.RacerEntry state, long now) {
            return new RenderRacer(state, state, now);
        }

        RenderRacer next(DesertFestivalRaceSnapshot.RacerEntry nextState, long now) {
            DesertFestivalRaceSnapshot.RacerEntry visualState = new DesertFestivalRaceSnapshot.RacerEntry(
                targetState.racerIndex(), x(now), y(now), targetState.direction(), targetState.frame(),
                targetState.jumping(), targetState.tripping(), targetState.drawAboveMap(), height(now),
                targetState.progress(), targetState.sabotages());
            return new RenderRacer(visualState, nextState, now);
        }

        float x(long now) {
            return lerp(startState.x(), targetState.x(), blend(now));
        }

        float y(long now) {
            return lerp(startState.y(), targetState.y(), blend(now));
        }

        float height(long now) {
            return lerp(startState.height(), targetState.height(), blend(now));
        }

        private float blend(long now) {
            return Math.min(1.0f, Math.max(0.0f, (float) (now - startTime) / SNAPSHOT_BLEND_NANOS));
        }

        private static float lerp(float start, float end, float amount) {
            return start + (end - start) * amount;
        }
    }

}
