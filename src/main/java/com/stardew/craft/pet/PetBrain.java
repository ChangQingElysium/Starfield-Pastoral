package com.stardew.craft.pet;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Rare bounded path searches, source-timed behavior decisions, and smooth authored posture transitions. */
final class PetBrain {
    private record Part(String clip, int ticks) {}
    private final PetEntity entity;
    private final ArrayDeque<Part> parts = new ArrayDeque<>();
    private String stateName = "Walk", queuedState;
    private com.stardew.craft.api.v1.pet.StardewPetBehavior.State state;
    private int until, nextPath;
    private boolean sleeping, nap;

    PetBrain(PetEntity entity) { this.entity = entity; }
    void tick() {
        var level = (ServerLevel) entity.level(); var data = PetWorldData.get(level.getServer()); var pet = data.find(entity.getUUID());
        if (pet == null || !pet.variant.available()) { entity.discard(); return; }
        if (entity.tickCount % 20 == 0) PetService.remember(level, entity);
        if (entity.tickCount % 40 == 0) PetService.environment(level, pet, entity);
        boolean night = com.stardew.craft.time.StardewTimeManager.get().getCurrentTime() >= 1200;
        if (nap && night) nap = false;
        // Original: a daytime nap ends with 0.001 chance per 60 Hz frame (3 frames per tick).
        if (nap && until == 0 && parts.isEmpty() && entity.getRandom().nextDouble() < 0.003) { nap = false; setSleeping(false, pet); }
        else if (!nap && night != sleeping) setSleeping(night, pet);
        if (until > 0) {
            if (entity.tickCount < until && (!parts.isEmpty() || queuedState != null)) return;
            if (entity.tickCount >= until) {
                if (!parts.isEmpty()) { advance(); return; }
                until = 0;
                if (queuedState != null) { String next = queuedState; queuedState = null; enter(next); return; }
                if (state != null) { transition(!state.timeout().isEmpty() ? state.timeout() : state.end(), pet.indoors); return; }
            }
        }
        if (sleeping || state == null) { if (state == null && !sleeping) enter("Walk"); return; }
        if (entity.tickCount % 2 == 0) {
            if (!state.nearby().isEmpty() && level.getNearestPlayer(entity, 2) != null) { transition(state.nearby(), pet.indoors); return; }
            // Original chance is per 60 Hz frame. Two Minecraft ticks contain six such frames.
            if (state.chance() > 0 && entity.getRandom().nextDouble() < 1 - Math.pow(1 - state.chance(), 6)) { transition(state.random(), pet.indoors); return; }
        }
        if (state.move() && !state.clip().equals("pounce") && entity.getNavigation().isDone() && !entity.clip().equals("idle")) entity.play("idle");
        if (state.move() && !state.clip().equals("pounce") && entity.tickCount >= nextPath && entity.getNavigation().isDone()) {
            nextPath = entity.tickCount + 40;
            var farm = PetService.farm(pet.farm);
            if (farm == null) return;
            BlockPos target = entity.blockPosition().offset(entity.getRandom().nextInt(13) - 6, 0, entity.getRandom().nextInt(13) - 6);
            var safe = PetHomes.near(level, farm, target, pet.variant, 2);
            if (safe != null && (!pet.indoors || !level.canSeeSky(safe))) {
                var path = entity.getNavigation().createPath(safe, 0);
                if (path != null && path.canReach()) { entity.getNavigation().moveTo(path, 1); if (!entity.clip().equals(state.clip())) entity.play(state.clip()); }
            }
            if (entity.getNavigation().isDone() && !entity.clip().equals("idle")) entity.play("idle");
        }
    }
    void napNow() {
        var pet = PetWorldData.get(entity.level().getServer()).find(entity.getUUID());
        if (pet == null || sleeping) return;
        nap = true; setSleeping(true, pet);
    }
    private void setSleeping(boolean night, PetRecord pet) {
        entity.feedback.stop();
        sleeping = night; parts.clear(); queuedState = null; entity.getNavigation().stop();
        String exit = postureExit();
        if (night && exit != null) parts.add(new Part(exit, PetBehaviors.ticks(pet.variant, exit)));
        parts.add(new Part(night ? "sleep_enter" : "sleep_exit", PetBehaviors.ticks(pet.variant, night ? "sleep_enter" : "sleep_exit")));
        queuedState = night ? "Sleep" : "Walk"; advance();
    }
    private void transition(java.util.List<com.stardew.craft.api.v1.pet.StardewPetBehavior.Choice> choices, boolean indoors) {
        String next = PetBehaviors.choose(choices, indoors, entity.getRandom());
        if (next == null) return;
        entity.feedback.stop();
        if (!state.exit().isEmpty()) { queuedState = next; parts.add(new Part(state.exit(), PetBehaviors.ticks(entity.variant(), state.exit()))); entity.getNavigation().stop(); advance(); }
        else enter(next);
    }
    private void enter(String name) {
        stateName = name; until = 0; parts.clear(); entity.feedback.stop();
        if (name.equals("Sleep")) { state = null; entity.play("sleep"); return; }
        state = PetBehaviors.get(entity.variant()).states().get(name);
        if (state == null) throw new IllegalArgumentException("Unknown pet behavior " + name);
        if (!state.move() || state.clip().equals("pounce")) entity.getNavigation().stop();
        double duration = state.duration();
        if (state.minDuration() >= 0) duration = state.minDuration() + entity.getRandom().nextDouble() * (state.maxDuration() - state.minDuration());
        if (state.minLoops() >= 0) duration = PetBehaviors.get(entity.variant()).clips().get(state.clip()) * (state.minLoops() + entity.getRandom().nextInt(state.maxLoops() - state.minLoops() + 1));
        if (duration < 0 && !state.end().isEmpty()) duration = PetBehaviors.get(entity.variant()).clips().get(state.clip());
        if (!state.entry().isEmpty()) {
            int entryTicks = PetBehaviors.ticks(entity.variant(), state.entry());
            parts.add(new Part(state.entry(), entryTicks));
            parts.add(new Part(state.clip(), duration < 0 ? 0 : Math.max(1, (int) Math.ceil(duration * 20) - entryTicks)));
            advance();
        } else { entity.play(state.clip()); if (duration >= 0) until = entity.tickCount + (int) Math.ceil(duration * 20); }
        entity.feedback.begin(name);
        if (state.move()) nextPath = entity.tickCount;
    }
    private void advance() { var part = parts.remove(); entity.play(part.clip()); until = part.ticks() == 0 ? 0 : entity.tickCount + part.ticks(); }
    private String postureExit() {
        return PetBehaviors.get(entity.variant()).postureExits().get(stateName);
    }
}
