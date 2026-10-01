package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * particle: spawns particles at a position over a duration.
 * JSON: {"cmd":"particle", "type":"heart", "x":100, "y":66, "z":200, "count":5, "ticks":20}
 *
 * Supported types: heart, happy_villager, smoke, flame, portal, note, cloud, explosion
 */
@OnlyIn(Dist.CLIENT)
public class ParticleCommand implements EventCommand {

    private final String particleType;
    private final double x, y, z;
    private final int count;
    private final int durationTicks;
    private final String anchor;
    private int elapsed = 0;

    public ParticleCommand(String particleType, double x, double y, double z, int count, int durationTicks) {
        this(particleType, x, y, z, count, durationTicks, null);
    }

    public ParticleCommand(String particleType, double x, double y, double z, int count, int durationTicks, String anchor) {
        this.particleType = particleType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.count = count;
        this.durationTicks = durationTicks;
        this.anchor = anchor;
    }

    @Override
    public void start(EventPlayer player) {
        elapsed = 0;
    }

    @SuppressWarnings("null")
    @Override
    public void tick(EventPlayer player) {
        elapsed++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ParticleOptions particle = resolveType(particleType);
        double ax = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetX(anchor);
        double ay = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetY(anchor);
        double az = com.stardew.craft.cutscene.runtime.CutsceneAnchorRegistry.offsetZ(anchor);
        for (int i = 0; i < count; i++) {
            double ox = (Math.random() - 0.5) * 1.5;
            double oy = Math.random() * 1.0;
            double oz = (Math.random() - 0.5) * 1.5;
            mc.level.addParticle(particle, x + ax + ox, y + ay + oy, z + az + oz, 0, 0.05, 0);
        }
    }

    @Override
    public boolean isComplete() {
        return elapsed >= durationTicks;
    }

    private static ParticleOptions resolveType(String type) {
        return switch (type.toLowerCase()) {
            case "heart" -> ParticleTypes.HEART;
            case "happy_villager", "happy" -> ParticleTypes.HAPPY_VILLAGER;
            case "smoke" -> ParticleTypes.SMOKE;
            case "large_smoke" -> ParticleTypes.LARGE_SMOKE;
            case "flame" -> ParticleTypes.FLAME;
            case "portal" -> ParticleTypes.PORTAL;
            case "note" -> ParticleTypes.NOTE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "explosion" -> ParticleTypes.EXPLOSION;
            case "snowflake" -> ParticleTypes.SNOWFLAKE;
            case "enchant" -> ParticleTypes.ENCHANT;
            case "witch" -> ParticleTypes.WITCH;
            default -> ParticleTypes.HAPPY_VILLAGER;
        };
    }
}
