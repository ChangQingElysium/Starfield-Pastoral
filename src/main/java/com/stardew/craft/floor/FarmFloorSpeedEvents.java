package com.stardew.craft.floor;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.port.PortAttributeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Flooring.doCollisionAction: walking on flooring or roads on the farm (or Ginger Island west)
 * gives temporarySpeedBuff = FarmSpeedBuff; all 13 floor entries use the default 0.1 (+10% speed).
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class FarmFloorSpeedEvents {
    private static final ResourceLocation SPEED_ID = new ResourceLocation(StardewCraft.MODID, "farm_floor_speed");
    private static final double FARM_SPEED_BUFF = 0.1;

    private FarmFloorSpeedEvents() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean boosted = player.onGround() && onFarmFloor(player);
        boolean has = PortAttributeModifiers.getModifier(speed, SPEED_ID) != null;
        if (boosted && !has) {
            speed.addTransientModifier(PortAttributeModifiers.create(SPEED_ID, FARM_SPEED_BUFF, AttributeModifier.Operation.MULTIPLY_BASE));
        } else if (!boosted && has) {
            PortAttributeModifiers.removeModifier(speed, SPEED_ID);
        }
    }

    private static boolean onFarmFloor(ServerPlayer player) {
        var level = player.serverLevel();
        var below = player.getOnPos();
        if (SurfaceFloorData.get(level).at(below) == null) return false;
        boolean farm = com.stardew.craft.farm.FarmInstanceAllocator.isInFarmInstanceRegion(below)
                && com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(level, below);
        return farm || com.stardew.craft.api.v1.world.StardewLocations.hierarchy(level.dimension().location(), below)
                .stream().anyMatch(com.stardew.craft.mining.IslandStoneRewards::isWest);
    }
}
