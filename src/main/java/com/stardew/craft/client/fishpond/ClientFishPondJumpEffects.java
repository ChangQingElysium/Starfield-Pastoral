package com.stardew.craft.client.fishpond;

import com.stardew.craft.network.payload.FishPondJumpSyncPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ClientFishPondJumpEffects {
    private static ClientLevel activeLevel;
    private static final List<JumpingFishEffect> ACTIVE = new ArrayList<>();
    private static final float JUMP_TIME_SECONDS = 1.0F;
    private static final int JUMP_LIFETIME_TICKS = Mth.ceil(JUMP_TIME_SECONDS * 20.0F);

    private ClientFishPondJumpEffects() {
    }

    public static void spawn(FishPondJumpSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !payload.dimensionId().equals(level.dimension().location().toString())) {
            return;
        }

        if(activeLevel!=level){ACTIVE.clear();activeLevel=level;}
        ResourceLocation fishId = ResourceLocation.tryParse(payload.fishItemId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return;
        }

        ItemStack fishStack = new ItemStack(BuiltInRegistries.ITEM.get(fishId));
        if (fishStack.isEmpty() || !ClientFishPondFishRenderer.available(fishStack) || ClientFishPondFishRenderer.bottomDweller(fishStack)) {
            return;
        }

        ClientFishPondSwimVisuals.JumpFishBinding binding = ClientFishPondSwimVisuals.reserveForJump(
            payload.dimensionId(),
            fishStack,
            new Vec3(payload.startX(), payload.startY(), payload.startZ()),
            payload.delayTicks() + JUMP_LIFETIME_TICKS
        );
        Vec3 startPosition = binding != null
            ? binding.startPosition()
            : new Vec3(payload.startX(), payload.startY(), payload.startZ());
        float baseYawDegrees = binding != null
            ? binding.yawDegrees()
            : computeTravelYaw(startPosition, new Vec3(payload.endX(), payload.endY(), payload.endZ()));
        float basePitchDegrees = binding != null ? binding.pitchDegrees() : 0.0F;
        float renderScale = binding != null ? binding.scale() : 0.9F;

        JumpingFishEffect effect = new JumpingFishEffect(
            fishStack,
            startPosition,
            new Vec3(payload.endX(), payload.endY(), payload.endZ()),
            payload.jumpHeight(),
            payload.angularVelocity(),
            payload.delayTicks(),
            payload.flipped(),
            baseYawDegrees,
            basePitchDegrees,
            renderScale
        );
        ACTIVE.add(effect);
        if (effect.delayTicks == 0) {
            effect.started = true;
            spawnSplash(level, effect.startPosition);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level!=activeLevel) {
            ACTIVE.clear();
            activeLevel=level;
            return;
        }

        if(minecraft.isPaused())return;
        Iterator<JumpingFishEffect> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            JumpingFishEffect effect = iterator.next();
            if (!effect.started && effect.ageTicks >= 0) {
                effect.started = true;
                spawnSplash(level, effect.startPosition);
            }
            effect.ageTicks++;
            if (effect.ageTicks < 0) {
                continue;
            }
            if (effect.ageTicks < effect.lifetimeTicks) {
                continue;
            }

            spawnSplash(level, effect.endPosition);
            iterator.remove();
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || ACTIVE.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        for (JumpingFishEffect effect : ACTIVE) {
            if (effect.ageTicks < 0) {
                continue;
            }
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float ageSeconds = (effect.ageTicks + partialTick) / 20.0F;
            float progress = Mth.clamp(ageSeconds / JUMP_TIME_SECONDS, 0.0F, 1.0F);
            Vec3 linearPosition = effect.startPosition.lerp(effect.endPosition, progress);
            double arcY = Math.sin(progress * Math.PI) * effect.jumpHeight;
            Vec3 renderPos = linearPosition.add(0.0D, arcY, 0.0D);
            double deltaY = Math.PI * effect.jumpHeight * Math.cos(progress * Math.PI);
            Vec3 travelVector = effect.endPosition.subtract(effect.startPosition);
            double horizontalDistance = Math.max(0.001D, Math.sqrt(travelVector.x * travelVector.x + travelVector.z * travelVector.z));
            float flightPitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

            event.getPoseStack().pushPose();
            event.getPoseStack().translate(
                renderPos.x - cameraPos.x,
                renderPos.y - cameraPos.y,
                renderPos.z - cameraPos.z
            );
            ClientFishPondFishRenderer.renderFish(
                effect.fishStack,
                event.getPoseStack(),
                buffer,
                level,
                net.minecraft.client.renderer.LevelRenderer.getLightColor(level, net.minecraft.core.BlockPos.containing(renderPos)),
                computeTravelYaw(effect.startPosition,effect.endPosition),
                flightPitch,
                (float) Math.toDegrees(effect.flipped ? -effect.angularVelocity * ageSeconds : effect.angularVelocity * ageSeconds),
                effect.renderScale
            );
            event.getPoseStack().popPose();
        }

        buffer.endBatch();
    }

    private static void spawnSplash(ClientLevel level, Vec3 position) {
        level.playLocalSound(
            position.x,
            position.y,
            position.z,
            ModSounds.DROP_ITEM_IN_WATER.get(),
            SoundSource.BLOCKS,
            0.45F,
            0.95F + level.random.nextFloat() * 0.1F,
            false
        );
        for (int i = 0; i < 8; i++) {
            double speedX = (level.random.nextDouble() - 0.5D) * 0.12D;
            double speedY = 0.03D + level.random.nextDouble() * 0.05D;
            double speedZ = (level.random.nextDouble() - 0.5D) * 0.12D;
            level.addParticle(
                ParticleTypes.SPLASH,
                position.x + (level.random.nextDouble() - 0.5D) * 0.4D,
                position.y,
                position.z + (level.random.nextDouble() - 0.5D) * 0.4D,
                speedX,
                speedY,
                speedZ
            );
        }
    }

    private static float computeTravelYaw(Vec3 startPosition, Vec3 endPosition) {
        Vec3 delta = endPosition.subtract(startPosition);
        return (float) Math.toDegrees(Math.atan2(delta.z, delta.x));
    }

    private static final class JumpingFishEffect {
        private final ItemStack fishStack;
        private final Vec3 startPosition;
        private final Vec3 endPosition;
        private final float jumpHeight;
        private final float angularVelocity;
        private final int delayTicks;
        private final boolean flipped;
        private final float baseYawDegrees;
        private final float basePitchDegrees;
        private final float renderScale;
        private final int lifetimeTicks;
        private int ageTicks;
        private boolean started;

        private JumpingFishEffect(ItemStack fishStack,
                                  Vec3 startPosition,
                                  Vec3 endPosition,
                                  float jumpHeight,
                                  float angularVelocity,
                                  int delayTicks,
                                  boolean flipped,
                                  float baseYawDegrees,
                                  float basePitchDegrees,
                                  float renderScale) {
            this.fishStack = fishStack.copy();
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.jumpHeight = jumpHeight;
            this.angularVelocity = angularVelocity;
            this.delayTicks = Math.max(0, delayTicks);
            this.flipped = flipped;
            this.baseYawDegrees = baseYawDegrees;
            this.basePitchDegrees = basePitchDegrees;
            this.renderScale = renderScale;
            this.lifetimeTicks = JUMP_LIFETIME_TICKS;
            this.ageTicks = -this.delayTicks;
        }
    }
}