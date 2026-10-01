package com.stardew.craft.client.route;

import com.stardew.craft.StardewCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class RouteGuidanceParticleRenderer {
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.18F), 1.15F);
    private static int tick;

    private RouteGuidanceParticleRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) {
            return;
        }
        if (++tick % 4 != 0) {
            return;
        }

        RouteGuidanceClientState.ActiveRoute active = RouteGuidanceClientState.active(level.getGameTime());
        if (active == null) {
            return;
        }

        double progress = RouteGuidanceClientState.progressDistance(player.position(), active.points());
        if (RouteGuidanceClientState.remainingDistance(player.position(), active.points()) <= 3.0D) {
            RouteGuidanceClientState.clear();
            return;
        }
        spawnRemainingRoute(level, active.points(), progress);
    }

    private static void spawnRemainingRoute(ClientLevel level, List<BlockPos> points, double progress) {
        double walked = 0.0D;
        int spawned = 0;
        for (int i = 0; i + 1 < points.size() && spawned < 120; i++) {
            Vec3 a = routePoint(points.get(i));
            Vec3 b = routePoint(points.get(i + 1));
            double segmentLength = a.distanceTo(b);
            double segmentStart = walked;
            double segmentEnd = walked + segmentLength;
            walked = segmentEnd;
            if (segmentEnd + 0.35D < progress) {
                continue;
            }

            double startT = segmentLength <= 0.0001D ? 0.0D : Math.max(0.0D, (progress - segmentStart) / segmentLength);
            int steps = Math.max(1, Math.min(32, (int) Math.ceil(segmentLength / 0.65D)));
            for (int step = 0; step <= steps && spawned < 120; step++) {
                double t = Math.max(startT, step / (double) steps);
                if (t > 1.0D) {
                    continue;
                }
                Vec3 p = a.lerp(b, t);
                level.addParticle(GOLD, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
                if (spawned % 12 == 0) {
                    level.addParticle(ParticleTypes.END_ROD, p.x, p.y + 0.04D, p.z, 0.0D, 0.01D, 0.0D);
                }
                spawned++;
            }
        }
    }

    private static Vec3 routePoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }
}
