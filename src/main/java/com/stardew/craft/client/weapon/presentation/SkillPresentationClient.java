package com.stardew.craft.client.weapon.presentation;

import com.stardew.craft.client.weapon.WeaponSkillAnimationClient;
import com.stardew.craft.client.weapon.trail.WeaponTrailClient;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import com.stardew.craft.port.net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Registry and lifecycle owner for world-space skill presentations.
 *
 * <p>Only presentation code lives here. Damage, targeting, healing and cooldowns
 * remain server authoritative in the combat skill runtime.</p>
 */
public final class SkillPresentationClient {
    private static final Map<String, Function<SkillPresentationContext, SkillPresentation>> FACTORIES = Map.of(
            "crescent_slash", CrescentSlashPresentation::new
    );
    private static final List<SkillPresentation> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private SkillPresentationClient() {}

    /**
     * @return {@code true} when the skill has migrated to the presentation runtime.
     */
    public static boolean start(
            WeaponSkillAnimPayload payload,
            long playbackStartTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        Function<SkillPresentationContext, SkillPresentation> factory = FACTORIES.get(payload.skillId());
        if (factory == null) {
            return false;
        }

        if (activeLevel != minecraft.level) {
            ACTIVE.clear();
            activeLevel = minecraft.level;
        }
        SkillPresentationContext context = new SkillPresentationContext(
                payload,
                minecraft.level,
                playbackStartTick
        );
        ACTIVE.removeIf(presentation ->
                presentation.casterEntityId() == payload.casterEntityId()
                        && presentation.skillId().equals(payload.skillId()));
        ACTIVE.add(factory.apply(context));
        return true;
    }

    public static void onImpact(WeaponSkillImpactPayload payload) {
        for (SkillPresentation presentation : ACTIVE) {
            if (presentation.casterEntityId() == payload.casterEntityId()
                    && presentation.skillId().equals(payload.skillId())) {
                presentation.onImpact(payload);
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            activeLevel = null;
            return;
        }
        if (activeLevel != minecraft.level) {
            ACTIVE.clear();
            activeLevel = minecraft.level;
            return;
        }

        Iterator<SkillPresentation> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            SkillPresentation presentation = iterator.next();
            presentation.tick();
            if (presentation.isComplete()) {
                iterator.remove();
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        for (SkillPresentation presentation : ACTIVE) {
            presentation.render(event);
        }
        WeaponTrailClient.render(event);
    }
}
