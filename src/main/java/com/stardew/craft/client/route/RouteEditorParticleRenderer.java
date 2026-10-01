package com.stardew.craft.client.route;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class RouteEditorParticleRenderer {
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.72F, 0.16F), 0.9F);
    private static int tick;

    private RouteEditorParticleRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.ROUTE_EDITOR_WAND.get())
            && !player.getOffhandItem().is(ModItems.ROUTE_EDITOR_WAND.get())) {
            return;
        }
        if (++tick % 5 != 0) {
            return;
        }

        List<BlockPos> points = RouteEditorClientState.points();
        if (points.isEmpty()) {
            return;
        }

        for (BlockPos point : points) {
            level.addParticle(ParticleTypes.END_ROD, point.getX() + 0.5D, point.getY() + 0.2D, point.getZ() + 0.5D, 0.0D, 0.015D, 0.0D);
        }
        for (int i = 0; i + 1 < points.size(); i++) {
            spawnSegment(level, points.get(i), points.get(i + 1));
        }
    }

    private static void spawnSegment(ClientLevel level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from).add(0.0D, -0.3D, 0.0D);
        Vec3 b = Vec3.atCenterOf(to).add(0.0D, -0.3D, 0.0D);
        double distance = a.distanceTo(b);
        int steps = Math.max(1, Math.min(64, (int) Math.ceil(distance / 0.75D)));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.z + (b.z - a.z) * t;
            level.addParticle(GOLD_DUST, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
