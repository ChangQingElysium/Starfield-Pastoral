package com.stardew.craft.client.weapon.trail;

import com.stardew.craft.client.weapon.WeaponSkillAnimationClient;
import com.stardew.craft.client.weapon.LavaKatanaVisuals;
import com.stardew.craft.client.weapon.MeleeWeaponVisuals;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bridges the final item model transform to the shared trail runtime.
 */
public final class WeaponRenderCaptureContext {
    private static final ThreadLocal<ArrayDeque<Capture>> CAPTURES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final Vector3f BLADE_BASE = new Vector3f(-0.14f, -0.14f, 0.0f);
    private static final Vector3f BLADE_TIP = new Vector3f(0.36f, 0.40f, 0.0f);
    private static final Map<TextureAtlasSprite, BladeAnchors> SPRITE_ANCHORS = new WeakHashMap<>();

    private WeaponRenderCaptureContext() {}

    public static void begin(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {
        CAPTURES.get().addLast(new Capture(entity, stack, displayContext));
    }

    public static void end() {
        ArrayDeque<Capture> captures = CAPTURES.get();
        if (!captures.isEmpty()) {
            captures.removeLast();
        }
        if (captures.isEmpty()) {
            CAPTURES.remove();
        }
    }

    public static void capture(Matrix4f itemTransform, BakedModel model, net.minecraft.client.renderer.MultiBufferSource buffers) {
        ArrayDeque<Capture> captures = CAPTURES.get();
        Capture capture = captures.peekLast();
        if (capture == null
                || !(capture.stack.getItem() instanceof IStardewWeapon weapon)
                || !isMainArmContext(capture)
                || !isHeldContext(capture.displayContext)) {
            return;
        }

        if ("dragontooth_shiv".equals(weapon.getWeaponId())
                && com.stardew.craft.client.weapon.DragontoothShivBreathClientState.isActive(capture.entity)) {
            var mc = Minecraft.getInstance();
            BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
            com.stardew.craft.client.weapon.DragontoothShivVisuals.blade(itemTransform,
                    new Vec3(blade.base.x, blade.base.y, blade.base.z), new Vec3(blade.tip.x, blade.tip.y, blade.tip.z),
                    buffers, mc.level.getGameTime() + com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
        }

        if ("obsidian_edge".equals(weapon.getWeaponId())
                && capture.entity == Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            float charge = com.stardew.craft.client.weapon.ObsidianResonanceClientState.getChargeRatio(Minecraft.getInstance().player);
            if (charge > 0) {
                BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
                com.stardew.craft.client.weapon.MineralEffectGeometry.chargedBlade(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW), itemTransform,
                        new Vec3(blade.base.x,blade.base.y,blade.base.z), new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),charge);
            }
        }

        if ("wooden_blade".equals(weapon.getWeaponId()) && capture.entity==Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()
                && com.stardew.craft.client.weapon.RustWoodVisuals.recentBlessing(capture.entity.getId())) {
            var shelter=capture.entity.getEffect(com.stardew.craft.effect.ModMobEffects.SHELTER.get());
            if(shelter!=null){
                BladeAnchors blade=SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(),WeaponRenderCaptureContext::findAnchors);
                float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
                com.stardew.craft.client.weapon.RustWoodGeometry.shelter(buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),itemTransform,
                        new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),com.stardew.craft.port.PortJava.clamp((shelter.getDuration()-partial)/8,0,1));
            }
        }
        if ("steel_smallsword".equals(weapon.getWeaponId()) && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
            var action=com.stardew.craft.client.weapon.MeleeWeaponVisuals.action(capture.entity,partial);
            if(action!=null){
                boolean guard="light_counter".equals(action.skillId()),blocked="light_counter_counter".equals(action.skillId());
                if(guard||blocked){
                    BladeAnchors blade=SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(),WeaponRenderCaptureContext::findAnchors);
                    float fade=guard?com.stardew.craft.port.PortJava.clamp(action.progress()/.1f,0,1)*com.stardew.craft.port.PortJava.clamp((1-action.progress())/.15f,0,1):com.stardew.craft.port.PortJava.clamp(1-action.progress()/.35f,0,1);
                    com.stardew.craft.client.weapon.GuardSpineGeometry.guard(buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),itemTransform,
                            new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),fade,blocked);
                }
            }
        }
        if ("iron_edge".equals(weapon.getWeaponId()) && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            int phase=com.stardew.craft.client.weapon.GuardSpineVisuals.bladePhase(capture.entity);
            if(phase>=0){
                BladeAnchors blade=SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(),WeaponRenderCaptureContext::findAnchors);
                com.stardew.craft.client.weapon.GuardSpineGeometry.blade(buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),itemTransform,
                        new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),phase,1);
            }
        }
        if ("pirate_sword".equals(weapon.getWeaponId()) && capture.entity == Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.PirateSilverVisuals.ensureLevel();
            var player = Minecraft.getInstance().player;
            float partial = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
            BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
            Vec3 base = new Vec3(blade.base.x, blade.base.y, blade.base.z), tip = new Vec3(blade.tip.x, blade.tip.y, blade.tip.z);
            var out = buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW);
            var fury = player.getEffect(com.stardew.craft.effect.ModMobEffects.FURY.get());
            if (fury != null && com.stardew.craft.client.weapon.PirateSilverVisuals.recentPlunder(player.getId()))
                com.stardew.craft.client.weapon.PirateSilverGeometry.furyBlade(out, itemTransform, base, tip, com.stardew.craft.port.PortJava.clamp((fury.getDuration() - partial) / 8, 0, 1));
            com.stardew.craft.client.weapon.NeedleBurglarGeometry.loot(out, itemTransform, base, tip,
                    com.stardew.craft.client.weapon.PirateSilverVisuals.healAge(partial));
        }

        if ("wind_spire".equals(weapon.getWeaponId()) && capture.entity == Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.IronWindVisuals.ensureLevel();
            float partial = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
            float remaining = com.stardew.craft.client.weapon.WindSpireClientState.getRemainingTicks(Minecraft.getInstance().player) - partial;
            if (remaining > 0) {
                BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
                com.stardew.craft.client.weapon.IronWindGeometry.galeBlade(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW), itemTransform,
                        new Vec3(blade.base.x, blade.base.y, blade.base.z), new Vec3(blade.tip.x, blade.tip.y, blade.tip.z), com.stardew.craft.port.PortJava.clamp(remaining / 8, 0, 1));
            }
        }

        if (("dwarf_sword".equals(weapon.getWeaponId()) || "dwarf_dagger".equals(weapon.getWeaponId()))
                && capture.entity == Minecraft.getInstance().player && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.DwarfWeaponVisuals.ensureLevel();
            var player = Minecraft.getInstance().player;
            boolean dagger = "dwarf_dagger".equals(weapon.getWeaponId());
            float remaining = dagger ? com.stardew.craft.client.weapon.DwarfDaggerRushClientState.getRemainingTicks(player)
                    : com.stardew.craft.client.weapon.DwarfFortressClientState.getRemainingTicks(player);
            float visibility = com.stardew.craft.port.PortJava.clamp(remaining / 8, 0, 1);
            if (!dagger && com.stardew.craft.client.weapon.DwarfWeaponVisuals.guarding(player.getId())
                    && player.hasEffect(com.stardew.craft.effect.ModMobEffects.SHELTER.get())) visibility = Math.max(visibility, .65f);
            if (visibility > 0) {
                BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
                com.stardew.craft.client.weapon.DwarfWeaponGeometry.bladeRune(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW), itemTransform,
                        new Vec3(blade.base.x, blade.base.y, blade.base.z), new Vec3(blade.tip.x, blade.tip.y, blade.tip.z), visibility, dagger);
            }
        }

        if (("iridium_needle".equals(weapon.getWeaponId()) || "burglars_shank".equals(weapon.getWeaponId()))
                && capture.entity == Minecraft.getInstance().player && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.NeedleBurglarVisuals.ensureLevel();
            BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
            float partial = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
            var out = buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW);
            Vec3 base = new Vec3(blade.base.x, blade.base.y, blade.base.z), tip = new Vec3(blade.tip.x, blade.tip.y, blade.tip.z);
            if ("iridium_needle".equals(weapon.getWeaponId())) {
                float remaining = com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.getRemainingTicks(Minecraft.getInstance().player) - partial;
                com.stardew.craft.client.weapon.NeedleBurglarGeometry.needleBlade(out, itemTransform, base, tip,
                        com.stardew.craft.port.PortJava.clamp(remaining / 8, 0, 1), com.stardew.craft.client.weapon.NeedleBurglarVisuals.hitAge(partial));
            } else com.stardew.craft.client.weapon.NeedleBurglarGeometry.loot(out, itemTransform, base, tip,
                    com.stardew.craft.client.weapon.NeedleBurglarVisuals.lootAge(partial));
        }

        if ("insect_head".equals(weapon.getWeaponId()) && capture.entity == Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.ShadowInsectVisuals.ensureLevel();
            var player = Minecraft.getInstance().player;
            if (com.stardew.craft.client.weapon.InsectEyeStanceClientState.isActive(player)) {
                BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
                float partial = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
                float remaining = com.stardew.craft.client.weapon.InsectEyeStanceClientState.getRemainingTicks(player) - partial;
                com.stardew.craft.client.weapon.ShadowInsectGeometry.eyeLights(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW), itemTransform,
                        new Vec3(blade.base.x, blade.base.y, blade.base.z), new Vec3(blade.tip.x, blade.tip.y, blade.tip.z),
                        remaining, com.stardew.craft.client.weapon.InsectEyeStanceClientState.getTotalTicks() - remaining);
            }
        }

        if("crystal_dagger".equals(weapon.getWeaponId()) && capture.entity==Minecraft.getInstance().player
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            com.stardew.craft.client.weapon.CrystalVenomVisuals.ensureLevel();
            BladeAnchors blade=SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(),WeaponRenderCaptureContext::findAnchors);
            float partial=com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
            com.stardew.craft.client.weapon.CrystalVenomGeometry.crystalLayers(
                    buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),itemTransform,
                    new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),
                    com.stardew.craft.client.weapon.CrystalDaggerLayerClientState.getStacks(Minecraft.getInstance().player),
                    com.stardew.craft.client.weapon.CrystalVenomVisuals.burstAge(partial));
        }

        if ("dark_sword".equals(weapon.getWeaponId()) && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            boolean moon = com.stardew.craft.client.weapon.BloodForgeVisuals.active(capture.entity.getId(),true);
            if(moon || com.stardew.craft.client.weapon.BloodForgeVisuals.active(capture.entity.getId(),false)) {
                BladeAnchors blade = SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
                com.stardew.craft.client.weapon.BloodForgeGeometry.blade(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW), itemTransform,
                        new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),moon);
            }
        }

        WeaponSkillAnimPayload action =
                WeaponSkillAnimationClient.getWorldAction(capture.entity.getId());
        var molten = LavaKatanaVisuals.action(capture.entity,
                com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
        var melee = MeleeWeaponVisuals.action(capture.entity,
                com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
        if (com.stardew.craft.combat.skill.handler.HeavyHammerRules.isWeapon(weapon.getWeaponId())
                && com.stardew.craft.Config.ENABLE_WEAPON_SPECIAL_EFFECTS.get()) {
            boolean empowered=com.stardew.craft.client.weapon.HeavyHammerVisuals.empowered(capture.entity.getId());
            if(empowered || melee!=null && com.stardew.craft.client.weapon.animation.HeavyHammerAnimation.supports(melee.skillId())) {
                BladeAnchors blade=SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(),WeaponRenderCaptureContext::findAnchors);
                var mc=Minecraft.getInstance();
                float power=empowered?.85f:(float)Math.sin(Math.PI*melee.progress())*.75f;
                com.stardew.craft.client.weapon.HeavyHammerBurstGeometry.hammerHead(
                        buffers.getBuffer(com.stardew.craft.client.weapon.WeaponEffectRenderTypes.MOLTEN_GLOW),itemTransform,
                        new Vec3(blade.base.x,blade.base.y,blade.base.z),new Vec3(blade.tip.x,blade.tip.y,blade.tip.z),
                        "infinity_gavel".equals(weapon.getWeaponId()),power,
                        mc.level.getGameTime()+com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false));
            }
        }
        if (molten == null && melee == null && (action == null
                || !action.weaponId().equals(weapon.getWeaponId())
                || !WeaponTrailClient.supports(action.skillId()))) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partialTick = com.stardew.craft.port.net.minecraft.client.DeltaTracker.client().getGameTimeDeltaPartialTick(false);
        float progress = molten != null ? molten.progress() : melee != null ? melee.progress() : WeaponSkillAnimationClient.getWorldActionProgress(
                capture.entity.getId(),
                partialTick
        );
        if (progress < 0.0f) {
            return;
        }

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        BladeAnchors anchors = molten == null && melee == null ? new BladeAnchors(BLADE_BASE, BLADE_TIP)
                : SPRITE_ANCHORS.computeIfAbsent(model.getParticleIcon(), WeaponRenderCaptureContext::findAnchors);
        Vec3 base = transform(itemTransform, anchors.base).add(camera);
        Vec3 tip = transform(itemTransform, anchors.tip).add(camera);
        if (melee != null) MeleeWeaponVisuals.chargeTip(capture.entity.getId(), melee, tip);
        WeaponTrailClient.capture(
                capture.entity.getId(),
                molten != null ? molten.skillId() : melee != null ? melee.skillId() : action.skillId(),
                molten != null ? molten.startTick() : melee != null ? melee.startTick() : action.startGameTick(),
                progress,
                base,
                tip,
                minecraft.level.getGameTime() + partialTick,
                capture.displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                        || capture.displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                weapon.getWeaponId()
        );
    }

    /** Built-in weapon sprites run grip-to-tip along the rising diagonal, including forge appearances. */
    private static BladeAnchors findAnchors(TextureAtlasSprite sprite) {
        var contents = sprite.contents();
        Vector3f grip = new Vector3f(BLADE_BASE), tip = new Vector3f(BLADE_TIP);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                if (contents.isTransparent(0, x, y)) continue;
                float px = (x + 0.5f) / contents.width() - 0.5f;
                float py = 0.5f - (y + 0.5f) / contents.height();
                if (px + py < min) { min = px + py; grip.set(px, py, 0); }
                if (px + py > max) { max = px + py; tip.set(px, py, 0); }
            }
        }
        return new BladeAnchors(new Vector3f(grip).lerp(tip, 0.28f), tip);
    }

    private record BladeAnchors(Vector3f base, Vector3f tip) {}

    private static boolean isMainArmContext(Capture capture) {
        boolean right = capture.displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || capture.displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return right == (capture.entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
    }

    private static boolean isHeldContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    private static Vec3 transform(Matrix4f matrix, Vector3f point) {
        Vector3f transformed = new Vector3f(point);
        matrix.transformPosition(transformed);
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private record Capture(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext
    ) {}
}
