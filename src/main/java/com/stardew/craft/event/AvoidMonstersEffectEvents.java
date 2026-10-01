package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.effect.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class AvoidMonstersEffectEvents {
    private AvoidMonstersEffectEvents() {
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!isHostileMob(event.getEntity())) {
            return;
        }

        // PORT(1.20.1): Forge names NeoForge's getNewAboutToBeSetTarget() getNewTarget().
        LivingEntity target = event.getNewTarget();
        if (target instanceof Player player && player.hasEffect(ModMobEffects.AVOID_MONSTERS.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !isHostileMob(mob)) {
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target instanceof Player player && player.hasEffect(ModMobEffects.AVOID_MONSTERS.get())) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    private static boolean isHostileMob(LivingEntity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }
}