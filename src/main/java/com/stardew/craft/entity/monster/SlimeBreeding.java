package com.stardew.craft.entity.monster;

import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.monster.MonsterFactory;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** GreenSlime courtship: 3-tile search, pursuing male/avoiding female, 2-second contact, 2-minute growth. */
final class SlimeBreeding {
    private final GreenSlimeEntity owner;
    private int readyMs, childhoodMs, contactMs = 2000;
    private UUID partner;
    private boolean avoiding;
    private float adultScale = 1;
    SlimeBreeding(GreenSlimeEntity owner) { this.owner = owner; }
    boolean adult() { return childhoodMs <= 0; }
    void initialize() { readyMs = 1000 + owner.getRandom().nextInt(119000); }
    void adultScale(float scale) { adultScale=scale;owner.growth(scale); }
    void child() { childhoodMs = 120000; owner.growth(Math.max(.2F, adultScale - .4F)); }
    void stop() {
        var mate = partner();
        partner = null; avoiding = false; readyMs = 120000; contactMs = 2000;
        if (mate != null && owner.getUUID().equals(mate.breeding().partner)) mate.breeding().stop();
    }
    private GreenSlimeEntity partner() {
        return partner != null && owner.level() instanceof ServerLevel level
                && level.getEntity(partner) instanceof GreenSlimeEntity slime && slime.isAlive() ? slime : null;
    }
    boolean tick() {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        if (childhoodMs > 0) childhoodMs = Math.max(0, childhoodMs - 50);
        else readyMs -= 50;
        owner.growth(Math.max(.2F, adultScale - .4F * childhoodMs / 120000F));
        owner.syncAntenna();
        var mate = partner();
        if (partner != null && mate == null) { stop(); return false; }
        if (mate != null) {
            if (readyMs <= -35000 || owner.distanceToSqr(mate) > 36) { stop(); return false; }
            var delta = mate.position().subtract(owner.position()).multiply(1, 0, 1);
            // Decorative antenna clearance must not change courtship contact distance.
            if (delta.length() < GreenSlimeRules.COLLISION_WIDTH * owner.growth() * owner.getScale() + 4 / 64.0) {
                avoiding = false;
                mate.breeding().avoiding = false;
                contactMs -= 50;
                if (owner.tickCount % 10 == 0) level.sendParticles(ParticleTypes.HEART,
                        owner.getX(), owner.getY() + .6, owner.getZ(), 1, .1, .08, .1, 0);
                if (contactMs <= 0) { reproduce(level, mate); stop(); }
            } else {
                double speed = (avoiding ? -1 : 1) * 3 * 3 / 64.0;
                boolean x = Math.abs(delta.x) > Math.abs(delta.z);
                owner.move(MoverType.SELF, new Vec3(x ? Math.signum(delta.x) * speed : 0, 0,
                        x ? 0 : Math.signum(delta.z) * speed));
            }
            owner.phase(GreenSlimeEntity.WALK);
            return true;
        }
        if (readyMs < 0 && childhoodMs == 0 && owner.male()) {
            readyMs = -1;
            for (int i = 0; i < 3; i++) if (owner.getRandom().nextDouble() < .001) {
                var candidates = level.getEntitiesOfClass(GreenSlimeEntity.class, owner.getBoundingBox().inflate(3),
                        other -> other != owner && other.isAlive() && !other.male() && other.breeding().readyMs <= 0
                                && other.breeding().childhoodMs == 0 && other.breeding().partner == null);
                if (!candidates.isEmpty()) {
                    mate = com.stardew.craft.port.PortJava.getFirst(candidates); partner = mate.getUUID();
                    mate.breeding().partner = owner.getUUID(); mate.breeding().avoiding = true;
                    contactMs = mate.breeding().contactMs = 2000;
                    return true;
                }
            }
        }
        return false;
    }
    private void reproduce(ServerLevel level, GreenSlimeEntity mate) {
        if (level.canSeeSky(owner.blockPosition()) && level.getEntitiesOfClass(GreenSlimeEntity.class,
                owner.getBoundingBox().inflate(1)).size() > 4) return;
        var random = StardewDeterministicRandom.createFromDoubles(StardewTimeManager.get().getAbsoluteDay(),
                level.getSeed() / 10.0, adultScale * 100, mate.breeding().adultScale * 100, 0);
        int kind = random.nextInt(4), color = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int a = owner.color() >> shift & 255, b = mate.color() >> shift & 255;
            int value;
            if (kind == 1 || kind == 2) value = (a + b) / 2;
            else {
                int channel = kind == 0 ? a : b;
                int variation = (int) (channel * .25F);
                value = channel + (variation == 0 ? 0 : random.nextInt(variation * 2) - variation);
            }
            color |= com.stardew.craft.port.PortJava.clamp(value, 0, 255) << shift;
        }
        var baby = owner.variant().type().create(level);
        if (baby == null) return;
        boolean placed = false;
        for (int i = 0; i < 30; i++) {
            double angle = i * Math.PI * .764;
            baby.moveTo(owner.getX() + Math.cos(angle) * (.8 + i / 10.0), owner.getY(),
                    owner.getZ() + Math.sin(angle) * (.8 + i / 10.0), owner.getYRot(), 0);
            if (level.noCollision(baby) && !level.getBlockState(baby.blockPosition().below()).isAir()) { placed = true; break; }
        }
        if (!placed) return;
        baby.initializeOffspring(owner.monsterState().context().offspring(), color);
        if ((color >> 16 & 255) > 100 && (color & 255) > 100 && (color >> 8 & 255) < 50) {
            while (random.nextDouble() < .1) baby.monsterState().addBornDrop(new net.minecraft.resources.ResourceLocation("stardewcraft:iridium_ore"));
            if (random.nextDouble() < .01) baby.monsterState().addBornDrop(new net.minecraft.resources.ResourceLocation("stardewcraft:iridium_bar"));
        }
        var stats = MonsterStats.fromEntity(owner);
        random.nextInt(2); // Source Choose is overwritten, but still consumes a draw.
        int hp = Math.max(1, (int) owner.getHealth() + random.nextInt(9) - 4);
        random.nextInt(2);
        int attack = Math.max(0, (int) stats.getDamage() + random.nextInt(3) - 1);
        random.nextInt(2);
        int resilience = Math.max(0, (int) stats.getResilience() + random.nextInt(3) - 1);
        baby.setSourceStats(hp, attack, resilience);
        random.nextInt(2);
        MonsterStats.builder().damage(attack).resilience(resilience).experience(baby.monsterState().stats().getExperience())
                .missChance(Math.max(0, stats.getMissChance() + (random.nextInt(3) - 1) / 100F)).build().writeToEntity(baby);
        random.nextInt(2);
        baby.breeding().adultScale = com.stardew.craft.port.PortJava.clamp(adultScale + (random.nextInt(5) - 2) / 100F, .6F, 1.5F);
        baby.startChild();
        double selectedSpeed = (random.nextInt(2) == 0 ? owner : mate).getAttributeValue(Attributes.MOVEMENT_SPEED);
        owner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(selectedSpeed);
        if (random.nextDouble() < .015) {
            double speed = owner.getAttributeValue(Attributes.MOVEMENT_SPEED) / .25 * 2;
            owner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(com.stardew.craft.port.PortJava.clamp(speed + random.nextInt(3) - 1, 1, 6) / 2 * .25);
        }
        MonsterFactory.addOffspring(level, baby);
    }
    void save(CompoundTag tag) {
        tag.putInt("SlimeMateReadyMs", readyMs); tag.putInt("SlimeChildhoodMs", childhoodMs);
        tag.putInt("SlimeMateContactMs", contactMs); tag.putFloat("SlimeAdultScale", adultScale);
        tag.putBoolean("SlimeAvoidingMate", avoiding);
        if (partner != null) tag.putUUID("SlimeMate", partner);
    }
    void load(CompoundTag tag) {
        readyMs = tag.getInt("SlimeMateReadyMs"); childhoodMs = tag.getInt("SlimeChildhoodMs");
        contactMs = tag.contains("SlimeMateContactMs") ? tag.getInt("SlimeMateContactMs") : 2000;
        adultScale = tag.contains("SlimeAdultScale") ? tag.getFloat("SlimeAdultScale") : 1;
        avoiding = tag.getBoolean("SlimeAvoidingMate"); partner = tag.hasUUID("SlimeMate") ? tag.getUUID("SlimeMate") : null;
        owner.growth(Math.max(.2F, adultScale - .4F * childhoodMs / 120000F));
    }
}
