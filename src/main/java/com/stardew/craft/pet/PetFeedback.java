package com.stardew.craft.pet;

import com.stardew.craft.api.v1.pet.StardewPetFeedback;
import com.stardew.craft.floor.SurfaceFloorData;
import com.stardew.craft.network.payload.EmoteBroadcastPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** One entity owns its sound timeline, so loop, transition and unload cannot leave orphaned timers. */
final class PetFeedback {
    private final PetEntity entity;
    private StardewPetFeedback.Track track;
    private String stateName = "";
    private int started, emoteUntil;
    private double previous = -1;
    PetFeedback(PetEntity entity) { this.entity = entity; }

    void begin(String state) {
        stateName = state;
        track = entity.variant().species().feedback().states().get(state);
        started = entity.tickCount; previous = -1;
        if (track != null && track.start() != null) cue(track.start());
        else {
            var behavior = PetBehaviors.get(entity.variant()).states().get(state);
            if (behavior != null && !behavior.sound().isEmpty()) cue(new StardewPetFeedback.Cue(behavior.sound(), true, -1, -1));
        }
    }
    void stop() { track = null; stateName = ""; }
    void restartWalk() { if (stateName.equals("Walk")) { started = entity.tickCount; previous = -1; } }
    void content() {
        voice(entity.variant().species().contentSound(), true, 2, -1, 0);
        int delay = entity.variant().species().feedback().repeatContentTicks();
        if (delay >= 0) voice(entity.variant().species().contentSound(), true, 2, -1, Math.max(1, delay));
    }
    void tick() {
        if (track != null && (!stateName.equals("Walk") || entity.clip().equals("walk") && entity.getDeltaMovement().horizontalDistanceSqr() > 1e-8)) {
            double now = (entity.tickCount - started) / 20.;
            for (var frame : track.frames()) {
                int first = track.loop() ? Math.max(0, (int) Math.floor((previous + 1e-8 - frame.time()) / track.length()) + 1) : 0;
                double time = first * track.length() + frame.time();
                if (time > previous + 1e-8 && time <= now + 1e-8) { if (frame.cue() != null) cue(frame.cue()); if (frame.terrain()) terrain(); }
            }
            previous = now;
        }
        var pet = PetWorldData.get(((ServerLevel) entity.level()).getServer()).find(entity.getUUID());
        if (pet == null || entity.tickCount < emoteUntil) return;
        if (pet.bowlSadDay >= 0 && !entity.clip().startsWith("sleep") && entity.level().getNearestPlayer(entity, 16) != null) {
            emote(28); pet.bowlSadDay = -1; PetWorldData.get(((ServerLevel) entity.level()).getServer()).setDirty();
        } else if (entity.clip().equals("sleep") && entity.getRandom().nextDouble() < 1 - Math.pow(1 - .002, 3)) emote(24);
    }
    void emote(int base) {
        emoteUntil = entity.tickCount + 28;
        PacketDistributor.sendToPlayersTrackingEntity(entity, new EmoteBroadcastPayload(entity.getId(), base));
    }
    private void cue(StardewPetFeedback.Cue cue) {
        ResourceLocation sound;
        if (cue.sound().equals("BARK")) {
            sound = entity.variant().breed().barkOverride();
            if (sound == null) sound = entity.variant().species().ambientSound();
        } else sound = cue.sound().equals("content") ? entity.variant().species().contentSound() : ResourceLocation.tryParse(cue.sound());
        voice(sound, cue.voice(), cue.rangeFromBorder(), cue.range(), 0);
    }
    private void voice(ResourceLocation id, boolean voice, int border, int range, int delay) {
        if (id == null || entity.isSilent()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(id).ifPresent(sound -> play(sound, voice, border, range, delay));
    }
    private void play(SoundEvent sound, boolean voice, int border, int range, int delay) {
        var species = entity.variant().species();
        float pitch = voice ? species.voicePitch() * entity.variant().breed().voicePitch() : 1;
        var packet = new PetSoundPayload(sound.getLocation(), entity.position(), voice,
                voice ? species.voiceVolume() : .55f, pitch, border, range, delay, stateName.equals("Walk") && sound == ModSounds.COWBOY_FOOTSTEP.get());
        for (var player : ((ServerLevel) entity.level()).players()) {
            boolean nearby = range > 0 ? Math.abs(player.getX() - entity.getX()) <= range && Math.abs(player.getZ() - entity.getZ()) <= range
                    && Math.abs(player.getY() - entity.getY()) <= range : player.distanceToSqr(entity) <= 16 * 16;
            if (nearby) PacketDistributor.sendToPlayer(player, packet);
        }
    }
    private void terrain() {
        var pos = entity.blockPosition().below();
        var cover = SurfaceFloorData.get((ServerLevel) entity.level()).at(pos);
        SoundEvent sound = entity.level().getBlockState(pos).getSoundType(entity.level(), pos, entity).getStepSound();
        if (cover != null) sound = switch (cover.type()) {
            case WOOD, RUSTIC_PLANK, WEATHERED, WOOD_PATH -> ModSounds.WOODY_STEP.get();
            case STRAW -> ModSounds.GRASSY_STEP.get();
            default -> ModSounds.STONE_STEP.get();
        };
        play(sound, false, 2, -1, 0);
    }
}
