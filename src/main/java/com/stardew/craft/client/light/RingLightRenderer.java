package com.stardew.craft.client.light;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerGlowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Visual-only light samples. Immutable snapshots are safe for chunk compilation workers. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class RingLightRenderer {
    private static final long REFRESH_NANOS = 33_333_333L;
    private static volatile Map<Integer, Source> sources = Map.of();
    private static ClientLevel lastLevel;
    private static long lastRefresh;

    private RingLightRenderer() {}

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (lastLevel != minecraft.level) {
            sources = Map.of();
            lastLevel = minecraft.level;
            lastRefresh = 0;
        }
        if (minecraft.level == null) return;
        long now = System.nanoTime();
        if (now - lastRefresh < REFRESH_NANOS) return;
        lastRefresh = now;

        Map<Integer, Source> next = new HashMap<>();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var player : minecraft.level.players()) {
            int strength = ((PlayerGlowState) player).stardewcraft$getRingLight();
            if (strength <= 0 || !player.isAlive() || player.isSpectator()) continue;
            Vec3 position = player.getPosition(partialTick).add(0, player.getBbHeight() * 0.6, 0);
            Source previous = sources.get(player.getId());
            Source source = new Source(position, strength);
            // Accumulate sub-threshold movement against the last published position.
            if (previous != null && previous.strength == strength
                    && previous.position.distanceToSqr(position) < 0.0025) source = previous;
            next.put(player.getId(), source);
        }
        if (sources.equals(next)) return;

        Set<Long> dirtySections = new HashSet<>();
        for (var entry : sources.entrySet()) {
            if (!entry.getValue().equals(next.get(entry.getKey()))) addSections(dirtySections, entry.getValue());
        }
        for (var entry : next.entrySet()) {
            if (!entry.getValue().equals(sources.get(entry.getKey()))) addSections(dirtySections, entry.getValue());
        }
        sources = Map.copyOf(next);
        // This only invalidates render meshes. No block state/light-engine/neighbor updates.
        for (long section : dirtySections) {
            int y = SectionPos.y(section);
            if (y >= minecraft.level.getMinSection() && y < minecraft.level.getMaxSection()) {
                minecraft.levelRenderer.setSectionDirty(SectionPos.x(section), y, SectionPos.z(section));
            }
        }
    }

    private static void addSections(Set<Long> sections, Source source) {
        // Include the adjacent samples used by ambient occlusion at section edges.
        double radius = source.strength + 1.0;
        int minX = SectionPos.blockToSectionCoord(Mth.floor(source.position.x - radius));
        int minY = SectionPos.blockToSectionCoord(Mth.floor(source.position.y - radius));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(source.position.z - radius));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(source.position.x + radius));
        int maxY = SectionPos.blockToSectionCoord(Mth.floor(source.position.y + radius));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(source.position.z + radius));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) sections.add(SectionPos.asLong(x, y, z));
            }
        }
    }

    /** Preserve sky light and stronger block light, including another dynamic-light mod. */
    public static int lightColor(double x, double y, double z, int packedLight) {
        int blockLight = packedLight & 0xFFFF;
        for (Source source : sources.values()) {
            double dx = x - source.position.x;
            double dy = y - source.position.y;
            double dz = z - source.position.z;
            double squaredDistance = dx * dx + dy * dy + dz * dz;
            if (squaredDistance >= source.strength * source.strength) continue;
            int light = (int) ((source.strength - Math.sqrt(squaredDistance)) * 16.0);
            blockLight = Math.max(blockLight, light);
        }
        return (packedLight & 0xFFFF0000) | blockLight;
    }

    private record Source(Vec3 position, int strength) {}
}
