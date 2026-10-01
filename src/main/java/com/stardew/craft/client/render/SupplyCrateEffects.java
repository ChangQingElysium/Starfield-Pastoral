package com.stardew.craft.client.render;

import com.mojang.math.Axis;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.SupplyCrateBlock;
import com.stardew.craft.blockentity.SupplyCrateBlockEntity;
import com.stardew.craft.client.model.SupplyCrateModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.ArrayList;
import java.util.List;

/** Native break notifications drive the seven board fragments; no entities or server ticking needed. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class SupplyCrateEffects {
    private record Burst(BlockState state, BlockPos pos, double tick, double waterline, int light) {}
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static ClientLevel lastLevel;
    // floor, front, back, left, right, lid_left, lid_right; pixels per second.
    private static final int[][] VELOCITIES = {{0, 10, 1}, {1, 16, -19}, {-2, 18, 17}, {-18, 19, -3},
            {19, 17, 2}, {-11, 29, -5}, {12, 26, 4}};

    private static void ensureLevel(ClientLevel level) {
        if (lastLevel != level) { BURSTS.clear(); lastLevel = level; }
    }

    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ensureLevel(null);
    }

    @EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
            event.registerBlock(new IClientBlockExtensions() {
                @Override public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine engine) {
                    if (target instanceof BlockHitResult hit && level.getBlockEntity(hit.getBlockPos()) instanceof SupplyCrateBlockEntity crate) {
                        var player = Minecraft.getInstance().player;
                        if (player != null && SupplyCrateBlock.isHeavyHitter(player.getMainHandItem())) crate.lastHitTick = level.getGameTime();
                    }
                    return true;
                }

                @Override public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
                    if (!(level instanceof ClientLevel client)) return true;
                    ensureLevel(client);
                    double tick = client.getGameTime() + com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
                    double waterline = client.getFluidState(pos.below()).getHeight(client, pos.below()) - 1;
                    if (BURSTS.size() >= 128) com.stardew.craft.port.PortJava.removeFirst(BURSTS);
                    BURSTS.add(new Burst(state, pos.immutable(), tick, waterline, LevelRenderer.getLightColor(client, pos)));
                    return true;
                }
            }, ModBlocks.SUPPLY_CRATE.get());
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        ensureLevel(mc.level);
        if (mc.level == null || BURSTS.isEmpty()) return;
        double now = mc.level.getGameTime() + event.getPartialTick();
        BURSTS.removeIf(b -> now - b.tick >= 13 || now < b.tick);
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        for (var burst : BURSTS) {
            if (burst.pos.distToCenterSqr(camera) > 64 * 64) continue;
            float seconds = (float) ((now - burst.tick) / 20);
            float scale = seconds < .36F ? 1 : Math.max(0, 1 - (seconds - .36F) / .29F);
            int variant = burst.state.getValue(SupplyCrateBlock.VARIANT);
            pose.pushPose();
            pose.translate(burst.pos.getX() - camera.x, burst.pos.getY() - camera.y, burst.pos.getZ() - camera.z);
            SupplyCrateBlockEntityRenderer.floatingPose(pose, burst.pos, burst.tick, burst.waterline);
            for (int part = 0; part < 7; part++) {
                var pivot = SupplyCrateModels.center(variant, part);
                var velocity = VELOCITIES[part];
                pose.pushPose();
                pose.translate(pivot.x + velocity[0] * seconds / 16,
                        pivot.y + (velocity[1] * seconds - 38 * seconds * seconds) / 16,
                        pivot.z + velocity[2] * seconds / 16);
                pose.mulPose(Axis.XP.rotation(velocity[2] * seconds * .1F));
                pose.mulPose(Axis.YP.rotation(velocity[0] * seconds * .07F));
                pose.mulPose(Axis.ZP.rotation(-velocity[0] * seconds * .1F));
                pose.scale(scale, scale, scale);
                pose.translate(-pivot.x, -pivot.y, -pivot.z);
                SupplyCrateBlockEntityRenderer.draw(burst.state, mc.getModelManager().getModel(SupplyCrateModels.id(variant, part)),
                        pose, buffers, burst.light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
        buffers.endBatch(net.minecraftforge.client.RenderTypeHelper.getEntityRenderType(net.minecraft.client.renderer.RenderType.cutout(), false));
    }
}
