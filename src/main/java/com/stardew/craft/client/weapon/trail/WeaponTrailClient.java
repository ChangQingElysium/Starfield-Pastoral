package com.stardew.craft.client.weapon.trail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.Config;
import com.stardew.craft.client.weapon.LavaKatanaVisuals;
import com.stardew.craft.client.weapon.MeleeWeaponVisuals;
import com.stardew.craft.client.weapon.WeaponGlowGeometry;
import com.stardew.craft.combat.WeaponMeleeProfile;
import com.stardew.craft.combat.WeaponMeleeProfile.Material;
import com.stardew.craft.client.weapon.WeaponEffectRenderTypes;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Shared, bounded blade trail renderer fed by the final item render transform.
 */
public final class WeaponTrailClient {
    private static final int MAX_SAMPLES = 24;
    private static final int MAX_RESAMPLE_STEPS = 4;
    private static final double SAMPLE_INTERVAL_TICKS = 0.20;
    private static final double MIN_SAMPLE_DISTANCE_SQR = 0.0004;
    private static final double MAX_RENDER_DISTANCE_SQR = 48.0 * 48.0;
    private static final Map<String, TrailProfile> PROFILES = Map.ofEntries(
            Map.entry("dragontooth_club_swing",new TrailProfile(0,.35f,2f,Glow.DRAGON)),
            Map.entry("rapier_swing",new TrailProfile(0,.35f,1.5f,Glow.IRON)),
            Map.entry("dragontooth_club_jaw",new TrailProfile(.331f,.56f,2.2f,Glow.DRAGON)),
            Map.entry("dragontooth_club_breath",new TrailProfile(.225f,.36f,2.2f,Glow.DRAGON)),
            Map.entry("rapier_riposte",new TrailProfile(.33f,.59f,1.7f,Glow.IRON)),
            Map.entry("the_slammer_swing",new TrailProfile(0f,0.35f,2f,Glow.SLAMMER)),
            Map.entry("dwarf_hammer_swing",new TrailProfile(0f,0.35f,2f,Glow.DWARF_HAMMER)),
            Map.entry("slammer_upheaval",new TrailProfile(.293f,.5f,2.4f,Glow.SLAMMER)),
            Map.entry("slammer_rampage",new TrailProfile(.1375f,.875f,1.8f,Glow.SLAMMER)),
            Map.entry("dwarf_hammer_rebound",new TrailProfile(.257f,.715f,1.6f,Glow.DWARF_HAMMER)),
            Map.entry("dwarf_hammer_faultline",new TrailProfile(.516f,.75f,2.2f,Glow.DWARF_HAMMER)),
            Map.entry("lead_rod_swing",new TrailProfile(0,.35f,2f,Glow.SPINE)),
            Map.entry("kudgel_swing",new TrailProfile(0,.35f,2f,Glow.KUDGEL)),
            Map.entry("lead_rod_press",new TrailProfile(.35f,.55f,2.1f,Glow.SPINE)),
            Map.entry("kudgel_sweep",new TrailProfile(.386f,.59f,2.4f,Glow.KUDGEL)),
            Map.entry("wood_club_swing",new TrailProfile(0,.35f,2f,Glow.WOOD)),
            Map.entry("wood_mallet_swing",new TrailProfile(0,.35f,2f,Glow.WOOD)),
            Map.entry("wood_club_whirl",new TrailProfile(.2f,.723f,2.1f,Glow.WOOD)),
            Map.entry("wood_mallet_leap",new TrailProfile(.371f,.586f,2.4f,Glow.WOOD)),
            Map.entry("galaxy_hammer_swing", new TrailProfile(0,.35f,2f,Glow.GALAXY)),
            Map.entry("infinity_gavel_swing", new TrailProfile(0,.35f,2f,Glow.INFINITY)),
            Map.entry("galaxy_hammer_starshock_sweep", new TrailProfile(.29f,.59f,2.8f,Glow.GALAXY)),
            Map.entry("galaxy_hammer_starfall_quake", new TrailProfile(.3375f,.525f,2.3f,Glow.GALAXY)),
            Map.entry("infinity_gavel_singularity_press", new TrailProfile(.413f,.9f,2.4f,Glow.INFINITY)),
            Map.entry("infinity_gavel_pound", new TrailProfile(.14f,.39f,1.5f,Glow.INFINITY)),
            Map.entry(MeleeWeaponVisuals.CUTLASS_SWING,new TrailProfile(0,.44f,2.5f,Glow.CRESCENT)),
            Map.entry(MeleeWeaponVisuals.FALCHION_SWING,new TrailProfile(0,.4f,2f,Glow.FALCHION)),
            Map.entry(MeleeWeaponVisuals.FALCHION_LINE,new TrailProfile(0,.42f,2.3f,Glow.FALCHION)),
            Map.entry(MeleeWeaponVisuals.RUST_SWING,new TrailProfile(0,.42f,1.8f,Glow.RUST)),
            Map.entry(MeleeWeaponVisuals.RUST_STRIKE,new TrailProfile(0,.43f,2.1f,Glow.RUST)),
            Map.entry(MeleeWeaponVisuals.WOOD_SWING,new TrailProfile(0,.4f,1.7f,Glow.WOOD)),
            Map.entry(MeleeWeaponVisuals.WOOD_BLESS,new TrailProfile(0,.42f,2.1f,Glow.WOOD)),
            Map.entry(MeleeWeaponVisuals.LIGHT_SWING,new TrailProfile(0,.36f,1.7f,Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.LIGHT_COUNTER,new TrailProfile(0,.38f,2.1f,Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.SPINE_SWING,new TrailProfile(0,.46f,2.7f,Glow.SPINE)),
            Map.entry(MeleeWeaponVisuals.SPINE_STRIKE,new TrailProfile(0,.46f,3f,Glow.SPINE)),
            Map.entry(MeleeWeaponVisuals.SPINE_WEAK,new TrailProfile(0,.46f,2.7f,Glow.SPINE)),
            Map.entry(MeleeWeaponVisuals.PIRATE_SWING, new TrailProfile(0, .42f, 2.2f, Glow.PIRATE)),
            Map.entry(MeleeWeaponVisuals.PIRATE_PLUNDER, new TrailProfile(0, .43f, 2.5f, Glow.PIRATE)),
            Map.entry(MeleeWeaponVisuals.SILVER_SWING, new TrailProfile(0, .39f, 1.8f, Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.SILVER_OUT, new TrailProfile(0, .4f, 2.2f, Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.SILVER_RETURN, new TrailProfile(0, .4f, 2.4f, Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.SILVER_STAY, new TrailProfile(0, .4f, 2.1f, Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.SILVER_EMPTY, new TrailProfile(0, .4f, 1.6f, Glow.SILVER)),
            Map.entry(MeleeWeaponVisuals.IRON_SWING, new TrailProfile(0, .36f, 1.3f, Glow.IRON)),
            Map.entry(MeleeWeaponVisuals.IRON_THRUST, new TrailProfile(0, .37f, 1.7f, Glow.IRON)),
            Map.entry(MeleeWeaponVisuals.WIND_SWING, new TrailProfile(0, .4f, 1.7f, Glow.WIND)),
            Map.entry(MeleeWeaponVisuals.WIND_THRUST, new TrailProfile(0, .43f, 2.1f, Glow.WIND)),
            Map.entry(MeleeWeaponVisuals.BONE_SWORD_SWING, new TrailProfile(0, .42f, 2.0f, Glow.BONE_SWORD)),
            Map.entry(MeleeWeaponVisuals.BONE_FRACTURE, new TrailProfile(0, .4f, 2.2f, Glow.BONE_SWORD)),
            Map.entry(MeleeWeaponVisuals.CLAYMORE_SWING, new TrailProfile(0, .44f, 2.6f, Glow.CLAYMORE)),
            Map.entry(MeleeWeaponVisuals.CLAYMORE_OUT, new TrailProfile(0, .39f, 3.0f, Glow.CLAYMORE)),
            Map.entry(MeleeWeaponVisuals.CLAYMORE_RETURN, new TrailProfile(0, .39f, 2.6f, Glow.CLAYMORE)),
            Map.entry(MeleeWeaponVisuals.DWARF_SWORD_SWING, new TrailProfile(0, .44f, 2.1f, Glow.DWARF_SWORD)),
            Map.entry(MeleeWeaponVisuals.DWARF_GUARD, new TrailProfile(0, .46f, 2.3f, Glow.DWARF_SWORD)),
            Map.entry(MeleeWeaponVisuals.DWARF_DAGGER_SWING, new TrailProfile(0, .4f, 1.4f, Glow.DWARF_DAGGER)),
            Map.entry(MeleeWeaponVisuals.DWARF_THRUST, new TrailProfile(0, .65f, 1.7f, Glow.DWARF_DAGGER)),
            Map.entry(MeleeWeaponVisuals.NEEDLE_SWING, new TrailProfile(0, .39f, 1.3f, Glow.NEEDLE)),
            Map.entry(MeleeWeaponVisuals.NEEDLE_STRIKE, new TrailProfile(0, .42f, .85f, Glow.NEEDLE)),
            Map.entry(MeleeWeaponVisuals.NEEDLE_FINAL, new TrailProfile(0, .44f, 1.1f, Glow.NEEDLE)),
            Map.entry(MeleeWeaponVisuals.BURGLAR_SWING, new TrailProfile(0, .45f, 1.5f, Glow.BURGLAR)),
            Map.entry(MeleeWeaponVisuals.BURGLAR_STRIKE, new TrailProfile(0, .48f, 1.9f, Glow.BURGLAR)),
            Map.entry(MeleeWeaponVisuals.SHADOW_SWING, new TrailProfile(0, .39f, 1.5f, Glow.SHADOW)),
            Map.entry(MeleeWeaponVisuals.SHADOW_EXECUTE, new TrailProfile(0, .46f, 2.0f, Glow.SHADOW)),
            Map.entry(MeleeWeaponVisuals.INSECT_SWING, new TrailProfile(0, .42f, 1.7f, Glow.INSECT)),
            Map.entry(MeleeWeaponVisuals.INSECT_DASH, new TrailProfile(0, .48f, 2.1f, Glow.INSECT)),
            Map.entry(MeleeWeaponVisuals.CRYSTAL_SWING,new TrailProfile(0,.40f,1.5f,Glow.CRYSTAL)),
            Map.entry(MeleeWeaponVisuals.CRYSTAL_LAYER,new TrailProfile(0,.43f,2.0f,Glow.CRYSTAL)),
            Map.entry(MeleeWeaponVisuals.VENOM_SWING,new TrailProfile(0,.44f,1.8f,Glow.VENOM)),
            Map.entry(MeleeWeaponVisuals.VENOM_RIPPLE,new TrailProfile(0,.4f,1.8f,Glow.VENOM)),
            Map.entry(MeleeWeaponVisuals.VENOM_NEST,new TrailProfile(0,.43f,2.2f,Glow.VENOM)),
            Map.entry(MeleeWeaponVisuals.DARK_SWING, new TrailProfile(0, 0.48f, 2.2f, Glow.BLOOD)),
            Map.entry(MeleeWeaponVisuals.DARK_DEBT, new TrailProfile(0, 0.5f, 2.6f, Glow.BLOOD)),
            Map.entry(MeleeWeaponVisuals.FORGE_SWING, new TrailProfile(0, 0.46f, 2.2f, Glow.FORGE)),
            Map.entry(MeleeWeaponVisuals.FORGE_QUENCH, new TrailProfile(0, 0.5f, 2.7f, Glow.FORGE)),
            Map.entry(MeleeWeaponVisuals.OBSIDIAN_SWING, new TrailProfile(0, 0.46f, 2.1f, Glow.OBSIDIAN)),
            Map.entry(MeleeWeaponVisuals.OSSIFIED_SWING, new TrailProfile(0, 0.43f, 1.8f, Glow.OSSIFIED)),
            Map.entry(MeleeWeaponVisuals.OBSIDIAN_CRACK, new TrailProfile(0.48f, 0.8f, 2.7f, Glow.OBSIDIAN)),
            Map.entry(MeleeWeaponVisuals.MEOW_SWING, new TrailProfile(0, 0.5f, 2.3f, Glow.MEOW)),
            Map.entry(MeleeWeaponVisuals.HOLY_SWING, new TrailProfile(0, 0.52f, 2.3f, Glow.HOLY)),
            Map.entry(MeleeWeaponVisuals.TEMPLAR_SWING, new TrailProfile(0, 0.48f, 2.0f, Glow.TEMPLAR)),
            Map.entry(MeleeWeaponVisuals.HOLY_SMITE, new TrailProfile(0, 0.54f, 2.8f, Glow.HOLY)),
            Map.entry(MeleeWeaponVisuals.TEMPLAR_STRIKE, new TrailProfile(0, 0.5f, 2.7f, Glow.TEMPLAR)),
            Map.entry("crescent_slash", new TrailProfile(0.20f, 0.625f, 3.0f, Glow.CRESCENT)),
            Map.entry("forest_blessing", new TrailProfile(0, 0.58f, 2.6f, Glow.GROVE)),
            Map.entry(LavaKatanaVisuals.SWING, new TrailProfile(0.0f, 0.60f, 3.0f, Glow.LAVA)),
            Map.entry(LavaKatanaVisuals.BRAND, new TrailProfile(0.0f, 0.60f, 3.5f, Glow.LAVA)),
            Map.entry(MeleeWeaponVisuals.SWORD_SWING, new TrailProfile(0.0f, 0.60f, 3.0f, Glow.SWORD)),
            Map.entry(MeleeWeaponVisuals.CARVING_SWING, new TrailProfile(0.0f, 0.42f, 1.5f, Glow.DAGGER)),
            Map.entry(MeleeWeaponVisuals.CARVING_STRIKE, new TrailProfile(0.0f, 0.60f, 1.2f, Glow.DAGGER)),
            Map.entry(MeleeWeaponVisuals.CARVING_BONUS, new TrailProfile(0.0f, 0.52f, 1.7f, Glow.DAGGER)),
            Map.entry(MeleeWeaponVisuals.FEMUR_SWING, new TrailProfile(0.04f, 0.35f, 3.2f, Glow.BONE)),
            Map.entry(MeleeWeaponVisuals.FEMUR_SLAM, new TrailProfile(0.0f, 0.28f, 3.5f, Glow.BONE)),
            Map.entry(MeleeWeaponVisuals.SHIV_SWING, new TrailProfile(0, 0.42f, 1.3f, Glow.SHIV)),
            Map.entry(MeleeWeaponVisuals.SHIV_EMPOWERED, new TrailProfile(0, 0.46f, 1.8f, Glow.SHIV)),
            Map.entry(MeleeWeaponVisuals.SHIV_STAB, new TrailProfile(0, 0.46f, 1.8f, Glow.SHIV)),
            Map.entry(MeleeWeaponVisuals.GALAXY_RIFT, new TrailProfile(0, 0.6f, 3.0f, Glow.GALAXY)),
            Map.entry(MeleeWeaponVisuals.GALAXY_JUDGEMENT, new TrailProfile(0, 0.45f, 3.2f, Glow.GALAXY)),
            Map.entry(MeleeWeaponVisuals.GALAXY_STAB, new TrailProfile(0, 0.46f, 1.2f, Glow.STAR_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.GALAXY_LEAP, new TrailProfile(0, 0.5f, 2.0f, Glow.STAR_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.ELF_SWING, new TrailProfile(0, 0.45f, 1.3f, Glow.DAGGER)),
            Map.entry(MeleeWeaponVisuals.TIDE_ANCHOR, new TrailProfile(0, 0.46f, 2.6f, Glow.TIDE)),
            Map.entry(MeleeWeaponVisuals.TIDE_STAB, new TrailProfile(0, 0.46f, 1.5f, Glow.TIDE_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.TIDE_REEL, new TrailProfile(0, 0.5f, 2.3f, Glow.TIDE_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.INFINITY_RELEASE, new TrailProfile(0, 0.55f, 3.0f, Glow.INFINITY)),
            Map.entry(MeleeWeaponVisuals.INFINITY_STAB, new TrailProfile(0, 0.42f, 1.1f, Glow.INFINITY_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.INFINITY_BACK, new TrailProfile(0, 0.48f, 1.8f, Glow.INFINITY_NEEDLE)),
            Map.entry(MeleeWeaponVisuals.YETI_MARK, new TrailProfile(0, 0.50f, 2.6f, Glow.SWORD)),
            Map.entry(MeleeWeaponVisuals.YETI_SPINE, new TrailProfile(0, 0.36f, 3.0f, Glow.SWORD)),
            Map.entry(MeleeWeaponVisuals.DRAGON_PIERCE, new TrailProfile(0, 0.5f, 2.2f, Glow.DRAGON)),
            Map.entry(MeleeWeaponVisuals.DRAGON_JUDGEMENT, new TrailProfile(0, 0.5f, 3.2f, Glow.DRAGON))
    );
    private static final Map<Integer, TrailState> STATES = new HashMap<>();
    private static ClientLevel activeLevel;

    private WeaponTrailClient() {}

    public static boolean supports(String skillId) {
        return PROFILES.containsKey(skillId);
    }

    public static void capture(
            int entityId,
            String skillId,
            long actionStartTick,
            float actionProgress,
            Vec3 bladeBase,
            Vec3 bladeTip,
            double sampleTime,
            boolean firstPerson,
            String weaponId
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            return;
        }
        if (activeLevel != minecraft.level) {
            STATES.clear();
            activeLevel = minecraft.level;
        }

        if("slammer_rampage".equals(skillId)) {
            float tick=actionProgress*32;
            if(!((tick>=4.4f&&tick<=7)||(tick>=10&&tick<=13)||(tick>=16&&tick<=19)||(tick>=24.3f&&tick<=28)))return;
        }
        if("dwarf_hammer_rebound".equals(skillId)&&actionProgress>6f/14&&actionProgress<7.5f/14)return;
        if("wood_club_whirl".equals(skillId)&&actionProgress>5.8f/18&&actionProgress<9.5f/18)return;
        if("infinity_gavel_singularity_press".equals(skillId)&&actionProgress>9f/15&&actionProgress<11f/15)return;
        TrailProfile profile = PROFILES.get(skillId);
        if (profile == null
                || actionProgress < profile.startProgress
                || actionProgress > profile.endProgress) {
            return;
        }

        TrailState state = STATES.computeIfAbsent(
                entityId,
                ignored -> new TrailState(profile, actionStartTick, firstPerson, weaponId)
        );
        if (state.actionStartTick != actionStartTick || state.profile != profile
                || state.firstPerson != firstPerson || !state.weaponId.equals(weaponId)) {
            state.samples.clear();
            state.actionStartTick = actionStartTick;
            state.profile = profile;
            state.firstPerson = firstPerson;
            state.weaponId = weaponId;
        }

        TrailSample previous = state.samples.peekLast();
        if (previous == null) {
            state.samples.addLast(new TrailSample(bladeBase, bladeTip, sampleTime));
            return;
        }

        double elapsed = sampleTime - previous.time;
        double travelSqr = Math.max(
                previous.base.distanceToSqr(bladeBase),
                previous.tip.distanceToSqr(bladeTip)
        );
        if (elapsed < 0 || elapsed > profile.lifetimeTicks || travelSqr > 16.0) {
            state.samples.clear();
            state.samples.addLast(new TrailSample(bladeBase, bladeTip, sampleTime));
            return;
        }
        if (elapsed < SAMPLE_INTERVAL_TICKS
                || travelSqr < MIN_SAMPLE_DISTANCE_SQR) {
            return;
        }

        int resampleCount = calculateResampleCount(elapsed);
        for (int index = 1; index <= resampleCount; index++) {
            double amount = resampleFraction(index, resampleCount);
            double time = previous.time + elapsed * amount;
            Vec3 sampleBase = previous.base.lerp(bladeBase, amount);
            Vec3 sampleTip = previous.tip.lerp(bladeTip, amount);
            state.samples.addLast(new TrailSample(sampleBase, sampleTip, time));
        }
        while (state.samples.size() > MAX_SAMPLES) {
            state.samples.removeFirst();
        }
        if (profile.glow == Glow.LAVA && sampleTime - state.lastSparkTime >= 0.75
                && bladeTip.distanceToSqr(minecraft.gameRenderer.getMainCamera().getPosition()) < 24 * 24) {
            state.lastSparkTime = sampleTime;
            Vec3 drift = bladeTip.subtract(previous.tip).normalize().scale(0.025);
            minecraft.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    bladeTip.x, bladeTip.y, bladeTip.z, drift.x, 0.012, drift.z);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) {
            STATES.clear();
            activeLevel = minecraft.level;
            return;
        }
        if (activeLevel != minecraft.level) {
            STATES.clear();
            activeLevel = minecraft.level;
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        Map<TrailProfile, RenderType> renderTypes = new LinkedHashMap<>();

        Iterator<Map.Entry<Integer, TrailState>> states = STATES.entrySet().iterator();
        while (states.hasNext()) {
            var entry = states.next();
            TrailState state = entry.getValue();
            var entity = minecraft.level.getEntity(entry.getKey());
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()
                    || !(living.getMainHandItem().getItem() instanceof IStardewWeapon weapon)
                    || !state.weaponId.equals(weapon.getWeaponId())
                    || state.firstPerson != (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())) {
                states.remove();
                continue;
            }
            while (!state.samples.isEmpty()
                    && now - state.samples.peekFirst().time > state.profile.lifetimeTicks) {
                state.samples.removeFirst();
            }
            if (state.samples.size() < 2) {
                if (state.samples.isEmpty()) {
                    states.remove();
                }
                continue;
            }
            TrailSample newest = state.samples.peekLast();
            if (state.firstPerson || newest.tip.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            renderTypes.computeIfAbsent(
                    state.profile,
                    profile -> WeaponEffectRenderTypes.MOLTEN_GLOW
            );
        }
        if (renderTypes.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (TrailState state : STATES.values()) {
            if (state.firstPerson || state.samples.size() < 2
                    || state.samples.peekLast().tip.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            RenderType renderType = renderTypes.get(state.profile);
            if (renderType == null) {
                continue;
            }
            renderTrail(
                    minecraft.level,
                    buffers.getBuffer(renderType),
                    pose,
                    state,
                    now
            );
        }
        poseStack.popPose();
        for (RenderType renderType : renderTypes.values()) {
            buffers.endBatch(renderType);
        }
    }

    /** The hand is drawn after the world, with its own FOV. Draw its ribbon in the same pass. */
    public static void renderFirstPerson() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || activeLevel != mc.level
                || !Config.ENABLE_WEAPON_SPECIAL_EFFECTS.getAsBoolean()) return;
        TrailState state = STATES.get(mc.player.getId());
        if (state == null || !state.firstPerson || state.samples.size() < 2) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f pose = new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z);
        var buffers = mc.renderBuffers().bufferSource();
        RenderType type = WeaponEffectRenderTypes.MOLTEN_GLOW;
        double now = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        renderTrail(mc.level, buffers.getBuffer(type), pose, state, now);
        buffers.endBatch(type);
    }

    private static void renderTrail(
            ClientLevel level,
            VertexConsumer consumer,
            Matrix4f pose,
            TrailState state,
            double now
    ) {
        TrailSample[] samples = state.samples.toArray(TrailSample[]::new);
        var weaponProfile = WeaponMeleeProfile.get(state.weaponId);
        Material material = weaponProfile == null ? Material.METAL : weaponProfile.material();
        for (int index = 1; index < samples.length; index++) {
            TrailSample previous = samples[index - 1];
            TrailSample current = samples[index];
            renderGlowSegment(consumer, pose, previous, current, state.profile, material, now, state.weaponId);
        }
    }

    private static void renderGlowSegment(VertexConsumer out, Matrix4f pose, TrailSample from,
                                           TrailSample to, TrailProfile profile, Material material, double now, String weaponId) {
        float a = trailCoordinate(from, profile, now), b = trailCoordinate(to, profile, now);
        if(profile.glow==Glow.CRESCENT||profile.glow==Glow.FALCHION){
            boolean crescent=profile.glow==Glow.CRESCENT;
            glowBand(out,pose,from,to,crescent?.58:.76,1.016,crescent?116:76,crescent?111:152,crescent?170:184,90,a,b);
            glowBand(out,pose,from,to,.9,1.014,crescent?213:159,crescent?207:217,crescent?245:244,165,a,b);
            glowBand(out,pose,from,to,.987,1.011,246,248,240,225,a,b);return;
        }
        if(profile.glow==Glow.RUST||profile.glow==Glow.WOOD){
            boolean wood=profile.glow==Glow.WOOD;
            glowBand(out,pose,from,to,.65,1.013,wood?112:137,wood?135:77,wood?68:42,90,a,b);
            glowBand(out,pose,from,to,.88,1.012,wood?203:221,wood?217:142,wood?143:84,155,a,b);
            glowBand(out,pose,from,to,.985,1.009,245,wood?241:216,wood?202:176,205,a,b);return;
        }
        if(profile.glow==Glow.SLAMMER||profile.glow==Glow.DWARF_HAMMER){
            boolean dwarf=profile.glow==Glow.DWARF_HAMMER;
            glowBand(out,pose,from,to,.51,1.016,dwarf?145:144,dwarf?97:101,dwarf?48:81,95,a,b);
            glowBand(out,pose,from,to,.83,1.014,dwarf?241:220,dwarf?185:194,dwarf?105:163,160,a,b);
            glowBand(out,pose,from,to,.985,1.011,251,240,dwarf?201:222,220,a,b);return;
        }
        if(profile.glow==Glow.KUDGEL){
            glowBand(out,pose,from,to,.53,1.016,128,98,68,95,a,b);
            glowBand(out,pose,from,to,.84,1.014,227,203,165,160,a,b);
            glowBand(out,pose,from,to,.987,1.012,250,244,224,220,a,b);return;
        }
        if(profile.glow==Glow.SPINE){
            glowBand(out,pose,from,to,.53,1.016,84,108,132,100,a,b);
            glowBand(out,pose,from,to,.86,1.014,185,207,228,160,a,b);
            glowBand(out,pose,from,to,.987,1.012,246,243,225,220,a,b);return;
        }
        if (profile.glow == Glow.PIRATE || profile.glow == Glow.SILVER) {
            boolean pirate = profile.glow == Glow.PIRATE;
            glowBand(out, pose, from, to, pirate ? .59 : .69, 1.015, pirate ? 154 : 115, pirate ? 83 : 143, pirate ? 49 : 179, 90, a, b);
            glowBand(out, pose, from, to, .89, 1.015, pirate ? 226 : 210, pirate ? 169 : 225, pirate ? 104 : 246, 160, a, b);
            glowBand(out, pose, from, to, .985, 1.012, 249, 238, pirate ? 199 : 240, 210, a, b);
            return;
        }
        if (profile.glow == Glow.IRON || profile.glow == Glow.WIND) {
            boolean wind = profile.glow == Glow.WIND;
            glowBand(out, pose, from, to, wind ? .73 : .8, 1.013, wind ? 69 : 103, wind ? 152 : 122, wind ? 146 : 150, 85, a, b);
            glowBand(out, pose, from, to, .93, 1.012, wind ? 163 : 199, wind ? 226 : 214, wind ? 214 : 231, 160, a, b);
            glowBand(out, pose, from, to, .992, 1.01, 239, 247, 235, 210, a, b);
            return;
        }
        if (profile.glow == Glow.BONE_SWORD || profile.glow == Glow.CLAYMORE) {
            boolean boneSword = profile.glow == Glow.BONE_SWORD;
            glowBand(out, pose, from, to, boneSword ? .64 : .48, 1.02, boneSword ? 148 : 82, boneSword ? 128 : 116, boneSword ? 91 : 150, 90, a, b);
            glowBand(out, pose, from, to, .88, 1.015, boneSword ? 228 : 177, boneSword ? 214 : 202, boneSword ? 169 : 227, 165, a, b);
            glowBand(out, pose, from, to, .983, 1.012, 248, 240, boneSword ? 209 : 237, 215, a, b);
            return;
        }
        if (profile.glow == Glow.DWARF_SWORD || profile.glow == Glow.DWARF_DAGGER) {
            boolean sword = profile.glow == Glow.DWARF_SWORD;
            glowBand(out, pose, from, to, sword ? .58 : .76, 1.015, sword ? 156 : 69, sword ? 104 : 141, sword ? 51 : 143, 95, a, b);
            glowBand(out, pose, from, to, .9, 1.018, sword ? 227 : 174, sword ? 187 : 218, sword ? 106 : 213, 160, a, b);
            glowBand(out, pose, from, to, .985, 1.012, 247, 235, sword ? 191 : 225, 210, a, b);
            return;
        }
        if (profile.glow == Glow.NEEDLE || profile.glow == Glow.BURGLAR) {
            boolean needle = profile.glow == Glow.NEEDLE;
            glowBand(out, pose, from, to, .77, 1.015, needle ? 111 : 116, needle ? 76 : 134, needle ? 185 : 150, 85, a, b);
            glowBand(out, pose, from, to, .92, 1.012, needle ? 202 : 206, needle ? 178 : 219, needle ? 249 : 223, 165, a, b);
            glowBand(out, pose, from, to, .989, 1.012, 249, needle ? 235 : 239, needle ? 255 : 201, 215, a, b);
            return;
        }
        if (profile.glow == Glow.SHADOW || profile.glow == Glow.INSECT) {
            boolean shadow = profile.glow == Glow.SHADOW;
            glowBand(out, pose, from, to, .68, 1.02, shadow ? 80 : 71, shadow ? 43 : 149, shadow ? 142 : 123, 90, a, b);
            glowBand(out, pose, from, to, .89, 1.02, shadow ? 169 : 226, shadow ? 140 : 205, shadow ? 240 : 134, 155, a, b);
            glowBand(out, pose, from, to, .988, 1.012, 245, shadow ? 230 : 244, shadow ? 255 : 197, 205, a, b);
            return;
        }
        if(profile.glow==Glow.CRYSTAL || profile.glow==Glow.VENOM) {
            boolean crystal=profile.glow==Glow.CRYSTAL;
            glowBand(out,pose,from,to,.66,1.02,crystal?92:79,crystal?191:160,crystal?245:68,100,a,b);
            glowBand(out,pose,from,to,.88,1.02,crystal?201:196,crystal?237:226,crystal?255:96,165,a,b);
            glowBand(out,pose,from,to,.985,1.015,245,253,crystal?255:190,205,a,b);
            return;
        }
        if (profile.glow == Glow.BLOOD || profile.glow == Glow.FORGE) {
            boolean blood = profile.glow == Glow.BLOOD;
            glowBand(out, pose, from, to, 0.5, 1.02, blood ? 132 : 208, blood ? 17 : 70, blood ? 44 : 17, 80, a, b);
            glowBand(out, pose, from, to, 0.8, 1.02, 245, blood ? 55 : 173, blood ? 80 : 55, 160, a, b);
            glowBand(out, pose, from, to, 0.975, 1.015, 255, blood ? 198 : 243, blood ? 196 : 201, 220, a, b);
            return;
        }
        if (profile.glow == Glow.OBSIDIAN || profile.glow == Glow.OSSIFIED) {
            boolean bone = profile.glow == Glow.OSSIFIED;
            glowBand(out, pose, from, to, 0.7, 1.015, bone ? 183 : 105, bone ? 169 : 48, bone ? 137 : 166, 90, a, b);
            glowBand(out, pose, from, to, 0.9, 1.012, bone ? 241 : 192, bone ? 233 : 139, bone ? 211 : 249, 185, a, b);
            glowBand(out, pose, from, to, 0.985, 1.015, 249, bone ? 245 : 214, bone ? 230 : 255, 215, a, b);
            return;
        }
        if (profile.glow == Glow.MEOW) {
            for (int i = 0; i < 6; i++) {
                int[] color = com.stardew.craft.client.weapon.RainbowTrailGeometry.color(i);
                glowBand(out, pose, from, to, 0.4 + i * 0.1, 0.5 + i * 0.1,
                        color[0], color[1], color[2], 150, a, b);
            }
            return;
        }
        if (profile.glow == Glow.GROVE || (profile.glow == Glow.SWORD && "forest_sword".equals(weaponId))) {
            glowBand(out, pose, from, to, 0.55, 1.025, 30, 123, 82, 65, a, b);
            glowBand(out, pose, from, to, 0.79, 1.01, 88, 190, 117, 130, a, b);
            glowBand(out, pose, from, to, 0.96, 1.012, 221, 244, 174, 205, a, b);
            return;
        }
        if (profile.glow == Glow.TIDE_NEEDLE) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 side = thrustWidth(to.tip.subtract(from.tip), camera.subtract(to.tip));
            float fade = (a + b) * 0.5f;
            for (int i = -1; i <= 1; i++) {
                Vec3 offset = side.scale(i * 0.085);
                WeaponGlowGeometry.strip(out, pose, from.tip.add(offset), to.tip.add(offset), side.scale(0.028 * fade),
                        47, 199, 225, Math.round(145 * fade * fade));
                WeaponGlowGeometry.strip(out, pose, from.tip.add(offset), to.tip.add(offset), side.scale(0.008 * fade),
                        220, 255, 255, Math.round(240 * fade * fade));
            }
            return;
        }
        if (profile.glow == Glow.HOLY || profile.glow == Glow.TEMPLAR) {
            boolean holy = profile.glow == Glow.HOLY;
            glowBand(out, pose, from, to, 0.22, 1.07, holy ? 255 : 110, holy ? 195 : 170, holy ? 75 : 228, 105, a, b);
            glowBand(out, pose, from, to, 0.64, 1.03, 255, 226, 155, 185, a, b);
            glowBand(out, pose, from, to, 0.92, 1.02, 255, 253, 234, 250, a, b);
            return;
        }
        if (profile.glow == Glow.TIDE) {
            glowBand(out, pose, from, to, 0.15, 1.08, 25, 111, 164, 120, a, b);
            glowBand(out, pose, from, to, 0.60, 1.04, 58, 219, 231, 185, a, b);
            glowBand(out, pose, from, to, 0.92, 1.02, 220, 255, 255, 245, a, b);
            return;
        }
        if (profile.glow == Glow.DAGGER || profile.glow == Glow.SHIV || profile.glow == Glow.STAR_NEEDLE || profile.glow == Glow.INFINITY_NEEDLE) {
            // Axial thrusts collapse a blade-base ribbon to a line. Give the tip path its own width.
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 width = thrustWidth(to.tip.subtract(from.tip), camera.subtract(to.tip));
            float fade = (a + b) * 0.5f;
            WeaponGlowGeometry.strip(out, pose, from.tip, to.tip, width.scale(0.04 * fade),
                    profile.glow == Glow.INFINITY_NEEDLE ? 250 : profile.glow == Glow.SHIV ? 178 : material.red(),
                    profile.glow == Glow.INFINITY_NEEDLE ? 197 : profile.glow == Glow.SHIV ? 83 : material.green(),
                    profile.glow == Glow.INFINITY_NEEDLE ? 107 : profile.glow == Glow.SHIV ? 246 : material.blue(), Math.round(130 * fade * fade));
            WeaponGlowGeometry.strip(out, pose, from.tip, to.tip, width.scale(0.012 * fade),
                    217, 249, 255, Math.round(240 * fade * fade));
            return;
        }
        if (profile.glow == Glow.INFINITY) {
            glowBand(out, pose, from, to, 0.18, 1.08, 157, 105, 233, 100, a, b);
            glowBand(out, pose, from, to, 0.62, 1.03, 255, 207, 116, 180, a, b);
            glowBand(out, pose, from, to, 0.92, 1.02, 255, 248, 223, 245, a, b);
            return;
        }
        if (profile.glow == Glow.GALAXY) {
            glowBand(out, pose, from, to, 0.15, 1.10, 105, 83, 240, 130, a, b);
            glowBand(out, pose, from, to, 0.60, 1.04, 89, 206, 255, 190, a, b);
            glowBand(out, pose, from, to, 0.91, 1.02, 237, 250, 255, 250, a, b);
            return;
        }
        if (profile.glow == Glow.DRAGON) {
            glowBand(out, pose, from, to, 0.12, 1.16, 126, 52, 210, 150, a, b);
            glowBand(out, pose, from, to, 0.50, 1.10, 224, 174, 250, 190, a, b);
            glowBand(out, pose, from, to, 0.87, 1.04, 255, 246, 220, 250, a, b);
            return;
        }
        if (profile.glow == Glow.BONE) {
            glowBand(out, pose, from, to, 0.08, 1.14, material.red(), material.green(), material.blue(), 95, a, b);
            glowBand(out, pose, from, to, 0.38, 1.04, material.red(), material.green(), material.blue(), 165, a, b);
            glowBand(out, pose, from, to, 0.83, 1.025, 255, 250, 226, 225, a, b);
            return;
        }
        if (profile.glow == Glow.SWORD) {
            glowBand(out, pose, from, to, 0.10, 1.09, material.red(), material.green(), material.blue(), 90, a, b);
            glowBand(out, pose, from, to, 0.30, 1.015, material.red(), material.green(), material.blue(), 170, a, b);
            glowBand(out, pose, from, to, 0.86, 1.025, 245, 250, 255, 235, a, b);
            return;
        }
        // Wider, low-opacity mantle; hot blade face; narrow incandescent edge.
        glowBand(out, pose, from, to, 0.04, 1.10, 255, 47, 2, 80, a, b);
        glowBand(out, pose, from, to, 0.22, 1.01, 255, 127, 12, 180, a, b);
        glowBand(out, pose, from, to, 0.84, 1.015, 255, 244, 188, 250, a, b);
    }

    private static void glowBand(VertexConsumer out, Matrix4f pose, TrailSample from, TrailSample to,
                                   double inner, double outer, int r, int g, int b, int alpha,
                                   float fromFade, float toFade) {
        double fromInner = Mth.lerp(fromFade, outer - 0.03, inner);
        double toInner = Mth.lerp(toFade, outer - 0.03, inner);
        WeaponGlowGeometry.vertex(out, pose, from.base.lerp(from.tip, fromInner), r, g / 2, b,
                Math.round(alpha * fromFade * fromFade * 0.3f));
        WeaponGlowGeometry.vertex(out, pose, from.base.lerp(from.tip, outer), r, g, b,
                Math.round(alpha * fromFade * fromFade));
        WeaponGlowGeometry.vertex(out, pose, to.base.lerp(to.tip, outer), r, g, b,
                Math.round(alpha * toFade * toFade));
        WeaponGlowGeometry.vertex(out, pose, to.base.lerp(to.tip, toInner), r, g / 2, b,
                Math.round(alpha * toFade * toFade * 0.3f));
    }

    static double resampleFraction(int index, int count) {
        return index / (double) count;
    }

    static Vec3 thrustWidth(Vec3 travel, Vec3 towardCamera) {
        Vec3 width = travel.cross(towardCamera);
        if (width.lengthSqr() < 1.0E-8) width = travel.cross(new Vec3(0, 1, 0));
        if (width.lengthSqr() < 1.0E-8) width = new Vec3(1, 0, 0);
        return width.normalize();
    }

    private static float trailCoordinate(
            TrailSample sample,
            TrailProfile profile,
            double now
    ) {
        return calculateTrailCoordinate(
                sample.time,
                profile.lifetimeTicks,
                now
        );
    }

    static int calculateResampleCount(double elapsedTicks) {
        return Mth.clamp(
                (int) Math.floor(elapsedTicks / SAMPLE_INTERVAL_TICKS),
                1,
                MAX_RESAMPLE_STEPS
        );
    }

    static float calculateTrailCoordinate(
            double sampleTime,
            float lifetimeTicks,
            double now
    ) {
        return 1.0f - Mth.clamp(
                (float) ((now - sampleTime) / lifetimeTicks),
                0.0f,
                1.0f
        );
    }

    static int sampleCapacity() {
        return MAX_SAMPLES;
    }

    static double sampleIntervalTicks() {
        return SAMPLE_INTERVAL_TICKS;
    }

    private enum Glow { SLAMMER, DWARF_HAMMER, KUDGEL, LAVA, DAGGER, BONE, SWORD, DRAGON, SHIV, GALAXY, STAR_NEEDLE, INFINITY, INFINITY_NEEDLE, TIDE, TIDE_NEEDLE, GROVE, HOLY, TEMPLAR, MEOW, OBSIDIAN, OSSIFIED, BLOOD, FORGE, CRYSTAL, VENOM, SHADOW, INSECT, NEEDLE, BURGLAR, DWARF_SWORD, DWARF_DAGGER, BONE_SWORD, CLAYMORE, IRON, WIND, PIRATE, SILVER, SPINE, RUST, WOOD, CRESCENT, FALCHION }

    private record TrailProfile(
            float startProgress,
            float endProgress,
            float lifetimeTicks,
            Glow glow
    ) {}

    private record TrailSample(Vec3 base, Vec3 tip, double time) {}

    private static final class TrailState {
        private TrailProfile profile;
        private long actionStartTick;
        private boolean firstPerson;
        private String weaponId;
        private double lastSparkTime;
        private final ArrayDeque<TrailSample> samples = new ArrayDeque<>();

        private TrailState(TrailProfile profile, long actionStartTick, boolean firstPerson, String weaponId) {
            this.profile = profile;
            this.actionStartTick = actionStartTick;
            this.firstPerson = firstPerson;
            this.weaponId = weaponId;
        }
    }
}
